root = VLayout([title, radar])
title = Text("Device Health Metrics Comparison", "large")
radar = RadarChart(data.labels, data.series)