package hyperdex

import sttp.tapir.json.play._
import sttp.tapir.{EndpointInput, _}

object API {

  type AttributeType = Int
  type AttributeMapping = Map[String, AttributeType]
  type Error = String

  object Get {
    case class Input(table: String, key: String)

    val endpointInput: EndpointInput[Input] =
      ("get" / path[String]("table") / path[String]("key"))
        .mapTo(Input)

    val endp: Endpoint[Input, Error, Option[AttributeMapping], Nothing] = endpoint.get
      .in(endpointInput)
      .errorOut(stringBody)
      .out(jsonBody[Option[AttributeMapping]])

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
