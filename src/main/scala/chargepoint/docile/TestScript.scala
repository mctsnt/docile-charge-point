package chargepoint.docile

import java.net.URI

import scala.language.{higherKinds, postfixOps}
import cats.Monad
import cats.implicits._
import com.thenewmotion.ocpp.messages._
import dsl.CoreOps

object TestScript {

  def connectAndSendBootAndBye[F[_]: Monad](ops: CoreOps[F]): F[Unit] = {

    import ops._

    for {
      _ <- connect("03000001",new URI("ws://localhost:8017/ocppws"), None)
      _ <- send(BootNotificationReq(
        chargePointVendor = "NewMotion",
        chargePointModel = "Lolo 1337",
        chargePointSerialNumber = Some("03000001"),
        chargeBoxSerialNumber = Some("03000001"),
        firmwareVersion = Some("1"),
        iccid = None,
        imsi = None,
        meterType = None,
        meterSerialNumber = None)
      )
      _ <- expectIncoming printingTheMessage; // matching { case _: BootNotificationRes => };
      _ <- expectIncoming printingTheMessage;
      _ <- expectIncoming.getConfigurationReq.respondingWith(GetConfigurationRes(List(KeyValue(key = "aap", readonly = true, value = Some("zlurf"))), List("schaap", "blaat")))

    // meant to fail, but at least make us wait for above response to arrive
      _ <- expectIncoming printingTheMessage;
      _ <- disconnect()
    } yield ()
  }

}
