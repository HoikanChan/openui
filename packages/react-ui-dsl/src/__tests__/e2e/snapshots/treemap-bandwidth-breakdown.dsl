root = VLayout([title, treemap])
title = Text("Bandwidth Breakdown by Subnet and Interface", "large")
treemap = TreeMapChart(data.data)