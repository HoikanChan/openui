root = Stack([header, top3Card, noDataCard], "column", "m")

header = CardHeader("Server Leaf CPU Analysis", "Top 3 highest CPU usage devices and devices with no data")

top3Card = Card([top3Header, top3Table])
top3Header = CardHeader("Top 3 Server Leaf CPU Usage", "Devices with highest CPU utilization")
top3Table = Table([rankCol, nameCol, ipCol, cpuCol, fabricCol], data.top3_serverleaf_cpu)

rankCol = Col("Rank", "rank")
nameCol = Col("Device Name", "neName")
ipCol = Col("IP Address", "neIp")
cpuCol = Col("CPU Usage (%)", "cpu_usage", {cell: @Render("v", Tag(v, v >= 80 ? "danger" : v >= 60 ? "warning" : "info"))})
fabricCol = Col("Fabric", "fabricName")

noDataCard = Card([noDataHeader, noDataList])
noDataHeader = CardHeader("Devices With No Data", "Devices that did not report data")
noDataList = Stack(@Each(data.no_data_devices, "device", deviceTagTpl), "column", "none")
deviceTagTpl = Tag(device, "neutral")
