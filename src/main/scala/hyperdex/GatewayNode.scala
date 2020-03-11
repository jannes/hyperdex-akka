package hyperdex

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import akka.util.Timeout
import hyperdex.API.{AttributeMapping, Error, Get, Put, Search}
import hyperdex.DataNode.AcceptedMessage
import sttp.tapir.server.akkahttp._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object GatewayNode {

  /**
    * MUTABLE REFERENCE TO RECEIVERS
    * TODO: rewrite to not have mutable shared state
    */
  @volatile private var receiversMut =
    Set.empty[ActorRef[DataNode.AcceptedMessage]]

  /**
    * messages
    */
  sealed trait GatewayMessage

  // all replies sent from data nodes
  sealed trait DataNodeResponse extends GatewayMessage
  final case class LookupResult(value: String) extends DataNodeResponse
//  final case class SearchResult
//  final case class PutResult

  sealed trait RuntimeMessage extends GatewayMessage
  sealed trait StartupMessage extends GatewayMessage

  // to discover receivers
  private final case class AllReceivers(
      receivers: Set[ActorRef[AcceptedMessage]])
      extends RuntimeMessage
  case object Stop extends RuntimeMessage with StartupMessage

  // all message concerned with http server startup
  private final case class StartFailed(cause: Throwable) extends StartupMessage
  private final case class Started(binding: ServerBinding)
      extends StartupMessage

  def getReceiverAdapter(
      ctx: ActorContext[GatewayMessage]): ActorRef[Receptionist.Listing] = {
    ctx.messageAdapter[Receptionist.Listing] {
      case DataNode.receiverNodeKey.Listing(receivers) =>
        AllReceivers(receivers)
    }
  }

  def apply(host: String, port: Int): Behavior[GatewayMessage] =
    Behaviors.setup { ctx =>
      implicit val typedSystem: ActorSystem[Nothing] = ctx.system
      // http doesn't know about akka typed so provide untyped system
      implicit val untypedSystem: akka.actor.ActorSystem = typedSystem.toClassic
      implicit val materializer: ActorMaterializer =
        ActorMaterializer()(ctx.system.toClassic)
      //implicit val ec: ExecutionContextExecutor = typedSystem.executionContext

      /**
        * routes
        */
      def getRouteLogic(
          inp: Get.Input): Future[Either[Error, AttributeMapping]] = {

        val key = inp.key.toInt

        if (receiversMut.isEmpty) {
          Future.successful(Left("no receivers"))
        } else {
          val lookupRes: Future[LookupResult] = receiversMut.head ? (
              ref => DataNode.LookupMessage(ref, "key")
          )
          lookupRes
            .map(lr => Map("example" -> 0))
            .transformWith {
              case Failure(exception) =>
                Future.successful(Left(exception.getMessage))
              case Success(value) =>
                Future.successful(Right(value))
            }
        }
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

      // asking someone requires a timeout and a scheduler, if the timeout hits without response
      // the ask is failed with a TimeoutException
      implicit val timeout: Timeout = 3.seconds
      // implicit scheduler only needed in 2.5
      // in 2.6 having an implicit typed ActorSystem in scope is enough

      val getRoute = Get.endp.toRoute(getRouteLogic)
      val putRoute = Put.endp.toRoute(putRouteLogic)
      val searchRoute = Search.endp.toRoute(searchRouteLogic)
      val routes = {
        import akka.http.scaladsl.server.Directives._
        getRoute ~ putRoute ~ searchRoute
      }

      val serverBinding: Future[Http.ServerBinding] =
        Http.apply().bindAndHandle(routes, host, port)

      ctx.pipeToSelf(serverBinding) {
        case Success(binding) => Started(binding)
        case Failure(ex)      => StartFailed(ex)
      }

      startingServerBehavior(ctx, wasStopped = false)
    }

  private def running(
      ctx: ActorContext[GatewayMessage],
      receivers: Set[ActorRef[DataNode.AcceptedMessage]],
      binding: ServerBinding
  ): Behavior[GatewayMessage] = {

    val msgBehavior: Behaviors.Receive[GatewayMessage] = Behaviors
      .receiveMessage {
        case Stop =>
          ctx.log.info(
            "Stopping server http://{}:{}/",
            binding.localAddress.getHostString,
            binding.localAddress.getPort
          )
          Behaviors.stopped
        case AllReceivers(newReceivers) =>
          ctx.log.info(s"updating receivers, new size: ${newReceivers.size}")
          receiversMut = newReceivers
          running(ctx, newReceivers, binding)
        case _: DataNodeResponse =>
          Behaviors.same
      }

    msgBehavior
      .receiveSignal {
        case (_, PostStop) =>
          binding.unbind()
          Behaviors.same
      }
  }

  private def startingServerBehavior(
      ctx: ActorContext[GatewayMessage],
      wasStopped: Boolean
  ): Behaviors.Receive[GatewayMessage] = {

    Behaviors.receiveMessage[GatewayMessage] {
      case StartFailed(cause) =>
        throw new RuntimeException("Server failed to start", cause)
      case Started(binding) =>
        ctx.log.info("Server online at http://{}:{}/",
                     binding.localAddress.getHostString,
                     binding.localAddress.getPort)
        if (wasStopped) ctx.self ! Stop
        // request receiver node information and start normal run behavior
        ctx.log.info("subscribe to receptionist for receiver nodes")
        ctx.system.receptionist ! Receptionist.subscribe(
          DataNode.receiverNodeKey,
          getReceiverAdapter(ctx))
        running(ctx, Set.empty, binding)
      case Stop =>
        // we got a stop message but haven't completed starting yet,
        // we cannot stop until starting has completed
        startingServerBehavior(ctx, wasStopped = true)
      case _: RuntimeMessage =>
        // ignore
        Behaviors.same
    }
  }
}
