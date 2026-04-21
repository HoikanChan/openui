root = VLayout([summaryCard])
summaryCard = Card("clear", "standard", header, [summaryContent])
header = {title: data.summary.heading}
summaryContent = Text("The quarterly growth was **" + data.summary.growth + "** over the previous period.", "markdown")