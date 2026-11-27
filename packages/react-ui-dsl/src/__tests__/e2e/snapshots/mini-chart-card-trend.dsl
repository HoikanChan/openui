root = Stack([kpiCard])
kpiCard = Card([cardTitle, cardTrend], "card", "standard")
cardTitle = TextContent("Latency Sparkline", "large")
cardTrend = MiniChart("line", data.sparkline)