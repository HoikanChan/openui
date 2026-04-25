root = VLayout([header, chart])
header = Text("Monthly Revenue Trend", "large")
chart = LineChart(data.labels, [data.series[0]], "smooth", "Month", "Revenue (USD)")