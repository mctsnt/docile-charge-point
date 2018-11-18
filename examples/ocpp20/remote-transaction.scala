say("Booting...")

bootNotification()

say("Setting EVSE 1 available")

statusNotification() // EVSE 1 available

say("Waiting for remote start")

val req = expectIncoming(requestMatching { case r: RequestStartTransactionRequest => r }.respondingWith(RequestStartTransactionResponse(RequestStartStopStatus.Accepted, transactionId = Some("zlunk"))))

say ("Received request to start, starting transaction...")
statusNotification(connectorStatus = ConnectorStatus.Occupied, evseId = req.evseId.get)

val (tx, startReq) = startTransaction(
  transactionData = Transaction(
    id = "zlunk",
    chargingState = None,
    timeSpentCharging = Some(0),
    stoppedReason = None,
    remoteStartId = Some(1)
  ),
  triggerReason = TriggerReason.RemoteStart,
  idToken = Some(req.idToken.copy(additionalInfo = None))
) 
sendSync(startReq)

prompt("Press ENTER to plug in cable")
sendSync(tx.plugInCable())

say("Starting to deliver energy...")
sendSync(tx.startEnergyOffer())

say("Waiting for remote stop")
val stopReq = expectIncoming(requestMatching { case r: RequestStopTransactionRequest => r }.respondingWith(RequestStopTransactionResponse(status = RequestStartStopStatus.Accepted)))

if (stopReq.transactionId == "zlunk") {

  say("Stop request received, stopping")

  sendSync(tx.end(stoppedReason = Reason.Remote, triggerReason = TriggerReason.RemoteStop))

  statusNotification(connectorStatus = ConnectorStatus.Available)
} else {
  fail("Received unexpected request")
}

// vim: set ts=4 sw=4 et:
