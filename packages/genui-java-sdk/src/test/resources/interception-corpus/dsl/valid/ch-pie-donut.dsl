root = Stack([title, chart])
title = TextContent("Budget Allocation", "large")
labels = data.labels
values = data.values
chart = PieChart(labels, values, "donut")
