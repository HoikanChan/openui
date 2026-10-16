root = Stack([summaryRow, tableCard], "column", "m")

summaryRow = Stack([totalCard, activeCard, inactiveCard], "row", "m", "stretch")
totalCard = Card([TextContent("Total Interfaces", "small"), TextContent("" + @Count(data.aiInstanceData), "large-heavy")])
activeCard = Card([TextContent("Active", "small"), TextContent("" + @Count(@Filter(data.aiInstanceData, "adminStatus", "==", "active")), "large-heavy")])
inactiveCard = Card([TextContent("Inactive", "small"), TextContent("" + @Count(@Filter(data.aiInstanceData, "adminStatus", "==", "inactive")), "large-heavy")])

tableCard = Card([tableHeader, interfaceTable])
tableHeader = CardHeader("Interface Details", "List of network interfaces and their status")

interfaceTable = Table([nameCol, deviceCol, roleCol, adminCol, operCol, speedCol, macCol, fabricCol], data.aiInstanceData)

nameCol = Col("Interface Name", "ifName")
deviceCol = Col("Device", "neName", {cell: @Render("v", "row", TextContent(v + " (" + row.neIp + ")"))})
roleCol = Col("Role", "neRole")
adminCol = Col("Admin Status", "adminStatus", {cell: @Render("v", v == "active" ? Tag("Active", "success") : Tag("Inactive", "danger"))})
operCol = Col("Oper Status", "operStatus", {cell: @Render("v", v == "active" ? Tag("Active", "success") : Tag("Inactive", "danger"))})
speedCol = Col("Speed", "ifHighSpeed", {cell: @Render("v", v == "0" ? TextContent("--") : TextContent(v + " Mbps"))})
macCol = Col("MAC Address", "ifPhysAddress")
fabricCol = Col("Fabric", "fabricName")
