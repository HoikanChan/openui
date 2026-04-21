root = VLayout([treemapCard])
treemapCard = Card([treemapTitle, treemapChart])
treemapTitle = Text("Bandwidth Breakdown by Subnet and Interface", "markdown")
treemapChart = TreeMapChart(treemapData)
treemapData = data.data