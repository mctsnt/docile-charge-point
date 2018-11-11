package chargepoint.docile
package dsl
package ocpp20transactions

import java.util.UUID

import scala.collection.mutable
import com.thenewmotion.ocpp.VersionFamily
import com.thenewmotion.ocpp.messages.v20._

// TODO mutable state per EVSE sucks; better keep this state in objects returned to caller?
// also makes it easier to interleave transactions, which can be a nicely abusive thing to
// do to a CSMS.
/**
  * DSL operations for OCPP 2.0 transactions.
  *
  * In order to easily do this in a way that is consistent with the rules of
  * OCPP 2.0 transactions, this trait keeps local state per EVSE in a mutable
  * map. It thus remembers which transactions you started, and their IDs and
  * states.
  */
trait Ops {

  self: CoreOps[
    VersionFamily.V20.type,
    CsmsRequest,
    CsmsResponse,
    CsmsReqRes,
    CsRequest,
    CsResponse,
    CsReqRes
    ] with
    expectations.Ops[
      VersionFamily.V20.type,
      CsmsRequest,
      CsmsResponse,
      CsmsReqRes,
      CsRequest,
      CsResponse,
      CsReqRes
      ] with
    shortsend.OpsV20 =>

  val evseStates: mutable.Map[Int, EvseState] = mutable.Map.empty[Int, EvseState]

  def startTransactionAtAuthorized(
    evseId: Int = 1,
    connectorId: Int = 1,
    idToken: IdToken = IdToken(idToken = "01020304", `type` = IdTokenType.ISO14443, additionalInfo = None)
  )(implicit awaitTimeout: AwaitTimeout): TransactionEventResponse = {
    val transactionData = Transaction(
      id = UUID.randomUUID().toString,
      chargingState = None,
      timeSpentCharging = None,
      stoppedReason = None,
      remoteStartId = None
    )

    startTransaction(transactionData, TriggerReason.Authorized, evseId, connectorId)
  }

  def startTransactionAtCablePluggedIn(
    evseId: Int = 1,
    connectorId: Int = 1
  )(implicit awaitTimeout: AwaitTimeout): TransactionEventResponse = {
    val transactionData = Transaction(
      id = UUID.randomUUID().toString,
      chargingState = Some(ChargingState.EVDetected),
      timeSpentCharging = Some(0),
      stoppedReason = None,
      remoteStartId = None
    )

    startTransaction(transactionData, TriggerReason.CablePluggedIn, evseId, connectorId)
  }

  def startTransaction(
    transactionData: Transaction,
    triggerReason: TriggerReason,
    evseId: Int = 1,
    connectorId: Int = 1
  )(implicit awaitTimeout: AwaitTimeout)
  : TransactionEventResponse = {
    val seqNo = getAndIncrementTxCounter(evseId)

    updateEvseState(evseId)(_.copy(currentTx = Some(transactionData)))

    transactionEvent(
      seqNo = seqNo,
      evse = EVSE(evseId, Some(connectorId)),
      eventType = TransactionEvent.Started,
      triggerReason = triggerReason,
      transactionData = transactionData
    )

  }

  /** Assuming there's an unauthorized transaction going on on an EVSE already,
    * notify the CSMS that that transaction has been authorized.
    *
    * Will fail the test if no transaction is going on on that EVSE.
    * @param evseId
    * @param connectorId
    */
  def authorizeTransaction(
    evseId: Int = 1,
    idToken: IdToken = IdToken(idToken = "01020304", `type` = IdTokenType.ISO14443, additionalInfo = None)
  )(implicit awaitTimeout: AwaitTimeout): TransactionEventResponse = {

    withOngoingTransaction(evseId) { tx =>
      val nextTransactionState = tx.copy(
        chargingState = Some(ChargingState.Charging)
      )
      updateTransaction(
        triggerReason = TriggerReason.Authorized,
        transactionData = nextTransactionState,
        idToken = Some(idToken),
        evseId = evseId,
      )
    }
  }

  def plugInCableInTransaction(
    evseId: Int = 1,
    connectorId: Int = 1
  )(implicit awaitTimeout: AwaitTimeout): TransactionEventResponse = {
    withOngoingTransaction(evseId) { tx =>
      val nextTransactionState = tx.copy(chargingState = Some(ChargingState.EVDetected))
      updateTransaction(
        nextTransactionState,
        TriggerReason.CablePluggedIn,
        idToken = None,
        evseId = evseId,
      )
    }
  }

  def startEnergyOfferInTransaction(
    evseId: Int = 1
  )(implicit awaitTimeout: AwaitTimeout): TransactionEventResponse = {
    withOngoingTransaction(evseId) { tx =>
      val nextTransactionState = tx.copy(chargingState = Some(ChargingState.Charging))

      updateTransaction(
        nextTransactionState,
        TriggerReason.ChargingStateChanged,
        evseId = evseId
      )
    }
  }

  def updateTransaction(
    transactionData: Transaction,
    triggerReason: TriggerReason,
    idToken: Option[IdToken] = None,
    evseId: Int = 1,
  )(implicit awaitTimeout: AwaitTimeout): TransactionEventResponse = {
    val seqNo = getAndIncrementTxCounter(evseId)
    transactionEvent(
      seqNo = seqNo,
      eventType = TransactionEvent.Updated,
      triggerReason = triggerReason,
      transactionData = transactionData,
      idToken = idToken
    )
  }

  def endTransaction(
    evseId: Int = 1,
    triggerReason: TriggerReason,
    stoppedReason: Reason
  )(implicit awaitTimeout: AwaitTimeout): TransactionEventResponse = {
    val seqNo = getAndIncrementTxCounter(evseId)
    withOngoingTransaction(evseId) { tx =>
      transactionEvent(
        seqNo = seqNo,
        eventType = TransactionEvent.Ended,
        triggerReason = triggerReason,
        transactionData = tx.copy(chargingState = None, stoppedReason = Some(stoppedReason)),
        idToken = None
      )
    }
  }

  private def getAndIncrementTxCounter(evseId: Int): Int =
    evseStates.get(evseId) match {
      case None =>
        evseStates.put(evseId, EvseState(transactionCounter = 1, currentTx = None))
        0
      case Some(state) =>
        evseStates.update(
          evseId,
          state.copy(transactionCounter = state.transactionCounter + 1)
        )
        state.transactionCounter
    }

  private def updateEvseState(evseId: Int)(f: EvseState => EvseState): Unit = {
    evseStates.get(evseId) foreach { s =>
      evseStates.update(evseId, f(s))
    }
  }

  /**
    * Look for a transaction on the given EVSE
    *
    * If there is a transaction on that EVSE, run a block of code that gets that transaction as an agrument
    *
    * If there is no transaction on that EVSE, fail the docile test
    *
    * @param evseId
    * @param f
    * @tparam T
    * @return
    */
  private def withOngoingTransaction[T](evseId: Int)(f: Transaction => T): T =
    evseStates.get(evseId) match {
      case None =>
        fail(s"Trying to do something with a transaction on EVSE $evseId, but EVSE $evseId was not used before")
      case Some(state) =>
        state.currentTx match {
          case None =>
            fail(s"Trying to do something with a transaction on EVSE $evseId, but no transaction is ongoing on EVSE $evseId")
          case Some(tx) =>
            f(tx)
        }
    }

}

case class EvseState(
  transactionCounter: Int,
  currentTx: Option[Transaction]
)
