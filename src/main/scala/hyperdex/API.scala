package hyperdex

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import sttp.tapir.EndpointInput
import sttp.tapir.server.akkahttp._
import sttp.tapir.json.play._
import sttp.tapir._

import scala.concurrent.Future
import scala.io.StdIn

object API {

  type AttributeType = Int
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
}
