package hyperdex

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors

object DataNode {

  val receiverNodeKey = ServiceKey[DataNode.AcceptedMessage]("Receiver")

  type AcceptedMessage = GatewayNode.Query

  def apply(): Behavior[AcceptedMessage] = Behaviors.setup { ctx =>
    // make receiver node discoverable for sender
    ctx.log.info("registering with receptionist")
    ctx.system.receptionist ! Receptionist.register(receiverNodeKey, ctx.self)

    // define ping response behavior
    Behaviors.receive[AcceptedMessage] {
      case (context, message) =>
        message match {
          case GatewayNode.Lookup(from, table, key) => {
            context.log.info(s"received LookupMessage for key: $key from: $from")
            val dummyResult = Some(Map("key" -> key, "attr1" -> 0))
            from ! GatewayNode.LookupResult(dummyResult)
          }
          case GatewayNode.Search(from, table, mapping) => {}
          case GatewayNode.Put(from, table, mapping)    => {}
        }

        Behaviors.same
    }
  }
}
