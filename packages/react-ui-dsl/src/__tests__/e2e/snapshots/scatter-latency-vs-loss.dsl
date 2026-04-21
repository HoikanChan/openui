root = VLayout([scatterChartContainer])
scatterChartContainer = Card([scatterChart])
scatterChart = ScatterChart([data.scatterSeries], data.xLabel, data.yLabel)