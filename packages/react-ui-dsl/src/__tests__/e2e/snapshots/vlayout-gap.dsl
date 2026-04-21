root = VLayout([revenueStack])
revenueStack = VLayout([revenueLine1, revenueLine2], 16)
revenueLine1 = Text(data.report.revenueLines[0])
revenueLine2 = Text(data.report.revenueLines[1])