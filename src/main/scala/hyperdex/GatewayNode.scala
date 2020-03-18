package hyperdex

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import hyperdex.API.AttributeMapping
import hyperdex.DataNode.AcceptedMessage

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object GatewayNode {

  val NUM_DATANODES = 1

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
      ctx.log.info("subscribe to receptionist for receiver nodes")
      ctx.system.receptionist ! Receptionist.subscribe(DataNode.receiverNodeKey, getReceiverAdapter(ctx))
      starting(ctx, Set.empty)
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
    dataNodes: Set[ActorRef[DataNode.AcceptedMessage]],
  ): Behavior[GatewayMessage] = {

    Behaviors
      .receiveMessage {
        case AllReceivers(newReceivers) =>
          if (newReceivers.size < NUM_DATANODES) {
            ctx.log.info(s"Not enough receivers, we have ${newReceivers.size} out of ${NUM_DATANODES}.")
            Behaviors.same
          } else {
            ctx.log.info(s"We have ${newReceivers.size} receivers, so lets start running.")
            running(ctx, Map.empty, newReceivers.toSeq)
          }
        case _ =>
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
    implicit val system = ctx.system
    implicit val timeout: Timeout = 5.seconds
    implicit val ec: ExecutionContext = ctx.executionContext

    def handleValidLookup(lookup: Lookup, hyperspace: HyperSpace) = {
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
    def handleValidPut(put: Put, hyperSpace: HyperSpace): Unit = ???
    def handleValidSearch(search: Search, hyperSpace: HyperSpace): Unit = ???

    // TODO: error when table does not exist
    query match {
      case lookup @ Lookup(from, table, key) =>
        hyperspaces.get(table) match {
          case Some(hyperspace) =>
            handleValidLookup(lookup, hyperspace)
          case None =>
            from ! LookupResult(None)
        }
      case search @ Search(from, table, mapping) =>
        hyperspaces.get(table) match {
          case Some(hyperspace) =>
            handleValidSearch(search, hyperspace)
          case None =>
            from ! SearchResult(Map.empty)
        }
      case put @ Put(from, table, key, mapping) =>
        hyperspaces.get(table) match {
          case Some(hyperspace) =>
            handleValidPut(put, hyperspace)
          case None =>
            from ! PutResult(false)
        }
    }
  }
}
