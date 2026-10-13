root = Stack([localCard, neighborCard], "column", "m")

localCard = Card([localHeader, localDetails], "card")
localHeader = CardHeader("Local Interface", data.localInterface.deviceName)
localDetails = Descriptions([
  DescField("Device IP", data.localInterface.deviceIp),
  DescField("Interface", data.localInterface.interfaceName)
])

neighborCard = Card([neighborHeader, neighborTable], "card")
neighborHeader = CardHeader("OSPF Neighbors", "Count: " + @Count(data.ospfNeighbors))
neighborTable = Table([
  Col("Device Name", "deviceName"),
  Col("Device IP", "deviceIp"),
  Col("Interface", "interfaceName")
], data.ospfNeighbors)
