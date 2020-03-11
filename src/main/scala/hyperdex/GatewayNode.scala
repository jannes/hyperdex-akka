package hyperdex

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import hyperdex.API.{AttributeMapping, Error, Get, Put, Search}
import hyperdex.DataNode.AcceptedMessage
import sttp.tapir.server.akkahttp._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn

object GatewayNode {

  /** messages **/
  sealed trait GatewayMessage

  /** user queries **/
  sealed trait Query extends GatewayMessage
  final case class Lookup(table: String, key: Int)
  // search
  // put

  /** responses from data nodes **/
  sealed trait DataNodeResponse extends GatewayMessage
  final case class LookupResult(value: String) extends DataNodeResponse
  // search result
  // put result

  /** configuration messages **/
  sealed trait RuntimeMessage extends GatewayMessage
  // to discover receivers
  private final case class AllReceivers(
      receivers: Set[ActorRef[AcceptedMessage]])
      extends RuntimeMessage

  /** ------- **/
  /**
    * MUTABLE REFERENCE TO RECEIVERS
    * TODO: rewrite to not have mutable shared state
    */
  @volatile private var receiversMut =
    Set.empty[ActorRef[DataNode.AcceptedMessage]]

  def actorBehavior(): Behavior[GatewayMessage] = {
    Behaviors.setup { ctx =>
      ctx.log.info("subscribe to receptionist for receiver nodes")
      ctx.system.receptionist ! Receptionist.subscribe(DataNode.receiverNodeKey,
                                                       getReceiverAdapter(ctx))
      starting(ctx, Set.empty)
    }
  }

  private def getReceiverAdapter(
      ctx: ActorContext[GatewayMessage]): ActorRef[Receptionist.Listing] = {
    ctx.messageAdapter[Receptionist.Listing] {
      case DataNode.receiverNodeKey.Listing(receivers) =>
        AllReceivers(receivers)
    }
  }

  private def starting(
      ctx: ActorContext[GatewayMessage],
      receivers: Set[ActorRef[DataNode.AcceptedMessage]],
  ): Behavior[GatewayMessage] = {

    Behaviors
      .receiveMessage {
        case AllReceivers(newReceivers) =>
          ctx.log.info(s"updating receivers, new size: ${newReceivers.size}")
          receiversMut = newReceivers
          running(ctx, newReceivers)
        case _ =>
          Behaviors.same
      }
  }

  private def running(
      ctx: ActorContext[GatewayMessage],
      receivers: Set[ActorRef[DataNode.AcceptedMessage]],
  ): Behavior[GatewayMessage] = {

    Behaviors
      .receiveMessage {
        case AllReceivers(newReceivers) =>
          ctx.log.info(s"updating receivers, new size: ${newReceivers.size}")
          receiversMut = newReceivers
          running(ctx, newReceivers)
        case query: Query =>
          handleQuery(query)
        case _: DataNodeResponse =>
          Behaviors.same
      }

  }

  private def handleQuery(
      query: GatewayNode.Query): Behavior[GatewayMessage] = {
    null
  }

  def runHttpServer(host: String,
                    port: Int,
                    typedSystem: ActorSystem[GatewayMessage]): Unit = {
    implicit val untypedSystem: akka.actor.ActorSystem = typedSystem.toClassic
    implicit val materializer: ActorMaterializer =
      ActorMaterializer()(untypedSystem)

    /**
      * routes
      */
    implicit val timeout: Timeout = 3.seconds

    def getRouteLogic(
        inp: Get.Input): Future[Either[Error, AttributeMapping]] = {
      val example = Map.from(List(("exampleAttr", 0)))
      Future.successful(Right(example))
    }

    def putRouteLogic(inp: Put.Input): Future[Either[Error, String]] = {
      val s = inp.value.foldLeft("")({ case (s, (k, v)) => s"$s$k: $v\n" })
      Future.successful(Right(s))
    }

    def searchRouteLogic(
        inp: Search.Input): Future[Either[Error, Seq[AttributeMapping]]] = {
      val res1 = Map.from(List(("exampleAttr", 0)))
      val res2 = Map.from(List(("exampleAttr", 0)))
      val example = List(res1, res2)
      Future.successful(Right(example))
    }

    val getRoute = Get.endp.toRoute(getRouteLogic)
    val putRoute = Put.endp.toRoute(putRouteLogic)
    val searchRoute = Search.endp.toRoute(searchRouteLogic)
    val routes = {
      import akka.http.scaladsl.server.Directives._
      getRoute ~ putRoute ~ searchRoute
    }

    val serverBinding: Future[Http.ServerBinding] =
      Http.apply().bindAndHandle(routes, host, port)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    serverBinding
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => typedSystem.terminate()) // and shutdown when done
  }
}
