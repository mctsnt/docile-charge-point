send(BootNotificationRequest(chargingStation = ChargingStation(serialNumber = Some("newmotion"), model = "Docile", vendorName = "NewMotion", firmwareVersion = Some("1"), modem = None), reason = BootReason.PowerUp)) 

expectIncoming(matching { case BootNotificationResponse(_, _, _) => })

send(HeartbeatRequest())

expectIncoming(matching { case HeartbeatResponse(_) => })

// vim: set ts=4 sw=4 et:
