root = VLayout([chart])
chart = AreaChart(data.labels, data.series, "smooth", "Time (Hours)", "Bandwidth (Mbps)")