root = VLayout([regionSelectorCard])
regionSelectorCard = Card([regionSelectorHeader, regionSelectorForm])
regionSelectorHeader = CardHeader("Region Selection", "Choose your deployment region")
regionSelectorForm = Form([regionField])
regionField = {label: "Region", name: "region", component: regionSelect}
regionSelect = Select(data.options, data.defaultValue)