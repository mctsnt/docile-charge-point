package chargepoint.docile
package test

import scala.language.higherKinds
import scala.concurrent.ExecutionContext.global
import com.thenewmotion.ocpp.VersionFamily
import com.thenewmotion.ocpp.messages.{Request, Response, ReqRes}
import com.thenewmotion.ocpp.messages.v1x.{CentralSystemReq, CentralSystemReqRes, CentralSystemRes, ChargePointReq, ChargePointReqRes, ChargePointRes}
import com.thenewmotion.ocpp.messages.v20.{CsmsRequest, CsmsResponse, CsmsReqRes, CsRequest, CsResponse, CsReqRes}
import dsl._

trait InteractiveOcppTest[VFam <: VersionFamily] {

  self: OcppTest[VFam] =>

  protected def importsSnippet: String =
    """
      |import ops._
      |import promptCommands._
      |import com.thenewmotion.ocpp.messages._
      |
      |import scala.language.postfixOps
      |import scala.concurrent.duration._
      |import scala.util.Random
      |import java.time._
      |
      |import chargepoint.docile.dsl.AwaitTimeout
      |import chargepoint.docile.dsl.Randomized._
      |
      |implicit val rand: Random = new Random()
      |implicit val awaitTimeout: AwaitTimeout = AwaitTimeout(45.seconds)
      |
      """.stripMargin

}

abstract class InteractiveOcpp1XTest extends InteractiveOcppTest[VersionFamily.V1X.type] with Ocpp1XTest {
  val ops: Ocpp1XTest.V1XOps

  protected val promptCommands: InteractiveOcpp1XTest.V1XPromptCommands

  def run(): Unit = {

      val predefCode = importsSnippet +
                       """
                         | import com.thenewmotion.ocpp.messages.v1x._
                       """.stripMargin

      ammonite.Main(predefCode = predefCode).run(
        "ops" -> ops,
        "promptCommands" -> promptCommands
      )

    ()
  }
}

object InteractiveOcpp1XTest {

  trait V1XPromptCommands extends InteractiveOcppTest.PromptCommands[
    VersionFamily.V1X.type,
    CentralSystemReq,
    CentralSystemRes,
    CentralSystemReqRes,
    ChargePointReq,
    ChargePointRes,
    ChargePointReqRes
  ]
}

abstract class InteractiveOcpp20Test extends InteractiveOcppTest[VersionFamily.V20.type] with Ocpp20Test {

  val ops: Ocpp20Test.V20Ops

  val promptCommands: InteractiveOcpp20Test.V20PromptCommands

  def run(): Unit = {

    val predefCode = importsSnippet +
      """
        | import com.thenewmotion.ocpp.messages.v20._
      """.stripMargin

    ammonite.Main(predefCode = predefCode).run(
      "ops" -> ops,
      "promptCommands" -> promptCommands
    )

    ()
  }
}

object InteractiveOcpp20Test {

  trait V20PromptCommands extends InteractiveOcppTest.PromptCommands[
    VersionFamily.V20.type,
    CsmsRequest,
    CsmsResponse,
    CsmsReqRes,
    CsRequest,
    CsResponse,
    CsReqRes
  ]
}

object InteractiveOcppTest {

  def apply(vfam: VersionFamily): OcppTest[vfam.type] = vfam match {
    case VersionFamily.V1X =>

      new InteractiveOcpp1XTest {

        private def connDat = connectionData

        implicit val csmsMessageTypes = VersionFamily.V1XCentralSystemMessages
        implicit val csMessageTypes = VersionFamily.V1XChargePointMessages
        implicit val executionContext = global

        val ops: Ocpp1XTest.V1XOps = new Ocpp1XTest.V1XOps
                with expectations.Ops[VersionFamily.V1X.type, CentralSystemReq, CentralSystemRes, CentralSystemReqRes, ChargePointReq, ChargePointRes, ChargePointReqRes]
                with shortsend.OpsV1X {
          def connectionData = connDat

          implicit val csmsMessageTypes = VersionFamily.V1XCentralSystemMessages
          implicit val csMessageTypes = VersionFamily.V1XChargePointMessages
          implicit val executionContext = global
        }

        val promptCommands: InteractiveOcpp1XTest.V1XPromptCommands = new InteractiveOcpp1XTest.V1XPromptCommands {
          def connectionData = connDat
        }
      }.asInstanceOf[OcppTest[vfam.type]]

    case VersionFamily.V20 =>

      new InteractiveOcpp20Test {

        private def connDat = connectionData

        implicit val csmsMessageTypes = VersionFamily.V20CsmsMessages
        implicit val csMessageTypes = VersionFamily.V20CsMessages
        implicit val executionContext = global

        val ops: Ocpp20Test.V20Ops = new Ocpp20Test.V20Ops
                with expectations.Ops[VersionFamily.V20.type, CsmsRequest, CsmsResponse, CsmsReqRes, CsRequest, CsResponse, CsReqRes] {
          def connectionData = connDat

          implicit val csmsMessageTypes = VersionFamily.V20CsmsMessages
          implicit val csMessageTypes = VersionFamily.V20CsMessages
          implicit val executionContext = global
        }

        val promptCommands: InteractiveOcpp20Test.V20PromptCommands = new InteractiveOcpp20Test.V20PromptCommands {
          def connectionData = connDat
        }
      }.asInstanceOf[OcppTest[vfam.type]]
  }

  trait PromptCommands[
    VFam <: VersionFamily,
    OutReq <: Request,
    InRes <: Response,
    OutReqRes[_ <: OutReq, _ <: InRes] <: ReqRes[_, _],
    InReq <: Request,
    OutRes <: Response,
    InReqRes[_ <: InReq, _ <: OutRes] <: ReqRes[_, _]
  ] {

    protected def connectionData: OcppConnectionData[VFam, OutReq, InRes, OutReqRes, InReq, OutRes, InReqRes]

    def q: Unit =
      connectionData.receivedMsgManager.currentQueueContents foreach println

    def whoami: Unit =
      println(connectionData.chargePointIdentity)
  }
}
