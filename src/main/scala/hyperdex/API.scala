package hyperdex

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import sttp.tapir.{EndpointInput, _}
import sttp.tapir.json.play._
import sttp.tapir.server.akkahttp._

import scala.concurrent.Future
import scala.io.StdIn

object API extends App {

  type AttributeType = String
  type AttributeMapping = Map[String, AttributeType]

  type Error = String

  object Get {
    case class Input(table: String, key: String)

    val endpointInput: EndpointInput[Input] =
      ("get" / path[String]("table") / path[String]("key"))
        .mapTo(Input)

    val endp: Endpoint[Input, Error, AttributeMapping, Nothing] = endpoint.get
      .in(endpointInput)
      .errorOut(stringBody)
      .out(jsonBody[AttributeMapping])

  }

  object Put {
    case class Input(table: String, key: String, value: AttributeMapping)

    val endpointInput: EndpointInput[Input] =
      ("put" / path[String]("table") / path[String]("key"))
        .and(jsonBody[AttributeMapping])
        .mapTo(Input)

    val endp: Endpoint[Input, Error, String, Nothing] = endpoint.post
      .in(endpointInput)
      .errorOut(stringBody)
      .out(stringBody)
  }

  object Search {
    case class Input(table: String, query: AttributeMapping)

    val endpointInput: EndpointInput[Input] =
      ("search" / path[String]("table"))
        .and(jsonBody[AttributeMapping])
        .mapTo(Input)

    // ignore Intellij warning about no implicit found
    val endp: Endpoint[Input, Error, Seq[AttributeMapping], Nothing] =
      endpoint.get
        .in(endpointInput)
        .errorOut(stringBody)
        .out(jsonBody[Seq[AttributeMapping]])
  }

  def getRouteLogic(inp: Get.Input): Future[Either[Error, AttributeMapping]] = {
    val example = Map.from(List(("exampleAttr", "exampleVal")))
    Future.successful(Right(example))
  }

  def putRouteLogic(inp: Put.Input): Future[Either[Error, String]] = {
    val s = inp.value.foldLeft("")({ case (s, (k, v)) => s"$s$k: $v\n" })
    Future.successful(Right(s))
  }

  def searchRouteLogic(
      inp: Search.Input): Future[Either[Error, Seq[AttributeMapping]]] = {
    val res1 = Map.from(List(("exampleAttr", "exampleVal")))
    val res2 = Map.from(List(("exampleAttr", "exampleVal")))
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

  /**
    * akka http fluff
    */
  import akka.actor.typed.scaladsl.adapter._

  implicit val system =
    ActorSystem[Nothing](Behaviors.ignore, "my-system", ConfigFactory.empty())
  implicit val untypedSystem = system.toClassic
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = untypedSystem.dispatcher

  val bindingFuture = Http().bindAndHandle(routes, "localhost", 8080)

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done

}
