package com.virtuslab.auctionHouse.sync.auctions

import java.util.UUID

import com.datastax.driver.core.{ResultSet, Session, Statement}
import com.datastax.driver.mapping.{Mapper, Result}
import com.virtuslab.auctionHouse.sync.BaseServletTest
import com.virtuslab.auctionHouse.sync.cassandra._
import com.virtuslab.auctionHouse.sync.commons.ServletModels.{Auctions, CreateAuctionRequest}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{mock, when}
import org.scalatra.test.JettyContainer
import org.scalatra.{BadRequest, Ok, Unauthorized}

class AuctionsServletTest extends BaseServletTest(classOf[TestableAuctionsServlet]) { scalatraTest: JettyContainer =>

  addServlet(classOf[AuctionsServlet], "/original/*")

  "Listing auctions" should {
    "return unauthorized" when {
      "user is not logged" in {
        get(s"/original/?category=${Categories.head}") {
          status should equal(Unauthorized().status)
        }
      }
    }

    "return bad request" when {
      "requested category is unknown" in {
        get("/?category=foo") {
          status should equal(BadRequest().status)
        }
      }

      "requested category is not specified" in {
        get("/") {
          status should equal(BadRequest().status)
        }
      }
    }

    "return auctions" when {
      "category is specified" in {
        get(s"/?category=${Categories.head}") {
          status should equal(Ok().status)
          val auctions = parse(body).extract[Auctions]
          auctions.category should equal(Categories.head)
          auctions.auctions.size should equal(1)
        }
      }
    }
  }

  "Creating auctions" should {
    "return unauthorized" when {
      "user is not logged" in {
        post(s"/original/") {
          status should equal(Unauthorized().status)
        }
      }
    }

    "return auction id" when {
      "auction was created successfully" in {
        post("/",
          s"""{ "category": "c1", "title": "t1", "description": "d1", "minimumPrice": 1,
             | "details": {"some": "details"} }""".stripMargin, jsonHeader) {
          status should equal(Ok().status)
          body should equal(AuctionId(Categories.head, 1, TestableAuctionsServlet.auctionUuid).idString)
        }
      }
    }
  }
}

class TestableAuctionsServlet extends AuctionsServlet {
  import scala.collection.JavaConverters._

  override def auth[T](fun: Account => T): T = fun(new Account("u1", "p1"))

  private val mapperMock = mock(classOf[Mapper[Auction]])
  private val sessionMock = mock(classOf[Session])
  private val resultMock = mock(classOf[Result[Auction]])


  when(mapperMock.map(ArgumentMatchers.any(classOf[ResultSet]))).thenReturn(resultMock)
  when(sessionMock.execute(ArgumentMatchers.any(classOf[Statement]))).thenReturn(mock(classOf[ResultSet]))
  when(resultMock.all()).thenReturn(seqAsJavaList(Seq(new Auction(Categories.head, new java.util.Date(),
    UUID.randomUUID(), "a", "a", "a", "a", new java.math.BigDecimal(0)))))

  override lazy val auctionsService = new AuctionsService {
    override lazy val auctionsMapper = mapperMock
    override lazy val session = sessionMock

    override def createAuction(auctionRequest: CreateAuctionRequest, owner: String): AuctionId = {
      AuctionId(Categories.head, 1, TestableAuctionsServlet.auctionUuid)
    }
  }
}
object TestableAuctionsServlet {
  val auctionUuid = UUID.randomUUID()
}