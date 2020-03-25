package hyperdex

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import hyperdex.API.{AttributeMapping, Key}
import hyperdex.DataNode.AcceptedMessage

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object GatewayNode {

  /** messages **/
  sealed trait GatewayMessage extends CBorSerializable

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

  /** configuration messages **/
  sealed trait RuntimeMessage extends GatewayMessage
  // to discover receivers
  private final case class AllReceivers(receivers: Set[ActorRef[AcceptedMessage]]) extends RuntimeMessage

  def actorBehavior(numDataNodes: Int): Behavior[GatewayMessage] = {
    Behaviors.setup { ctx =>
      ctx.log.info("Subscribing to receptionist for receiver nodes...")
      ctx.system.receptionist ! Receptionist.subscribe(DataNode.receiverNodeKey, getReceiverAdapter(ctx))
      starting(ctx, numDataNodes, Set.empty)
    }
  }

  private def getReceiverAdapter(ctx: ActorContext[GatewayMessage]): ActorRef[Receptionist.Listing] = {
    ctx.messageAdapter[Receptionist.Listing] {
      case DataNode.receiverNodeKey.Listing(receivers) =>
        AllReceivers(receivers)
    }
  }

  /**
    * stage of resolving all data nodes
    * @param ctx
    * @param dataNodes
    * @return
    */
  private def starting(
    ctx: ActorContext[GatewayMessage],
    requiredAmountDataNodes: Int,
    dataNodes: Set[ActorRef[DataNode.AcceptedMessage]],
  ): Behavior[GatewayMessage] = {

    Behaviors
      .receiveMessage {
        case AllReceivers(newReceivers) =>
          if (newReceivers.size < requiredAmountDataNodes) {
            ctx.log.info(s"Not enough receivers, we have ${newReceivers.size} out of $requiredAmountDataNodes.")
            starting(ctx, requiredAmountDataNodes, newReceivers)
          } else {
            ctx.log.info(s"We have ${newReceivers.size} receivers, so lets start running.")
            // datanodes receive random position in sequence, so random IDs from 0..requiredAmountDataNodes-1
            running(ctx, Map(), newReceivers.toSeq)
          }
        case _ =>
          ctx.log.info(s"The gateway first needs enough receivers.")
          Behaviors.same
      }
  }

  /**
    * the runtime behavior after all setup has completed
    * @param ctx
    * @param dataNodes
    * @return
    */
  private def running(
    ctx: ActorContext[GatewayMessage],
    hyperspaces: Map[String, HyperSpace],
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  ): Behavior[GatewayMessage] = {

    Behaviors
      .receiveMessage {

        case Create(from, table, attributes) =>
          val newHyperspace = new HyperSpace(attributes, dataNodes.size, 2)
          dataNodes.foreach(dataNode => dataNode ! Create(ctx.self, table, attributes))
          from ! CreateResult(true)
          running(ctx, hyperspaces.+((table, newHyperspace)), dataNodes)
        case tableQuery: TableQuery =>
          handleTableQuery(tableQuery, ctx, hyperspaces, dataNodes)
          Behaviors.same
        // shouldn't receive any except create result which is ignored
        case _: DataNodeResponse =>
          Behaviors.same
        // should not happen (if it does, system is broken)
        case _: AllReceivers =>
          Behaviors.same
      }

  }

  /**
    * process a put/get/search by routing to the correct datanodes
    * and potentially merging results
    * @param query
    * @param ctx
    * @param hyperspaces
    * @param dataNodes
    */
  private def handleTableQuery(
    query: TableQuery,
    ctx: ActorContext[GatewayMessage],
    hyperspaces: Map[String, HyperSpace],
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  ): Unit = {

    // implicits needed for ask pattern
    implicit val system: ActorSystem[Nothing] = ctx.system
    implicit val timeout: Timeout = 5.seconds
    implicit val ec: ExecutionContext = ctx.executionContext

    query match {
      case lookup @ Lookup(from, table, key) =>
        hyperspaces.get(table) match {
          case Some(hyperspace) =>
            handleValidLookup(lookup, ctx, hyperspace, dataNodes)
          case None =>
            from ! LookupResult(Left(TableNotExistError))
        }
      case search @ Search(from, table, mapping) =>
        hyperspaces.get(table) match {
          case Some(hyperspace) =>
            // if mapping contains invalid attributes
            if (hyperspace.attributes.toSet != mapping.keys.toSet) {
              if (!mapping.keys.map(x => hyperspace.attributes.contains(x)).forall(x => x))
                from ! SearchResult(Left(InvalidAttributeError(mapping.keys.toSet.diff(hyperspace.attributes.toSet))))
            } else
              handleValidSearch(search, ctx, hyperspace, dataNodes)

          case None =>
            from ! SearchResult(Left(TableNotExistError))
        }
      case put @ Put(from, table, key, mapping) =>
        hyperspaces.get(table) match {
          case Some(hyperspace) =>
            // if mapping contains invalid attributes
            if (hyperspace.attributes.toSet != mapping.keys.toSet) {
              if (hyperspace.attributes.toSet.size <= mapping.keys.toSet.size)
                from ! PutResult(Left(InvalidAttributeError(mapping.keys.toSet.diff(hyperspace.attributes.toSet))))
              else if (hyperspace.attributes.toSet.size > mapping.keys.toSet.size)
                from ! PutResult(Left(IncompleteAttributesError(hyperspace.attributes.toSet.diff(mapping.keys.toSet))))
            } else
              handleValidPut(put, ctx, hyperspace, dataNodes)
          case None =>
            from ! PutResult(Left(TableNotExistError))
        }
    }
  }

  def handleValidLookup(
    lookup: Lookup,
    ctx: ActorContext[GatewayMessage],
    hyperspace: HyperSpace,
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  )(implicit as: ActorSystem[Nothing], timeout: Timeout, ec: ExecutionContext): Unit = {

    val key = lookup.key
    val table = lookup.table

    val answers: Seq[Future[LookupResult]] = hyperspace
      .getResponsibleNodeIds(key)
      .map(dataNodes(_))
      .map(dn => {
        dn ? [LookupResult](ref => Lookup(ref, table, key))
      })
      .toSeq

    val answersSingleSuccessFuture: Future[Seq[Try[LookupResult]]] = Future.sequence(
      answers.map(f => f.map(Success(_)).recover(x => Failure(x)))
    )

    val processedFuture: Future[LookupResult] = answersSingleSuccessFuture.map(seq => {
      var nonExceptionLookups = mutable.Set.empty[Option[AttributeMapping]]
      for (tried <- seq) {
        tried match {
          case Success(value) =>
            value.result match {
              case Right(value) => nonExceptionLookups.add(value)
            }
          case Failure(exception) =>
            ctx.log.error(s"exception occurred when asking for lookup result: ${exception.getMessage}")
        }
      }
      // if exactly one -> return it
      if (nonExceptionLookups.size == 1) {
        LookupResult(Right(nonExceptionLookups.head))
      }
      // if no non-exceptional response -> internal server error
      else if (nonExceptionLookups.isEmpty) {
        ctx.log.error("did not got any answers for lookup")
        LookupResult(Left(InternalServerError))
      }
      // if more than one -> filter Nones and
      else {
        val filtered = nonExceptionLookups.toSet.filter(lr => lr.isDefined)
        //// if one -> return it
        if (filtered.size == 1) {
          LookupResult(Right(filtered.head))
        }
        //// if more than one -> inconsistency
        else {
          ctx.log.error("got inconsistent answers for a lookup")
          LookupResult(Left(InconsistentResultError))
        }
      }
    })

    // send processed result back to gateway server (on completion of future)
    processedFuture.foreach(lr => lookup.from ! lr)
  }

  def handleValidPut(
    put: Put,
    ctx: ActorContext[GatewayMessage],
    hyperspace: HyperSpace,
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  )(implicit as: ActorSystem[Nothing], timeout: Timeout, ec: ExecutionContext): Unit = {
    val responsibleDataNodeIds = hyperspace.getResponsibleNodeIds(put.key, put.mapping)
    assert(responsibleDataNodeIds.size == 1)
    dataNodes(responsibleDataNodeIds.head) ! put
  }

  def handleValidSearch(
    search: Search,
    ctx: ActorContext[GatewayMessage],
    hyperspace: HyperSpace,
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  )(implicit as: ActorSystem[Nothing], timeout: Timeout, ec: ExecutionContext): Unit = {

    val answers: Seq[Future[SearchResult]] = hyperspace
      .getResponsibleNodeIds(search.mapping)
      .map(dataNodes(_))
      .map(dn => {
        dn ? [SearchResult](ref => Search(ref, search.table, search.mapping))
      })
      .toSeq

    val answersSingleSuccessFuture: Future[Seq[Try[SearchResult]]] = Future.sequence(
      answers.map(f => f.map(Success(_)).recover(x => Failure(x)))
    )
    ctx.log.info(answersSingleSuccessFuture.isCompleted.toString)
    val processedFuture: Future[SearchResult] = answersSingleSuccessFuture.map(seq => {
      var nonExceptionSearchResults = mutable.Set.empty[Map[String, AttributeMapping]]
      var exception: QueryError = null
      for (tried <- seq) {
        tried match {
          case Success(value) =>
            value.result match {
              case Left(value)  => exception = value
              case Right(value) => nonExceptionSearchResults.add(value)
            }

          case Failure(exception) =>
            ctx.log.error(s"exception occurred when asking for lookup result: ${exception.getMessage}")
        }
      }
      if (exception == null) {
        val mergedMatches = nonExceptionSearchResults.toSet
          .map((sr: Map[String, AttributeMapping]) => sr.toSet[(String, AttributeMapping)])
          .fold(Set.empty)(_.union(_))
          .toMap
        SearchResult(Right(mergedMatches))
      } else {

        SearchResult(Left(exception))
      }
    })
    // send processed result back to gateway server (on completion of future)
    processedFuture.onComplete(sr => search.from ! sr.get)
  }

}
