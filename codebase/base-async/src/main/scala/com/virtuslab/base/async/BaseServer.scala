package com.virtuslab.base.async

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.scalalogging.Logger
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

trait BaseServer {

  DefaultExports.initialize()
  private val metricsServer = new HTTPServer(8081)
  protected def logger: Logger

  lazy implicit val system: ActorSystem = ActorSystem("auctionHouseServer")
  lazy implicit val materializer: Materializer = ActorMaterializer()
  protected lazy implicit val executionContext: ExecutionContext = system.dispatcher

  def routes: Route

  def main(args: Array[String]) {

    val port = Option(System.getProperty("http.port")).map(_.toInt).getOrElse(8080)

    Http().bindAndHandle(routes, "0.0.0.0", port)

    logger.info(s"Server online at http://0.0.0.0:$port/")

    Await.result(system.whenTerminated, Duration.Inf)
    metricsServer.stop()
  }

}
