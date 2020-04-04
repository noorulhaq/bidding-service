package com.auction.impl

import java.util.UUID
import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.persistence.typed.PersistenceId
import org.scalatest.{FeatureSpecLike, GivenWhenThen, Matchers}
import java.time.Instant
import akka.actor.typed.ActorRef
import Auction._
import BiddingCommands._

class AuctionAggregateSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """) with FeatureSpecLike with GivenWhenThen with Matchers {


  scenario("As an auctioneer, I should be able to start an auction for a specific product, so that it is open for bidding") {

    Given("An auctioneer and a product")
    val auctioneer = UUID.randomUUID
    val product = UUID.randomUUID

    When("auction is started")
    val probe = createTestProbe[Confirmation]()
    val ref = spawn(AuctionBehavior.create(PersistenceId("Auction", UUID.randomUUID.toString)))
    ref ! StartAuction(auctioneer, product, Bid(auctioneer, 10d, Instant.now), Instant.now.plusSeconds(60), probe.ref)

    Then("auction should be running")
    probe.expectMessage(Accepted(Running))
  }


  scenario("As a bidder, I should be able to bid for a product if there is an auction for a product I am interested") {

    Given("A bidder and a running auction")
    val bidder = UUID.randomUUID
    val probe = createTestProbe[Confirmation]()
    val ref = startAuction(probe)

    When("the bidder offer a bid for a product")
    ref ! OfferBid(bidder,12d,probe.ref)

    Then("it should be accepted")
    probe.expectMessage(Accepted(Running))
  }


  scenario("As a bidder, when I offer a bid higher than the last one then my bid should be considered as highest") {

    Given("A bidder and a running auction")
    val bidder = UUID.randomUUID
    val confirmationProbe = createTestProbe[Confirmation]
    val highestBidProbe = createTestProbe[Bid]
    val ref = startAuction(confirmationProbe)

    When("the bidder offer a highest bid for a product")
    ref ! OfferBid(bidder,12d,confirmationProbe.ref)
    ref ! GetHighestBid(highestBidProbe.ref)

    Then("it should be accepted as the highest bid")
    confirmationProbe.expectMessage(Accepted(Running))
    assert(highestBidProbe.expectMessageType[Bid].offer == 12d)
  }

  scenario("As a bidder, when I offer a bid lower than the last one then my bid should not be considered as highest") {

    Given("A bidder and a running auction")
    val bidder = UUID.randomUUID
    val confirmationProbe = createTestProbe[Confirmation]
    val highestBidProbe = createTestProbe[Bid]
    val ref = startAuction(confirmationProbe)

    When("the bidder offer a highest bid for a product")
    ref ! OfferBid(bidder,8d,confirmationProbe.ref)
    ref ! GetHighestBid(highestBidProbe.ref)

    Then("it should be accepted as the highest bid")
    confirmationProbe.expectMessage(Accepted(Running))
    assert(highestBidProbe.expectMessageType[Bid].offer != 8d)
  }


  scenario("A running auction should automatically close after the closing time elapsed") {

    Given("A bidder and a running auction")
    val bidder1 = UUID.randomUUID
    val bidder2 = UUID.randomUUID
    val confirmationProbe = createTestProbe[Confirmation]
    val isClosedProbe = createTestProbe[Confirmation]
    val highestBidProbe = createTestProbe[Bid]
    val ref = startAuction(confirmationProbe,closingAt = Instant.now.plusSeconds(2))

    When("auction is automatically closed")
    ref ! OfferBid(bidder1,14d,confirmationProbe.ref)
    ref ! OfferBid(bidder2,12d,confirmationProbe.ref)
    Thread.sleep(3500)

    Then("auction should transition to closed phase and the winner is also decided")
    ref ! IsClosed(isClosedProbe.ref)
    isClosedProbe.expectMessage(Accepted(Closed))
    ref ! GetHighestBid(highestBidProbe.ref)
    confirmationProbe.expectMessage(Accepted(Running))
    assert(highestBidProbe.expectMessageType[Bid].offer == 14d)
  }

  private def startAuction(probe:TestProbe[Confirmation],auctionId:PersistenceId = PersistenceId("Auction", UUID.randomUUID.toString),
                           auctioneer: Auctioneer = UUID.randomUUID(),
                           product: Product = UUID.randomUUID(),
                           closingAt:Instant = Instant.now.plusSeconds(5)): ActorRef[AuctionCommand] ={
    val ref = spawn(AuctionBehavior.create(auctionId))
    ref ! StartAuction(auctioneer, product, Bid(auctioneer, 10d, Instant.now), closingAt, probe.ref)
    probe.expectMessage(Accepted(Running))
    ref
  }
}
