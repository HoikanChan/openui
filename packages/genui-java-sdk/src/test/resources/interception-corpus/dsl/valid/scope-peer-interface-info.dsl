root = Stack([infoCard])
infoCard = Card([header, details])
header = CardHeader("Peer Interface Information", "Fabric: " + (data.peer_interface_info["Fabric"] ?? "N/A"))
details = Descriptions([
  DescField("Local Device", data.peer_interface_info["本端设备"] ?? "No data"),
  DescField("Local Device IP", data.peer_interface_info["本端设备IP"] ?? "No data"),
  DescField("Local Interface", data.peer_interface_info["本端接口"] ?? "No data"),
  DescField("Remote Device", data.peer_interface_info["对端设备"] ?? "No data"),
  DescField("Remote Device IP", data.peer_interface_info["对端设备IP"] ?? "No data"),
  DescField("Remote Interface", data.peer_interface_info["对端接口"] ?? "No data"),
  DescField("Remote IP Address", data.peer_interface_info["对端IP地址"] ?? "No data"),
  DescField("Management Status", data.peer_interface_info["对端管理状态"] ?? "No data"),
  DescField("Operational Status", data.peer_interface_info["对端操作状态"] ?? "No data")
], "Connection Details")
