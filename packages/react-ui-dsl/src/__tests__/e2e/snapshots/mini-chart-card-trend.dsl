root = VLayout([card])
card = Card([title, trend], "card", "standard")
title = Text("7-Day Latency Trend", "large")
trend = MiniChart("line", data.sparkline, 96, "#1677ff")
