root = VLayout([radarChartCard])
radarChartCard = Card([radarChartHeader, radarChart])
radarChartHeader = CardHeader("Device Health Comparison", "Radar chart showing metrics across routers")
radarChart = RadarChart(data.labels, data.series)