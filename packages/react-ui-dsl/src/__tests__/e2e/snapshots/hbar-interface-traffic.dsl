root = Stack([title, chart])
title = Text("Top Interfaces by Traffic (Mbps)", "large")
trafficSeries = Series(data.series[0].category, data.series[0].values)
chart = HorizontalBarChart(data.labels, [trafficSeries], "grouped", "Interface", "Traffic (Mbps)")