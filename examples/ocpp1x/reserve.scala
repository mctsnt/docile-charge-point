import java.time.ZonedDateTime
import chargepoint.docile.dsl._
import com.thenewmotion.ocpp.messages.v1x._
import scala.concurrent.duration._
import scala.util._

/**
 * Continuously listens to reservation requests.
 * reservation.head = faulted
 * reservation.tail.head = unaivalable
 * reservation.tail.tail.head = accepted
 */

say("Waiting for reservation")

val noTimeout: AwaitTimeout = InfiniteAwaitTimeout
val r= Reservation.values.tail.tail.head
val reserveRequest = expectIncoming(reserveNowReq.respondingWith(ReserveNowRes(r)))(noTimeout)
val chargeTokenId = reserveRequest.idTag
val rid = reserveRequest.reservationId

say("Received reservation, authorizing...")
val auth = authorize(chargeTokenId).idTag
if (auth.status == AuthorizationStatus.Accepted) {
  say("Obtained authorization from Central System")
  Thread.sleep(2000) // to simulate real chargepoint behaviour

  statusNotification(status = ChargePointStatus.Available())

} else {
  say("Authorization denied by Central System")
  fail("Not authorized")
}

// vim: set ts=4 sw=4 et:
