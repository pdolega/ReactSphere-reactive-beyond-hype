package com.virtuslab

import com.typesafe.config.{Config => TypesafeConfig, ConfigFactory}

object Config {
  lazy val conf: TypesafeConfig = ConfigFactory.load()
  lazy val cassandraContactPoint: String = conf.getString("cassandra.contactPoint")
}
