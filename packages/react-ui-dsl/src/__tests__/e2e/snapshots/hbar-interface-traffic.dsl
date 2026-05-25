root = Stack([chartTitle, trafficChart])
chartTitle = TextContent("Top Interfaces by Traffic", "large")
labels = data.labels
seriesValues = data.series[0].values
trafficChart = HorizontalBarChart(labels, [Series("Traffic (Mbps)", seriesValues)], "grouped", "Interface", "Traffic (Mbps)")