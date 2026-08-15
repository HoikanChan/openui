root = Stack([invoiceTable])
invoiceTable = Table([idCol, amountCol], data.invoices)
idCol = Col("Invoice", "id")
amountCol = Col("Amount", "amount", {cell: @Render("v", "row", TextContent(row.id + ": " + v))})
