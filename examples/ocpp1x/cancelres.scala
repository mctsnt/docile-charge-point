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

say("Waiting for cancel reservation")

val noTimeout: AwaitTimeout = InfiniteAwaitTimeout
val reserveRequest = expectIncoming(cancelReservationReq.respondingWith(CancelReservationRes(true)))(noTimeout)

say("Received cancelation")


// vim: set ts=4 sw=4 et:
