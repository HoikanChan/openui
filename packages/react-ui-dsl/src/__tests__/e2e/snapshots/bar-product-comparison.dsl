root = VLayout([chartTitle, revenueChart])
chartTitle = Text("Quarterly Revenue Comparison", "large")
revenueChart = BarChart(data.labels, data.series, "grouped", "Quarter", "Revenue (USD)")