root = Stack([title, chart])
title = TextContent("Regional Sales by Product", "large")
labels = data.labels
series = data.series
northValues = @Each(series, "s", s.category == "North" ? s.values : null)
southValues = @Each(series, "s", s.category == "South" ? s.values : null)
northSeries = Series("North", northValues)
southSeries = Series("South", southValues)
chart = BarChart(labels, [northSeries, southSeries], "grouped", "Product", "Units Sold")
