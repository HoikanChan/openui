root = Stack([header, summaryCards], "column", "m")

header = Card([titleText, subtitleText])
titleText = TextContent(data.title, "large-heavy")
subtitleText = TextContent(data.subtitle, "small")

summaryCards = Stack(@Each(data.summary, "item", deviceCardTpl), "column", "m")

deviceCardTpl = Card([deviceHeader, versionList], "column", "s")
deviceHeader = Stack([neTypeTag, totalCountTag], "row", "s")
neTypeTag = Tag(item.neType, "info", "md")
totalCountTag = Tag("共 " + item.totalCount + " 台", "neutral", "md")

versionList = Stack(@Each(item.versionList, "ver", versionRowTpl), "column", "s")

versionRowTpl = Card([versionInfo, deviceNamesList], "column", "xs", "start", "start", false)
versionInfo = Stack([versionText, countText], "row", "s", "center")
versionText = TextContent("版本: " + ver.version, "default")
countText = Tag(ver.deviceCount + " 台", "neutral", "sm")

deviceNamesList = Stack(@Each(ver.deviceNames, "name", nameTagTpl), "row", "xs", "start", "start", true)
nameTagTpl = Tag(name, "neutral", "sm")
