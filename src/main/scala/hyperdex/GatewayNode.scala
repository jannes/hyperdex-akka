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

  def actorBehavior(numDataNodes: Int): Behavior[GatewayMessage] = {
    Behaviors.setup { ctx =>
      ctx.log.info("Subscribing to receptionist for receiver nodes...")
      ctx.system.receptionist ! Receptionist.subscribe(DataNode.receiverNodeKey, getReceiverAdapter(ctx))
      starting(ctx, numDataNodes, Set.empty)
    }
  }

  private def getReceiverAdapter(ctx: ActorContext[GatewayMessage]): ActorRef[Receptionist.Listing] = {
    ctx.messageAdapter[Receptionist.Listing] {
      case DataNode.receiverNodeKey.Listing(receivers) =>
        AllReceivers(receivers)
    }
  }

  /**
    * stage of resolving all data nodes
    * @param ctx
    * @param dataNodes
    * @return
    */
  private def starting(
    ctx: ActorContext[GatewayMessage],
    requiredAmountDataNodes: Int,
    dataNodes: Set[ActorRef[DataNode.AcceptedMessage]]
  ): Behavior[GatewayMessage] = {

    Behaviors
      .receiveMessage {
        case AllReceivers(newReceivers) =>
          if (newReceivers.size < requiredAmountDataNodes) {
            ctx.log.info(s"Not enough receivers, we have ${newReceivers.size} out of $requiredAmountDataNodes.")
            starting(ctx, requiredAmountDataNodes, newReceivers)
          } else {
            ctx.log.info(s"We have ${newReceivers.size} receivers, so lets start running.")
            // datanodes receive random position in sequence, so random IDs from 0..requiredAmountDataNodes-1
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
          val newHyperspace = new HyperSpace(attributes, dataNodes.size, 2)
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

  // TODO: some minimal error handling instead of using null/empty data
  /**
    * process a put/get/search by routing to the correct datanodes
    * and potentially merging results
    * @param query
    * @param ctx
    * @param hyperspaces
    * @param dataNodes
    */
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
            // if mapping contains invalid attributes
            // TODO: return actual invalid attribute error
            if (hyperspace.attributes.toSet.union(mapping.keys.toSet) != hyperspace.attributes.toSet)
              from ! SearchResult(Map.empty)
            else
              handleValidSearch(search, ctx, hyperspace, dataNodes)
          case None =>
            from ! SearchResult(Map.empty)
        }
      case put @ Put(from, table, key, mapping) =>
        hyperspaces.get(table) match {
          case Some(hyperspace) =>
            // if mapping contains invalid attributes
            // TODO: return actual invalid attribute error
            if (hyperspace.attributes.toSet != mapping.keys.toSet)
              from ! PutResult(false)
            else
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

    val responsibleNode = dataNodes(hyperspace.getResponsibleNodeId(lookup.key))
    // let the single responsible datanode reply to frontend
    responsibleNode ! lookup
  }

  def handleValidPut(
    put: Put,
    ctx: ActorContext[GatewayMessage],
    hyperspace: HyperSpace,
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  )(implicit as: ActorSystem[Nothing], timeout: Timeout, ec: ExecutionContext): Unit = {

    val answers: Seq[Future[PutResult]] = hyperspace
      .getResponsibleNodeIds(put.key, put.mapping)
      .map(dataNodes(_))
      .map(dn => {
        dn ? [PutResult](ref => Put(ref, put.table, put.key, put.mapping))
      })
      .toSeq

    val answersSingleSuccessFuture: Future[Seq[Try[PutResult]]] = Future.sequence(
      answers.map(f => f.map(Success(_)).recover({ case x: Throwable => Failure(x) }))
    )

    val processedFuture: Future[PutResult] = answersSingleSuccessFuture.map(seq => {
      var nonExceptionPutResults = mutable.Set.empty[PutResult]
      for (tried <- seq) {
        tried match {
          case Success(value) => nonExceptionPutResults.add(value)
          case Failure(exception) =>
            ctx.log.error(s"exception occurred when asking for put result: ${exception.getMessage}")
        }
      }
      val allNonExceptionsSuccessful = nonExceptionPutResults.toSet
        .map((r: PutResult) => r.succeeded)
        .fold(true)(_ && _)

      if (allNonExceptionsSuccessful && answers.size == nonExceptionPutResults.size)
        PutResult(true)
      else
        PutResult(false)
    })

    // send processed result back to gateway server (on completion of future)
    processedFuture.foreach(pr => put.from ! pr)

  }

  def handleValidSearch(
    search: Search,
    ctx: ActorContext[GatewayMessage],
    hyperspace: HyperSpace,
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  )(implicit as: ActorSystem[Nothing], timeout: Timeout, ec: ExecutionContext): Unit = {

    val answers: Seq[Future[SearchResult]] = hyperspace
      .getResponsibleNodeIds(search.mapping)
      .map(dataNodes(_))
      .map(dn => {
        dn ? [SearchResult](ref => Search(ref, search.table, search.mapping))
      })
      .toSeq

    val answersSingleSuccessFuture: Future[Seq[Try[SearchResult]]] = Future.sequence(
      answers.map(f => {
        f.map(Success(_)).recover({ case x: Throwable => Failure(x) })
      })
    )

    val processedFuture: Future[SearchResult] = answersSingleSuccessFuture.map(seq => {
      var nonExceptionSearchResults = mutable.Set.empty[SearchResult]
      for (tried <- seq) {
        tried match {
          case Success(value) => nonExceptionSearchResults.add(value)
          case Failure(exception) =>
            ctx.log.error(s"exception occurred when asking for lookup result: ${exception.getMessage}")
        }
      }
      val mergedMatches = nonExceptionSearchResults.toSet
        .map((sr: SearchResult) => sr.objects.toSet[(String, AttributeMapping)])
        .fold(Set.empty)(_.union(_))
        .toMap
      SearchResult(mergedMatches)
    })

    // send processed result back to gateway server (on completion of future)
    processedFuture.foreach(sr => search.from ! sr)
  }

}
