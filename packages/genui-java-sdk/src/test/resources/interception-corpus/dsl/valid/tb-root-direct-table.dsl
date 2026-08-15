root = metricsTable
metricsTable = Table([metricCol, valueCol, changeCol], data.metrics)
metricCol = Col("Metric", "metric")
valueCol = Col("Value", "value", {cell: @Render("v", TextContent(@FormatNumber(v, 1, "en-US")))})
changeCol = Col("Change", "change", {cell: @Render("v", "row", TextContent(row.value > 0 ? "Up" : "Down"))})
