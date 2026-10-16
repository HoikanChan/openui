root = Stack([title, chart])
title = TextContent("Website Traffic Trend", "large")
labels = data.labels
trafficSeries = Series("Sessions", data.values)
chart = LineChart(labels, [trafficSeries], "smooth", "Week", "Sessions")
