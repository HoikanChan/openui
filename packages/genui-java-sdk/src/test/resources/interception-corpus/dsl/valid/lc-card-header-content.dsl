root = Stack([summaryCard])
summaryCard = Card([summaryHeader, summaryBody])
summaryHeader = CardHeader("Monthly Summary")
summaryBody = Stack([TextContent("Total revenue increased 12% versus last month.", "default"), TextContent("New customers: 340", "small")], "column", "m", "start", "start")
