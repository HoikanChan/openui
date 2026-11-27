root = Stack([scatterChartTitle, scatterChart])
scatterChartTitle = TextContent("Latency vs Packet Loss for Core Routers", "large")
scatterChart = ScatterChart([data.scatterSeries], data.xLabel, data.yLabel, "time")