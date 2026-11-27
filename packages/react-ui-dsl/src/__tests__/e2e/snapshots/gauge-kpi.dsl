root = Stack([kpiCard])
kpiCard = Card([cardTitle, cardGauge], "card", "standard")
cardTitle = TextContent("System Health Score", "large")
cardGauge = GaugeChart(data.readings, 0, 100)