// Showcase array literals, object literals, arithmetic, member access, and comments.
root = Stack([summary, itemsTable])
// arithmetic with member access and grouping
summary = TextContent("Net: " + (data.gross - data.fees) / 100)
// array literal of column defs; object literals for column options
itemsTable = Table([nameCol, priceCol], data.items)
nameCol = Col("Name", "name", {sortable: true})
priceCol = Col("Price", "price", {sortable: false})
