root = Stack([header, summaryRow, deviceTableCard], "column", "m")

header = CardHeader("Fabric Device Inventory", "Scene: " + data.deployScene)

summaryRow = Stack([totalCard, onlineCard], "row", "m", "stretch", "start")
totalCard = Card([TextContent("Total Devices", "small"), TextContent("" + data.deviceTotal, "large-heavy")])
onlineCount = @Count(@Filter(data.deviceInfo, "neState", "==", "online"))
onlineCard = Card([TextContent("Online", "small"), TextContent("" + onlineCount, "large-heavy")])

deviceTableCard = Card([deviceTable])
deviceTable = Table([nameCol, ipCol, typeCol, roleCol, versionCol, stateCol], data.deviceInfo)

nameCol = Col("Name", "neName")
ipCol = Col("IP", "neIp")
typeCol = Col("Type", "neType")
roleCol = Col("Role", "neRole")
versionCol = Col("Version", "neVersion")
stateCol = Col("State", "neState", {cell: @Render("v", stateCellTpl)})

stateCellTpl = @Switch(v, {"online": Tag("Online", "success")}, Tag("Offline", "danger"))
