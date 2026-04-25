root = VLayout([summaryCard])
summaryCard = Card([summaryHeader, summaryBody], "card", "column", "m")
summaryHeader = CardHeader(data.summary.heading)
summaryBody = Text("Growth: " + data.summary.growth, "large-heavy")