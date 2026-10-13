root = Stack([title, chart])
title = TextContent("Revenue vs Cost", "large")
labels = data.labels
revenueValues = @Each(data.series, "s", s.category == "Revenue" ? s.values : null)
costValues = @Each(data.series, "s", s.category == "Cost" ? s.values : null)
revenueSeries = Series("Revenue", revenueValues)
costSeries = Series("Cost", costValues)
chart = LineChart(labels, [revenueSeries, costSeries], "raw", "Quarter", "USD")
