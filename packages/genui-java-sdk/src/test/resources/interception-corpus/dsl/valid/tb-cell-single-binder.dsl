root = Stack([priceTable])
priceTable = Table([itemCol, priceCol], data.catalog)
itemCol = Col("Item", "item")
priceCol = Col("Price", "price", {cell: @Render("v", TextContent(v + " USD"))})
