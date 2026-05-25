root = Stack([heatmapTitle, heatmapChart])
heatmapTitle = TextContent("Alert Frequency by Hour and Day", "large")
heatmapChart = HeatmapChart(data.xLabels, data.yLabels, data.values)