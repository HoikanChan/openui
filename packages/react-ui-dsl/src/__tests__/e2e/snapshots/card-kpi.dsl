root = Stack([performanceCard])
performanceCard = Card([cardHeader, cardContent], "card", "standard")
cardHeader = CardHeader("Q1 Performance")
cardContent = Stack([TextContent("This is the Q1 performance summary for the quarter.", "default")], "column", "m", "start", "start")