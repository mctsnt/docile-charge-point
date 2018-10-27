send(HeartbeatRequest())
expectIncoming(matching { case HeartbeatResponse(_) => })
