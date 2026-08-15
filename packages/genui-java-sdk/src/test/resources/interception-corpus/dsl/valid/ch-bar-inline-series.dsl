root = Stack([title, chart])
title = TextContent("Quarterly Headcount", "large")
chart = BarChart(data.labels, [Series("Headcount", data.values)], "grouped", "Quarter", "People")
