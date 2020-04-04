package com.auction.impl

import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import org.scalatest.{AsyncWordSpec, BeforeAndAfterAll, Matchers}
import com.auction.api._

class BiddingServiceSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {

  private val server = ServiceTest.startServer(
    ServiceTest.defaultSetup
      .withCassandra()
  ) { ctx =>
    new AuctionApplication(ctx) with LocalServiceLocator
  }

  val client: BiddingService = server.serviceClient.implement[BiddingService]

  override protected def afterAll(): Unit = server.stop()

  "auction service" should {

  }
}
