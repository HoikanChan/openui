root = Stack([title, heatmap])
title = Text("Alert Frequency by Hour and Day of Week", "large")
heatmap = HeatmapChart(data.xLabels, data.yLabels, data.values)