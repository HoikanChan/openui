root = List(@Each(data.tasks, "t", TextContent(t.name + " (" + t.status + ")", "small")), "Tasks")
