root = Stack([nameText, countText, combo])
nameText = TextContent(data.user.name ?? "Guest")
countText = TextContent(data.count ?? 0)
combo = TextContent((data.title ?? "Untitled") + " - " + (data.subtitle ?? "no subtitle"))
