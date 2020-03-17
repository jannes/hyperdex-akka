package hyperdex

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import hyperdex.API.{Attribute, AttributeMapping, Key}
import hyperdex.DataNode.AcceptedMessage

object GatewayNode {

  val NUM_DATANODES = 1
  var hyperSpaceMapping: Map[String, HyperSpace] = Map()
  var dataNodeMapping: Map[Int, ActorRef[DataNode.AcceptedMessage]] = Map()

  /** messages **/
  sealed trait GatewayMessage extends CBorSerializable

  /** user queries **/
  sealed trait Query extends GatewayMessage
  final case class Lookup(from: ActorRef[LookupResult], table: String, key: Int) extends Query
  final case class Search(from: ActorRef[SearchResult], table: String, mapping: Map[String, Int]) extends Query
  final case class Put(from: ActorRef[PutResult], table: String, key: Int, mapping: Map[String, Int]) extends Query
  final case class PutAttribute(from: ActorRef[PutResult], table: String, key: Int, hashValue: Int, attribute: String) extends Query
  final case class PutObject(from: ActorRef[PutResult], table: String, key: Int, mapping: Map[String, Int]) extends Query

  final case class Create(from: ActorRef[CreateResult], table: String, attributes: Seq[String], bucketSize: Int) extends Query

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

  def initTestHyperSpace(ctx: ActorContext[GatewayMessage],
                         receivers: Set[ActorRef[DataNode.AcceptedMessage]]) = {
    create(ctx, receivers, "TestTable1", Seq("attribute1","attribute2", "attribute3"))
    val r = scala.util.Random

    for(a <- 0 until 1000){
      var mapping = Map[String, Attribute]("attribute1" -> r.nextInt(10), "attribute2" -> r.nextInt(400),"attribute3" ->  r.nextInt(100) )
      var testobj = Map[Key, AttributeMapping] (a -> mapping )
      put(ctx, receivers, mapping, a, "TestTable1")
    }
//    val objects: Array[AttributeMapping] = generateTestObjects(10000).filter(x => x != null);
//    var IDs: Array[Int] = objects.map( x => x("Id") );
//    var attr1list: Array[Int] = objects.map( x => x("attribute1") );
//    var attr2list: Array[Int] = objects.map( x => x("attribute2") );
//    var attr3list: Array[Int] = objects.map( x => x("attribute3") );
//    var attr4list: Array[Int] = objects.map( x => x("attribute4") );
//    var attributeValuesList: Array[Array[Int]]= Array(attr1list,attr2list, attr3list, attr4list);
//    var hyperspace = new HyperSpace(attributeslist, 100, objects, IDs, attributeValuesList);
//    return hyperspace
  }

  private def put(ctx: ActorContext[GatewayMessage], receivers: Set[ActorRef[DataNode.AcceptedMessage]], mapping: AttributeMapping, key: Key, table: String): PutResult ={
    var hyperSpace = hyperSpaceMapping(table)
    for(attribute <- mapping){
      var hashIndex = hyperSpace.hashValue(attribute._2);
      var dataNodeIndex = hyperSpace.obtainDataNodeIndex(hashIndex)
      dataNodeMapping(dataNodeIndex) ! PutAttribute(ctx.self,table, key, hashIndex, attribute._1)
    }

    var dataNodeIndex = key % NUM_DATANODES
    dataNodeMapping(dataNodeIndex) ! PutObject(ctx.self, table, key, mapping)

    PutResult(true)
  }

  private def create(ctx: ActorContext[GatewayMessage],
                     receivers: Set[ActorRef[DataNode.AcceptedMessage]], name: String, attributes: Seq[String]): CreateResult = {
    var newHyperSpace = new HyperSpace(attributes,NUM_DATANODES)
    val bucketSize = 100 / NUM_DATANODES
    hyperSpaceMapping += (name -> newHyperSpace)
    var partOfHyperSpace= 0
    for(dataNode <- receivers){

      dataNodeMapping += (partOfHyperSpace -> dataNode)
      partOfHyperSpace += 1
      dataNode ! Create(ctx.self,name, attributes, bucketSize)
    }
    CreateResult(true)
  }

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
          if (newReceivers.size < NUM_DATANODES) {
            ctx.log.info(s"Not enough receivers, we have ${newReceivers.size} out of ${NUM_DATANODES}.")
            Behaviors.same
          } else {
            ctx.log.info(s"We have ${newReceivers.size} receivers, so lets start running.")
            running(ctx, newReceivers)

          }
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
    initTestHyperSpace(ctx, receivers)
    Behaviors
      .receiveMessage {

        case query: Query =>
          // get the right hyperspace for table
          // handle query through hyperspace
//          handleQuery(query)
          receivers.head ! query
          Behaviors.same
        case _: DataNodeResponse =>
          Behaviors.same
        case _: AllReceivers =>
          Behaviors.same
      }

  }

  // TODO: route queries to hyperspace object
  private def handleQuery(query: GatewayNode.Query, ctx: ActorContext[GatewayMessage],
                          receivers: Set[ActorRef[DataNode.AcceptedMessage]]): Unit = {
    query match {
      case Lookup(from, table, key) => {
        // get Future from Hyperspace
        // call onComplete and send value back to requeste
        from ! LookupResult(Some(Map("key" -> 1, "attr1" -> 2)))
      }
      case Search(from, table, mapping) => {
//        var hyperSpace = hyperSpaces(table);
//        val coordinates: Map[Int, List[(String, Int)]] = hyperSpace.search(mapping)

//        for (coordinate <- coordinates) {
//          //from ! SearchResult(coordinates)
//        }

      }
      case Put(from, table, key, mapping) => {
        from ! put(ctx, receivers, mapping, key, table)
      }
      case Create(from, table, attributes, bucketSize) => {
        from ! create(ctx, receivers, table, attributes)

      }

    }
  }
}
