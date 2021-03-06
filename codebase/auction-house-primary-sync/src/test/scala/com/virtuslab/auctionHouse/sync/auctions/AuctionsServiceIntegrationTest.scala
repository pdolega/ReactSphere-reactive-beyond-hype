package com.virtuslab.auctionHouse.sync.auctions

import java.util.{Date, UUID}

import com.datastax.driver.mapping.Mapper
import com.virtuslab.auctionHouse.sync.auctions.AuctionsService.{InvalidCategoryException, UnknownOwnerException}
import com.virtuslab.auctionHouse.sync.cassandra.{Account, Auction, Categories, SessionManager}
import com.virtuslab.auctionHouse.sync.commons.ServletModels.CreateAuctionRequest
import com.virtuslab.auctionhouse.cassandra.CassandraIntegrationTest
import org.json4s.jackson.JsonMethods.parse
import org.scalatest.{Matchers, WordSpec}

class AuctionsServiceIntegrationTest extends WordSpec with CassandraIntegrationTest with Matchers {

  val sessionManager = new SessionManager {
    override lazy val session = getSession
  }

  val auctionsService = new AuctionsService {
    override lazy val auctionsMapper: Mapper[Auction] = sessionManager.mapper(classOf[Auction])
    override lazy val accountsMapper = sessionManager.mapper(classOf[Account])
    override lazy val session = sessionManager.session
  }

  private def randomAuction = new Auction(Categories.head, new java.util.Date(),
    UUID.randomUUID(), "a", "a", "a", "a", new java.math.BigDecimal(0))

  "Listing auctions" should {
    "return empty list" when {
      "there is no auctions" in {
        assert(auctionsService.listAuctions(Categories.head).auctions.isEmpty)
      }
    }

    "return non empty list" when {
      "there are auctions" in {
        auctionsService.auctionsMapper.save(randomAuction)
        auctionsService.auctionsMapper.save(randomAuction)
        auctionsService.listAuctions(Categories.head).auctions.size should equal(2)
        auctionsService.listAuctions(Categories.last).auctions.isEmpty should equal(true)
      }
    }

    "limit results to 10" when {
      "amount is > 10" in {
        (0 until 12).foreach(_ =>
          auctionsService.auctionsMapper.save(randomAuction)
        )
        auctionsService.listAuctions(Categories.head).auctions.size should equal(10)
      }
    }

    "throws exception" when {
      "category is unknown" in {
        intercept[InvalidCategoryException] {
          auctionsService.listAuctions("foo")
        }
      }
    }
  }


  "Creating auctions" should {
    "successfully create auction" when {
      "correct data is provided" in {
        val req = CreateAuctionRequest(Categories.head, "t1", "desc1", 1, parse("""{"details": "foo"}"""))
        val beforeCreation = new Date().getTime
        auctionsService.accountsMapper.save(new Account("o1", "p1"))
        val id = auctionsService.createAuction(req, "o1")
        id.category should equal(Categories.head)
        id.createdAt should be <= new Date().getTime
        id.createdAt should be >= beforeCreation
      }
    }

    "throw exception" when {
      "owner is invalid" in {
        val req = CreateAuctionRequest(Categories.head, "t1", "desc1", 1, parse("""{"details": "foo"}"""))
        intercept[UnknownOwnerException] {
          auctionsService.createAuction(req, "foo_owner")
        }
      }
    }
  }
}
