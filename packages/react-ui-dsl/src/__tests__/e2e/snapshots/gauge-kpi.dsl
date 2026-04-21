root = VLayout([healthGauge])
healthGauge = GaugeChart(data.readings, 0, 100)