root = Stack([headerCard, metricsRow, logsCard], "column", "m")

headerCard = Card([headerTitle, headerInfo], "column", "none")
headerTitle = TextContent("设备监控概览", "large-heavy")
headerInfo = Stack([deviceNameText, deviceIpText], "row", "s")
deviceNameText = TextContent("设备: " + data.memorySection.data.deviceName, "small")
deviceIpText = TextContent("IP: " + data.memorySection.data.deviceIP, "small")

metricsRow = Stack([cpuCard, memoryCard], "row", "m", "stretch")

cpuCard = Card([cpuHeader, cpuValue], "column", "s")
cpuHeader = TextContent(data.cpuSection.title, "small")
cpuValue = TextContent(@FormatPercent(data.cpuSection.data.cpuUsageAvg / 100, 2), "large-heavy")

memoryCard = Card([memHeader, memValue], "column", "s")
memHeader = TextContent(data.memorySection.title, "small")
memValue = TextContent(@FormatPercent(data.memorySection.data.memUsageAvg / 100, 2), "large-heavy")

logsCard = Card([logsHeader, logsTable], "column", "m")
logsHeader = CardHeader(data.logsSection.title, "时间范围: " + data.logsSection.timeRange)
logsTable = Table([timeCol, severityCol, briefCol, detailCol], data.logsSection.data)

timeCol = Col("时间", "time")
severityCol = Col("级别", "severity", {cell: @Render("v", severityCellTpl)})
briefCol = Col("摘要", "brief")
detailCol = Col("详情", "detail")

severityCellTpl = @Switch(v, {"Notice": Tag("Notice", "info"), "Warning": Tag("Warning", "warning")}, Tag(v, "neutral"))
