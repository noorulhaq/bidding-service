package com.auction.impl

import java.util.UUID
import scala.concurrent.duration._
import akka.stream.testkit.scaladsl.TestSink
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.{ServiceTest, TestTopicComponents}
import org.scalatest.{AsyncFeatureSpec, BeforeAndAfterAll, GivenWhenThen, Matchers}
import com.auction.api._

class BiddingServiceSpec extends AsyncFeatureSpec with GivenWhenThen with Matchers with BeforeAndAfterAll {

  scenario("As an auctioneer, I should be able to start auction for a specific product, so that it is open for bidding") {

    ServiceTest.withServer(ServiceTest.defaultSetup.withCassandra()) { ctx =>
      new AuctionApplication(ctx) with LocalServiceLocator with TestTopicComponents
    } { server =>

        implicit val system = server.actorSystem
        implicit val mat = server.materializer

        Given("An auctioneer and a product")
        val auctioneer: String = UUID.randomUUID().toString
        val product: String = UUID.randomUUID().toString

        val client: BiddingService = server.serviceClient.implement[BiddingService]

        When("auction is started")
        val response = client.startAuction().invoke(client.NewAuction(auctioneer, product))

        Then("the product is open for bidding")
        response.map (_ should === (product))
        val source = client.auctionsTopic().subscribe.atMostOnceSource
        source.runWith(TestSink.probe[client.PublishableBiddingEvent])
          .requestNext(Duration.create(1,"minute")) shouldBe a [client.AuctionStarted]
    }
  }
}