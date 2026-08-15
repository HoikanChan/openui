root = Stack([deviceCard, detailsCard], "column", "m")

deviceCard = Card([deviceHeader, cpuTag], "column", "m", "center", "center")
deviceHeader = TextContent("CPU Usage", "large-heavy")
cpuTag = TextContent(data.cpu_usage_percent + "%", "large-heavy")

detailsCard = Card([detailsDesc], "column", "none")
detailsDesc = Descriptions([ipField, deviceField, macField, timeField, aggField], "Device Details", 1, true)
ipField = DescField("IP Address", data.ip)
deviceField = DescField("Device", data.device)
macField = DescField("MAC Address", data.mac)
timeField = DescField("Time Range", data.time_range)
aggField = DescField("Aggregation", data.aggregation)
