val chargeTokenId = "04EC2CC2552280"
val auth = authorize(chargeTokenId).idTag

if (auth.status == AuthorizationStatus.Accepted) {

  statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Preparing)))

  val startTransactionTimestamp = java.time.ZonedDateTime.now
  val stopTransactionTimestamp = startTransactionTimestamp.plusMinutes(10)
  val connectorScope = ConnectorScope(0) // meaning the 1st EVSE

  val transId = startTransaction(
    meterStart = 300,
    idTag = chargeTokenId,
    connector = connectorScope,
    timestamp = startTransactionTimestamp
  ).transactionId

  statusNotification(
    status = ChargePointStatus.Occupied(Some(OccupancyKind.Charging)),
    scope = connectorScope,
    timestamp = Some(startTransactionTimestamp)
  )

  prompt("Press ENTER to stop charging")

  // sleep(randomize(1.second))
  // statusNotification(
  //   status = ChargePointStatus.Faulted(
  //     errorCode = Some(com.thenewmotion.ocpp.messages.v1x.ChargePointErrorCode.OtherError),
  //     vendorErrorCode = Some("349")
  //   ),
  //   scope = connectorScope,
  //   timestamp = Some(stopTransactionTimestamp)
  // )

  statusNotification(
    status = ChargePointStatus.Occupied(Some(OccupancyKind.Finishing)),
    scope = connectorScope,
    timestamp = Some(stopTransactionTimestamp)
  )

  stopTransaction(
    transactionId = transId,
    meterStop = 310,
    idTag = Some(chargeTokenId),
    timestamp = stopTransactionTimestamp
  )

  statusNotification(
    status = ChargePointStatus.Available(),
    scope = connectorScope,
    timestamp = Some(stopTransactionTimestamp)
  )
} else {
  fail("Not authorized")
}
