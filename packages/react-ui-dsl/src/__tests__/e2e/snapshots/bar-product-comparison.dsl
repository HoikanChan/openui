root = VLayout([chartCard])
chartCard = Card([chartHeader, barChart])
chartHeader = CardHeader("Quarterly Revenue Comparison", "Product A vs Product B")
barChart = BarChart(data.labels, data.series, "grouped", "Quarter", "Revenue ($)")