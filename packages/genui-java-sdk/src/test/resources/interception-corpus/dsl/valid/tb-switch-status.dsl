root = Stack([ticketTable])
ticketTable = Table([idCol, statusCol], data.tickets)
idCol = Col("Ticket", "id")
statusCol = Col("Status", "status", {cell: @Render("v", TextContent(@Switch(v, {"open": "Open", "closed": "Closed", "wip": "In Progress"}, "Unknown")))})
