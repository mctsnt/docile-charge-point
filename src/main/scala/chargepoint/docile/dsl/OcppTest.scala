package chargepoint.docile.dsl

import java.net.URI

import chargepoint.docile.dsl.expectations.IncomingMessage

import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.concurrent.duration.DurationInt
import com.thenewmotion.ocpp.Version1X
import com.thenewmotion.ocpp.json.api._
import com.thenewmotion.ocpp.messages.v1x.{ChargePointReq, ChargePointRes}
import com.typesafe.scalalogging.Logger
import javax.net.ssl.SSLContext
import org.slf4j.LoggerFactory


trait OcppTest extends MessageLogging {
  private val connectionLogger = Logger(LoggerFactory.getLogger("connection"))

  /**
    * The current OCPP with some associated data
    *
    * This is a var instead of a val an immutable because I hope this will allow
    * us to write tests that disconnect and reconnect when we have a more
    * complete test DSL.
    */
  protected var connectionData: OcppConnectionData = _

  def runConnected(
    receivedMsgManager: ReceivedMsgManager,
    chargerId: String,
    endpoint: URI,
    version: Version1X,
    authKey: Option[String]
  )(implicit sslContext: SSLContext): Unit = {
    connect(receivedMsgManager, chargerId, endpoint, version, authKey)
    run()
    disconnect()
  }

  private def connect(
    receivedMsgManager: ReceivedMsgManager,
    chargerId: String,
    endpoint: URI,
    version: Version1X,
    authKey: Option[String]
  )(implicit sslContext: SSLContext): Unit = {

    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

    connectionLogger.info(s"Connecting to OCPP v${version.name} endpoint $endpoint")

    val connection: Ocpp1XJsonClient = OcppJsonClient.forVersion1x(chargerId, endpoint, List(version), authKey) {
      req: ChargePointReq =>

        incomingLogger.info(s"$req")

        val responsePromise = Promise[ChargePointRes]()

        def respond(res: ChargePointRes): Unit = {
          outgoingLogger.info(s"$res")
          responsePromise.success(res)
          ()
        }

        receivedMsgManager.enqueue(
          IncomingMessage(req, respond)
        )

        responsePromise.future
    }

    connection.onClose.foreach { _ =>
      connectionLogger.info(s"Gracefully disconnected from endpoint $endpoint")
      connectionData = connectionData.copy(ocppClient = None)
    }

    connectionData = OcppConnectionData(Some(connection), receivedMsgManager, chargerId)
  }

  private def disconnect(): Unit = connectionData.ocppClient.foreach { conn =>
    Await.result(conn.close(), 45.seconds)
  }

  protected def run(): Unit
}

case class OcppConnectionData(
  ocppClient: Option[Ocpp1XJsonClient],
  receivedMsgManager: ReceivedMsgManager,
  chargePointIdentity: String
)
