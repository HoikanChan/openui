root = Stack([header, treemapChart])
header = TextContent("Bandwidth Breakdown by Subnet and Interface", "large")
treemapData = @Each(data.data, "item", {name: item.name, value: item.value, group: item.group})
treemapChart = TreeMapChart(treemapData)