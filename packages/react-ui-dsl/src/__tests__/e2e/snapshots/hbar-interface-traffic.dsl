root = VLayout([title, chart])
title = Text("Top Interfaces by Traffic", "large")
chart = HorizontalBarChart(data.labels, data.series, "grouped", "Interface", "Traffic (Mbps)")