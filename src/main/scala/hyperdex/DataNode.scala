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

  val exampleKeyVal = Map(0 -> Map("at1" -> 0, "at2" -> 1))
  val exampleTable: Table = (Set("at1", "at2"), exampleKeyVal)
  val tables: Map[String, Table] = Map("test" -> exampleTable)

  def apply(): Behavior[AcceptedMessage] = Behaviors.setup { ctx =>
    ctx.log.info("registering with receptionist")
    ctx.system.receptionist ! Receptionist.register(receiverNodeKey, ctx.self)
    running(tables)
  }

  /**
    * behavior of a data node in operation
    * NOTE: query responses are send before data is "committed",
    * in case of failure right after replying a client gets a untrue response
    *
    * @param tables
    * @return
    */
  def running(tables: Map[String, Table]): Behavior[AcceptedMessage] = {
    Behaviors.receive[AcceptedMessage] {
      case (context, message) =>
        message match {
          case GatewayNode.Lookup(from, table, key) => {
            context.log.debug(s"received LookupMessage for key: $key from: $from")
            val optResult = tables
              .get(table)
              .flatMap(_._2.get(key))
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
                  // should not happen, gateway's responsibility to check
                  from ! GatewayNode.PutResult(false)
                  Behaviors.same
                } else {
                  from ! GatewayNode.PutResult(true)
                  val updatedData = data.+((key, mapping))
                  val updatedTable = (attributes, updatedData)
                  running(tables.+((tableName, updatedTable)))
                }
              }
              // this should never happen as the gateway checks for existance of table
              case None => {
                context.log.error(s"table $tableName does not exist")
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
                  // should not happen, gateway's responsibility to check
                  context.log.error(s"some of the given attributes do not exist in table")
                  from ! GatewayNode.SearchResult(Map.empty)
                } else {
                  val searchResult = search(data, mapping)
                  context.log.debug(s"matching objects keys: ${searchResult}")
                  val castedSearchResult = searchResult
                    .map({ case (key, mapping) => (key.toString, mapping) })
                  from ! GatewayNode.SearchResult(castedSearchResult)
                }
                Behaviors.same
              }
              // this should never happen as the gateway checks for existance of table
              case None => {
                context.log.error(s"table $tableName does not exist")
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
            running(newTables)
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
