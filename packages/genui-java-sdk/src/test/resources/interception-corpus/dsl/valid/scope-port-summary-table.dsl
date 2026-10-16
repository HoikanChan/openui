root = Stack([summaryHeader, kpiRow, deviceTableCard], "column", "m")

summaryHeader = CardHeader("Network Device Port Summary", "Overview of 4 devices and 228 total ports")

kpiRow = Stack([totalDevicesKpi, totalPortsKpi, activeOperKpi, activeAdminKpi], "row", "m", "stretch", "start", true)

totalDevicesKpi = Card([TextContent("Total Devices", "small"), TextContent("" + data.summary.total_devices, "large-heavy")])
totalPortsKpi = Card([TextContent("Total Ports", "small"), TextContent("" + data.summary.total_ports, "large-heavy")])
activeOperKpi = Card([TextContent("Operationally Active", "small"), TextContent("" + data.summary.operStatus_dist.active, "large-heavy")])
activeAdminKpi = Card([TextContent("Administratively Active", "small"), TextContent("" + data.summary.adminStatus_dist.active, "large-heavy")])

deviceTableCard = Card([deviceTableHeader, deviceTable])
deviceTableHeader = CardHeader("Device Details", "Port status breakdown per device")

deviceTable = Table([nameCol, ipCol, totalPortsCol, adminActiveCol, adminInactiveCol, operActiveCol, operInactiveCol], data.devices)

nameCol = Col("Device Name", "deviceName", {cell: @Render("v", TextContent(v, "default"))})
ipCol = Col("IP Address", "neIp", {cell: @Render("v", TextContent(v, "default"))})
totalPortsCol = Col("Total Ports", "totalPorts", {cell: @Render("v", TextContent("" + v, "default"))})
adminActiveCol = Col("Admin Active", "adminStatus", {cell: @Render("v", TextContent("" + (v.active ?? 0), "default"))})
adminInactiveCol = Col("Admin Inactive", "adminStatus", {cell: @Render("v", TextContent("" + (v.inactive ?? 0), "default"))})
operActiveCol = Col("Oper Active", "operStatus", {cell: @Render("v", TextContent("" + (v.active ?? 0), "default"))})
operInactiveCol = Col("Oper Inactive", "operStatus", {cell: @Render("v", TextContent("" + (v.inactive ?? 0), "default"))})
