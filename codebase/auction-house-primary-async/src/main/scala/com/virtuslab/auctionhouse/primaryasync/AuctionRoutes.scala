package com.virtuslab.auctionhouse.primaryasync

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.AuthenticationFailedRejection._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.{RejectionHandler, Route, _}
import com.typesafe.scalalogging.Logger
import com.virtuslab.{TraceId, TraceIdSupport}
import com.virtuslab.auctions.Categories
import com.virtuslab.base.async.{IdentityHelpers, RoutingUtils}
import io.prometheus.client.Histogram
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait AuctionRoutes extends SprayJsonSupport with DefaultJsonProtocol with RoutingUtils {
  this: AuctionService with IdentityHelpers with TraceIdSupport =>

  type AuthFunction = Credentials => Future[Option[String]]

  implicit lazy val cauctrFormat: RootJsonFormat[CreateAuctionRequest] = jsonFormat5(CreateAuctionRequest)
  implicit lazy val mteFormat: RootJsonFormat[MissingTokenError] = jsonFormat1(MissingTokenError)
  implicit lazy val iteFormat: RootJsonFormat[InvalidTokenError] = jsonFormat1(InvalidTokenError)
  implicit lazy val caFormat: RootJsonFormat[CreatedAuction] = jsonFormat1(CreatedAuction)
  implicit lazy val aiFormat: RootJsonFormat[AuctionInfo] = jsonFormat5(AuctionInfo)
  implicit lazy val bidFormat: RootJsonFormat[Bid] = jsonFormat3(Bid)
  implicit lazy val auctionsFormat: RootJsonFormat[Auctions] = jsonFormat2(Auctions)
  implicit lazy val auctionFormat: RootJsonFormat[AuctionResponse] = jsonFormat9(AuctionResponse)
  implicit lazy val bidReqFormat: RootJsonFormat[BidRequest] = jsonFormat1(BidRequest)

  protected val categoriesSet: Set[String] = Categories.toSet

  def rejectionHandler: RejectionHandler =
    RejectionHandler.newBuilder()
      .handle { case MissingQueryParamRejection(_) =>
        complete(BadRequest)
      }
      .handle { case AuthenticationFailedRejection(cause, _) =>
        cause match {
          case CredentialsMissing => complete((Unauthorized, MissingTokenError()))
          case CredentialsRejected => complete((Forbidden, InvalidTokenError()))
        }
      }
      .result()

  protected def logger: Logger

  protected def requestsLatency: Histogram

  protected implicit def executionContext: ExecutionContext

  lazy val auctionRoutes: Route =
    handleRejections(rejectionHandler) {
      optionalHeaderValueByName("X-Trace-Id") { maybeTraceId =>
        implicit val traceId: TraceId = extractTraceId(maybeTraceId)
        val authenticator: AuthFunction = {
          case Credentials.Provided(token) =>
            val histogramTimer = requestsLatency.labels("authenticate").startTimer()
            val result = validateToken(token)
            result.onComplete(_ => histogramTimer.observeDuration())
            result
          case _ => Future.successful(None)
        }
        authenticate(traceId, authenticator) { username =>
          path("auctions" / Segment / "bids") { auctionId =>
            post {
              entity(as[BidRequest]) { request =>
                logger.info(s"[${traceId.id}] Received bid in auction request for auction '$auctionId'.")

                val histogramTimer = requestsLatency.labels("bidInAuction").startTimer()
                val bid = request.enrich(username, auctionId)

                onComplete(bidInAuction(bid)) {
                  case Success(_) =>
                    logger.info(s"[${traceId.id}] Added bid for auction '$auctionId'.")
                    histogramTimer.observeDuration()
                    complete(Created)

                  case Failure(_: BidTooSmall) =>
                    logger.warn(s"[${traceId.id}] Bid was too small for auction '$auctionId'.")
                    histogramTimer.observeDuration()
                    complete(Conflict, Error("your bid is not high enough"))

                  case Failure(_: AuctionNotFound) =>
                    logger.warn(s"[${traceId.id}] Auction '$auctionId' was not found.")
                    histogramTimer.observeDuration()
                    complete(NotFound)

                  case Failure(exception) =>
                    logger.error(s"[${traceId.id}] Error occured while adding bid for auction '$auctionId':", exception)
                    histogramTimer.observeDuration()
                    failWith(exception)
                }
              }
            }
          } ~
            path("auctions" / Segment) { auctionId =>
              get {
                logger.info(s"[${traceId.id}] Got fetch request for auction '$auctionId'.")
                val histogramTimer = requestsLatency.labels("fetchAuction").startTimer()

                onComplete(getAuction(auctionId)) {
                  case Success(auction) =>
                    logger.info(s"[${traceId.id}] Fetched auction '$auctionId'.")
                    histogramTimer.observeDuration()
                    complete(OK, auction)

                  case Failure(AuctionNotFound(_)) =>
                    logger.warn(s"[${traceId.id}] Auction '$auctionId' was not found!")
                    histogramTimer.observeDuration()
                    complete(NotFound)

                  case Failure(exception) =>
                    logger.error(s"[${traceId.id}] Error occured while fetching auction '$auctionId':", exception)
                    histogramTimer.observeDuration()
                    failWith(exception)
                }
              }
            } ~
            path("auctions") {
              get {
                parameter("category") { category =>
                  logger.info(s"[${traceId.id}] Got list auctions request for category '$category'.")
                  if (categoriesSet contains category) {
                    val histogramTimer = requestsLatency.labels("listAuctions").startTimer()

                    onComplete(listAuctions(category)) {
                      case Success(listOfAuctions) =>
                        logger.info(s"[${traceId.id}] Fetched ${listOfAuctions.size} auctions for category '$category'.")
                        histogramTimer.observeDuration()
                        complete(OK, Auctions(category, listOfAuctions))

                      case Failure(exception) =>
                        logger.error(s"[${traceId.id}] Error occured while listing auctions for category '$category':", exception)
                        histogramTimer.observeDuration()
                        failWith(exception)
                    }
                  }
                  else {
                    logger.warn(s"[${traceId.id}] Invalid category: '$category'.")
                    complete(BadRequest)
                  }
                }
              } ~
                post {
                  entity(as[CreateAuctionRequest]) { request =>
                    logger.info(s"[${traceId.id}] Got create auction request for user '$username'.")
                    val histogramTimer = requestsLatency.labels("createAuction").startTimer()

                    onComplete(createAuction(request addOwner username)) {
                      case Success(auctionId) =>
                        logger.info(s"[${traceId.id}] Created auction '$auctionId' for user '$username'.")
                        histogramTimer.observeDuration()
                        complete(Created, CreatedAuction(auctionId))

                      case Failure(exception) =>
                        logger.error(s"[${traceId.id}] Error occured while creating auction for user '$username':", exception)
                        histogramTimer.observeDuration()
                        failWith(exception)
                    }
                  }
                }
            }
        }
      }
    }

  def authenticate(traceId: TraceId, authenticator: AuthFunction): Directive1[String] = {
    authenticateOAuth2Async(realm = "auction-house", authenticator)
  }

}
