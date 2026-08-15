root = Stack([userTable])
userTable = Table([nameCol, roleCol], data.users)
nameCol = Col("Name", "name")
roleCol = Col("Role", "role", {cell: @Render("v", "row", TextContent(row.name + " is " + v))})
