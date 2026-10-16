root = Stack([header, notificationsList], "column", "m", "start", "start")
header = TextContent("Recent Notifications", "large")
notificationsList = List(@Each(data.notifications, "n", TextContent(n.title + " - " + n.time, "small")), "Notifications")
