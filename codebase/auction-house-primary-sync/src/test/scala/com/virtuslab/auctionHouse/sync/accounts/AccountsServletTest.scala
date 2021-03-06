package com.virtuslab.auctionHouse.sync.accounts

import com.datastax.driver.mapping.Mapper
import com.virtuslab.auctionHouse.sync.BaseServletTest
import com.virtuslab.auctionHouse.sync.cassandra.Account
import org.mockito.Mockito._


class AccountsServletTest extends BaseServletTest(classOf[TestableAccountsServlet]) {

  "Accounts servlet" should {
    "create account" when {
      "user provide correct credentials" in {
        post("/", """{"username": "u1", "password" : "p1"}""", jsonHeader) {
          status should equal (201)
        }
      }
    }

    "return bad request" when {
      "user already exists" in {
        post("/", """{"username": "u2", "password" : "p2"}""", jsonHeader) {
          status should equal (400)
        }
      }
    }
  }
}

class TestableAccountsServlet extends AccountsServlet {

  private val mapperMock = mock(classOf[Mapper[Account]])
  when(mapperMock.get("u1")).thenReturn(null)
  when(mapperMock.get("u2")).thenReturn(new Account("u2", "p2"))

  override lazy val accountService = new AccountService {
    override lazy val accountMapper: Mapper[Account] = mapperMock
  }
}