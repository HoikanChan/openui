root = Stack([title, barChart, lineChart])
title = TextContent("Sales Dashboard", "large")
labels = data.labels
barSeries = Series("Units", data.units)
barChart = BarChart(labels, [barSeries], "grouped", "Month", "Units")
lineSeries = Series("Revenue", data.revenue)
lineChart = LineChart(labels, [lineSeries], "smooth", "Month", "Revenue ($)")
