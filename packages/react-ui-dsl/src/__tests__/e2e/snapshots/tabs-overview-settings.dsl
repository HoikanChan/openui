root = VLayout([tabsContainer])
tabsContainer = Tabs(tabItems)
tabItems = [
  {value: "overview", label: "Overview", content: [overviewContent]},
  {value: "settings", label: "Settings", content: [settingsContent]}
]
overviewContent = Card([overviewText], "card", "standard", {title: "Overview", subtitle: "System summary"})
overviewText = Text("Welcome to the system overview. Here you can monitor key metrics and recent activity.")
settingsContent = Card([settingsForm], "card", "standard", {title: "Configuration", subtitle: "Adjust system settings"})
settingsForm = Form(formFields, "vertical", "left", {notifications: true, theme: "light"})
formFields = [
  {label: "Theme", name: "theme", rules: [{required: true}], component: themeSelect},
  {label: "Notifications", name: "notifications", rules: [], component: notificationsToggle}
]
themeSelect = Select([{label: "Light", value: "light"}, {label: "Dark", value: "dark"}, {label: "Auto", value: "auto"}], "light", false)
notificationsToggle = Select([{label: "Enabled", value: true}, {label: "Disabled", value: false}], true, false)