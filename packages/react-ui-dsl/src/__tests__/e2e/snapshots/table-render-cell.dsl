root = VLayout([ordersTable])
ordersTable = Table([idCol, statusCol], data.orders)
idCol = Col("Order ID", "id")
statusCol = Col("Status", "status", {cell: @Render("v", "row", Text(row.id + ": " + @Switch(v, {"paid": "Paid", "pending": "Pending"}, "Unknown")))})