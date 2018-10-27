package chargepoint.docile.dsl.expectations

import scala.collection.JavaConverters._
import scala.concurrent.TimeoutException
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.global
import chargepoint.docile.dsl.{AwaitTimeout, CoreOps, OcppConnectionData}
import com.thenewmotion.ocpp.VersionFamily.{V1X, V1XCentralSystemMessages, V1XChargePointMessages}
import com.thenewmotion.ocpp.json.api.OcppError
import com.thenewmotion.ocpp.messages.v1x._
import org.specs2.mutable.Specification

class OpsSpec extends Specification {

  "Ops" should {
    "await for messages ignoring not matched" in {
      val mock = new MutableOpsMock()

      import mock.ops._

      implicit val awaitTimeout: AwaitTimeout = AwaitTimeout(5.seconds)

      mock send GetConfigurationReq(keys = List())
      mock send ClearCacheReq

      val result: Seq[ChargePointReq] =

      expectAllIgnoringUnmatched(
        clearCacheReq respondingWith ClearCacheRes(accepted = true)
      )

      result must_=== Seq(ClearCacheReq)
      mock.responses.size must_=== 1
      mock.responses.head must_=== ClearCacheRes(accepted = true)
    }
  }

  class MutableOpsMock {
    import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue, TimeUnit}

    private val requestsQueue: BlockingQueue[IncomingMessage[CentralSystemReq, CentralSystemRes, CentralSystemReqRes, ChargePointReq, ChargePointRes, ChargePointReqRes]] = new ArrayBlockingQueue[IncomingMessage[CentralSystemReq, CentralSystemRes, CentralSystemReqRes, ChargePointReq, ChargePointRes, ChargePointReqRes]](1000)
    private val responsesQueue: BlockingQueue[ChargePointRes] = new ArrayBlockingQueue[ChargePointRes](1000)

    def responses: Iterable[ChargePointRes] = responsesQueue.asScala

    private def enqueueResponse(x: ChargePointRes): Unit = {
      responsesQueue.put(x)
    }

    def send(req: ChargePointReq): Unit = {
      requestsQueue.put(IncomingRequest[
        CentralSystemReq,
        CentralSystemRes,
        CentralSystemReqRes,
        ChargePointReq,
        ChargePointRes,
        ChargePointReqRes
      ](req, enqueueResponse))
    }

    def send(res: CentralSystemRes): Unit = {
      requestsQueue.put(IncomingResponse[
        CentralSystemReq,
        CentralSystemRes,
        CentralSystemReqRes,
        ChargePointReq,
        ChargePointRes,
        ChargePointReqRes
      ](res))
    }

    def sendError(err: OcppError): Unit = {
      requestsQueue.put(IncomingError[
        CentralSystemReq,
        CentralSystemRes,
        CentralSystemReqRes,
        ChargePointReq,
        ChargePointRes,
        ChargePointReqRes
      ](err))
    }

    val ops: Ops[
      V1X.type,
      CentralSystemReq,
      CentralSystemRes,
      CentralSystemReqRes,
      ChargePointReq,
      ChargePointRes,
      ChargePointReqRes
    ] = new Ops[V1X.type, CentralSystemReq, CentralSystemRes, CentralSystemReqRes, ChargePointReq, ChargePointRes, ChargePointReqRes]
            with CoreOps[V1X.type, CentralSystemReq, CentralSystemRes, CentralSystemReqRes, ChargePointReq, ChargePointRes, ChargePointReqRes] {
      implicit val csMessageTypes = V1XChargePointMessages
      implicit val csmsMessageTypes = V1XCentralSystemMessages
      implicit val executionContext = global

      override protected def connectionData: OcppConnectionData[
        V1X.type,
        CentralSystemReq,
        CentralSystemRes,
        CentralSystemReqRes,
        ChargePointReq,
        ChargePointRes,
        ChargePointReqRes
      ] = {
        throw new AssertionError("This method should not be called")
      }

      override def awaitIncoming(num: Int)(implicit awaitTimeout: AwaitTimeout): Seq[IncomingMessage] = {
        for (_ <- 0 until num) yield {
          val value = requestsQueue.poll(awaitTimeout.timeout.toMillis, TimeUnit.MILLISECONDS)
          if (value == null) {
            throw new TimeoutException("Failed to receive the message on time")
          }
          value
        }
      }
    }
  }
}
