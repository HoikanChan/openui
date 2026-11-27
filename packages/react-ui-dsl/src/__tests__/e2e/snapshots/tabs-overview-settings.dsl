root = Stack([tabs])
tabs = Tabs([
  {value: "overview", label: "Overview", content: [overviewContent]},
  {value: "settings", label: "Settings", content: [settingsContent]}
], {style: {height: "100%"}}
)
overviewContent = Stack([TextContent("Welcome to the Overview tab. This is where you can view system status and key metrics.", "default")], "column", "m", "start", "start")
settingsContent = Stack([TextContent("This is the Settings tab. Configure your preferences and system options here.", "default")], "column", "m", "start", "start")