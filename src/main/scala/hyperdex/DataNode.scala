package hyperdex

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors

object DataNode {

  val receiverNodeKey = ServiceKey[DataNode.AcceptedMessage]("Receiver")

  type AcceptedMessage = GatewayNode.Query
  type Table = Map[Int, API.AttributeMapping]

  def apply(): Behavior[AcceptedMessage] = Behaviors.setup { ctx =>
    // make receiver node discoverable for sender
    ctx.log.info("registering with receptionist")
    println("registering")
    ctx.system.receptionist ! Receptionist.register(receiverNodeKey, ctx.self)
    running(Map("exampleTable" -> Map.empty))
  }

  def running(tables: Map[String, Table]): Behavior[AcceptedMessage] = {
    Behaviors.receive[AcceptedMessage] {
      case (context, message) =>
        message match {
          case GatewayNode.Lookup(from, table, key) => {
            context.log.info(s"received LookupMessage for key: $key from: $from")
            val optResult = tables
              .get(table)
              .flatMap(_.get(key))
            from ! GatewayNode.LookupResult(optResult)
            Behaviors.same
          }
          case GatewayNode.Put(from, tableName, key, mapping) => {
            context.log.info(s"received put from ${from}")
            tables.get(tableName) match {
              case Some(targetTable) => {
                // TODO: does not guarantee consistency in case of failure
                from ! GatewayNode.PutResult(true)
                val updatedTable = targetTable.+((key, mapping))
                running(tables.+((tableName, updatedTable)))
              }
              case None => {
                from ! GatewayNode.PutResult(false)
                Behaviors.same
              }
            }
          }
          case GatewayNode.Search(from, table, mapping) => {
            context.log.info(s"received search from ${from}")
          }
        }

        Behaviors.same
    }
  }
}
