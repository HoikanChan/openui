root = Stack([title, gaugeChart])
title = Text("System Health Score", "large")
gaugeChart = GaugeChart(data.readings, 0, 100)