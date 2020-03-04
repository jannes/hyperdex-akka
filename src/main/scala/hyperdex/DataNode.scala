package hyperdex

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import examples.cluster.SenderNode

object DataNode {

  val receiverNodeKey = ServiceKey[DataNode.PingMessage]("Receiver")
  final case class PingMessage(from: ActorRef[SenderNode.AcceptedMessage])
      extends CBorSerializable

  def apply(): Behavior[PingMessage] = Behaviors.setup { ctx =>
    // make receiver node discoverable for sender
    ctx.log.info("registering with receptionist")
    println("registering")
    ctx.system.receptionist ! Receptionist.register(receiverNodeKey, ctx.self)

    // define ping response behavior
    Behaviors.receive { (context, message) =>
      context.log.info(s"received message from ${message.from}")
      message.from ! SenderNode.PingAcknowledgement
      Behaviors.same
    }
  }
}
