root = VLayout([interfaceTrafficCard])
interfaceTrafficCard = Card([interfaceTrafficHeader, barChart])
interfaceTrafficHeader = CardHeader("Interface Traffic", "Inbound vs Outbound (Mbps)")
barChart = BarChart(data.labels, data.series, "grouped", "Interface", "Traffic (Mbps)")