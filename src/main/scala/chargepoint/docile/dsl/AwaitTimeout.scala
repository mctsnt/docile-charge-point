package chargepoint.docile.dsl

import scala.concurrent.duration._

sealed trait AwaitTimeout {
  def toDuration: Duration
}
case class AwaitTimeoutInMillis(millis: Int) extends AwaitTimeout {
  def toDuration: Duration = millis.milliseconds
}
case object InfiniteAwaitTimeout extends AwaitTimeout {
  def toDuration: Duration = Duration.Inf
}
