root = Stack([delta])
delta = TextContent(data.totalAlarms.delta < 0 ? "down" : "up" + Math.abs(data.totalAlarms.delta).toString(), "small")
