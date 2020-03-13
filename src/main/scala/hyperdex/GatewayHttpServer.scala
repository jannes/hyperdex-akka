package hyperdex

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import hyperdex.API.{AttributeMapping, Error, Get, Put, Search}
import hyperdex.GatewayNode.GatewayMessage
import sttp.tapir.server.akkahttp._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success}

object GatewayHttpServer {

  def run(host: String, port: Int, typedSystem: ActorSystem[GatewayMessage]): Unit = {

    implicit val ts: ActorSystem[GatewayMessage] = typedSystem
    implicit val untypedSystem: akka.actor.ActorSystem = ts.toClassic
    implicit val materializer: ActorMaterializer =
      ActorMaterializer()(untypedSystem)

    /**
      * routes
      */
    implicit val timeout: Timeout = 10.seconds

    def getRouteLogic(inp: Get.Input): Future[Either[Error, Option[AttributeMapping]]] = {

      // TODO: handle invalid non integer key
      val key = inp.key.toInt
      val lookupResult: Future[GatewayNode.LookupResult] = typedSystem ? { ref =>
        GatewayNode.Lookup(ref, inp.table, key)
      }

      lookupResult
        .map(lr => lr.value)
        .transformWith {
          case Failure(exception) =>
            Future.successful(Left(exception.getMessage))
          case Success(value) =>
            Future.successful(Right(value))
        }
    }

    def putRouteLogic(inp: Put.Input): Future[Either[Error, String]] = {

      // TODO: handle invalid non integer key
      val key = inp.key.toInt
      val putResult: Future[GatewayNode.PutResult] = typedSystem ? { ref =>
        GatewayNode.Put(ref, inp.table, key, inp.value)
      }

      putResult
        .transformWith {
          case Failure(exception) =>
            Future.successful(Left(exception.getMessage))
          case Success(value) =>
            if (value.succeeded)
              Future.successful(Right("success"))
            else
              Future.successful(Right("failure"))
        }
    }

    // TODO
    def searchRouteLogic(inp: Search.Input): Future[Either[Error, Seq[AttributeMapping]]] = {
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
