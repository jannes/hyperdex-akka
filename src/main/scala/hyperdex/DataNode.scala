package hyperdex

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import hyperdex.API.{Attribute, AttributeMapping, Key}

object DataNode {

  val receiverNodeKey: ServiceKey[AcceptedMessage] = ServiceKey("Receiver")

  type AcceptedMessage = GatewayNode.Query
  type AttributeNames = Set[String]
  type TableData = Map[Key, AttributeMapping]
  type Table = (AttributeNames, TableData)

  val exampleTable: Table = (Set("at1", "at2"), Map.empty)
  val tables: Map[String,Table] = Map("test"-> exampleTable)

  def apply(hyperSpaceNode: HyperSpaceNode): Behavior[AcceptedMessage] = Behaviors.setup { ctx =>
    // make receiver node discoverable for sender
    ctx.log.info("registering with receptionist")
    ctx.system.receptionist ! Receptionist.register(receiverNodeKey, ctx.self)
    running(hyperSpaceNode)
  }

  /**
    * behavior of a data node in operation
    * NOTE: query responses are send before data is "committed",
    * in case of failure right after replying a client gets a untrue response
    *
    * @param tables
    * @return
    */
  def running(hyperSpaceNode: HyperSpaceNode): Behavior[AcceptedMessage] = {
    Behaviors.receive[AcceptedMessage] {
      case (context, message) =>
        message match {
          case GatewayNode.Lookup(from, table, key) => {
            context.log.debug(s"received LookupMessage for key: $key from: $from")
            val optResult = hyperSpaceNode.objects.get(key)
            context.log.debug(s"found object: $optResult")
            from ! GatewayNode.LookupResult(optResult)
            Behaviors.same
          }
          case GatewayNode.Put(from, tableName, key, mapping) => {
            context.log.debug(s"received put from ${from}")
            tables.get(tableName) match {
              case Some(targetTable) => {
                val attributes = targetTable._1
                val data = targetTable._2
                val givenAttributes = mapping.keys.toSet
                if (givenAttributes != attributes) {
                  from ! GatewayNode.PutResult(false)
                  Behaviors.same
                } else {
                  from ! GatewayNode.PutResult(true)
                  val updatedData = data.+((key, mapping))
                  val updatedTable = (attributes, updatedData)
                  running(hyperSpaceNode)
                }
              }
              case None => {
                from ! GatewayNode.PutResult(false)
                Behaviors.same
              }
            }
          }
          case GatewayNode.Search(from, tableName, mapping) => {
            context.log.debug(s"received search from ${from}")
            tables.get(tableName) match {
              case Some(targetTable) => {
                val attributes = targetTable._1
                val data = targetTable._2
                val givenAttributes = mapping.keys.toSet
                if (givenAttributes.diff(attributes).nonEmpty) {
                  context.log.debug(s"some of the given attributes do not exist in table")
                  // TODO: report error instead of empty set
                  from ! GatewayNode.SearchResult(Map.empty)
                } else {
                  val searchResult = search(data, mapping)
                  context.log.debug(s"matching objects keys: ${searchResult}")
                  from ! GatewayNode.SearchResult(searchResult)
                }
                Behaviors.same
              }
              case None => {
                context.log.debug(s"table $tableName does not exist")
                // TODO: report error instead of empty set
                from ! GatewayNode.SearchResult(Map.empty)
                Behaviors.same
              }
            }
          }
          case GatewayNode.Create(from, tableName, attributes) => {
            context.log.info(s"received create from ${from}")
            val newTable: Table = (attributes.toSet, Map.empty)
            val newTables = tables.+((tableName, newTable))
            from ! GatewayNode.CreateResult(true)
            running(hyperSpaceNode)
          }
        }
    }
  }

  private def search(tableData: Map[Key, AttributeMapping], query: AttributeMapping): Map[Key, AttributeMapping] = {

    def matches(v: AttributeMapping, query: AttributeMapping): Boolean = {
      query
        .map({ case (attrName, attrVal) => v.get(attrName).contains(attrVal) })
        .forall(b => b == true)
    }

    tableData.toSet
      .filter(kv => matches(kv._2, query))
      .toMap
  }

}
