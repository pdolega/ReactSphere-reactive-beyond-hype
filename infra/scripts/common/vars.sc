object StackType {
  def fromString(name: String): StackType = name.trim.toLowerCase match {
    case "sync" => SyncStack
    case "async" => AsyncStack
    case _ => throw new IllegalArgumentException(s"Unrecognized stack type: ${name}")
  }
}

sealed trait StackType {
  def paradigm: String
}

case object SyncStack extends StackType {
  override val paradigm = "sync"
}
case object AsyncStack extends StackType {
  override val paradigm = "async"
}

val AUCTION_APP = "auction-house-primary"
val AUCTION_PORT = 8080

val BILLING_APP = "billing-service-secondary"
val BILLING_PORT = 8090

val IDENTITY_APP = "identity-service-tertiary"
val IDENTITY_PORT = 8100

val PAYMENT_PORT = 9000
val PAYMENT_SYSTEM = "payment-system"

val CASSANDRA_HOST = "cassandra"
val CASSANDRA_PORT = 9042

def apps = Seq(
  AUCTION_APP -> AUCTION_PORT,
  BILLING_APP -> BILLING_PORT,
  IDENTITY_APP -> IDENTITY_PORT
)

def backingServices = Seq(
  PAYMENT_SYSTEM -> PAYMENT_PORT
)

sealed trait Environment {
  def name: String
}

case object Dev extends Environment {
  def name: String = "dev"
}

case object Prod extends Environment {
  def name: String = "prod"
}

sealed trait Registry {
  def value: String
}

case object Local extends Registry {
  def value: String = "docker-registry.local"
}

case object Quay extends Registry {
  def value: String = "quay.io/virtuslab"
}