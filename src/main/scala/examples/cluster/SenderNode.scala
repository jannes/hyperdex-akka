package examples.cluster

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import examples.cluster.ReceiverNode.PingMessage

import scala.concurrent.duration.{Duration, SECONDS}

object SenderNode {

  sealed trait Event
  sealed trait AcceptedMessage extends Event
  sealed trait PrivateEvent extends Event

  final case object PingAcknowledgement
      extends AcceptedMessage
      with CBorSerializable
  private final case object Tick extends PrivateEvent
  private final case class AllReceivers(
      receivers: Set[ActorRef[ReceiverNode.PingMessage]])
      extends PrivateEvent

  def getReceiverAdapter(
      ctx: ActorContext[Event]): ActorRef[Receptionist.Listing] = {
    ctx.messageAdapter[Receptionist.Listing] {
      case ReceiverNode.receiverNodeKey.Listing(receivers) =>
        AllReceivers(receivers)
    }
  }

  def apply(): Behavior[Event] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      ctx.log.info("asking receptionist for receiver nodes")

      /**
        * first call to find always gives back empty set, why??
        */
      ctx.system.receptionist ! Receptionist.find(ReceiverNode.receiverNodeKey,
                                                  getReceiverAdapter(ctx))
      timers.startTimerWithFixedDelay(Tick, Tick, Duration(2, SECONDS))
      running(ctx, Set.empty)
    }
  }

  private def running(
      ctx: ActorContext[Event],
      receivers: Set[ActorRef[PingMessage]]): Behavior[Event] = {
    Behaviors.receiveMessage {
      case AllReceivers(newReceivers) =>
        ctx.log.info(
          s"received set of receivers from receptionist, size: ${newReceivers.size}")
        running(ctx, newReceivers)
      case Tick =>
        // ask for updated receivers
        ctx.system.receptionist ! Receptionist.find(
          ReceiverNode.receiverNodeKey,
          getReceiverAdapter(ctx))
        ctx.log.info(s"got a tick, sending ping to ${receivers.size} receivers")
        receivers.foreach { r =>
          r ! PingMessage(ctx.self)
        }
        Behaviors.same
      case PingAcknowledgement =>
        ctx.log.info("ping was acknowledged")
        Behaviors.same
    }
  }

}
