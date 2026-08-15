root = Stack([dashboardTabs])
dashboardTabs = Tabs([overviewTab, detailsTab])
overviewTab = Card([CardHeader("Overview"), TextContent("High-level metrics for the account.", "default")])
detailsTab = Card([CardHeader("Details"), TextContent("Line-by-line breakdown of usage.", "default")])
