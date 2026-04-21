root = VLayout([treemapCard])
treemapCard = Card([treemapHeader, treemapChart])
treemapHeader = CardHeader("Bandwidth Breakdown", "Traffic volume by subnet and interface")
treemapChart = TreeMapChart(treeData)
treeData = data.data