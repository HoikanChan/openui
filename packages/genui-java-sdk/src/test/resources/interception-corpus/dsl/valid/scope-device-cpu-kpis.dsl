root = Stack([header, kpiRow, deviceTableCard], "column", "m")

header = CardHeader("Device CPU Usage", "Top " + @Count(data.devices) + " devices by CPU load")

kpiRow = Stack([totalCard, avgCard, maxCard], "row", "m", "stretch", "start", true)
totalCard = Card([TextContent("Total Devices", "small"), TextContent("" + data.total_devices, "large-heavy")])
avgCard = Card([TextContent("Avg CPU Usage", "small"), TextContent(@FormatPercent(@Avg(data.devices.cpu_usage), 1), "large-heavy")])
maxCard = Card([TextContent("Max CPU Usage", "small"), TextContent(@FormatPercent(@Max(data.devices.cpu_usage), 1), "large-heavy")])

deviceTableCard = Card([tableHeader, deviceTable])
tableHeader = CardHeader("Device Details", "Ranked by CPU usage")
deviceTable = Table([rankCol, nameCol, ipCol, cpuCol], data.devices)

rankCol = Col("Rank", "rank")
nameCol = Col("Device Name", "neName")
ipCol = Col("IP Address", "neIp")
cpuCol = Col("CPU Usage", "cpu_usage", {cell: @Render("v", TextContent(@FormatPercent(v, 1)))})
