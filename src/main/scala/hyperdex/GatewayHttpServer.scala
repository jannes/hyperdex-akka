package hyperdex

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import hyperdex.MessageProtocol._
import sttp.tapir.server.akkahttp._

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
    def createRouteLogic(inp: API.Create.Input): Future[Either[API.Error, String]] = {
      val createResult: Future[CreateResult] = typedSystem ? { ref =>
        Create(ref, inp.table, inp.attributes)
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
    def getRouteLogic(inp: API.Get.Input): Future[Either[API.Error, Option[API.AttributeMapping]]] = {

      val lookupResult: Future[LookupResult] = typedSystem ? { ref =>
        Lookup(ref, inp.table, inp.key)
      }

      lookupResult
        .map(lr => lr.result)
        .transformWith {
          case Success(value) =>
            value match {
              case Left(TableNotExistError) => Future.successful(Left("No such table."))
              case Right(null)              => Future.successful(Left("No record with that key."))
              case Right(option)            => Future.successful(Right(option))
            }
          case Failure(exception) =>
            Future.successful(Left(exception.getMessage))
        }
    }

    // TODO: better error reporting
    def putRouteLogic(inp: API.Put.Input): Future[Either[API.Error, String]] = {

      val putResult: Future[PutResult] = typedSystem ? { ref =>
        Put(ref, inp.table, inp.key, inp.value)
      }

      putResult
        .transformWith {
          case Failure(exception) => Future.successful(Left(exception.getMessage))
          case Success(value) =>
            value.result match {
              case Left(TableNotExistError) => Future.successful(Left("No such table."))
              case Left(InvalidAttributeError(invalidAttributes)) =>
                Future.successful(Left(s"Provided attributes do not exist: ${invalidAttributes}"))
              case Left(IncompleteAttributesError(missingAttributes)) =>
                Future.successful(Left(s"Missing the following attributes: ${missingAttributes}"))
              case Right(value) => Future.successful(Right("Put succeeded."))

            }
        }
    }

    // TODO: better error reporting
    def searchRouteLogic(inp: API.Search.Input): Future[Either[API.Error, Set[(API.Key, API.AttributeMapping)]]] = {
      val searchResult: Future[SearchResult] = typedSystem ? { ref =>
        Search(ref, inp.table, inp.query)
      }

      searchResult
        .map(lr => {
          println(s"received from data node: ${lr.result}")
          lr.result
        })
        .transformWith {
          case Failure(exception) =>
            Future.successful(Left(exception.getMessage))
          case Success(value) => {
            value match {
              case Left(TableNotExistError) => Future.successful(Left("No such table"))
              case Left(InvalidAttributeError(invalidAttributes)) =>
                Future.successful(Left(s"Provided attributes do not exist: ${invalidAttributes}"))
              case Right(value) =>
                val castedValue = value.map({ case (key, mapping) => (key.toInt, mapping) })
                Future.successful(Right(castedValue.toSet))
            }

          }
        }
    }

    val getRoute = API.Get.endp.toRoute(getRouteLogic)
    val putRoute = API.Put.endp.toRoute(putRouteLogic)
    val searchRoute = API.Search.endp.toRoute(searchRouteLogic)
    val createRoute = API.Create.endp.toRoute(createRouteLogic)
    val routes = {
      import akka.http.scaladsl.server.Directives._
      getRoute ~ putRoute ~ searchRoute ~ createRoute
    }

    val serverBinding: Future[Http.ServerBinding] =
      Http.apply().bindAndHandle(routes, host, port)

    println(s"Server online at http://localhost:8080/")
  }
}
