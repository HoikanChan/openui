root = VLayout([chartContainer])
chartContainer = Card([chartTitle, revenueChart], "card", "standard", {title: "Quarterly Revenue Comparison"})
chartTitle = Text("Revenue by Product Line (in thousands)", "default")
revenueChart = BarChart(data.labels, data.series, "grouped", "Quarter", "Revenue ($)")