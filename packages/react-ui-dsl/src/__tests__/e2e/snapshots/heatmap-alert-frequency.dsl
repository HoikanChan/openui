root = VLayout([heatmapTitle, heatmapCard])
heatmapTitle = Text("Alert Frequency Heatmap", "default")
heatmapCard = Card([heatmapChart], "card", "standard")
heatmapChart = HeatmapChart(data.xLabels, data.yLabels, data.values)