root = VLayout([areaChartContainer])
areaChartContainer = Card([areaChart])
areaChart = AreaChart(data.labels, data.series, "smooth", "Time of Day", "Bandwidth (Mbps)")