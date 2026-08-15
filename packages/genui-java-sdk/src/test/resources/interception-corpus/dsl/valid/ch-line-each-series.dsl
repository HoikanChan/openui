root = Stack([title, chart])
title = TextContent("Sensor Readings Over Time", "large")
labels = data.labels
readingData = @Each(data.series, "point", point.value)
readingSeries = Series("Reading", readingData)
chart = LineChart(labels, [readingSeries], "smooth", "Time", "Value")
