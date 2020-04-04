package com.auction.impl

import java.util.UUID
import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{EventSourcedBehavior, ReplyEffect}
import com.auction.impl.Auction.Auctioneer
import java.time.Instant
import Auction._
import BiddingCommands._
import BiddingEvents._
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import com.lightbend.lagom.scaladsl.persistence.AkkaTaggerAdapter


trait JsonSerializable

object Auction extends JsonSerializable {

  /**
   * Replies
   */

  sealed trait Confirmation extends JsonSerializable

  case class Accepted(phase:AuctionPhase) extends Confirmation

  case class Rejected(reason: String) extends Confirmation

  /**
   * Auction values objects
   */

  type Product = UUID
  type Auctioneer = UUID
  type Bidder = UUID
  type Amount = Double
  def initial: Auction = Auction(UUID.randomUUID(),UUID.randomUUID(),null)

  val typeKey = EntityTypeKey[AuctionCommand]("AuctionAggregate")

  sealed trait AuctionPhase extends JsonSerializable
  object Halt extends AuctionPhase
  object Running extends AuctionPhase
  object Closed extends AuctionPhase

  case class Bid(bidder: Bidder, offer: Amount, timestamp: Instant)

}

/**
 * Auction state
 */

case class Auction(auctioneer: Auctioneer, product: Product, highestBid: Bid ,closingAt: Instant  = Instant.now, phase: AuctionPhase = Halt){
  def applyCommand(timers: TimerScheduler[AuctionCommand],cmd: AuctionCommand): ReplyEffect[BiddingEvent, Auction] = BiddingCommands.commandHandler(timers,this,cmd)
  def applyEvent(evt: BiddingEvent): Auction = BiddingEvents.eventHandler(this,evt)
}

/**
 * Behavior
 */

object AuctionBehavior {

  /**
   * Given a sharding [[EntityContext]] this function produces an Akka [[Behavior]] for the aggregate.
   */
  def create(entityContext: EntityContext[AuctionCommand]): Behavior[AuctionCommand] = {
    val persistenceId: PersistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)
    create(persistenceId,entityContext)
  }

  private[impl] def create(persistenceId: PersistenceId) =
    Behaviors.withTimers[AuctionCommand] { timers => eventSourcedBehavior(persistenceId,timers)}

  private[impl] def create(persistenceId: PersistenceId,entityContext: EntityContext[AuctionCommand]) =
    Behaviors.withTimers[AuctionCommand] { timers =>
      eventSourcedBehavior(persistenceId,timers).withTagger(
        // Using Akka Persistence Typed in Lagom requires tagging your events
        // in Lagom-compatible way so Lagom ReadSideProcessors and TopicProducers
        // can locate and follow the event streams.
        AkkaTaggerAdapter.fromLagom(entityContext, BiddingEvent.Tag)
      )
    }

  private def eventSourcedBehavior(persistenceId: PersistenceId,timers: TimerScheduler[AuctionCommand]) =
    EventSourcedBehavior
      .withEnforcedReplies[AuctionCommand, BiddingEvent, Auction](
        persistenceId = persistenceId,
        emptyState = Auction.initial,
        commandHandler = (cart, cmd) => cart.applyCommand(timers, cmd),
        eventHandler = (cart, evt) => cart.applyEvent(evt)
      )
}