root = VLayout([title, chart])
title = Text("Bandwidth Utilization Over 24 Hours", "large")
chart = AreaChart(data.labels, data.series, "smooth", "Time (Hours)", "Download (Mbps)")