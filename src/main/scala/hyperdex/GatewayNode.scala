package hyperdex

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import examples.cluster.ReceiverNode

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}


object GatewayNode {

  /**
   * MUTABLE REFERENCE TO RECEIVERS
   * TODO: rewrite to not have mutable shared state
   */
  @volatile private var receiversMut =
  Set.empty[ActorRef[ReceiverNode.PingMessage]]

  /**
   * messages
   */
  sealed trait GatewayMessage
  sealed trait RuntimeMessage extends GatewayMessage
  sealed trait StartupMessage extends GatewayMessage

  // to discover receivers
  private final case class AllReceivers(
                                         receivers: Set[ActorRef[ReceiverNode.PingMessage]])
    extends RuntimeMessage
  case object Stop extends RuntimeMessage with StartupMessage

  // all message concerned with http server startup
  private final case class StartFailed(cause: Throwable) extends StartupMessage
  private final case class Started(binding: ServerBinding)
    extends StartupMessage

  def getReceiverAdapter(
                          ctx: ActorContext[GatewayMessage]): ActorRef[Receptionist.Listing] = {
    ctx.messageAdapter[Receptionist.Listing] {
      case ReceiverNode.receiverNodeKey.Listing(receivers) =>
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
      implicit val ec: ExecutionContextExecutor = typedSystem.executionContext

      /**
       * routes
       */
      // asking someone requires a timeout and a scheduler, if the timeout hits without response
      // the ask is failed with a TimeoutException
      implicit val timeout: Timeout = 3.seconds
      // implicit scheduler only needed in 2.5
      // in 2.6 having an implicit typed ActorSystem in scope is enough

      // need to wrap with context to the state gets reevaluated for every request
      lazy val routes: Route =
        get {
          path("lookup") {
            ctx.log.info("received lookup request")
            val dummyKey = "dummy"
            if (receiversMut.isEmpty) {
              complete("no receivers")
            } else {
              val lookupRes = receiversMut.head
                .ask[LookupResult](ReceiverNode.LookupMessage(ctx.self, dummyKey))
              onSuccess(lookupRes) {
                case LookupResult(v) =>
                  complete(s"value: $v received")
              }
            }
          }
        }

      val serverBinding: Future[Http.ServerBinding] =
        Http.apply().bindAndHandle(routes, host, port)

      ctx.pipeToSelf(serverBinding) {
        case Success(binding) => Started(binding)
        case Failure(ex)      => StartFailed(ex)
      }

      startingServerBehavior(ctx, wasStopped = false)
    }

  private def running(ctx: ActorContext[GatewayMessage],
                      receivers: Set[ActorRef[ReceiverNode.PingMessage]],
                      binding: ServerBinding): Behavior[GatewayMessage] = {

    val msgBehavior: Behaviors.Receive[GatewayMessage] = Behaviors
      .receiveMessage {
        case Stop =>
          ctx.log.info("Stopping server http://{}:{}/",
            binding.localAddress.getHostString,
            binding.localAddress.getPort)
          Behaviors.stopped
        case AllReceivers(newReceivers) =>
          ctx.log.info(s"updating receivers, new size: ${newReceivers.size}")
          receiversMut = newReceivers
          running(ctx, newReceivers, binding)
        case _: StartupMessage =>
          // ignore
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
                                      wasStopped: Boolean): Behaviors.Receive[GatewayMessage] = {
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
          ReceiverNode.receiverNodeKey,
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
