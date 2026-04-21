root = VLayout([chartContainer])
chartContainer = Card([chartTitle, chart])
chartTitle = Text("Sales Distribution by Region", "markdown")
chart = PieChart(data.labels, data.values)