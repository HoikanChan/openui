root = Stack([chartTitle, areaChart])
chartTitle = TextContent("Bandwidth Utilization Over 24 Hours", "large")
labels = data.labels
seriesData = data.series[0].values
areaSeries = Series(data.series[0].category, seriesData)
areaChart = AreaChart(labels, [areaSeries], "smooth", "Time", "Bandwidth Utilization (Mbps)", true)