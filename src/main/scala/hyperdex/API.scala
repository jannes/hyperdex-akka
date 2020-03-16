package hyperdex

import play.api.libs.json.{JsError, JsResult, Json, Reads, Writes}
import sttp.tapir.{EndpointInput, _}
import sttp.tapir.json.play._

import scala.util.Try

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
    //  implicit def forMap[K, V, CF <: CodecFormat, R](implicit tm: Codec[(K, V), CF, R]): CodecForMany[Map[K, V], CF, R] =
    //    CodecForMany.forSet[(K, V), CF, R].map(_.toMap)(_.toSet)
    //  implicit val attributeMappingCodec: Codec[AttributeMapping, CodecFormat.Json, _] =
    //    implicitly[Codec[AttributeMapping, CodecFormat.Json, _]]

//    implicit lazy val searchResultReads: Reads[Map[Key, AttributeMapping]] =
//      Reads.mapReads(k => JsResult.fromTry(Try(k.toInt), throwable => JsError(throwable.toString)))
//    implicit lazy val searchResultWrites: Writes[Map[Key, AttributeMapping]] =
//      implicitly[Writes[Map[Key, AttributeMapping]]]
//    implicit val searchResultSchema: Schema[Map[Key, AttributeMapping]] =
//      implicitly[Schema[Map[Key, AttributeMapping]]]
//    implicit val searchResultCodec: Codec[Map[Key, AttributeMapping], CodecFormat.Json, _] = readsWritesCodec
    case class Input(table: String, query: AttributeMapping)

    val endpointInput: EndpointInput[Input] =
      ("search" / path[String]("table"))
        .and(jsonBody[AttributeMapping])
        .mapTo(Input)

//    val endp: Endpoint[Input, Error, Map[Key, AttributeMapping], Nothing] =
//      endpoint.get
//        .in(endpointInput)
//        .errorOut(stringBody)
//        .out(jsonBody[Map[Key, AttributeMapping]](CodecForOptional.fromCodec(searchResultCodec)))

    val endp: Endpoint[Input, Error, Set[(Key, AttributeMapping)], Nothing] =
      endpoint.get
        .in(endpointInput)
        .errorOut(stringBody)
        .out(jsonBody[Set[(Key, AttributeMapping)]])
  }

}
