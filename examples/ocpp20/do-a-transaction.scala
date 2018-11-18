val testIdToken = IdToken("01020304", IdTokenType.ISO14443, None)

say("Booting...")

bootNotification()

say("Setting EVSE 1 available")

statusNotification() // EVSE 1 available

val authRes = authorize(List(1), testIdToken, List())

if (authRes.idTokenInfo.status != AuthorizationStatus.Accepted) {
  fail("Token not accepted")
} else {
  say ("Authorized, starting transaction...")
  statusNotification(connectorStatus = ConnectorStatus.Occupied)
  val (tx, req) = startTransactionAtAuthorized(idToken = testIdToken)
  sendSync(req)

  prompt("Press ENTER to plug in cable")
  sendSync(tx.plugInCable())

  say("Starting to deliver energy...")
  sendSync(tx.startEnergyOffer())

  say("Sending some frivolous meter values")
  sendSync(tx.update(tx.data, triggerReason = TriggerReason.MeterValuePeriodic, meterValue = Some(List(
    MeterValue(List(
      SampledValue(
        value = BigDecimal(1400),
        context = None,
        measurand = None,
        phase = None,
        location = None,
        signedMeterValue = None,
        unitOfMeasure = None
      )), Instant.now()),
    MeterValue(List(
      SampledValue(
        value = BigDecimal(-4.3),
        context = Some(ReadingContext.SamplePeriodic),
        measurand = Some(Measurand.Frequency),
        phase = Some(Phase.`L1-N`),
        location = Some(Location.Inlet),
        signedMeterValue = Some(SignedMeterValue("jfalksdjfl;asj", signatureMethod = SignatureMethod.ECDSAP256SHA256, encodingMethod = EncodingMethod.DLMSMessage, encodedMeterValue = "lkjklsa")),
        unitOfMeasure = Some(UnitOfMeasure(unit = Some("Hertz"), multiplier = Some(3)))
      )), Instant.ofEpochSecond(2)
  )))))

  prompt("Press ENTER to stop charging")
  sendSync(tx.stopAuthorized(idToken = testIdToken))

  sendSync(tx.end(stoppedReason = Reason.Local, triggerReason = TriggerReason.StopAuthorized))

  statusNotification(connectorStatus = ConnectorStatus.Available)
}

// vim: set ts=4 sw=4 et:
