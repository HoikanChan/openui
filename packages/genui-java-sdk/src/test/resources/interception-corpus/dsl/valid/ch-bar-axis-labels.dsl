root = Stack([title, chart])
title = TextContent("Daily Temperature Highs", "large")
tempSeries = Series("High (C)", data.values)
chart = BarChart(data.labels, [tempSeries], "grouped", "Day", "Temperature (C)")
