root = VLayout([scatterChartContainer])
scatterChartContainer = Card([scatterChart], "clear", "full")
scatterChart = ScatterChart([scatterSeries], data.xLabel, data.yLabel)
scatterSeries = ScatterSeries(data.scatterSeries.name, data.scatterSeries.points)