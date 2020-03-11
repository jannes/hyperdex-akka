package hyperdex

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import hyperdex.API.AttributeMapping
import hyperdex.DataNode.AcceptedMessage

object GatewayNode {

  /** messages **/
  sealed trait GatewayMessage extends CBorSerializable

  /** user queries **/
  sealed trait Query extends GatewayMessage
  final case class Lookup(from: ActorRef[LookupResult], table: String, key: Int) extends Query
  final case class Search(from: ActorRef[SearchResult], table: String, mapping: Map[String, Int]) extends Query
  final case class Put(from: ActorRef[PutResult], table: String, mapping: Map[String, Int]) extends Query

  /** responses from data nodes **/
  sealed trait DataNodeResponse extends GatewayMessage
  final case class LookupResult(value: Option[AttributeMapping]) extends DataNodeResponse
  final case class SearchResult(objects: Set[AttributeMapping]) extends DataNodeResponse
  final case class PutResult(succeeded: Boolean) extends DataNodeResponse

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

  // TODO: complete starting stage once required number of data nodes is online
  /**
    * stage of resolving all data nodes
    * @param ctx
    * @param receivers
    * @return
    */
  private def starting(
    ctx: ActorContext[GatewayMessage],
    receivers: Set[ActorRef[DataNode.AcceptedMessage]],
  ): Behavior[GatewayMessage] = {

    Behaviors
      .receiveMessage {
        case AllReceivers(newReceivers) =>
          ctx.log.info(s"updating receivers, new size: ${newReceivers.size}")
          running(ctx, newReceivers)
        case _ =>
          Behaviors.same
      }
  }

  /**
    * the runtime behavior after all setup has completed
    * @param ctx
    * @param receivers
    * @return
    */
  private def running(
    ctx: ActorContext[GatewayMessage],
//    hyperspaces: Map[String, HyperSpace],
    receivers: Set[ActorRef[DataNode.AcceptedMessage]],
  ): Behavior[GatewayMessage] = {

    Behaviors
      .receiveMessage {
        case query: Query =>
          // get the right hyperspace for table
          // handle query through hyperspace
          handleQuery(query)
          Behaviors.same
        case _: DataNodeResponse =>
          Behaviors.same
        case _: AllReceivers =>
          Behaviors.same
      }

  }

  // TODO: route queries to hyperspace object
  private def handleQuery(query: GatewayNode.Query): Unit = {
    query match {
      case Lookup(from, table, key) => {
        // get Future from Hyperspace
        // call onComplete and send value back to requester
        from ! LookupResult(Some(Map("key" -> 1, "attr1" -> 2)))
      }
      case Search(from, table, mapping) => {}
      case Put(from, table, mapping)    => {}
    }
  }
}
