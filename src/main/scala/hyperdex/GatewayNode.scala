package hyperdex

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import hyperdex.API.{AttributeMapping, Key}
import hyperdex.DataNode.AcceptedMessage

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import Main.NUM_DATANODES

object GatewayNode {

  /** messages **/
  sealed trait GatewayMessage extends CBorSerializable

  /** user queries **/
  sealed trait Query extends GatewayMessage
  final case class Create(from: ActorRef[CreateResult], table: String, attributes: Seq[String]) extends Query
  sealed trait TableQuery extends Query
  final case class Lookup(from: ActorRef[LookupResult], table: String, key: Int) extends TableQuery
  final case class Search(from: ActorRef[SearchResult], table: String, mapping: Map[String, Int]) extends TableQuery
  final case class Put(from: ActorRef[PutResult], table: String, key: Int, mapping: Map[String, Int]) extends TableQuery

  /** responses from data nodes **/
  sealed trait DataNodeResponse extends GatewayMessage
  final case class LookupResult(value: Option[AttributeMapping]) extends DataNodeResponse
  // in order for cbor/json serialization to work a map can only have strings as keys
  final case class SearchResult(objects: Map[String, AttributeMapping]) extends DataNodeResponse
  final case class PutResult(succeeded: Boolean) extends DataNodeResponse
  final case class CreateResult(succeeded: Boolean) extends DataNodeResponse

  /** configuration messages **/
  sealed trait RuntimeMessage extends GatewayMessage
  // to discover receivers
  private final case class AllReceivers(receivers: Set[ActorRef[AcceptedMessage]]) extends RuntimeMessage

  def actorBehavior(): Behavior[GatewayMessage] = {
    Behaviors.setup { ctx =>
      ctx.log.info("Subscribing to receptionist for receiver nodes...")
      ctx.system.receptionist ! Receptionist.subscribe(DataNode.receiverNodeKey, getReceiverAdapter(ctx))
      starting(ctx, Seq[ActorRef[AcceptedMessage]]())
    }
  }

  private def getReceiverAdapter(ctx: ActorContext[GatewayMessage]): ActorRef[Receptionist.Listing] = {
    ctx.messageAdapter[Receptionist.Listing] {
      case DataNode.receiverNodeKey.Listing(receivers) =>
        AllReceivers(receivers)
    }
  }

//  def initTestHyperSpace(ctx: ActorContext[GatewayMessage], receivers: Seq[ActorRef[DataNode.AcceptedMessage]]) = {
//    var createRef = ActorRef[CreateResult]
//    create(createRef, receivers, "TestTable", Seq("attribute1", "attribute2", "attribute3"))
//    val r = scala.util.Random
//
//    for (a <- 0 until 1000) {
//      var mapping: AttributeMapping =
//        Map("attribute1" -> r.nextInt(10), "attribute2" -> r.nextInt(400), "attribute3" -> r.nextInt(100))
//      var testobj = Map[Key, AttributeMapping](a -> mapping)
//      var putRef = ActorRef[PutResult]
//      put(putRef, receivers, mapping, a, "TestTable")
//    }
//  }

//  private def lookup(
//                      from: ActorRef[LookupResult],
//                      receivers: Seq[ActorRef[DataNode.AcceptedMessage]],
//                      table: String,
//                      key: Key
//                    ) = {
//    var dataNodeIndex = key % NUM_DATANODES
//    dataNodeMapping(dataNodeIndex) ! Lookup(from, table, key)
//
//  }

//  private def put(
//    from: ActorRef[PutResult],
//    receivers: Seq[ActorRef[DataNode.AcceptedMessage]],
//    mapping: AttributeMapping,
//    key: Key,
//    table: String,
//    hyperspaces: Map[String, HyperSpace]
//  ): Unit = {
//    var hyperSpace = hyperspaces(table)
//    for (attribute <- mapping) {
//      var hashIndex = hyperSpace.hashValue(attribute._2);
//      var dataNodeIndex = hyperSpace.obtainDataNodeIndex(hashIndex)
//      receivers(dataNodeIndex) ! PutAttribute(from, table, key, hashIndex, attribute._1)
//    }
//
//    var dataNodeIndex = key % NUM_DATANODES
//    dataNodeMapping(dataNodeIndex) ! Put(from, table, key, mapping)
//
//  }
//
//  private def create(
//    from: ActorRef[CreateResult],
//    receivers: Seq[ActorRef[DataNode.AcceptedMessage]],
//    name: String,
//    attributes: Seq[String]
//  ) = {
//    var newHyperSpace = new HyperSpace(attributes, NUM_DATANODES, attributes.size + 1)
//    val bucketSize = 100 / NUM_DATANODES
//    hyperSpaceMapping += (name -> newHyperSpace)
//    var partOfHyperSpace = 0
//    for (dataNode <- receivers) {
//
//      dataNodeMapping += (partOfHyperSpace -> dataNode)
//      partOfHyperSpace += 1
//      dataNode ! Create(from, name, attributes)
//    }
//
//  }

  /**
    * stage of resolving all data nodes
    * @param ctx
    * @param dataNodes
    * @return
    */
  private def starting(
    ctx: ActorContext[GatewayMessage],
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]],
  ): Behavior[GatewayMessage] = {

    Behaviors
      .receiveMessage {
        case AllReceivers(newReceivers) =>
          if (newReceivers.size < NUM_DATANODES) {
            ctx.log.info(s"Not enough receivers, we have ${newReceivers.size} out of ${NUM_DATANODES}.")
            Behaviors.same
          } else {
            ctx.log.info(s"We have ${newReceivers.size} receivers, so lets start running.")
            running(ctx, Map(), newReceivers.toSeq)
          }
        case _ =>
          ctx.log.info(s"The gateway first needs enough receivers.")
          Behaviors.same
      }
  }

  /**
    * the runtime behavior after all setup has completed
    * @param ctx
    * @param dataNodes
    * @return
    */
  private def running(
    ctx: ActorContext[GatewayMessage],
    hyperspaces: Map[String, HyperSpace],
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  ): Behavior[GatewayMessage] = {

    Behaviors
      .receiveMessage {

        case Create(from, table, attributes) =>
          val newHyperspace = new HyperSpace(attributes, NUM_DATANODES, 2)
          dataNodes.foreach(dataNode => dataNode ! Create(ctx.self, table, attributes))
          // TODO: decide when a create result is really successful (could do parallel ask)
          from ! CreateResult(true)
          running(ctx, hyperspaces.+((table, newHyperspace)), dataNodes)
        case tableQuery: TableQuery =>
          handleTableQuery(tableQuery, ctx, hyperspaces, dataNodes)
          Behaviors.same
        // shouldn't receive any except create result which is ignored
        case _: DataNodeResponse =>
          Behaviors.same
        // should not happen (if it does, system is broken)
        case _: AllReceivers =>
          Behaviors.same
      }

  }

  // TODO: route queries to hyperspace object
  private def handleTableQuery(
    query: TableQuery,
    ctx: ActorContext[GatewayMessage],
    hyperspaces: Map[String, HyperSpace],
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  ): Unit = {

    // implicits needed for ask pattern
    implicit val system: ActorSystem[Nothing] = ctx.system
    implicit val timeout: Timeout = 5.seconds
    implicit val ec: ExecutionContext = ctx.executionContext

    // TODO: error when table does not exist
    query match {
      case lookup @ Lookup(from, table, key) =>
        hyperspaces.get(table) match {
          case Some(hyperspace) =>
            handleValidLookup(lookup, ctx, hyperspace, dataNodes)
          case None =>
            from ! LookupResult(None)
        }
      case search @ Search(from, table, mapping) =>
        hyperspaces.get(table) match {
          case Some(hyperspace) =>
            handleValidSearch(search, ctx, hyperspace, dataNodes)
          case None =>
            from ! SearchResult(Map.empty)
        }
      case put @ Put(from, table, key, mapping) =>
        hyperspaces.get(table) match {
          case Some(hyperspace) =>
            handleValidPut(put, ctx, hyperspace, dataNodes)
          case None =>
            from ! PutResult(false)
        }
    }
  }

  def handleValidLookup(
    lookup: Lookup,
    ctx: ActorContext[GatewayMessage],
    hyperspace: HyperSpace,
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  )(implicit as: ActorSystem[Nothing], timeout: Timeout, ec: ExecutionContext): Unit = {

    val key = lookup.key
    val from = lookup.from
    val table = lookup.table

    val answers: Seq[Future[LookupResult]] = hyperspace
      .getResponsibleNodeIds(key)
      .map(dataNodes(_))
      .map(dn => {
        dn ? [LookupResult](ref => Lookup(ref, table, key))
      })
      .toSeq

    val answersSingleSuccessFuture: Future[Seq[Try[LookupResult]]] = Future.sequence(
      answers.map(f => f.map(Success(_)).recover(x => Failure(x)))
    )

    val processedFuture = answersSingleSuccessFuture.map(seq => {
      var nonExceptionLookups = mutable.Set.empty[LookupResult]
      for (tried <- seq) {
        tried match {
          case Success(value) => nonExceptionLookups.add(value)
          case Failure(exception) =>
            ctx.log.error(s"exception occurred when asking for lookup result: ${exception.getMessage}")
        }
      }
      // if exactly one -> return it
      if (nonExceptionLookups.size == 1) {
        nonExceptionLookups.head
      }
      // if no non-exceptional response -> internal server error
      else if (nonExceptionLookups.isEmpty) {
        // TODO: absolutely need error here
        ctx.log.error("did not got any answers for lookup")
        LookupResult(None)
      }
      // if more than one -> filter Nones and
      else {
        val filtered = nonExceptionLookups.toSet.filter(lr => lr.value.isDefined)
        //// if one -> return it
        if (filtered.size == 1) {
          filtered.head
        }
        //// if more than one -> inconsistency
        else {
          // TODO: need error here
          ctx.log.error("got inconsistent answers for a lookup")
          LookupResult(None)
        }
      }
    })

    // send processed result back to gateway server (on completion of future)
    processedFuture.foreach(lr => from ! lr)
  }

  // TODO
  def handleValidPut(
    put: Put,
    ctx: ActorContext[GatewayMessage],
    hyperspace: HyperSpace,
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  )(implicit as: ActorSystem[Nothing], timeout: Timeout, ec: ExecutionContext): Unit = ???

  def handleValidSearch(
    search: Search,
    ctx: ActorContext[GatewayMessage],
    hyperspace: HyperSpace,
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  )(implicit as: ActorSystem[Nothing], timeout: Timeout, ec: ExecutionContext): Unit = ???

}
