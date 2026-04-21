root = VLayout([title, chartContainer])
title = Text("Device Health Metrics Comparison", "markdown")
chartContainer = Card([radarChart])
radarChart = RadarChart(data.labels, data.series)