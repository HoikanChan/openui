root = Stack([alarmText, totalText])
alarmText = TextContent(data.criticalAlarms > 0 ? "critical alarms present" : "no critical alarms")
totalText = TextContent(data.total ?? 0)
