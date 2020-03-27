package hyperdex

import akka.actor.typed.ActorRef
import hyperdex.API.AttributeMapping

object MessageProtocol {

  /** messages **/
  trait GatewayMessage extends CBorSerializable

  /** user queries **/
  sealed trait Query extends GatewayMessage
  final case class Create(from: ActorRef[CreateResult], table: String, attributes: Seq[String]) extends Query
  sealed trait TableQuery extends Query
  final case class Lookup(from: ActorRef[LookupResult], table: String, key: Int) extends TableQuery
  final case class Search(from: ActorRef[SearchResult], table: String, mapping: Map[String, Int]) extends TableQuery
  final case class Put(from: ActorRef[PutResult], table: String, key: Int, mapping: Map[String, Int]) extends TableQuery

  /** responses from data nodes **/
  sealed trait DataNodeResponse extends GatewayMessage
  final case class LookupResult(result: Either[QueryError, Option[AttributeMapping]]) extends DataNodeResponse
  // in order for cbor/json serialization to work a map can only have strings as keys
  final case class SearchResult(result: Either[QueryError, Map[String, AttributeMapping]]) extends DataNodeResponse
  final case class PutResult(result: Either[QueryError, Boolean]) extends DataNodeResponse
  final case class CreateResult(succeeded: Boolean) extends DataNodeResponse

  /**
    * errors within messages
    */
  sealed trait QueryError
  final case object TableNotExistError extends QueryError
  sealed trait AttributeError extends QueryError
  final case class InvalidAttributeError(invalidAttributes: Set[String]) extends AttributeError
  final case class IncompleteAttributesError(missingAttributes: Set[String]) extends AttributeError
  final case object InconsistentResultError extends QueryError
  final case object InternalServerError extends QueryError
}
