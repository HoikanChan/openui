root = VLayout([header, scatter])
header = Text("Core Routers: Latency vs Packet Loss", "large")
scatter = ScatterChart([data.scatterSeries], data.xLabel, data.yLabel)