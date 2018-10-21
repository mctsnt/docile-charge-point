package chargepoint.docile
package test

import scala.concurrent.ExecutionContext.global
import com.thenewmotion.ocpp.VersionFamily
import com.thenewmotion.ocpp.messages.v1x.{CentralSystemReq, CentralSystemReqRes, CentralSystemRes, ChargePointReq, ChargePointReqRes, ChargePointRes}
import com.thenewmotion.ocpp.messages.v20._
import dsl._

trait InteractiveOcppTest[VFam <: VersionFamily] {

  self: OcppTest[VFam] =>

  trait PromptCommands {

    def q: Unit =
      connectionData.receivedMsgManager.currentQueueContents foreach println

    def whoami: Unit =
      println(connectionData.chargePointIdentity)
  }

  protected val promptCommands = new PromptCommands {}

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

trait InteractiveOcpp1XTest extends InteractiveOcppTest[VersionFamily.V1X.type] with Ocpp1XTest {
  trait V1XOps extends CoreOps[
    VersionFamily.V1X.type,
    CentralSystemReq,
    CentralSystemRes,
    CentralSystemReqRes,
    ChargePointReq,
    ChargePointRes,
    ChargePointReqRes
  ]

  val ops: V1XOps

  def run(): Unit = {


      ammonite.Main(predefCode = importsSnippet).run(
        "ops" -> ops,
        "promptCommands" -> promptCommands
      )

    ()
  }
}

trait InteractiveOcpp20Test extends InteractiveOcppTest[VersionFamily.V20.type] with Ocpp20Test {
  trait V20Ops extends CoreOps[
    VersionFamily.V20.type,
    CsmsRequest,
    CsmsResponse,
    CsmsReqRes,
    CsRequest,
    CsResponse,
    CsReqRes
  ]

  val ops: V20Ops

  def run(): Unit = {


    ammonite.Main(predefCode = importsSnippet).run(
      "ops" -> ops,
      "promptCommands" -> promptCommands
    )

    ()
  }
}

object InteractiveOcppTest {

  def apply(vfam: VersionFamily): OcppTest[vfam.type] = vfam match {
    case VersionFamily.V1X =>
      // TODO such duplication, much sad
      new InteractiveOcpp1XTest {

        private def connDat = connectionData

        implicit val csmsMessageTypes = VersionFamily.V1XCentralSystemRequest
        implicit val csMessageTypes = VersionFamily.V1XChargePointMessages
        implicit val executionContext = global

        val ops: V1XOps = new V1XOps
                with expectations.Ops[VersionFamily.V1X.type, CentralSystemReq, CentralSystemRes, CentralSystemReqRes, ChargePointReq, ChargePointRes, ChargePointReqRes] with shortsend.OpsV1X {
          def connectionData = connDat

          implicit val csmsMessageTypesForVersionFamily = VersionFamily.V1XCentralSystemRequest
          implicit val csMessageTypesForVersionFamily = VersionFamily.V1XChargePointMessages
          implicit val executionContext = global
        }
      }.asInstanceOf[OcppTest[vfam.type]]
    case VersionFamily.V20 =>

      new InteractiveOcpp20Test {

        private def connDat = connectionData

        implicit val csmsMessageTypes = VersionFamily.V20CsmsMessages
        implicit val csMessageTypes = VersionFamily.V20CsMessages
        implicit val executionContext = global

        val ops: V20Ops = new V20Ops
                with expectations.Ops[VersionFamily.V20.type, CsmsRequest, CsmsResponse, CsmsReqRes, CsRequest, CsResponse, CsReqRes] {
          def connectionData = connDat

          implicit val csmsMessageTypesForVersionFamily  = VersionFamily.V20CsmsMessages
          implicit val csMessageTypesForVersionFamily = VersionFamily.V20CsMessages
          implicit val executionContext = global
        }
      }.asInstanceOf[OcppTest[vfam.type]]
  }

  /*
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
  */
}
