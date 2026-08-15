root = Stack([title, chart])
title = TextContent("Weekly Visitors", "large")
visitorSeries = Series("Visitors", data.values)
chart = BarChart(data.labels, [visitorSeries])
