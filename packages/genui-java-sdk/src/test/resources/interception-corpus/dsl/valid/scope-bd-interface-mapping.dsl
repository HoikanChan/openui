root = Stack([header, bdTableCard], "column", "m")

header = CardHeader("BD Interface Mapping", "Device: " + data.device + " (" + data.deviceIp + ")")

bdTableCard = Card([bdTable])

bdTable = Table([bdIdCol, subinterfacesCol], data.data)

bdIdCol = Col("BD ID", "bdId")

subinterfacesCol = Col("Sub-interfaces", "subinterfaces", {cell: @Render("v", v.length > 0 ? TagBlock(v) : TextContent("None"))})
