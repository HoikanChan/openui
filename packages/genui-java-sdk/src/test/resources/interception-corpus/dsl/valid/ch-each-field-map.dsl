root = Stack([title, chart])
title = TextContent("Order Totals by Day", "large")
labels = data.labels
totalData = @Each(data.orders, "order", order.total)
totalSeries = Series("Total", totalData)
chart = LineChart(labels, [totalSeries], "raw", "Day", "Total ($)")
