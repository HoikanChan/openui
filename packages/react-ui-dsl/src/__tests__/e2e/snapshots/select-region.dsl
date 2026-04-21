root = VLayout([regionSelector, regionText])
regionSelector = Select(data.options, data.defaultValue)
regionText = Text("Selected region: North America (default)")