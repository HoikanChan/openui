root = VLayout([interfaceTrafficCard])
interfaceTrafficCard = Card([interfaceTrafficChart])
interfaceTrafficChart = BarChart(data.labels, data.series, "grouped", "Interface", "Traffic (Mbps)")