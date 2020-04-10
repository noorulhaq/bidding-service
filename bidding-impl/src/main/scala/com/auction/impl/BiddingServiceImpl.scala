package com.auction.impl

import java.time.Instant
import java.util.UUID

import akka.NotUsed
import com.auction.api.BiddingService
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRegistry}
import BiddingCommands._
import akka.util.Timeout
import com.auction.impl.Auction._
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.transport.BadRequest
import com.lightbend.lagom.scaladsl.broker.TopicProducer

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
/**
  * Implementation of the AuctionService.
  */
class BiddingServiceImpl(
  clusterSharding: ClusterSharding,
  persistentEntityRegistry: PersistentEntityRegistry
)(implicit ec: ExecutionContext)
  extends BiddingService {

  implicit val timeout = Timeout(5.seconds)

  /**
   * Looks up the entity for the given ID.
   */
  private def entityRef(id: String): EntityRef[AuctionCommand] =
    clusterSharding.entityRefFor(Auction.typeKey, id)


  override def isAuctionClosed(id: String): ServiceCall[NotUsed, Boolean] = ServiceCall {
    _ =>
      // Look up the sharded entity (aka the aggregate instance) for the given ID.
      val ref = entityRef(id)
      // Ask the aggregate instance the IsClosed command.
      ref.ask[Confirmation](replyTo => IsClosed(replyTo)).map {
        case Accepted(Closed) => true
        case Accepted(_) => false
        case Rejected(msg) => throw BadRequest(msg)
      }
  }

  override def startAuction(): ServiceCall[NewAuction, String] = ServiceCall {

    newAuction => {
      val auctionId = newAuction.product
      // Look up the sharded entity (aka the aggregate instance) for the given ID.
      val auction = entityRef(auctionId)

      val startAuctionCommand = replyTo =>
        StartAuction(UUID.fromString(newAuction.auctioneer),
          UUID.fromString(newAuction.product),
          Bid(UUID.fromString(newAuction.auctioneer),
            10d, Instant.now),
          Instant.now.plusSeconds(86400),
          replyTo)

      auction.ask[Confirmation](replyTo => startAuctionCommand(replyTo))
        .map {
          case Accepted(Running) => auctionId
          case Rejected(msg) => throw BadRequest(msg)
          case _ => throw BadRequest("Unable to start auction.")
        }
    }
  }

  override def offerBid(id: String): ServiceCall[NewBid, String] = ServiceCall {

    bid =>
      val auction = entityRef(id)

      val offerBidCommand = replyTo => OfferBid(UUID.fromString(bid.bidder),bid.amount,replyTo)

      auction.ask[Confirmation](replyTo => offerBidCommand(replyTo)).map{
        case Accepted(Running) => "accepted"
        case Rejected(msg) => throw BadRequest(msg)
        case _ => throw BadRequest("Unable to accept bid.")
      }
  }

  override def getHighestBid(id: String): ServiceCall[NotUsed, HighestBid] = ServiceCall {
    _ =>
      val auction = entityRef(id)
      auction.ask[Bid](replyTo => GetHighestBid(replyTo)).map(bid => HighestBid(bid.bidder.toString,bid.offer,bid.timestamp))
  }

  override def close(id: String): ServiceCall[NotUsed, String] = ServiceCall {
    _ =>
      val auction = entityRef(id)
      auction.ask[Confirmation](replyTo => FinishAuction(replyTo)).map {
        case Accepted(Closed) => "closed"
      }
  }

  override def auctionsTopic(): Topic[AuctionStarted] =
    TopicProducer.singleStreamWithOffset { fromOffset =>
      persistentEntityRegistry
        .eventStream(BiddingEvents.BiddingEvent.Tag, fromOffset)
        .map(ev => (convertEvent(ev), ev.offset))
    }

    private def convertEvent(ese: EventStreamElement[BiddingEvents.BiddingEvent]): AuctionStarted = {
      ese.event match {
        case BiddingEvents.AuctionStarted(auctioneer, product, initialBid, closingAt) => {
          AuctionStarted(ese.entityId, auctioneer.toString, product.toString, initialBid.offer, closingAt)
        }
      }
    }
}
