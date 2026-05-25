root = Stack([chartTitle, revenueChart])
chartTitle = TextContent("Quarterly Revenue by Product Line", "large")
labels = data.labels
series = data.series
productAValues = @Each(series, "s", s.category == "Product A" ? s.values : null)
productBValues = @Each(series, "s", s.category == "Product B" ? s.values : null)
productASeries = Series("Product A", productAValues)
productBSeries = Series("Product B", productBValues)
revenueChart = BarChart(labels, [productASeries, productBSeries], "grouped", "Quarter", "Revenue ($)")