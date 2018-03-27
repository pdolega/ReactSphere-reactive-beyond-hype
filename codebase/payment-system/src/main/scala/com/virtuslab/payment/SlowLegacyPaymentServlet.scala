package com.virtuslab.payment

import com.virtuslab.base.sync.BaseServlet
import com.virtuslab.payments.PaymentModel.PaymentRequest
import org.scalatra.Ok



class SlowLegacyPaymentServlet extends BaseServlet {

  override def servletName: String = "Payments"

  val timeoutMs = 1000L

  protected def timout = timeoutMs

  post("/") {
    timing("payment") {
      val req = parsedBody.extract[PaymentRequest]
      logger.debug(s"Payment request received: payer: ${req.payer}, payee: ${req.payee}, amount (cents): ${req.amount}")
      Thread.sleep(1000L)
      Ok()
    }
  }
}