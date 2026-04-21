root = VLayout([chartTitle, interfaceChart])
chartTitle = Text("Top Interfaces by Traffic (Mbps)", "default")
interfaceChart = HorizontalBarChart(data.labels, data.series, "grouped", "Interface", "Traffic (Mbps)")