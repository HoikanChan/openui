root = Stack([title, chart])
title = TextContent("Market Share by Vendor", "large")
chart = PieChart(data.labels, data.values, "pie")
