root = Stack([title, chart])
title = TextContent("Static Benchmark Scores", "large")
labels = data.labels
rawSeries = Series("Raw", [10, 20, 30, 40])
chart = BarChart(labels, [rawSeries], "grouped", "Test", "Score")
