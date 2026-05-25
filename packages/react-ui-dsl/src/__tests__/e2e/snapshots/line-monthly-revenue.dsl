root = Stack([chartTitle, revenueChart])
chartTitle = TextContent("Monthly Revenue Trend", "large")
labels = data.labels
seriesData = @Each(data.series, "item", item.values)
revenueSeries = Series("Revenue", seriesData)
revenueChart = LineChart(labels, [revenueSeries], "smooth", "Month", "Revenue (USD)")