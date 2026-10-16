root = Stack([trendCard])
trendCard = Card([trendHeader, trendChart])
trendHeader = CardHeader("Client Experience Trend", "Value over time")
chartData = data.data[0]
timestampLabels = @Each(chartData, "item", @FormatDate(item.timestamp, "dateTime"))
sampleValues = @Each(chartData, "item", item.value)
trendChart = LineChart(timestampLabels, [Series("Value", sampleValues)], "smooth", "Time", "%")
