root = Stack([title, chart])
title = Text("Sales Distribution by Region", "large")
chart = PieChart(data.labels, data.values, "donut")