root = Stack([keysList, entriesList])
keysList = List(@ObjectKeys(data.config), "Config Keys")
entriesList = List(@Each(@ObjectEntries(data.config), "entry", TextContent(entry.key + ": " + entry.value, "small")), "Config Entries")
