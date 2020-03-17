package hyperdex

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import hyperdex.API.{AttributeMapping, Create, Error, Get, Key, Put, Search}
import hyperdex.GatewayNode.GatewayMessage
import sttp.tapir.server.akkahttp._
import scala.collection.mutable.Map

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
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

    // TODO: better error reporting
    def createRouteLogic(inp: Create.Input): Future[Either[Error, String]] = {
      val createResult: Future[GatewayNode.CreateResult] = typedSystem ? { ref =>
        GatewayNode.Create(ref, inp.table, inp.attributes, 0)
      }

      createResult
        .transformWith {
          case Failure(exception) =>
            Future.successful(Left(exception.getMessage))
          case Success(value) =>
            Future.successful(Right(value.succeeded.toString))
        }
    }

    // TODO: better error reporting
    def getRouteLogic(inp: Get.Input): Future[Either[Error, Option[AttributeMapping]]] = {

      val lookupResult: Future[GatewayNode.LookupResult] = typedSystem ? { ref =>
        GatewayNode.Lookup(ref, inp.table, inp.key)
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

    // TODO: better error reporting
    def putRouteLogic(inp: Put.Input): Future[Either[Error, String]] = {

      val putResult: Future[GatewayNode.PutResult] = typedSystem ? { ref =>
        GatewayNode.Put(ref, inp.table, inp.key, inp.value)
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

    // TODO: better error reporting
    def searchRouteLogic(inp: Search.Input): Future[Either[Error, Set[(Key, AttributeMapping)]]] = {
      val searchResult: Future[GatewayNode.SearchResult] = typedSystem ? { ref =>
        GatewayNode.Search(ref, inp.table, inp.query)
      }

      searchResult
        .map(lr => {
          println(s"received from data node: ${lr.objects}")
          lr.objects
        })
        .transformWith {
          case Failure(exception) =>
            Future.successful(Left(exception.getMessage))
          case Success(value) => {
            val castedValue = value.map({ case (key, mapping) => (key.toInt, mapping) })
            Future.successful(Right(castedValue.toSet))
          }
        }
    }

    val getRoute = Get.endp.toRoute(getRouteLogic)
    val putRoute = Put.endp.toRoute(putRouteLogic)
    val searchRoute = Search.endp.toRoute(searchRouteLogic)
    val createRoute = Create.endp.toRoute(createRouteLogic)
    val routes = {
      import akka.http.scaladsl.server.Directives._
      getRoute ~ putRoute ~ searchRoute ~ createRoute
    }

    val serverBinding: Future[Http.ServerBinding] =
      Http.apply().bindAndHandle(routes, host, port)

    println(s"Server online at http://localhost:8080/")
  }
}
