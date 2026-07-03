root = Stack([chart])
labels = data.labels
series = Series("Revenue")
chart = LineChart(labels, [series], "smooth", "Month", "Revenue")
