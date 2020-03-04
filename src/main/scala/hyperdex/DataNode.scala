package hyperdex

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object DataNode {

  val rng = util.Random
  val receiverNodeKey = ServiceKey[DataNode.AcceptedMessage]("Receiver" + rng.nextInt())

  sealed trait AcceptedMessage extends CBorSerializable;
  final case class LookupMessage(from: ActorRef[GatewayNode.LookupResult], key: String) extends AcceptedMessage

  def apply(): Behavior[AcceptedMessage] = Behaviors.setup { ctx =>
    // make receiver node discoverable for sender
    ctx.log.info("registering with receptionist")
    println("registering")
    ctx.system.receptionist ! Receptionist.register(receiverNodeKey, ctx.self)

    // define ping response behavior
    Behaviors.receive[AcceptedMessage] {
      case (context, message) =>
        message match {
          case LookupMessage(from, key) => {
            context.log.info(s"received LookupMessage for key: $key from: $from")
            from ! GatewayNode.LookupResult(ctx.self, "dummyvalue")
          }
          case _ => Behaviors.same
        }

        Behaviors.same
    }
  }
}
