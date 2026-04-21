root = VLayout([summaryCard])
summaryCard = Card([summaryHeader, summaryText])
summaryHeader = CardHeader(data.summary.heading)
summaryText = Text("**Growth:** " + data.summary.growth, "markdown")