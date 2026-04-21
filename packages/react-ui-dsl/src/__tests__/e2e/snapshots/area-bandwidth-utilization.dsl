root = VLayout([chartContainer])
chartContainer = Card([areaChart])
areaChart = AreaChart(data.labels, data.series, "smooth", "Time of Day", "Bandwidth (Mbps)")