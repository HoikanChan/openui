root = Stack([title, trafficChart])
title = Text("Interface Traffic", "large")
trafficChart = BarChart(data.labels, data.series, "grouped", "Interface", "Mbps")