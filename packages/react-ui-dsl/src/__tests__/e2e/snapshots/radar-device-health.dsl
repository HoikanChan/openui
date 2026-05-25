root = Stack([chartTitle, radarChartContainer])
chartTitle = TextContent("Device Health Metrics Comparison", "large")
radarLabels = data.labels
radarSeries = @Each(data.series, "router", Series(router.category, router.values))
radarChartContainer = RadarChart(radarLabels, radarSeries)