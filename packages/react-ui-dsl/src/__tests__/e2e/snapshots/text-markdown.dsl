root = VLayout([summaryCard])
summaryCard = Card([summaryContent], "card", "standard", {title: "Q1 Results Summary"})
summaryContent = VLayout([summaryText])
summaryText = Text("# Q1 Results\n\nRevenue growth for the quarter was **" + data.summary.growth + "** year‑over‑year.", "markdown")