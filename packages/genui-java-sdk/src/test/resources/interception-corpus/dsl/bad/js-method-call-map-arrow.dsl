root = Stack([totalIn])
totalIn = TextContent(@FormatNumber(@Sum(data.devices.map(d => d.inTraffic)), 1))
