root = Stack([chartTitle, pieChart])
chartTitle = TextContent("Sales Distribution by Region", "large")
pieChart = PieChart(data.labels, data.values, "pie")