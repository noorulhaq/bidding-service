package com.auction.api

import java.time.Instant
import akka.{NotUsed}
import com.lightbend.lagom.scaladsl.api.broker.{Message, Topic}
import com.lightbend.lagom.scaladsl.api.broker.kafka.{KafkaProperties, PartitionKeyStrategy}
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import play.api.libs.json._
import com.lightbend.lagom.scaladsl.api.transport.Method
import julienrf.json.derived

object BiddingService  {
  val TOPIC_NAME = "auctionEvents"
}

/**
  * The bidding service interface.
  * <p>
  * This describes everything that Lagom needs to know about how to serve and
  * consume the BiddingService.
  */
trait BiddingService extends Service {

  def isAuctionClosed(id: String): ServiceCall[NotUsed, Boolean]

  def startAuction(): ServiceCall[NewAuction,String]

  def offerBid(id: String): ServiceCall[NewBid,String]

  def getHighestBid(id: String): ServiceCall[NotUsed,HighestBid]

  def close(id: String): ServiceCall[NotUsed,String]

  def auctionsTopic(): Topic[PublishableBiddingEvent]

  override final def descriptor: Descriptor = {
    import Service._

    // @formatter:off
    named("auction")
      .withCalls(
        restCall(Method.GET,"/api/auction/:id/closed", isAuctionClosed _),
        restCall(Method.GET,"/api/auction/:id/highestBid", getHighestBid _),
        restCall(Method.POST,"/api/auction", startAuction _),
        restCall(Method.PUT,"/api/auction/:id", offerBid _),
        restCall(Method.PUT,"/api/auction/:id/closed/true", close _),
      )
      .withTopics(
        topic(BiddingService.TOPIC_NAME, auctionsTopic _)
          // Kafka partitions messages, messages within the same partition will
          // be delivered in order, to ensure that all messages for the same user
          // go to the same partition (and hence are delivered in order with respect
          // to that user), we configure a partition key strategy that extracts the
          // name as the partition key.
          .addProperty(
            KafkaProperties.partitionKeyStrategy,
            PartitionKeyStrategy[PublishableBiddingEvent](_.auctionId)
          )
      )
      .withAutoAcl(true)
  }
  // @formatter:on


  sealed trait PublishableBiddingEvent{
    def auctionId: String
  }

  case class AuctionStarted(auctionId: String,auctioneer: String, product: String, initialBid: Double,closingAt: Instant) extends PublishableBiddingEvent

  case class BidRegistered(auctionId: String,bidder: String, offer: Double, timestamp: Instant) extends PublishableBiddingEvent

  case class AuctionFinished(auctionId: String,bidder: String, offer: Double, timestamp: Instant) extends PublishableBiddingEvent

  case class NewAuction(auctioneer: String, product: String)

  case class NewBid(bidder: String, amount: Double)

  case class HighestBid(bidder: String, offer: Double, timestamp: Instant)

  object PublishableBiddingEvent {
    implicit val format: OFormat[PublishableBiddingEvent] = derived.flat.oformat((__ \ "event_type").format[String])
  }

  object NewAuction {
    implicit val format: Format[NewAuction] = Json.format[NewAuction]
  }

  object NewBid{
    implicit val format: Format[NewBid] = Json.format[NewBid]
  }

  object HighestBid{
    implicit val format: Format[HighestBid] = Json.format[HighestBid]
  }
}