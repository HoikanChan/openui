root = Stack([reportTabs])
reportTabs = Tabs([salesTab, opsTab], {style: {orientation: "horizontal", size: "large"}})
salesTab = Stack([TextContent("Sales", "large"), TextContent("Pipeline value $4.2M", "default")], "column", "m", "start", "start")
opsTab = Stack([TextContent("Operations", "large"), List(["Servers healthy", "Queues drained"], "Status")], "column", "m", "start", "start")
