root = Stack([header, radarChart])
header = Text("Device Health Metrics Comparison", "large")
radarChart = RadarChart(data.labels, data.series)