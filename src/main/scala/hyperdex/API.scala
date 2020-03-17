package hyperdex

import play.api.libs.json.{JsError, JsResult, Json, Reads, Writes}
import sttp.tapir.{EndpointInput, _}
import sttp.tapir.json.play._
import sttp.tapir.CodecFormat._

import scala.util.Try
import scala.collection.mutable.Map

object API {

  type Key = Int
  type Attribute = Int
  type AttributeMapping = Map[String, Attribute]
  type Error = String

  object Create {
    case class Input(table: String, attributes: Seq[String])

    val endpointInput: EndpointInput[Input] =
      ("create" / path[String]("table"))
        .and(jsonBody[Seq[String]])
        .mapTo(Input)

    val endp: Endpoint[Input, Error, String, Nothing] = endpoint.post
      .in(endpointInput)
      .errorOut(stringBody)
      .out(stringBody)
  }

  object Get {
    case class Input(table: String, key: Key)

    val endpointInput: EndpointInput[Input] =
      ("get" / path[String]("table") / path[Key]("key"))
        .mapTo(Input)

    val endp: Endpoint[Input, Error, Option[AttributeMapping], Nothing] = endpoint.get
      .in(endpointInput)
      .errorOut(stringBody)
      .out(jsonBody[Option[AttributeMapping]])

  }

  object Put {
    case class Input(table: String, key: Key, value: AttributeMapping)

    val endpointInput: EndpointInput[Input] =
      ("put" / path[String]("table") / path[Key]("key"))
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

    // return set of tuples instead of map because the keys are integers (json only has string keys)
    val endp: Endpoint[Input, Error, Set[(Key, AttributeMapping)], Nothing] =
      endpoint.get
        .in(endpointInput)
        .errorOut(stringBody)
        .out(jsonBody[Set[(Key, AttributeMapping)]])
  }

}
