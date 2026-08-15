root = Stack([scoreTable])
scoreTable = Table([playerCol, scoreCol], data.scores)
playerCol = Col("Player", "player")
scoreCol = Col("Score", "score", {cell: @Render("v", TextContent(v > 100 ? "High" : "Low"))})
