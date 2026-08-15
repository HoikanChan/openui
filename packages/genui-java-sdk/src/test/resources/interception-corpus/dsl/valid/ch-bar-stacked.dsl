root = Stack([title, chart])
title = TextContent("Monthly Expenses by Category", "large")
labels = data.labels
rentValues = @Each(data.series, "s", s.category == "Rent" ? s.values : null)
utilitiesValues = @Each(data.series, "s", s.category == "Utilities" ? s.values : null)
rentSeries = Series("Rent", rentValues)
utilitiesSeries = Series("Utilities", utilitiesValues)
chart = BarChart(labels, [rentSeries, utilitiesSeries], "stacked", "Month", "Amount ($)")
