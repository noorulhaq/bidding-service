package com.auction.impl

import java.time.Instant
import com.auction.impl.Auction.{Auctioneer, Bid, Closed, Product, Running}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag}

package object BiddingEvents {

  sealed trait BiddingEvent extends AggregateEvent[BiddingEvent] with JsonSerializable {
    def aggregateTag: AggregateEventTag[BiddingEvent] = BiddingEvent.Tag
  }

  object BiddingEvent {
    val Tag: AggregateEventTag[BiddingEvent] = AggregateEventTag[BiddingEvent]
  }

  case class AuctionStarted(auctioneer: Auctioneer, product: Product, initialBid: Bid, closingAt: Instant) extends BiddingEvent

  case class BidRegistered(bid: Bid) extends BiddingEvent

  case object AuctionFinished extends BiddingEvent

  val eventHandler : (Auction,BiddingEvent) => Auction = (auction, evt) =>
    evt match {
      case AuctionStarted(auctioneer, product, initialBid, closingAt) => auction.copy(auctioneer, product, initialBid, closingAt, Running)
      case BidRegistered(bid) => if(isHighestBid(bid,auction.highestBid)) withHighestBid(auction,bid) else auction
      case AuctionFinished => auction.copy(phase = Closed)
    }

  def withHighestBid(auction:Auction, bid: Bid): Auction = {
    require(auction.phase==Running)
    require(isHighestBid(bid,auction.highestBid))
    auction.copy(highestBid=bid)
  }

  def isHighestBid(first:Bid, second:Bid): Boolean = {
    first.offer > second.offer || (first.offer == second.offer && first.timestamp.isBefore(second.timestamp))
  }
}