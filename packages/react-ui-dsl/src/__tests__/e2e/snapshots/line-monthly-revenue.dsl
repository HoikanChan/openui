root = VLayout([chartTitle, revenueChart])
chartTitle = Text("Monthly Revenue Trend", "default")
revenueChart = LineChart(data.labels, data.series, "linear", "Month", "Revenue ($)")