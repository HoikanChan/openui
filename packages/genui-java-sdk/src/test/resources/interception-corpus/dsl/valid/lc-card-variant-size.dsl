root = Stack([kpiCard])
kpiCard = Card([kpiHeader, kpiValue], "highlight", "large")
kpiHeader = CardHeader("Net Promoter Score")
kpiValue = TextContent(@Round(data.metrics.nps, 0) + " NPS", "large")
