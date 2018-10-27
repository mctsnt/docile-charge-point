package chargepoint.docile
package dsl

import java.util.concurrent.TimeoutException

import com.thenewmotion.ocpp.VersionFamily
import com.thenewmotion.ocpp.VersionFamily.{CsMessageTypesForVersionFamily, CsmsMessageTypesForVersionFamily}

import scala.language.higherKinds
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}
import com.thenewmotion.ocpp.json.api.{OcppError, OcppException}
import com.thenewmotion.ocpp.messages.{ReqRes, Request, Response}
import com.typesafe.scalalogging.Logger
import expectations.{IncomingMessage => GenericIncomingMessage}
import org.slf4j.LoggerFactory

trait CoreOps[
  VFam <: VersionFamily,
  OutReq <: Request,
  InRes <: Response,
  OutReqRes[_ <: OutReq, _ <: InRes] <: ReqRes[_, _],
  InReq <: Request,
  OutRes <: Response,
  InReqRes[_ <: InReq, _ <: OutRes] <: ReqRes[_, _]
] extends OpsLogging with MessageLogging {


  implicit val csmsMessageTypes: CsmsMessageTypesForVersionFamily[VFam, OutReq, InRes, OutReqRes]
  implicit val csMessageTypes:     CsMessageTypesForVersionFamily[VFam, InReq, OutRes, InReqRes]

  implicit def executionContext: ExecutionContext

  type IncomingMessage = GenericIncomingMessage[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes]
  object IncomingMessage {
    def apply(res: InRes): IncomingMessage = GenericIncomingMessage[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes](res)
    def apply(req: InReq, respond: OutRes => Unit): IncomingMessage = GenericIncomingMessage[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes](req, respond)
    def apply(error: OcppError): IncomingMessage = GenericIncomingMessage[OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes](error)
  }

  val logger = Logger(LoggerFactory.getLogger("script"))
  def say(m: String): Unit = logger.info(m)

  protected def connectionData: OcppConnectionData[VFam, OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes]

  /**
   * Send an OCPP request to the Central System under test.
   *
   * This method works asynchronously. That means the method call returns immediately, and does not return the response.
   *
   * To get the response, await the incoming message using the awaitIncoming method defined below, or use the
   * synchronous methods from the "shortsend" package.
   *
   * @param req
   * @param reqRes
   * @tparam Q
   */
  def send[Q <: OutReq](req: Q)(implicit reqRes: OutReqRes[Q, _ <: InRes]): Unit =
    connectionData.ocppClient match {
      case None =>
        throw ExpectationFailed("Trying to send an OCPP message while not connected")
      case Some (client) =>
        outgoingLogger.info(s"$req")
        client.send(req)(reqRes) onComplete {
          case Success(res) =>
            incomingLogger.info(s"$res")
            connectionData.receivedMsgManager.enqueue(
              IncomingMessage(res)
            )
          case Failure(OcppException(ocppError)) =>
            incomingLogger.info(s"$ocppError")
            connectionData.receivedMsgManager.enqueue(
              IncomingMessage(ocppError)
            )
          case Failure(e) =>
            opsLogger.error(s"Failed to get response to outgoing OCPP request $req: ${e.getMessage}\n\t${e.getStackTrace.mkString("\n\t")}")
            throw ExecutionError(e)
    }
  }

  def awaitIncoming(num: Int)(implicit awaitTimeout: AwaitTimeout): Seq[IncomingMessage] = {

    val AwaitTimeout(timeout) = awaitTimeout
    def getMsgs = connectionData.receivedMsgManager.dequeue(num)

    Try(Await.result(getMsgs, timeout)) match {
      case Success(msgs)                => msgs
      case Failure(e: TimeoutException) => fail(s"Expected message not received after $timeout")
      case Failure(e)                   => error(e)
    }
  }

  /**
   * Throw away all incoming messages that have not yet been awaited.
   *
   * This can be used in interactive mode to get out of a situation where you've received a bunch of messages that you
   * don't really care about, and you want to get on with things.
   */
  def flushQ(): Unit = connectionData.receivedMsgManager.flush()

  def fail(message: String): Nothing = throw ExpectationFailed(message)

  def error(throwable: Throwable): Nothing = throw ExecutionError(throwable)

  def sleep(duration: Duration): Unit = {
    opsLogger.info(s"Sleeping for $duration")
    Thread.sleep(duration.toMillis)
  }

  def prompt(cue: String): String = {
    println(s"$cue: ")
    scala.io.StdIn.readLine()
  }
}
