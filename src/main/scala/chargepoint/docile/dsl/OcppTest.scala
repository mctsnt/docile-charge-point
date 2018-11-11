package chargepoint.docile.dsl

import java.net.URI

import com.thenewmotion.ocpp.Version.V20
import com.thenewmotion.ocpp.VersionFamily.{CsMessageTypesForVersionFamily, CsmsMessageTypesForVersionFamily}
import javax.net.ssl.SSLContext

import scala.language.higherKinds
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.DurationInt
import com.thenewmotion.ocpp.{Version, Version1X, VersionFamily}
import com.thenewmotion.ocpp.json.api._
import com.thenewmotion.ocpp.messages.{ReqRes, Request, Response}
import com.thenewmotion.ocpp.messages.v1x.{CentralSystemReq, CentralSystemReqRes, CentralSystemRes, ChargePointReq, ChargePointReqRes, ChargePointRes}
import com.thenewmotion.ocpp.messages.v20._
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import expectations.IncomingMessage

trait OcppTest[VFam <: VersionFamily] extends MessageLogging {
  private val connectionLogger = Logger(LoggerFactory.getLogger("connection"))

  // these type variables are filled in differently for OCPP 1.X and OCPP 2.0
  type OutgoingReqBound <: Request
  type IncomingResBound <: Response
  type OutgoingReqRes[_ <: OutgoingReqBound, _ <: IncomingResBound] <: ReqRes[_, _]
  type IncomingReqBound <: Request
  type OutgoingResBound <: Response
  type IncomingReqRes[_ <: IncomingReqBound, _ <: OutgoingResBound] <: ReqRes[_, _]
  type VersionBound <: Version

  implicit val csMessageTypes: CsMessageTypesForVersionFamily[VFam, IncomingReqBound, OutgoingResBound, IncomingReqRes]
  implicit val csmsMessageTypes: CsmsMessageTypesForVersionFamily[VFam, OutgoingReqBound, IncomingResBound, OutgoingReqRes]
  val executionContext: ExecutionContext

  /**
    * The current OCPP with some associated data
    *
    * This is a var instead of a val an immutable because I hope this will allow
    * us to write tests that disconnect and reconnect when we have a more
    * complete test DSL.
    */
  protected var connectionData: OcppConnectionData[
    VFam,
    OutgoingReqBound,
    IncomingResBound,
    OutgoingReqRes,
    IncomingReqBound,
    OutgoingResBound,
    IncomingReqRes
  ]

  def runConnected(
    chargePointId: String,
    endpoint: URI,
    version: VersionBound,
    authKey: Option[String]
  )(implicit sslContext: SSLContext): Unit = {
    val receivedMsgManager = new  ReceivedMsgManager[
      OutgoingReqBound,
      IncomingResBound,
      OutgoingReqRes,
      IncomingReqBound,
      OutgoingResBound,
      IncomingReqRes
    ]()

    connect(receivedMsgManager, chargePointId, endpoint, version, authKey)
    run()
    disconnect()
  }

  private def connect(
    receivedMsgManager: ReceivedMsgManager[OutgoingReqBound, IncomingResBound, OutgoingReqRes, IncomingReqBound, OutgoingResBound, IncomingReqRes],
    chargePointId: String,
    endpoint: URI,
    version: VersionBound,
    authKey: Option[String]
  )(implicit sslContext: SSLContext): Unit = {

    connectionLogger.info(s"Connecting to OCPP v${version.name} endpoint $endpoint")

    val connection = connect(chargePointId, endpoint, version, authKey)(sslContext) {
          new RequestHandler[IncomingReqBound, OutgoingResBound, IncomingReqRes] {
            def apply[REQ <: IncomingReqBound, RES <: OutgoingResBound](req: REQ)(implicit reqRes: IncomingReqRes[REQ, RES], ec: ExecutionContext): Future[RES] = {

              incomingLogger.info(s"$req")

              val responsePromise = Promise[OutgoingResBound]()

              def respond(res: OutgoingResBound): Unit = {
                outgoingLogger.info(s"$res")
                responsePromise.success(res)
                ()
              }

              receivedMsgManager.enqueue(
                IncomingMessage[OutgoingReqBound, IncomingResBound, OutgoingReqRes, IncomingReqBound, OutgoingResBound, IncomingReqRes](req, respond _)
              )

              // TODO nicer conversion?
              responsePromise.future.map(_.asInstanceOf[RES])
            }
          }
    }

    connection.onClose.foreach { _ =>
      connectionLogger.info(s"Gracefully disconnected from endpoint $endpoint")
      connectionData = connectionData.copy[
        VFam,
        OutgoingReqBound,
        IncomingResBound,
        OutgoingReqRes,
        IncomingReqBound,
        OutgoingResBound,
        IncomingReqRes
      ](ocppClient = None)
    }(executionContext)

    connectionData = OcppConnectionData[VFam, OutgoingReqBound, IncomingResBound, OutgoingReqRes, IncomingReqBound, OutgoingResBound, IncomingReqRes](Some(connection), receivedMsgManager, chargePointId)
  }

  protected def connect(
    chargePointId: String,
    endpoint: URI,
    version: VersionBound,
    authKey: Option[String]
  )(implicit sslContext: SSLContext): RequestHandler[IncomingReqBound, OutgoingResBound, IncomingReqRes] => OcppJsonClient[
    VFam,
    OutgoingReqBound,
    IncomingResBound,
    OutgoingReqRes,
    IncomingReqBound,
    OutgoingResBound,
    IncomingReqRes
  ]

  private def disconnect(): Unit = connectionData.ocppClient.foreach { conn =>
    Await.result(conn.close(), 45.seconds)
  }

  protected def run(): Unit
}

trait Ocpp1XTest extends OcppTest[VersionFamily.V1X.type] {

  type OutgoingReqBound = CentralSystemReq
  type IncomingResBound = CentralSystemRes
  type OutgoingReqRes[Q <: CentralSystemReq, S <: CentralSystemRes] = CentralSystemReqRes[Q, S]
  type IncomingReqBound = ChargePointReq
  type OutgoingResBound = ChargePointRes
  type IncomingReqRes[Q <: ChargePointReq, S <: ChargePointRes] = ChargePointReqRes[Q, S]
  type VersionBound = Version1X

  override protected var connectionData: OcppConnectionData[
    VersionFamily.V1X.type,
    CentralSystemReq,
    CentralSystemRes,
    CentralSystemReqRes,
    ChargePointReq,
    ChargePointRes,
    ChargePointReqRes
  ] = _

  protected override def connect(
    chargePointId: String,
    endpoint: URI,
    version: Version1X,
    authKey: Option[String]
  )(implicit sslCtx: SSLContext): ChargePointRequestHandler => Ocpp1XJsonClient = { reqHandler =>
    OcppJsonClient.forVersion1x(chargePointId, endpoint, List(version), authKey)(reqHandler)(executionContext, sslCtx)
  }
}

object Ocpp1XTest {

  trait V1XOps extends CoreOps[
    VersionFamily.V1X.type,
    CentralSystemReq,
    CentralSystemRes,
    CentralSystemReqRes,
    ChargePointReq,
    ChargePointRes,
    ChargePointReqRes
    ] with expectations.Ops[
    VersionFamily.V1X.type,
    CentralSystemReq,
    CentralSystemRes,
    CentralSystemReqRes,
    ChargePointReq,
    ChargePointRes,
    ChargePointReqRes
    ] with shortsend.OpsV1X
}

trait Ocpp20Test extends OcppTest[VersionFamily.V20.type] {

  override type OutgoingReqBound = CsmsRequest
  override type IncomingResBound = CsmsResponse
  override type OutgoingReqRes[Q <: CsmsRequest, S <: CsmsResponse] = CsmsReqRes[Q, S]
  override type IncomingReqBound = CsRequest
  override type OutgoingResBound = CsResponse
  override type IncomingReqRes[Q <: CsRequest, S <: CsResponse] = CsReqRes[Q, S]
  override type VersionBound = V20.type

  override protected var connectionData: OcppConnectionData[
    VersionFamily.V20.type,
    CsmsRequest,
    CsmsResponse,
    CsmsReqRes,
    CsRequest,
    CsResponse,
    CsReqRes
  ] = _

  protected override def connect(
    chargePointId: String,
    endpoint: URI,
    version: V20.type,
    authKey: Option[String]
  )(implicit sslCtx: SSLContext): CsRequestHandler => Ocpp20JsonClient = { reqHandler =>
    OcppJsonClient.forVersion20(chargePointId, endpoint, authKey)(reqHandler)(executionContext, sslCtx)
  }
}

object Ocpp20Test {

  trait V20Ops extends CoreOps[
    VersionFamily.V20.type,
    CsmsRequest,
    CsmsResponse,
    CsmsReqRes,
    CsRequest,
    CsResponse,
    CsReqRes
    ] with expectations.Ops[
    VersionFamily.V20.type,
    CsmsRequest,
    CsmsResponse,
    CsmsReqRes,
    CsRequest,
    CsResponse,
    CsReqRes
    ] with shortsend.OpsV20
    with ocpp20transactions.Ops
}

case class OcppConnectionData[
  VFam <: VersionFamily,
  OutgoingReqBound <: Request,
  IncomingResBound <: Response,
  OutgoingReqRes[_ <: OutgoingReqBound, _ <: IncomingResBound] <: ReqRes[_, _],
  IncomingReqBound <: Request,
  OutgoingResBound <: Response,
  IncomingReqRes[_ <: IncomingReqBound, _ <: OutgoingResBound] <: ReqRes[_, _]
](
  ocppClient:
    Option[OcppJsonClient[
      VFam,
      OutgoingReqBound,
      IncomingResBound,
      OutgoingReqRes,
      IncomingReqBound,
      OutgoingResBound,
      IncomingReqRes
    ]
  ],
  receivedMsgManager: ReceivedMsgManager[OutgoingReqBound, IncomingResBound, OutgoingReqRes, IncomingReqBound, OutgoingResBound, IncomingReqRes],
  chargePointIdentity: String
)
