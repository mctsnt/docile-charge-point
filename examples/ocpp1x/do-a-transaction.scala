val chargeTokenId = "04EC2CC2552280"
val auth = authorize(chargeTokenId).idTag

if (auth.status == AuthorizationStatus.Accepted) {

  statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Preparing)))
  send(StartTransactionReq(
    connector = ConnectorScope(0),
    idTag = chargeTokenId,
    timestamp = ZonedDateTime.now,
    meterStart = 300,
    reservationId = None
  ))

  val transId =
    expectInAnyOrder(
      matching { case res: StartTransactionRes => res },
      changeConfigurationReq.respondingWith(ChangeConfigurationRes(ConfigurationStatus.Accepted))
    ).collect {
      case StartTransactionRes(transactionId, _) => transactionId
    }.head

  statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Charging)))

  prompt("Press ENTER to stop charging")

  statusNotification(status = ChargePointStatus.Occupied(Some(OccupancyKind.Finishing)))
  stopTransaction(meterStop = 310, transactionId = transId, idTag = Some(chargeTokenId))
  statusNotification(status = ChargePointStatus.Available())

} else {
  fail("Not authorized")
}

// vim: set ts=4 sw=4 et:
