package com.auction.impl

import java.time.Instant
import akka.actor.typed.ActorRef
import akka.persistence.typed.scaladsl.{Effect, ReplyEffect}
import com.auction.impl.Auction.{Accepted, Amount, Auctioneer, Bid, Bidder, Closed, Confirmation, Halt, Product, Rejected, Running}
import BiddingEvents._
import akka.actor.typed.scaladsl.TimerScheduler
import scala.concurrent.duration.{Duration, MILLISECONDS}

package object BiddingCommands {

  sealed trait AuctionCommand extends JsonSerializable

  case class StartAuction(auctioneer: Auctioneer, product: Product, initialBid: Bid,closingAt: Instant, replyTo: ActorRef[Confirmation]) extends AuctionCommand

  case class OfferBid(bidder: Bidder, offer: Amount, replyTo: ActorRef[Confirmation]) extends AuctionCommand

  case class GetHighestBid(replyTo: ActorRef[Bid]) extends AuctionCommand

  case class FinishAuction(replyTo: ActorRef[Confirmation]) extends AuctionCommand

  case class IsClosed(replyTo: ActorRef[Confirmation]) extends AuctionCommand

  val commandHandler : (TimerScheduler[AuctionCommand],Auction,AuctionCommand) => ReplyEffect[BiddingEvent, Auction] = (timers, auction, cmd) =>
    auction.phase match {
      case Halt => halt(timers,auction,cmd)
      case Running => running(auction,cmd)
      case Closed => finished(auction,cmd)
    }

  def halt(timers: TimerScheduler[AuctionCommand],auction:Auction, cmd: AuctionCommand): ReplyEffect[BiddingEvent, Auction] = cmd match {
    case StartAuction(auctioneer, product, initialBid, closingAt, replyTo) => {
      val millisUntilClosing = closingAt.toEpochMilli - System.currentTimeMillis()
      timers.startSingleTimer("FinishTimer", FinishAuction(replyTo), Duration(millisUntilClosing, MILLISECONDS))
      Effect.persist(AuctionStarted(auctioneer, product, initialBid, closingAt)).thenReply(replyTo)(a => Accepted(a.phase))
    }
    case IsClosed(replyTo) => Effect.reply(replyTo)(Rejected("Auction is not started yet."))
  }

  def running(auction:Auction, cmd: AuctionCommand): ReplyEffect[BiddingEvent, Auction] = cmd match {
    case StartAuction(_, product, _, _, replyTo) => Effect.reply(replyTo)(Rejected(s"Auction for product $product is already open for bidding."))
    case OfferBid(bidder, amount, replyTo) =>
      Effect.persist(BidRegistered(Bid(bidder, amount, Instant.now))).thenReply(replyTo)(a => Accepted(a.phase))
    case GetHighestBid(replyTo) => Effect.reply(replyTo)(auction.highestBid)
    case FinishAuction(replyTo) => Effect.persist(AuctionFinished(auction.highestBid)).thenReply(replyTo)(a => Accepted(a.phase))
    case IsClosed(replyTo) => Effect.reply(replyTo)(Accepted(Running))
  }

  def finished(auction:Auction, cmd: AuctionCommand): ReplyEffect[BiddingEvent, Auction] = cmd match {
    case IsClosed(replyTo) => Effect.reply(replyTo)(Accepted(Closed))
    case GetHighestBid(replyTo) => Effect.reply(replyTo)(auction.highestBid)
    case OfferBid(_,_,replyTo) => Effect.reply(replyTo)(Rejected("Auction is closed."))
    case FinishAuction(replyTo) => Effect.reply(replyTo)(Rejected("Auction is already finished."))
  }
}