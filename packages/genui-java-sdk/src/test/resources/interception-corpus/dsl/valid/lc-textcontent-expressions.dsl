root = Stack([statusText, fallbackText, countText], "column", "m", "start", "start")
statusText = TextContent(data.system.healthy ? "All systems healthy" : "Degraded performance detected", "large")
fallbackText = TextContent(data.user.displayName ?? "Anonymous user", "default")
countText = TextContent("Active alerts: " + @Count(data.alerts), "small")
