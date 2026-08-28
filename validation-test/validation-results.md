# validation-test 真实校验结果

- Validator: `DefaultOpenuiLangValidator.INSTANCE`
- Mode: `FINAL`
- Contract: 当前 Java SDK `openui/base-contract.json`
- 总用例数: 49
- 状态统计: `{INVALID=26, VALID=23}`
- 原始 Issue 总数: 90
- 可操作 Issue 总数: 60
- 原始 Issue 统计: `{builtin-argument-type-mismatch=1, component-slot-type-mismatch=1, missing-required=1, root-missing=1, syntax-missing-assignment=2, syntax-unclosed-bracket=6, syntax-unexpected-token=10, type-chart-duplicate-series-value=1, type-chart-insufficient-data=4, type-operator-mismatch=2, type-prop-mismatch=35, type-table-column-missing=10, type-table-row-shape-mismatch=3, unknown-component=6, unresolved-ref=7}`
- 可操作 Issue 统计: `{component-slot-type-mismatch=1, syntax-missing-assignment=2, syntax-unexpected-token=4, type-chart-duplicate-series-value=1, type-chart-insufficient-data=4, type-operator-mismatch=2, type-prop-mismatch=26, type-table-column-missing=10, type-table-row-shape-mismatch=3, unknown-component=6, unresolved-ref=1}`
- 标注覆盖: `{DETECTED=18, MISSED=7, IGNORED=23, UNLABELED=1}`

每个结果均由当前代码实际运行生成。`actionableIssues` 用于修复与报告；`rawIssues` 保留完整诊断和跨语言 parity。

## 1. rw-046-CPU-CPU-cpu_usage

- 来源: `1.json` 第 1 条
- 状态: `INVALID`
- 原始 Issue 数: 4
- 可操作 Issue 数: 4

- `error_detail.txt` 标注: 没有趋势折线图。
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([cpuCard, memCard])
cpuCard = Card([cpuHeader, cpuChart])
cpuHeader = CardHeader("CPU利用率趋势", "近1小时")
cpuLabels = @Each(data.devices[0].cpu_data, "item", @FormatDate(item.timestamp, "time"))
cpuSeries1 = Series(data.devices[0].neIp, @Each(data.devices[0].cpu_data, "item", item.value))
cpuSeries2 = Series(data.devices[1].neIp, @Each(data.devices[1].cpu_data, "item", item.value))
cpuChart = LineChart(cpuLabels, [cpuSeries1, cpuSeries2], "smooth", "时间", "%")
memCard = Card([memHeader, memChart])
memHeader = CardHeader("内存利用率趋势", "近1小时")
memLabels = @Each(data.devices[0].mem_data, "item", @FormatDate(item.timestamp, "time"))
memSeries1 = Series(data.devices[0].neIp, @Each(data.devices[0].mem_data, "item", item.value))
memSeries2 = Series(data.devices[1].neIp, @Each(data.devices[1].mem_data, "item", item.value))
memChart = LineChart(memLabels, [memSeries1, memSeries2], "smooth", "时间", "%")
````

### 输入 dataModel

````json
{
  "devices": [
    {
      "neName": "O05-17F-Leaf2-Simu6-0001",
      "mem_data": [
        {
          "value": 70,
          "timestamp": 1785927600000
        }
      ],
      "neIp": "197.197.5.1",
      "cpu_data": [
        {
          "value": 70,
          "timestamp": 1785927600000
        }
      ]
    },
    {
      "neName": "O05-17F-Leaf2-Simu6-0002",
      "mem_data": [
        {
          "value": 70,
          "timestamp": 1785927600000
        }
      ],
      "neIp": "197.197.5.2",
      "cpu_data": [
        {
          "value": 70,
          "timestamp": 1785927600000
        }
      ]
    }
  ]
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-chart-insufficient-data
severity=ERROR
source=type
statementId=cpuChart
component=LineChart
path=/series/0
line=7
column=1
retryable=false
message=LineChart series 0 has only 1 point(s), so it cannot draw a trend line.
hint=Provide at least two ordered points for a trend line.

issue[1]
code=type-chart-insufficient-data
severity=ERROR
source=type
statementId=cpuChart
component=LineChart
path=/series/1
line=7
column=1
retryable=false
message=LineChart series 1 has only 1 point(s), so it cannot draw a trend line.
hint=Provide at least two ordered points for a trend line.

issue[2]
code=type-chart-insufficient-data
severity=ERROR
source=type
statementId=memChart
component=LineChart
path=/series/0
line=13
column=1
retryable=false
message=LineChart series 0 has only 1 point(s), so it cannot draw a trend line.
hint=Provide at least two ordered points for a trend line.

issue[3]
code=type-chart-insufficient-data
severity=ERROR
source=type
statementId=memChart
component=LineChart
path=/series/1
line=13
column=1
retryable=false
message=LineChart series 1 has only 1 point(s), so it cannot draw a trend line.
hint=Provide at least two ordered points for a trend line.
````

## 2. rw-064-GE1-Eth-Trunk101-Spine1

- 来源: `1.json` 第 2 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 归属关系未阐释。
- 标注覆盖结果: `MISSED`

### 输入 DSL

````openui
root = Stack([titleCard, tableCard])

titleCard = CardHeader("接口归属关系", "物理接口 → 聚合口 → 设备")

tableCard = Card([table])

table = Table([typeCol, nameCol, ipCol], rows)

typeCol = Col("类型", "type")
nameCol = Col("名称", "name")
ipCol = Col("IP 地址", "ip")

rows = [
  {
    "type": "物理接口",
    "name": data.physical_interface["interface-name"],
    "ip": data.physical_interface["device-ip"]
  },
  {
    "type": "聚合口",
    "name": data.aggregate_interface["interface-name"],
    "ip": data.aggregate_interface["device-ip"]
  },
  {
    "type": "设备",
    "name": data.device["device-name"],
    "ip": data.device["device-ip"]
  }
]
````

### 输入 dataModel

````json
{
  "aggregate_interface": {
    "device-ip": "10.136.193.102",
    "interface-name": "Eth-Trunk101"
  },
  "physical_interface": {
    "device-ip": "10.136.193.102",
    "interface-name": "25GE1/0/1"
  },
  "device": {
    "device-ip": "10.136.193.102",
    "device-name": "Spine1"
  }
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 3. rw-068-BGP-Peer-BGP

- 来源: `1.json` 第 3 条
- 状态: `INVALID`
- 原始 Issue 数: 1
- 可操作 Issue 数: 1

- `error_detail.txt` 标注: 表格未渲染
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([bgpCard])

bgpCard = Card([bgpHeader, bgpTable])
bgpHeader = CardHeader("BGP Peer邻居信息", "设备: 10.136.193.108")
allPeers = @Each(data.overlay_bgp_peers, "p", p) + @Each(data.underlay_bgp_peers, "p", p)
bgpTable = Table([typeCol, localIpCol, localIfCol, peerIpCol, peerIfCol], allPeers)

typeCol = Col("BGP类型", "bgp_type", {cell: @Render("v", @Switch(v, {"Overlay BGP": Tag("Overlay BGP", "info"), "Underlay BGP": Tag("Underlay BGP", "success")}, Tag(v)))})
localIpCol = Col("本端设备IP", "local_device_ip")
localIfCol = Col("本端接口", "local_interface")
peerIpCol = Col("对端设备IP", "peer_device_ip")
peerIfCol = Col("对端接口", "peer_interface")
````

### 输入 dataModel

````json
{
  "overlay_bgp_peers": [
    {
      "peer_interface": "LoopBack1",
      "peer_device_ip": "10.136.193.102",
      "bgp_type": "Overlay BGP",
      "local_interface": "LoopBack1",
      "local_device_ip": "10.136.193.108"
    }
  ],
  "underlay_bgp_peers": []
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-operator-mismatch
severity=ERROR
source=type
statementId=allPeers
component=<null>
path=<null>
line=5
column=1
retryable=false
message=Operator + cannot combine object[] with empty[].
hint=Use scalar operands; use @Each or a data builtin for arrays and objects.
````

## 4. rw-079-CPU-O05-17F-Leaf2-Simu6-0001-IP

- 来源: `1.json` 第 4 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 时间间隔不对，源数据有问题。
- 标注覆盖结果: `MISSED`

### 输入 DSL

````openui
root = Stack([infoCard, chartCard], "column", "m")

infoCard = Card([infoHeader, infoDesc], "card")
infoHeader = CardHeader("设备 CPU 利用率趋势", data.deviceName)
infoDesc = Descriptions([nameField, ipField, rangeField], "设备信息", 2, true)
nameField = DescField("设备名称", data.deviceName)
ipField = DescField("设备IP", data.deviceIp)
rangeField = DescField("时间范围", data.timeRange)

chartCard = Card([chartHeader, cpuChart], "card")
chartHeader = CardHeader("CPU 利用率变化", "近30分钟趋势 (每10秒采样)")

timeLabels = @Each(data.trendData, "item", @FormatDate(item.timestamp, "time"))
cpuValues = @Each(data.trendData, "item", item.cpu_usage)
cpuSeries = Series("CPU 利用率", cpuValues)

cpuChart = LineChart(timeLabels, [cpuSeries], "smooth", "时间", "CPU 利用率 (%)")
````

### 输入 dataModel

````json
{
  "deviceIp": "197.197.5.1",
  "trendData": [
    {
      "cpu_usage": 70,
      "timestamp": 1785991260000
    },
    {
      "cpu_usage": 70,
      "timestamp": 1785991320000
    },
    {
      "cpu_usage": 70,
      "timestamp": 1785991380000
    },
    {
      "cpu_usage": 70,
      "timestamp": 1785991440000
    },
    {
      "cpu_usage": 70,
      "timestamp": 1785991500000
    },
    {
      "cpu_usage": 70,
      "timestamp": 1785991560000
    },
    {
      "cpu_usage": 70,
      "timestamp": 1785991620000
    }
  ],
  "deviceName": "O05-17F-Leaf2-Simu6-0001",
  "timeRange": "2026-08-06 12:11:09 ~ 2026-08-06 13:11:09 (近30分钟)"
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 5. rw-080-Fabric-default1-ServerLeaf

- 来源: `1.json` 第 5 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 没有汇总
- 标注覆盖结果: `MISSED`

### 输入 DSL

````openui
root = Stack([headerCard, tableCard])

headerCard = CardHeader("Fabric \"default1\" ServerLeaf 400GE端口在线情况统计", "设备级端口状态汇总")

tableCard = Card([table])

table = Table([nameCol, totalCol, adminCol, upCol, inactiveCol], tableRows)

nameCol = Col("设备名称", "name")
totalCol = Col("400GE端口总数", "totalPorts")
adminCol = Col("adminStatus=active", "adminActive")
upCol = Col("operStatus=Up", "operUp")
inactiveCol = Col("operStatus=inactive", "operInactive")

tableRows = @Each(data.perDevice, "d", {
  "name": d.neName,
  "totalPorts": d.totalPorts,
  "adminActive": d.adminActive,
  "operUp": d.operUp,
  "operInactive": d.operInactive
})
````

### 输入 dataModel

````json
{
  "summary": {
    "totalPorts": 870,
    "totalDevices": 10,
    "totalAdminActive": 710,
    "totalOperInactive": 870,
    "totalOperUp": 0
  },
  "perDevice": [
    {
      "totalPorts": 87,
      "operInactive": 87,
      "operUp": 0,
      "neName": "O05-17F-Leaf2-Simu6-0001",
      "adminActive": 71
    },
    {
      "totalPorts": 87,
      "operInactive": 87,
      "operUp": 0,
      "neName": "O05-17F-Leaf2-Simu6-0002",
      "adminActive": 71
    },
    {
      "totalPorts": 87,
      "operInactive": 87,
      "operUp": 0,
      "neName": "O05-17F-Leaf2-Simu6-0003",
      "adminActive": 71
    },
    {
      "totalPorts": 87,
      "operInactive": 87,
      "operUp": 0,
      "neName": "O05-17F-Leaf2-Simu6-0004",
      "adminActive": 71
    },
    {
      "totalPorts": 87,
      "operInactive": 87,
      "operUp": 0,
      "neName": "O05-17F-Leaf2-Simu6-0005",
      "adminActive": 71
    },
    {
      "totalPorts": 87,
      "operInactive": 87,
      "operUp": 0,
      "neName": "O05-17F-Leaf2-Simu6-0006",
      "adminActive": 71
    },
    {
      "totalPorts": 87,
      "operInactive": 87,
      "operUp": 0,
      "neName": "O05-17F-Leaf2-Simu6-0007",
      "adminActive": 71
    },
    {
      "totalPorts": 87,
      "operInactive": 87,
      "operUp": 0,
      "neName": "O05-17F-Leaf2-Simu6-0008",
      "adminActive": 71
    },
    {
      "totalPorts": 87,
      "operInactive": 87,
      "operUp": 0,
      "neName": "O05-17F-Leaf2-Simu6-0009",
      "adminActive": 71
    },
    {
      "totalPorts": 87,
      "operInactive": 87,
      "operUp": 0,
      "neName": "O05-17F-Leaf2-Simu6-0010",
      "adminActive": 71
    }
  ]
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 6. rw-126-O05-17F-Leaf2-Simu6-0001-UP-active

- 来源: `2.json` 第 1 条
- 状态: `INVALID`
- 原始 Issue 数: 1
- 可操作 Issue 数: 1

- `error_detail.txt` 标注: 表格渲染错误。
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([header, chartsRow, abnormalTable, shutdownTable], "column", "l")
header = CardHeader("设备端口概览", "O05-17F-Leaf2-Simu6-0001 · 总端口: " + data.queryDevicePort.aiInstanceTotal)
chartsRow = Stack([statusPieCard, comboBarCard], "row", "m", "stretch", "start", true)
statusPieCard = Card([statusPieHeader, statusPieChart])
statusPieHeader = CardHeader("端口运行状态", "UP / DOWN 分布")
upCount = @Count(@Filter(data.queryDevicePort.aiInstanceData, "operStatus", "==", "active"))
downCount = @Count(@Filter(data.queryDevicePort.aiInstanceData, "operStatus", "==", "inactive"))
statusPieChart = PieChart(["UP (Active)", "DOWN (Inactive)"], [upCount, downCount], "donut")
comboBarCard = Card([comboBarHeader, comboBarChart])
comboBarHeader = CardHeader("端口状态组合", "Admin + Oper 分布")
adminUpOperUp = @Count(@Filter(data.queryDevicePort.aiInstanceData, "adminStatus", "==", "active"))
adminUpOperDown = @Count(@Filter(data.queryDevicePort.aiInstanceData, "adminStatus", "==", "active"))
adminDownOperDown = @Count(@Filter(data.queryDevicePort.aiInstanceData, "adminStatus", "==", "inactive"))
comboBarChart = BarChart(["Admin UP + Oper UP", "Admin UP + Oper DOWN", "Admin DOWN + Oper DOWN"], [Series("端口数", [adminUpOperUp, adminUpOperDown, adminDownOperDown])], "grouped", "状态组合", "端口数")
abnormalTable = Card([abnormalHeader, abnormalTableContent])
abnormalHeader = CardHeader("异常DOWN端口", "Admin=Active 但 Oper=Inactive · 共 " + @Count(@Filter(data.queryDevicePort.aiInstanceData, "adminStatus", "==", "active")) + " 个")
abnormalRows = @Filter(data.queryDevicePort.aiInstanceData, "adminStatus", "==", "active")
abnormalTableContent = Table([abnormalNameCol, abnormalAdminCol, abnormalOperCol, abnormalIpCol, abnormalAliasCol], abnormalRows)
abnormalNameCol = Col("端口名", "ifName")
abnormalAdminCol = Col("管理状态", "adminStatus", {cell: @Render("v", @Switch(v, {"active": Tag("Active", "success"), "inactive": Tag("Inactive", "danger")}, Tag(v)))})
abnormalOperCol = Col("运行状态", "operStatus", {cell: @Render("v", @Switch(v, {"active": Tag("Active", "success"), "inactive": Tag("Inactive", "danger")}, Tag(v)))})
abnormalIpCol = Col("IP", "ifIp")
abnormalAliasCol = Col("别名", "ifAlias")
shutdownTable = Card([shutdownHeader, shutdownTableContent])
shutdownHeader = CardHeader("人为Shutdown端口", "Admin=Inactive · 共 " + @Count(@Filter(data.queryDevicePort.aiInstanceData, "adminStatus", "==", "inactive")) + " 个")
shutdownRows = @Filter(data.queryDevicePort.aiInstanceData, "adminStatus", "==", "inactive")
shutdownTableContent = Table([shutdownNameCol, shutdownAdminCol, shutdownOperCol, shutdownIpCol, shutdownAliasCol], shutdownRows)
shutdownNameCol = Col("端口名", "ifName")
shutdownAdminCol = Col("管理状态", "adminStatus", {cell: @Render("v", @Switch(v, {"active": Tag("Active", "success"), "inactive": Tag("Inactive", "danger")}, Tag(v)))})
shutdownOperCol = Col("运行状态", "operStatus", {cell: @Render("v", @Switch(v, {"active": Tag("Active", "success"), "inactive": Tag("Inactive", "danger")}, Tag(v)))})
shutdownIpCol = Col("IP", "ifIp")
shutdownAliasCol = Col("别名", "ifAlias")
````

### 输入 dataModel

````json
{
  "queryDevicePort": {
    "isTruncated": false,
    "aiInstanceTotal": 172,
    "aiInstanceData": [
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "223",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16872754",
        "ifIp": "170.1.46.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/36:2",
        "ifSpeed": "200000000000",
        "resId": "581c8fbb-9baa-4032-892f-6820370c8d16",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "581c8fbb-9baa-4032-892f-6820370c8d16",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/42",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "46",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847147",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/42",
        "ifSpeed": "400000000000",
        "resId": "df90b94d-3974-4039-990a-a91d446edbc4",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "df90b94d-3974-4039-990a-a91d446edbc4",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "54",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847155",
        "ifIp": "150.1.82.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/50",
        "ifSpeed": "400000000000",
        "resId": "4f9c68fe-8cad-4049-912e-91fcd0dfea85",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "4f9c68fe-8cad-4049-912e-91fcd0dfea85",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/125",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "129",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847230",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/125",
        "ifSpeed": "400000000000",
        "resId": "f44bde3a-4a5e-4057-818b-2edfba69b51b",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "f44bde3a-4a5e-4057-818b-2edfba69b51b",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/97",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "101",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847202",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/97",
        "ifSpeed": "400000000000",
        "resId": "0c4d7c32-f783-4061-9d7f-2cd72a7fbaa3",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "0c4d7c32-f783-4061-9d7f-2cd72a7fbaa3",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "82",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847183",
        "ifIp": "150.1.42.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/78",
        "ifSpeed": "400000000000",
        "resId": "6536d632-91cc-408e-a121-45b651999391",
        "ifAlias": "To_Spine-CE9866-6_400GE1/0/102",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "6536d632-91cc-408e-a121-45b651999391",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/46",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "50",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847151",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/46",
        "ifSpeed": "400000000000",
        "resId": "51e1409e-9e8a-40b2-8b35-19082aac1b22",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "51e1409e-9e8a-40b2-8b35-19082aac1b22",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "149",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16867633",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/16:1",
        "ifSpeed": "200000000000",
        "resId": "27be1c48-24c5-40d5-9fa9-bd1bd8d34373",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "27be1c48-24c5-40d5-9fa9-bd1bd8d34373",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "228",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16871985",
        "ifIp": "170.1.32.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/33:1",
        "ifSpeed": "200000000000",
        "resId": "26917da3-5798-4143-a1eb-4586622ad331",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "26917da3-5798-4143-a1eb-4586622ad331",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/20",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "24",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847125",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/20",
        "ifSpeed": "400000000000",
        "resId": "c714b77b-2479-416d-8ca8-2276d4191831",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "c714b77b-2479-416d-8ca8-2276d4191831",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/41",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "45",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847146",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/41",
        "ifSpeed": "400000000000",
        "resId": "57f40682-f1bd-417d-b51b-57a9002378e4",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "57f40682-f1bd-417d-b51b-57a9002378e4",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/89",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "93",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847194",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/89",
        "ifSpeed": "400000000000",
        "resId": "f0d48c50-de14-4184-8b3c-6608ca3fb6bd",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "f0d48c50-de14-4184-8b3c-6608ca3fb6bd",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/102",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "106",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847207",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/102",
        "ifSpeed": "400000000000",
        "resId": "11fea997-4d41-4186-9c90-abba4013d141",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "11fea997-4d41-4186-9c90-abba4013d141",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/118",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "122",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847223",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/118",
        "ifSpeed": "400000000000",
        "resId": "74caa7fe-7cf1-419b-9ee5-ccaa217ad4f7",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "74caa7fe-7cf1-419b-9ee5-ccaa217ad4f7",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "207",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16880690",
        "ifIp": "170.1.10.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/67:2",
        "ifSpeed": "200000000000",
        "resId": "44caf487-93c2-41ab-9c5c-41ab40c06edc",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "44caf487-93c2-41ab-9c5c-41ab40c06edc",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "135",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16865329",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/7:1",
        "ifSpeed": "200000000000",
        "resId": "6dc1d9bf-a041-41e0-a8ec-c3daea916e88",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "6dc1d9bf-a041-41e0-a8ec-c3daea916e88",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "206",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16880689",
        "ifIp": "170.1.8.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/67:1",
        "ifSpeed": "200000000000",
        "resId": "fe126f12-f37e-422f-b390-606c4c681807",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "fe126f12-f37e-422f-b390-606c4c681807",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "150",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16867634",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/16:2",
        "ifSpeed": "200000000000",
        "resId": "785ba84a-7a80-4247-9db3-b138268abfdd",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "785ba84a-7a80-4247-9db3-b138268abfdd",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "220",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16882993",
        "ifIp": "170.1.28.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/76:1",
        "ifSpeed": "200000000000",
        "resId": "c383f300-4de5-4267-80ee-b30c3e0610fc",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "c383f300-4de5-4267-80ee-b30c3e0610fc",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "194",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16885042",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/84:2",
        "ifSpeed": "200000000000",
        "resId": "d0ffe05d-19fd-429b-8dd5-d2e929f9cbfb",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "d0ffe05d-19fd-429b-8dd5-d2e929f9cbfb",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "162",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16867122",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/14:2",
        "ifSpeed": "200000000000",
        "resId": "e9cf9ddc-4ab1-42a9-af18-203ae96a61d9",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "e9cf9ddc-4ab1-42a9-af18-203ae96a61d9",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "142",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16864050",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/2:2",
        "ifSpeed": "200000000000",
        "resId": "1d92bbe8-0011-42d7-8104-f9d61cdf4c01",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "1d92bbe8-0011-42d7-8104-f9d61cdf4c01",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "184",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16878385",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/58:1",
        "ifSpeed": "200000000000",
        "resId": "aa7109b5-73bc-42f6-9204-fa331d6e635a",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "aa7109b5-73bc-42f6-9204-fa331d6e635a",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "221",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16882994",
        "ifIp": "170.1.30.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/76:2",
        "ifSpeed": "200000000000",
        "resId": "c5fdfc11-1487-4314-a90b-bee5c06b0182",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "c5fdfc11-1487-4314-a90b-bee5c06b0182",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "147",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16867377",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/15:1",
        "ifSpeed": "200000000000",
        "resId": "938e81a2-4a35-4365-92d6-98a761a54429",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "938e81a2-4a35-4365-92d6-98a761a54429",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "165",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16864561",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/4:1",
        "ifSpeed": "200000000000",
        "resId": "c5202b80-7d4f-43ae-97a2-7ad9698c390b",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "c5202b80-7d4f-43ae-97a2-7ad9698c390b",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "161",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16867121",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/14:1",
        "ifSpeed": "200000000000",
        "resId": "72f8b052-f9a4-43d2-ace0-ae2b9f5dc95b",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "72f8b052-f9a4-43d2-ace0-ae2b9f5dc95b",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/88",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "92",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847193",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/88",
        "ifSpeed": "400000000000",
        "resId": "97cfae0e-8ad1-43d4-a111-351f396ca479",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "97cfae0e-8ad1-43d4-a111-351f396ca479",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "212",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16880945",
        "ifIp": "170.1.12.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/68:1",
        "ifSpeed": "200000000000",
        "resId": "c7aebdae-f824-43df-b3f7-7661f8bbdfb9",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "c7aebdae-f824-43df-b3f7-7661f8bbdfb9",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "144",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16864818",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/5:2",
        "ifSpeed": "200000000000",
        "resId": "ce259525-afef-43f8-a555-9bd568843299",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "ce259525-afef-43f8-a555-9bd568843299",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/19",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "23",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847124",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/19",
        "ifSpeed": "400000000000",
        "resId": "f4be354f-45f1-4427-bebe-670ad62e20a7",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "f4be354f-45f1-4427-bebe-670ad62e20a7",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "224",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16872497",
        "ifIp": "170.1.40.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/35:1",
        "ifSpeed": "200000000000",
        "resId": "83c8f694-3ca6-442b-8b0a-497c80dc6964",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "83c8f694-3ca6-442b-8b0a-497c80dc6964",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "232",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16873521",
        "ifIp": "170.1.56.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/39:1",
        "ifSpeed": "200000000000",
        "resId": "d8d6429b-7408-4458-b46b-8d659d144f26",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "d8d6429b-7408-4458-b46b-8d659d144f26",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "192",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16884786",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/83:2",
        "ifSpeed": "200000000000",
        "resId": "11fd7ea9-9c9a-445b-a64a-8fc4d690730e",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "11fd7ea9-9c9a-445b-a64a-8fc4d690730e",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "136",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16865330",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/7:2",
        "ifSpeed": "200000000000",
        "resId": "279a24e6-1dc7-44a7-8259-7f7172a46c55",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "279a24e6-1dc7-44a7-8259-7f7172a46c55",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "73",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847174",
        "ifIp": "150.1.8.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/69",
        "ifSpeed": "400000000000",
        "resId": "6da7837f-4114-44aa-a7fb-01f760fcd22c",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/53",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "6da7837f-4114-44aa-a7fb-01f760fcd22c",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "226",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16872241",
        "ifIp": "170.1.36.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/34:1",
        "ifSpeed": "200000000000",
        "resId": "3206b0f1-5121-44ae-a3b5-2092cfb4805a",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "3206b0f1-5121-44ae-a3b5-2092cfb4805a",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/31",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "35",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847136",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/31",
        "ifSpeed": "400000000000",
        "resId": "488abe84-1517-44b6-a895-2e5f90c9ae32",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "488abe84-1517-44b6-a895-2e5f90c9ae32",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "236",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16873009",
        "ifIp": "170.1.48.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/37:1",
        "ifSpeed": "200000000000",
        "resId": "8e20feb1-6912-44bb-99af-7f020706c087",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "8e20feb1-6912-44bb-99af-7f020706c087",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/121",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "125",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847226",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/121",
        "ifSpeed": "400000000000",
        "resId": "ef410954-d95b-44c4-92be-cfd0451f8ed2",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "ef410954-d95b-44c4-92be-cfd0451f8ed2",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "173",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16878129",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/57:1",
        "ifSpeed": "200000000000",
        "resId": "36ccfa2e-5050-4520-a4ac-cacf45456c20",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "36ccfa2e-5050-4520-a4ac-cacf45456c20",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "190",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16884530",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/82:2",
        "ifSpeed": "200000000000",
        "resId": "7f201207-90dd-4524-a6eb-ce6bf92e7e9a",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "7f201207-90dd-4524-a6eb-ce6bf92e7e9a",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "137",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16865585",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/8:1",
        "ifSpeed": "200000000000",
        "resId": "096991b3-de13-4546-96fe-6588c2b62ec4",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "096991b3-de13-4546-96fe-6588c2b62ec4",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "151",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16865841",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/9:1",
        "ifSpeed": "200000000000",
        "resId": "8df18a10-4116-455b-a448-a4b57452d2ba",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "8df18a10-4116-455b-a448-a4b57452d2ba",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "217",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16882482",
        "ifIp": "170.1.22.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/74:2",
        "ifSpeed": "200000000000",
        "resId": "f103420d-5ec3-45a0-9c31-2acd8e54b07b",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "f103420d-5ec3-45a0-9c31-2acd8e54b07b",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "158",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16866610",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/12:2",
        "ifSpeed": "200000000000",
        "resId": "3290ae01-189c-45b3-be82-8456eb4ee432",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "3290ae01-189c-45b3-be82-8456eb4ee432",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/110",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "114",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847215",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/110",
        "ifSpeed": "400000000000",
        "resId": "a52f285e-2de1-45e8-97e0-a3025184dfd5",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "a52f285e-2de1-45e8-97e0-a3025184dfd5",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "233",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16873522",
        "ifIp": "170.1.58.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/39:2",
        "ifSpeed": "200000000000",
        "resId": "05588765-af8d-4609-8592-b75a92b93fef",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "05588765-af8d-4609-8592-b75a92b93fef",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "160",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16866866",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/13:2",
        "ifSpeed": "200000000000",
        "resId": "20a68ae8-c77c-464e-b7d3-53a913b04b33",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "20a68ae8-c77c-464e-b7d3-53a913b04b33",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "143",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16864817",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/5:1",
        "ifSpeed": "200000000000",
        "resId": "a1350669-520b-4656-aa5c-acda6627e87c",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "a1350669-520b-4656-aa5c-acda6627e87c",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "154",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16866098",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/10:2",
        "ifSpeed": "200000000000",
        "resId": "c1249a03-6e74-465a-af10-323b3be4af0a",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "c1249a03-6e74-465a-af10-323b3be4af0a",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/101",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "105",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847206",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/101",
        "ifSpeed": "400000000000",
        "resId": "a2f0606c-ac8c-465a-9142-86f2188bc842",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "a2f0606c-ac8c-465a-9142-86f2188bc842",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "75",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847176",
        "ifIp": "150.1.12.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/71",
        "ifSpeed": "400000000000",
        "resId": "e08cf12d-df1d-4665-acf8-b1b1c8e643ae",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/55",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "e08cf12d-df1d-4665-acf8-b1b1c8e643ae",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "83",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847184",
        "ifIp": "150.1.44.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/79",
        "ifSpeed": "400000000000",
        "resId": "b6850ab1-59f4-4666-a2e7-e123939d2242",
        "ifAlias": "To_Spine-CE9866-6_400GE1/0/103",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "b6850ab1-59f4-4666-a2e7-e123939d2242",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688130",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "10GE1/0/2",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "134",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16850179",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "10000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "10GE1/0/2",
        "ifSpeed": "10000000000",
        "resId": "7eea60eb-64b1-4685-bd5b-027ad30b22bd",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "7eea60eb-64b1-4685-bd5b-027ad30b22bd",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/117",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "121",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847222",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/117",
        "ifSpeed": "400000000000",
        "resId": "d313406b-2ed1-4692-97d1-511f727b100d",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "d313406b-2ed1-4692-97d1-511f727b100d",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/100",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "104",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847205",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/100",
        "ifSpeed": "400000000000",
        "resId": "005be1f3-f4c7-4697-b803-c9e4d7ef6717",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "005be1f3-f4c7-4697-b803-c9e4d7ef6717",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/91",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "95",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847196",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/91",
        "ifSpeed": "400000000000",
        "resId": "71375122-51cc-46a3-993a-c41c12e0a58c",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "71375122-51cc-46a3-993a-c41c12e0a58c",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/114",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "118",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847219",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/114",
        "ifSpeed": "400000000000",
        "resId": "41c09f80-59fb-46ad-b01b-f0f812e0048e",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "41c09f80-59fb-46ad-b01b-f0f812e0048e",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/86",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "90",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847191",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/86",
        "ifSpeed": "400000000000",
        "resId": "026677ab-17f2-46ae-8e20-f029aeb000a9",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "026677ab-17f2-46ae-8e20-f029aeb000a9",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "56",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847157",
        "ifIp": "150.1.86.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/52",
        "ifSpeed": "400000000000",
        "resId": "ebc117b5-efb7-46b0-af7f-519e01d15534",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "ebc117b5-efb7-46b0-af7f-519e01d15534",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/98",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "102",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847203",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/98",
        "ifSpeed": "400000000000",
        "resId": "7a56e681-6cf7-46c7-8b96-c23f4e90c2f6",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "7a56e681-6cf7-46c7-8b96-c23f4e90c2f6",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "59",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847160",
        "ifIp": "150.1.92.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/55",
        "ifSpeed": "400000000000",
        "resId": "41c85d66-620f-46cd-8513-0f2b513f6e5d",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "41c85d66-620f-46cd-8513-0f2b513f6e5d",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/99",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "103",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847204",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/99",
        "ifSpeed": "400000000000",
        "resId": "fea379ca-0ee8-46ea-9932-bdbda27150db",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "fea379ca-0ee8-46ea-9932-bdbda27150db",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/28",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "32",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847133",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/28",
        "ifSpeed": "400000000000",
        "resId": "747090cd-4bc6-470c-9169-608e952c532b",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "747090cd-4bc6-470c-9169-608e952c532b",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "214",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16882737",
        "ifIp": "170.1.24.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/75:1",
        "ifSpeed": "200000000000",
        "resId": "96534163-77ad-4736-8d41-2869c63926de",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "96534163-77ad-4736-8d41-2869c63926de",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/93",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "97",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847198",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/93",
        "ifSpeed": "400000000000",
        "resId": "1a5c5a74-e61b-4758-88cb-0b49f5315e78",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "1a5c5a74-e61b-4758-88cb-0b49f5315e78",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "234",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16873265",
        "ifIp": "170.1.52.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/38:1",
        "ifSpeed": "200000000000",
        "resId": "8aa7625a-6e29-4771-aeda-a5a0fed12236",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "8aa7625a-6e29-4771-aeda-a5a0fed12236",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "55",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847156",
        "ifIp": "150.1.84.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/51",
        "ifSpeed": "400000000000",
        "resId": "5e2c3820-18f4-478c-bd24-7eb2c4e1228d",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "5e2c3820-18f4-478c-bd24-7eb2c4e1228d",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "218",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16882225",
        "ifIp": "170.1.16.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/73:1",
        "ifSpeed": "200000000000",
        "resId": "7487bdc8-00a3-47a1-9e92-7c571fc49d4a",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "7487bdc8-00a3-47a1-9e92-7c571fc49d4a",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "210",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16880177",
        "ifIp": "170.1.0.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/65:1",
        "ifSpeed": "200000000000",
        "resId": "35e6a373-688e-47ba-a77c-8ab1dac5531e",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "35e6a373-688e-47ba-a77c-8ab1dac5531e",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "209",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16880434",
        "ifIp": "170.1.6.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/66:2",
        "ifSpeed": "200000000000",
        "resId": "4b2bdaeb-b7ab-47d1-81b4-b286eb037ff1",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "4b2bdaeb-b7ab-47d1-81b4-b286eb037ff1",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/44",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "48",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847149",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/44",
        "ifSpeed": "400000000000",
        "resId": "53a1420f-8bfb-47d9-86eb-0b1a8289f60f",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "53a1420f-8bfb-47d9-86eb-0b1a8289f60f",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "188",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16884274",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/81:2",
        "ifSpeed": "200000000000",
        "resId": "05c975c5-7215-4812-9015-d07c97462aa5",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "05c975c5-7215-4812-9015-d07c97462aa5",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "146",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16865074",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/6:2",
        "ifSpeed": "200000000000",
        "resId": "d213e406-3865-485a-803d-f0fd4d7044b9",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "d213e406-3865-485a-803d-f0fd4d7044b9",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "164",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16864306",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/3:2",
        "ifSpeed": "200000000000",
        "resId": "d2747540-ef35-4861-b6db-cafccd7aaa5e",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "d2747540-ef35-4861-b6db-cafccd7aaa5e",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "219",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16882226",
        "ifIp": "170.1.18.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/73:2",
        "ifSpeed": "200000000000",
        "resId": "dc0547d4-1572-4869-a55c-c81cf33fac2c",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "dc0547d4-1572-4869-a55c-c81cf33fac2c",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/92",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "96",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847197",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/92",
        "ifSpeed": "400000000000",
        "resId": "4092f10d-7f8c-487d-bf85-ec84298ecc45",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "4092f10d-7f8c-487d-bf85-ec84298ecc45",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/94",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "98",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847199",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/94",
        "ifSpeed": "400000000000",
        "resId": "6219073b-9aed-4884-ba76-2039a54206d0",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "6219073b-9aed-4884-ba76-2039a54206d0",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/26",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "30",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847131",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/26",
        "ifSpeed": "400000000000",
        "resId": "98db4b57-0464-488a-a7ae-dea7a79b5c6d",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "98db4b57-0464-488a-a7ae-dea7a79b5c6d",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/109",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "113",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847214",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/109",
        "ifSpeed": "400000000000",
        "resId": "a91db477-fb4a-4890-a941-2c7fc12a61e1",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "a91db477-fb4a-4890-a941-2c7fc12a61e1",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/59",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "63",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847164",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/59",
        "ifSpeed": "400000000000",
        "resId": "930bf0d8-a3a5-4893-b70f-737fac3ebfbd",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "930bf0d8-a3a5-4893-b70f-737fac3ebfbd",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "159",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16866865",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/13:1",
        "ifSpeed": "200000000000",
        "resId": "9e829cb2-4c55-4898-94e5-687f790c85e5",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "9e829cb2-4c55-4898-94e5-687f790c85e5",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "140",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16863794",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/1:2",
        "ifSpeed": "200000000000",
        "resId": "31409be2-325c-48a2-a1df-ac7de96f3405",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "31409be2-325c-48a2-a1df-ac7de96f3405",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "81",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847182",
        "ifIp": "150.1.40.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/77",
        "ifSpeed": "400000000000",
        "resId": "3141b728-5fc9-48e4-a8a4-406597d51df9",
        "ifAlias": "To_Spine-CE9866-6_400GE1/0/101",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "3141b728-5fc9-48e4-a8a4-406597d51df9",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/106",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "110",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847211",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/106",
        "ifSpeed": "400000000000",
        "resId": "df0f778e-b55e-48ea-bea9-ef2ebc883084",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "df0f778e-b55e-48ea-bea9-ef2ebc883084",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "215",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16882738",
        "ifIp": "170.1.26.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/75:2",
        "ifSpeed": "200000000000",
        "resId": "dd69d5b3-7f35-48f4-b234-ff2f1c8d314a",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "dd69d5b3-7f35-48f4-b234-ff2f1c8d314a",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "148",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16867378",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/15:2",
        "ifSpeed": "200000000000",
        "resId": "a316be90-2970-4918-bbae-9ac2596a3277",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "a316be90-2970-4918-bbae-9ac2596a3277",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "237",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16873010",
        "ifIp": "170.1.50.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/37:2",
        "ifSpeed": "200000000000",
        "resId": "2a9fde40-868c-4919-9829-0e7c9b65a3f0",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "2a9fde40-868c-4919-9829-0e7c9b65a3f0",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/23",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "27",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847128",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/23",
        "ifSpeed": "400000000000",
        "resId": "4b44bbc1-97e8-491d-9dd2-cd6eb5ce2ef2",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "4b44bbc1-97e8-491d-9dd2-cd6eb5ce2ef2",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/27",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "31",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847132",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/27",
        "ifSpeed": "400000000000",
        "resId": "b76f9822-3262-4927-9412-b69742da73ee",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "b76f9822-3262-4927-9412-b69742da73ee",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "187",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16884273",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/81:1",
        "ifSpeed": "200000000000",
        "resId": "00973715-1330-4987-84bc-cd96b850bf66",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "00973715-1330-4987-84bc-cd96b850bf66",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "230",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16873777",
        "ifIp": "170.1.60.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/40:1",
        "ifSpeed": "200000000000",
        "resId": "2f5d23cd-d169-4989-b4a7-6dc128779874",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "2f5d23cd-d169-4989-b4a7-6dc128779874",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/128",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "132",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847233",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/128",
        "ifSpeed": "400000000000",
        "resId": "8f16633e-58fc-498d-9460-5e00faaddbb7",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "8f16633e-58fc-498d-9460-5e00faaddbb7",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/126",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "130",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847231",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/126",
        "ifSpeed": "400000000000",
        "resId": "8b4e5e5b-e2ea-49a5-af05-855d7755055b",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "8b4e5e5b-e2ea-49a5-af05-855d7755055b",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/22",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "26",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847127",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/22",
        "ifSpeed": "400000000000",
        "resId": "0f583dfe-247d-49c9-ad22-46f2732d7305",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "0f583dfe-247d-49c9-ad22-46f2732d7305",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/90",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "94",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847195",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/90",
        "ifSpeed": "400000000000",
        "resId": "260f768f-300f-49ca-bf92-057d2a6036fc",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "260f768f-300f-49ca-bf92-057d2a6036fc",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/116",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "120",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847221",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/116",
        "ifSpeed": "400000000000",
        "resId": "dcf8f77e-0b16-4a02-9d69-ef47630a0cdb",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "dcf8f77e-0b16-4a02-9d69-ef47630a0cdb",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "208",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16880433",
        "ifIp": "170.1.4.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/66:1",
        "ifSpeed": "200000000000",
        "resId": "c6d21199-5c56-4a0d-b989-18aa8b5caadf",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "c6d21199-5c56-4a0d-b989-18aa8b5caadf",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "139",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16863793",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/1:1",
        "ifSpeed": "200000000000",
        "resId": "768eef0e-b7bf-4a24-a1ba-d9fd8edf3d0e",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "768eef0e-b7bf-4a24-a1ba-d9fd8edf3d0e",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/30",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "34",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847135",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/30",
        "ifSpeed": "400000000000",
        "resId": "3ecfe3df-e315-4a26-b372-ea36f480b63a",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "3ecfe3df-e315-4a26-b372-ea36f480b63a",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/111",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "115",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847216",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/111",
        "ifSpeed": "400000000000",
        "resId": "3a78886e-2f22-4a27-9efb-b5b4fe2574b3",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "3a78886e-2f22-4a27-9efb-b5b4fe2574b3",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/24",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "28",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847129",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/24",
        "ifSpeed": "400000000000",
        "resId": "4e581bee-db95-4a29-8afc-0e06e8c7d307",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "4e581bee-db95-4a29-8afc-0e06e8c7d307",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/17",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "21",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847122",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/17",
        "ifSpeed": "400000000000",
        "resId": "06f15897-d110-4a51-96fe-beeecb63f2e6",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "06f15897-d110-4a51-96fe-beeecb63f2e6",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/127",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "131",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847232",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/127",
        "ifSpeed": "400000000000",
        "resId": "0c462d1c-ebf1-4a88-98f5-d50e8aa2ea26",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "0c462d1c-ebf1-4a88-98f5-d50e8aa2ea26",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/120",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "124",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847225",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/120",
        "ifSpeed": "400000000000",
        "resId": "d6bdceb5-e1ef-4a9c-a000-487f22e456c6",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "d6bdceb5-e1ef-4a9c-a000-487f22e456c6",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/29",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "33",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847134",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/29",
        "ifSpeed": "400000000000",
        "resId": "4c4025e9-b5c4-4aa9-9197-8fbe0369342d",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "4c4025e9-b5c4-4aa9-9197-8fbe0369342d",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/32",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "36",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847137",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/32",
        "ifSpeed": "400000000000",
        "resId": "d64f6ae2-3d03-4aab-8953-e4ad94f051b4",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "d64f6ae2-3d03-4aab-8953-e4ad94f051b4",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/112",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "116",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847217",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/112",
        "ifSpeed": "400000000000",
        "resId": "6eebdb54-4591-4acb-918e-7b5b569627da",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "6eebdb54-4591-4acb-918e-7b5b569627da",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/18",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "22",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847123",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/18",
        "ifSpeed": "400000000000",
        "resId": "737ac0c1-a11a-4ada-bab7-98f23e756be5",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "737ac0c1-a11a-4ada-bab7-98f23e756be5",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/115",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "119",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847220",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/115",
        "ifSpeed": "400000000000",
        "resId": "d8d6a87e-4d28-4ae0-bf95-becb304ef426",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "d8d6a87e-4d28-4ae0-bf95-becb304ef426",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "58",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847159",
        "ifIp": "150.1.90.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/54",
        "ifSpeed": "400000000000",
        "resId": "26532937-5d56-4aec-ac80-5ccb4da15fba",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "26532937-5d56-4aec-ac80-5ccb4da15fba",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "179",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16879922",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/64:2",
        "ifSpeed": "200000000000",
        "resId": "cc8c5592-f628-4b07-b9ca-04459c235d41",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "cc8c5592-f628-4b07-b9ca-04459c235d41",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "141",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16864049",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/2:1",
        "ifSpeed": "200000000000",
        "resId": "f870c264-82bb-4b12-a2bb-999bfa9cef5e",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "f870c264-82bb-4b12-a2bb-999bfa9cef5e",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "166",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16864562",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/4:2",
        "ifSpeed": "200000000000",
        "resId": "08bdf2ce-34a4-4b20-80a4-e5d3edc09250",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "08bdf2ce-34a4-4b20-80a4-e5d3edc09250",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "84",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847185",
        "ifIp": "150.1.46.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/80",
        "ifSpeed": "400000000000",
        "resId": "07b30c7c-1fdb-4b66-ba4b-e26484ba0e14",
        "ifAlias": "To_Spine-CE9866-6_400GE1/0/104",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "07b30c7c-1fdb-4b66-ba4b-e26484ba0e14",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/124",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "128",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847229",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/124",
        "ifSpeed": "400000000000",
        "resId": "caca74c3-8b22-4b89-93df-cb6ebd22f268",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "caca74c3-8b22-4b89-93df-cb6ebd22f268",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/62",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "66",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847167",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/62",
        "ifSpeed": "400000000000",
        "resId": "4314ff4f-65a7-4ba1-8d4f-1f5fe9e36393",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "4314ff4f-65a7-4ba1-8d4f-1f5fe9e36393",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/104",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "108",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847209",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/104",
        "ifSpeed": "400000000000",
        "resId": "d2401543-0763-4bc3-8c0e-a29ec16002c9",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "d2401543-0763-4bc3-8c0e-a29ec16002c9",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/25:2",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "171",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16869938",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/25:2",
        "ifSpeed": "200000000000",
        "resId": "b3939cac-7b2d-4bdb-b6ba-9acbea679c11",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "b3939cac-7b2d-4bdb-b6ba-9acbea679c11",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "156",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16866354",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/11:2",
        "ifSpeed": "200000000000",
        "resId": "3b09f8ac-70e3-4be1-8121-36e7f34fc08b",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "3b09f8ac-70e3-4be1-8121-36e7f34fc08b",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "177",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16879666",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/63:2",
        "ifSpeed": "200000000000",
        "resId": "9a61005f-3d76-4bf2-abe6-04357217fba1",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "9a61005f-3d76-4bf2-abe6-04357217fba1",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "152",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16865842",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/9:2",
        "ifSpeed": "200000000000",
        "resId": "220401d6-b060-4c16-af2c-dba020347e52",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "220401d6-b060-4c16-af2c-dba020347e52",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "216",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16882481",
        "ifIp": "170.1.20.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/74:1",
        "ifSpeed": "200000000000",
        "resId": "61a7dae0-457b-4c26-bcb8-d8748481bafe",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "61a7dae0-457b-4c26-bcb8-d8748481bafe",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/95",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "99",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847200",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/95",
        "ifSpeed": "400000000000",
        "resId": "8ec742be-6826-4c3f-9209-20c75ae8153e",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "8ec742be-6826-4c3f-9209-20c75ae8153e",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "153",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16866097",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/10:1",
        "ifSpeed": "200000000000",
        "resId": "025b88c8-f114-4c50-bdde-e36ad03e2d3c",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "025b88c8-f114-4c50-bdde-e36ad03e2d3c",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "145",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16865073",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/6:1",
        "ifSpeed": "200000000000",
        "resId": "5c91d103-374b-4c55-86fc-9dce49b403f5",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "5c91d103-374b-4c55-86fc-9dce49b403f5",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/48",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "52",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847153",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/48",
        "ifSpeed": "400000000000",
        "resId": "2e2f0e5f-7198-4c6f-9e9d-d62916470470",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "2e2f0e5f-7198-4c6f-9e9d-d62916470470",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688130",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "10GE1/0/1",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "133",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16850178",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "10000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "10GE1/0/1",
        "ifSpeed": "10000000000",
        "resId": "fd2d643b-8a36-4c8c-a2f1-e4a0d45eb8b6",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "fd2d643b-8a36-4c8c-a2f1-e4a0d45eb8b6",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/123",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "127",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847228",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/123",
        "ifSpeed": "400000000000",
        "resId": "9c087d7e-4397-4cb2-a0ed-146056bf419f",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "9c087d7e-4397-4cb2-a0ed-146056bf419f",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "222",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16872753",
        "ifIp": "170.1.44.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/36:1",
        "ifSpeed": "200000000000",
        "resId": "a620ed42-e66f-4ce1-abe4-c3308dadc864",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "a620ed42-e66f-4ce1-abe4-c3308dadc864",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/108",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "112",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847213",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/108",
        "ifSpeed": "400000000000",
        "resId": "f6358b04-98f2-4cec-8329-8543d9160d73",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "f6358b04-98f2-4cec-8329-8543d9160d73",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "74",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847175",
        "ifIp": "150.1.10.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/70",
        "ifSpeed": "400000000000",
        "resId": "bbc1e4a0-39fd-4d6d-a62d-e7f6881e8dc4",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/54",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "bbc1e4a0-39fd-4d6d-a62d-e7f6881e8dc4",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "189",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16884529",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/82:1",
        "ifSpeed": "200000000000",
        "resId": "a96b4e85-374f-4d76-bf29-a8546a4910b4",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "a96b4e85-374f-4d76-bf29-a8546a4910b4",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "185",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16878386",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/58:2",
        "ifSpeed": "200000000000",
        "resId": "6ca79cd5-39b8-4d94-993c-a3fe96d27f56",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "6ca79cd5-39b8-4d94-993c-a3fe96d27f56",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "193",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16885041",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/84:1",
        "ifSpeed": "200000000000",
        "resId": "3538079e-8c94-4da7-ad22-91a1e3f3da4e",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "3538079e-8c94-4da7-ad22-91a1e3f3da4e",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/25:1",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "170",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16869937",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/25:1",
        "ifSpeed": "200000000000",
        "resId": "38fedd28-dd8b-4dbe-a649-a4936562e154",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "38fedd28-dd8b-4dbe-a649-a4936562e154",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "174",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16878130",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/57:2",
        "ifSpeed": "200000000000",
        "resId": "96a19be2-6eec-4dc5-a7ce-484ad36ec804",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "96a19be2-6eec-4dc5-a7ce-484ad36ec804",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "178",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16879921",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/64:1",
        "ifSpeed": "200000000000",
        "resId": "c855c944-ca6a-4dcc-b805-b73bef2ece3a",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "c855c944-ca6a-4dcc-b805-b73bef2ece3a",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "227",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16872242",
        "ifIp": "170.1.38.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/34:2",
        "ifSpeed": "200000000000",
        "resId": "199ab753-01a5-4dd5-8a46-1f5a3b1891a9",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "199ab753-01a5-4dd5-8a46-1f5a3b1891a9",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "211",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16880178",
        "ifIp": "170.1.2.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/65:2",
        "ifSpeed": "200000000000",
        "resId": "b0b47f8b-1ee4-4dd8-934b-897d3dc545bc",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "b0b47f8b-1ee4-4dd8-934b-897d3dc545bc",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "60",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847161",
        "ifIp": "150.1.94.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/56",
        "ifSpeed": "400000000000",
        "resId": "0f052437-ff18-4ddd-9916-0ce318647ed3",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "0f052437-ff18-4ddd-9916-0ce318647ed3",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/96",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "100",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847201",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/96",
        "ifSpeed": "400000000000",
        "resId": "61b2ff87-c33e-4df1-a620-6fc498b9dcee",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "61b2ff87-c33e-4df1-a620-6fc498b9dcee",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "176",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16879665",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/63:1",
        "ifSpeed": "200000000000",
        "resId": "cfd253e8-db6e-4e07-ad8e-cbb5d7326017",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "cfd253e8-db6e-4e07-ad8e-cbb5d7326017",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/105",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "109",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847210",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/105",
        "ifSpeed": "400000000000",
        "resId": "79fef31c-a46d-4e43-b449-886248ba9ff7",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "79fef31c-a46d-4e43-b449-886248ba9ff7",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/113",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "117",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847218",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/113",
        "ifSpeed": "400000000000",
        "resId": "cd4d0241-c584-4e71-89d3-c8f2ab475057",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "cd4d0241-c584-4e71-89d3-c8f2ab475057",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/45",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "49",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847150",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/45",
        "ifSpeed": "400000000000",
        "resId": "7cdd6c67-8e53-4e91-951a-ac8ff3a838dc",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "7cdd6c67-8e53-4e91-951a-ac8ff3a838dc",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X16-70(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "191",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16884785",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/83:1",
        "ifSpeed": "200000000000",
        "resId": "41c8b9cf-fe17-4e97-9557-8b422e1c654c",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "41c8b9cf-fe17-4e97-9557-8b422e1c654c",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "53",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847154",
        "ifIp": "150.1.80.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/49",
        "ifSpeed": "400000000000",
        "resId": "68adc8e2-a2ac-4ea7-bdb7-e0513cb7d6a3",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "68adc8e2-a2ac-4ea7-bdb7-e0513cb7d6a3",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/119",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "123",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847224",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/119",
        "ifSpeed": "400000000000",
        "resId": "94f48d26-d4df-4ea9-96c6-1cde12959012",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "94f48d26-d4df-4ea9-96c6-1cde12959012",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/85",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "89",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847190",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/85",
        "ifSpeed": "400000000000",
        "resId": "a652bc33-a390-4eb4-859f-de680331a6cb",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "a652bc33-a390-4eb4-859f-de680331a6cb",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "155",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16866353",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/11:1",
        "ifSpeed": "200000000000",
        "resId": "fe63d18d-cd73-4ed6-9aac-6651246aa30f",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "fe63d18d-cd73-4ed6-9aac-6651246aa30f",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688133",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "22",
        "remark": "MEth0/0/0",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "4",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16843009",
        "ifIp": "80.17.8.20",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "1000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "MEth0/0/0",
        "ifSpeed": "1000000000",
        "resId": "089eb793-262f-4edd-b081-0c8f6522cc1c",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "089eb793-262f-4edd-b081-0c8f6522cc1c",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/87",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "91",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847192",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/87",
        "ifSpeed": "400000000000",
        "resId": "7fa4e6c3-26a8-4edf-8f10-00459930aa2b",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "7fa4e6c3-26a8-4edf-8f10-00459930aa2b",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/47",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "51",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847152",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/47",
        "ifSpeed": "400000000000",
        "resId": "d5515ff6-c630-4ee2-8e70-4123fd5574cc",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "d5515ff6-c630-4ee2-8e70-4123fd5574cc",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "225",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16872498",
        "ifIp": "170.1.42.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/35:2",
        "ifSpeed": "200000000000",
        "resId": "afab43ee-7030-4ee7-b83e-e283a2b6eecd",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "afab43ee-7030-4ee7-b83e-e283a2b6eecd",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "57",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847158",
        "ifIp": "150.1.88.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/53",
        "ifSpeed": "400000000000",
        "resId": "53909460-37ad-4ef9-8680-5dace1bc7608",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "53909460-37ad-4ef9-8680-5dace1bc7608",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "76",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847177",
        "ifIp": "150.1.14.1",
        "ltpTypeName": "",
        "adminStatus": "inactive",
        "adminState": "inactive",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/72",
        "ifSpeed": "400000000000",
        "resId": "1fec0008-2412-4f02-8ffb-87dd741ab823",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/56",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "1fec0008-2412-4f02-8ffb-87dd741ab823",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "157",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16866609",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/12:1",
        "ifSpeed": "200000000000",
        "resId": "955b8dd4-dc90-4f0c-a432-fd38dea6a39e",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "955b8dd4-dc90-4f0c-a432-fd38dea6a39e",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/21",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "25",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847126",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/21",
        "ifSpeed": "400000000000",
        "resId": "74175a50-e7b1-4f13-a420-4c5034295593",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "74175a50-e7b1-4f13-a420-4c5034295593",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "213",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16880946",
        "ifIp": "170.1.14.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/68:2",
        "ifSpeed": "200000000000",
        "resId": "a36fe4bd-7353-4f1a-a3e1-97dc7fef93e6",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "a36fe4bd-7353-4f1a-a3e1-97dc7fef93e6",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "231",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16873778",
        "ifIp": "170.1.62.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/40:2",
        "ifSpeed": "200000000000",
        "resId": "87faf857-6620-4f1b-9e28-99d7fe8ae603",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "87faf857-6620-4f1b-9e28-99d7fe8ae603",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/122",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "126",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847227",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/122",
        "ifSpeed": "400000000000",
        "resId": "0c84862d-88fb-4f21-b6b8-6bfa67415b83",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "0c84862d-88fb-4f21-b6b8-6bfa67415b83",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/60",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "64",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847165",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/60",
        "ifSpeed": "400000000000",
        "resId": "12c6c460-a194-4f64-afc7-d4e0dd189733",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "12c6c460-a194-4f64-afc7-d4e0dd189733",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/103",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "107",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847208",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/103",
        "ifSpeed": "400000000000",
        "resId": "620371dc-707a-4f6c-bb0c-2e17d293125f",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "620371dc-707a-4f6c-bb0c-2e17d293125f",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/61",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "65",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847166",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/61",
        "ifSpeed": "400000000000",
        "resId": "86c3beb0-ce4c-4f7d-85b4-f2c0cc3756a3",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "86c3beb0-ce4c-4f7d-85b4-f2c0cc3756a3",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "235",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16873266",
        "ifIp": "170.1.54.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/38:2",
        "ifSpeed": "200000000000",
        "resId": "79b6dca1-080e-4f80-afef-b63dbae477d9",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "79b6dca1-080e-4f80-afef-b63dbae477d9",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/107",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "111",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847212",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/107",
        "ifSpeed": "400000000000",
        "resId": "ab7138f2-104c-4fb1-9977-ef421dda1911",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "ab7138f2-104c-4fb1-9977-ef421dda1911",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "138",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16865586",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/8:2",
        "ifSpeed": "200000000000",
        "resId": "857f643c-16c3-4fbe-a8d6-7b4e780edb99",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "857f643c-16c3-4fbe-a8d6-7b4e780edb99",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688146",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE1/0/43",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "47",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16847148",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "400000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/43",
        "ifSpeed": "400000000000",
        "resId": "0fd167dd-8b01-4fd1-a74c-8ebf95c1fad7",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "inactive",
        "tenantId": "default-organization-id",
        "operStatus": "inactive",
        "nativeId": "0fd167dd-8b01-4fd1-a74c-8ebf95c1fad7",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-850nm-MPO 1X12-60(50um/125um OM3),100(50um/125um OM4),100(50um/125um OM5)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "163",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16864305",
        "ifIp": "--",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/3:1",
        "ifSpeed": "200000000000",
        "resId": "2a628b45-627f-4fd4-93fb-d37c48bb9316",
        "ifAlias": "(zero-length)",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "2a628b45-627f-4fd4-93fb-d37c48bb9316",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      },
      {
        "subnetId": "f254ac29-57ca-4354-937d-903930e430a2",
        "vendorType": "1.3.6.1.4.1.2011.20021210.14.688147",
        "subnetNativeId": "f254ac29-57ca-4354-937d-903930e430a2",
        "ifType": "9",
        "remark": "400GE-1310nm-LC-2000(9um/125um SMF)",
        "neName": "O05-17F-Leaf2-Simu6-0001",
        "portId": "229",
        "neIp": "197.197.5.1",
        "slotIndex": "16842753",
        "slotName": "CE9866-128DQ 1",
        "portIndex": "16871986",
        "ifIp": "170.1.34.1",
        "ltpTypeName": "",
        "adminStatus": "active",
        "adminState": "active",
        "slotId": "1",
        "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "ifHighSpeed": "200000",
        "ifMtu": 1500,
        "neRole": "ServerLeaf",
        "ifName": "400GE1/0/33:2",
        "ifSpeed": "200000000000",
        "resId": "5bd8b54d-57e5-4fef-8240-7647e75f19ed",
        "ifAlias": "To_Spine-CE9866-5_400GE1/0/49",
        "isPhysicalPort": true,
        "operState": "active",
        "tenantId": "default-organization-id",
        "operStatus": "active",
        "nativeId": "5bd8b54d-57e5-4fef-8240-7647e75f19ed",
        "neNativeId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
        "fabricName": "default1"
      }
    ],
    "marker": null
  }
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-chart-duplicate-series-value
severity=ERROR
source=type
statementId=comboBarChart
component=BarChart
path=/series/0
line=14
column=1
retryable=false
message=BarChart series 0 repeats the same derived expression for multiple categories.
hint=Use a distinct data expression for each category or remove the duplicate category.
````

## 7. rw-129-Spine1-Fabric-inactive

- 来源: `2.json` 第 2 条
- 状态: `INVALID`
- 原始 Issue 数: 1
- 可操作 Issue 数: 1

- `error_detail.txt` 标注: 概览展示不美观。
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([summaryCard, tableCard])

summaryCard = Card([summaryHeader, summaryGrid], "card", "column", "m", "start", "start", false)
summaryHeader = CardHeader("Spine1 光模块健康状况概览", "设备光模块在线/离线统计与性能指标说明")
summaryGrid = Stack([leftCol, rightCol], "row", "m", "start", "start", true)
leftCol = Stack([deviceField, fabricField, vendorField, totalField, activeField, inactiveField], "column", "none")
rightCol = Card([metricsNoteField], "sunk")

deviceField = Descriptions([DescField("设备名", data.summary.device)], "", 1, true)
fabricField = Descriptions([DescField("Fabric", data.summary.fabric)], "", 1, true)
vendorField = Descriptions([DescField("厂商", data.summary.vendor)], "", 1, true)
totalField = Descriptions([DescField("总光模块数", "" + data.summary.totalOpticalModules)], "", 1, true)
activeField = Descriptions([DescField("在线数量", "" + data.summary.activeCount)], "", 1, true)
inactiveField = Descriptions([DescField("离线数量", "" + data.summary.inactiveCount)], "", 1, true)
metricsNoteField = TextContent(data.summary.metricsNote, "small")

tableCard = Card([tableHeader, moduleTable], "card", "column", "m", "start", "start", false)
tableHeader = CardHeader("光模块详细信息", "接口名称、序列号、传输类型、管理状态与运行状态")
moduleTable = Table([ifNameCol, snCol, transTypeCol, adminStatusCol, operStatusCol], data.opticalModules, expandTpl)

ifNameCol = Col("接口名称", "ifName")
snCol = Col("序列号", "opticalSn")
transTypeCol = Col("传输类型", "transType")
adminStatusCol = Col("管理状态", "adminStatus", {cell: @Render("v", @Switch(v, {"active": Tag("Active", "success"), "inactive": Tag("Inactive", "warning")}, Tag(v, "neutral")))})
operStatusCol = Col("运行状态", "operStatus", {cell: @Render("v", @Switch(v, {"active": Tag("Active", "success"), "inactive": Tag("Inactive", "danger")}, Tag(v, "neutral")))})

expandTpl = @Render("row", inactiveRow ? Card([TextContent("该光模块当前处于离线状态，请检查物理连接与配置。", "small")], "card", "column", "s", "start", "start", false) : null)
inactiveRow = data.opticalModules.operStatus == "inactive"
````

### 输入 dataModel

````json
{
  "summary": {
    "activeCount": 6,
    "deviceIp": "10.136.193.102",
    "metricsAvailable": false,
    "fabric": "pod11",
    "vendor": "HUAWEI",
    "inactiveCount": 1,
    "totalOpticalModules": 7,
    "metricsNote": "近12小时内光模块性能指标（温度、电压、发送/接收功率）无上报数据，无法评估实时健康状态",
    "device": "Spine1"
  },
  "opticalModules": [
    {
      "opticalSn": "U7M86L30086",
      "huaweiCertified": 0,
      "transType": "10GBASE_SR",
      "ifName": "25GE1/0/1",
      "adminStatus": "active",
      "operStatus": "active"
    },
    {
      "opticalSn": "U7M86L20769",
      "huaweiCertified": 0,
      "transType": "10GBASE_SR",
      "ifName": "25GE1/0/2",
      "adminStatus": "active",
      "operStatus": "active"
    },
    {
      "opticalSn": "U7M86L31896",
      "huaweiCertified": 0,
      "transType": "10GBASE_SR",
      "ifName": "25GE1/0/5",
      "adminStatus": "active",
      "operStatus": "active"
    },
    {
      "opticalSn": "U7M86L39146",
      "huaweiCertified": 0,
      "transType": "10GBASE_SR",
      "ifName": "25GE1/0/6",
      "adminStatus": "active",
      "operStatus": "active"
    },
    {
      "opticalSn": "U7M86L22392",
      "huaweiCertified": 0,
      "transType": "10GBASE_SR",
      "ifName": "25GE1/0/7",
      "adminStatus": "active",
      "operStatus": "active"
    },
    {
      "opticalSn": "U7M86L17509",
      "huaweiCertified": 0,
      "transType": "10GBASE_SR",
      "ifName": "25GE1/0/8",
      "adminStatus": "active",
      "operStatus": "active"
    },
    {
      "opticalSn": "EM193300040035",
      "huaweiCertified": 0,
      "transType": "10GBASE_LR",
      "ifName": "25GE1/0/17",
      "adminStatus": "active",
      "operStatus": "inactive"
    }
  ]
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-operator-mismatch
severity=ERROR
source=type
statementId=inactiveRow
component=<null>
path=<null>
line=28
column=1
retryable=false
message=Operator == cannot compare string[] with string.
hint=Compare compatible scalar values; use @Each/@Filter for arrays.
````

## 8. rw-139-Spine1-IP-Fabric

- 来源: `2.json` 第 3 条
- 状态: `INVALID`
- 原始 Issue 数: 1
- 可操作 Issue 数: 1

- `error_detail.txt` 标注: 表格渲染失败。
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([infoCard, slotCard, portCard, loopbackCard, bdCard, mlCard, nveCard], "column", "m")
infoCard = Card([infoHeader, infoDesc], "card")
infoHeader = CardHeader("设备基本信息", data.device_info.neName)
infoDesc = Descriptions([DescField("设备名", data.device_info.neName), DescField("IP地址", data.device_info.neIp), DescField("角色", data.device_info.neRole), DescField("Fabric", data.device_info.fabricName), DescField("设备类型", data.device_info.neType), DescField("软件版本", data.device_info.softVersion)])
slotCard = Card([slotHeader, slotTable], "card")
slotHeader = CardHeader("单板信息", "共 " + @Count(data.slots) + " 块单板")
slotTable = Table([slotNameCol, adminStatusCol, operStatusCol, hardVerCol, softVerCol], data.slots)
slotNameCol = Col("单板名称", "slotName")
adminStatusCol = Col("管理状态", "adminStatus", {cell: @Render("v", @Switch(v, {"active": Tag("Active", "success"), "inactive": Tag("Inactive", "warning")}, Tag(v)))})
operStatusCol = Col("运行状态", "operStatus", {cell: @Render("v", @Switch(v, {"active": Tag("Active", "success"), "inactive": Tag("Inactive", "warning")}, Tag(v)))})
hardVerCol = Col("硬件版本", "hardVersion")
softVerCol = Col("软件版本", "softVersion")
portCard = Card([portHeader, portStats, portTable], "card")
portHeader = CardHeader("端口/接口列表", "共 " + data.ports_summary.total + " 个端口")
portStats = Stack([activePortTag, inactivePortTag], "row", "s")
activePortTag = Tag("Active: " + data.ports_summary.active_count, "success")
inactivePortTag = Tag("Inactive: " + data.ports_summary.inactive_count, "warning")
portTable = Table([portNameCol, portIpCol, portAdminCol, portOperCol, portSpeedCol], data.ports)
portNameCol = Col("接口名称", "ifName")
portIpCol = Col("IP地址", "ifIp", {cell: @Render("v", v != "" ? Tag(v, "info") : Tag("N/A", "neutral"))})
portAdminCol = Col("管理状态", "adminStatus", {cell: @Render("v", @Switch(v, {"active": Tag("Active", "success"), "inactive": Tag("Inactive", "warning")}, Tag(v)))})
portOperCol = Col("运行状态", "operStatus", {cell: @Render("v", @Switch(v, {"active": Tag("Active", "success"), "inactive": Tag("Inactive", "warning")}, Tag(v)))})
portSpeedCol = Col("速率", "ifHighSpeed", {cell: @Render("v", @Switch(v, {"1000": Tag("1G", "neutral"), "25000": Tag("25G", "info"), "10000": Tag("10G", "info"), "100000": Tag("100G", "info")}, Tag(v + "bps")))})
loopbackCard = Card([loopbackHeader, loopbackTable], "card")
loopbackHeader = CardHeader("Loopback接口列表", "共 " + @Count(data.loopback_interfaces) + " 个接口")
loopbackTable = Table([loopbackNameCol, loopbackIpCol], data.loopback_interfaces)
loopbackNameCol = Col("接口名称", "interface-name")
loopbackIpCol = Col("IP/掩码", "ip-mask")
bdCard = Card([bdHeader, bdTable], "card")
bdHeader = CardHeader("桥域(Bridge-Domain)列表", "共 " + @Count(data.bridge_domains) + " 个BD")
bdTable = Table([bdIdCol, bdVlanCol], data.bridge_domains)
bdIdCol = Col("BD ID", "bridge-domain-id")
bdVlanCol = Col("绑定的VLAN", "vlan-id", {cell: @Render("v", v != null ? Tag(v, "info") : Tag("未绑定", "neutral"))})
mlCard = Card([mlHeader, mlTable], "card")
mlHeader = CardHeader("Monitor-Link组列表", "共 " + @Count(data.monitor_link_groups) + " 个组")
mlTable = Table([mlIdCol, mlUpCol, mlDownCol], data.monitor_link_groups)
mlIdCol = Col("组ID", "group-id")
mlUpCol = Col("上行接口", "up-link-interface-name", {cell: @Render("v", @Count(v) > 0 ? TagBlock(v) : Tag("无", "neutral"))})
mlDownCol = Col("下行接口", "down-link-interface-name", {cell: @Render("v", @Count(v) > 0 ? TagBlock(v) : Tag("无", "neutral"))})
nveCard = Card([nveHeader, nveContent], "card")
nveHeader = CardHeader("NVE列表", "")
nveContent = @Count(data.nve) > 0 ? Table([nveIdCol], data.nve) : TextContent("未查询到NVE数据")
nveIdCol = Col("NVE", "nve-id")
````

### 输入 dataModel

````json
{
  "slots": [
    {
      "slotName": "CE6863-48S6CQ 1",
      "softVersion": "Version 8.220 V200R022C00",
      "hardVersion": "CEM48S6CQP04 VER A",
      "adminStatus": "active",
      "operStatus": "active",
      "slotId": "1",
      "sn": "101970068648"
    }
  ],
  "device_info": {
    "softVersion": "Version 8.220 V200R022C00",
    "neRole": "Spine",
    "neName": "Spine1",
    "neIp": "10.136.193.102",
    "neType": "CloudEngine 6800,CE6863-48S6CQ-F",
    "fabricName": "pod11"
  },
  "monitor_link_groups": [
    {
      "device-ip": "10.136.193.102",
      "up-link-interface-name": [],
      "group-id": 1,
      "down-link-interface-name": []
    }
  ],
  "ports_summary": {
    "total": 55,
    "inactive_count": 47,
    "active_count": 8
  },
  "loopback_interfaces": [
    {
      "interface-name": "LoopBack1",
      "ip-mask": "100.100.100.102/255.255.255.255"
    },
    {
      "interface-name": "LoopBack0",
      "ip-mask": "10.10.10.102/255.255.255.255"
    }
  ],
  "ports": [
    {
      "ifHighSpeed": "10000",
      "ifIp": "192.168.100.73",
      "ifName": "25GE1/0/8",
      "adminStatus": "active",
      "operStatus": "active"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/33",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "100000",
      "ifIp": "",
      "ifName": "100GE1/0/4",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "1000",
      "ifIp": "192.168.100.161",
      "ifName": "25GE1/0/48",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "25000",
      "ifIp": "",
      "ifName": "25GE1/0/27",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "25000",
      "ifIp": "",
      "ifName": "25GE1/0/28",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "25000",
      "ifIp": "",
      "ifName": "25GE1/0/41",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/10",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "25000",
      "ifIp": "",
      "ifName": "25GE1/0/43",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "100000",
      "ifIp": "192.168.100.93",
      "ifName": "100GE1/0/2",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/1",
      "adminStatus": "active",
      "operStatus": "active"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/11",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/22",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "192.168.100.69",
      "ifName": "25GE1/0/7",
      "adminStatus": "active",
      "operStatus": "active"
    },
    {
      "ifHighSpeed": "1000",
      "ifIp": "",
      "ifName": "25GE1/0/38",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "25000",
      "ifIp": "",
      "ifName": "25GE1/0/25",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/24",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/32",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "100000",
      "ifIp": "192.168.100.89",
      "ifName": "100GE1/0/1",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/18",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "100000",
      "ifIp": "",
      "ifName": "100GE1/0/5",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "25000",
      "ifIp": "",
      "ifName": "25GE1/0/14",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "1000",
      "ifIp": "",
      "ifName": "25GE1/0/46",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "1000",
      "ifIp": "",
      "ifName": "25GE1/0/37",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "25000",
      "ifIp": "",
      "ifName": "25GE1/0/13",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "192.168.100.65",
      "ifName": "25GE1/0/6",
      "adminStatus": "active",
      "operStatus": "active"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "192.168.100.25",
      "ifName": "25GE1/0/5",
      "adminStatus": "active",
      "operStatus": "active"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/35",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "1000",
      "ifIp": "192.168.100.145",
      "ifName": "25GE1/0/47",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "25000",
      "ifIp": "",
      "ifName": "25GE1/0/42",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "25000",
      "ifIp": "",
      "ifName": "25GE1/0/16",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "100000",
      "ifIp": "",
      "ifName": "100GE1/0/3",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "1000",
      "ifIp": "",
      "ifName": "25GE1/0/40",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "192.168.100.158",
      "ifName": "25GE1/0/20",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/31",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "25000",
      "ifIp": "",
      "ifName": "25GE1/0/44",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "100000",
      "ifIp": "",
      "ifName": "100GE1/0/6",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/34",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "192.168.100.166",
      "ifName": "25GE1/0/19",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/30",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/2",
      "adminStatus": "active",
      "operStatus": "active"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "192.168.100.189",
      "ifName": "25GE1/0/23",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/12",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "25000",
      "ifIp": "",
      "ifName": "25GE1/0/15",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/21",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "192.168.100.57",
      "ifName": "25GE1/0/4",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "192.168.100.77",
      "ifName": "25GE1/0/9",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/36",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "",
      "ifName": "25GE1/0/29",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "1000",
      "ifIp": "",
      "ifName": "25GE1/0/39",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "1000",
      "ifIp": "10.136.193.102",
      "ifName": "MEth0/0/0",
      "adminStatus": "active",
      "operStatus": "active"
    },
    {
      "ifHighSpeed": "1000",
      "ifIp": "",
      "ifName": "25GE1/0/45",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "192.168.100.53",
      "ifName": "25GE1/0/3",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "25000",
      "ifIp": "",
      "ifName": "25GE1/0/26",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifHighSpeed": "10000",
      "ifIp": "192.168.100.182",
      "ifName": "25GE1/0/17",
      "adminStatus": "active",
      "operStatus": "inactive"
    }
  ],
  "bridge_domains": [
    {
      "device-ip": "10.136.193.102",
      "vlan-id": null,
      "bridge-domain-id": 1
    },
    {
      "device-ip": "10.136.193.102",
      "vlan-id": null,
      "bridge-domain-id": 2
    },
    {
      "device-ip": "10.136.193.102",
      "vlan-id": null,
      "bridge-domain-id": 3
    },
    {
      "device-ip": "10.136.193.102",
      "vlan-id": null,
      "bridge-domain-id": 4
    },
    {
      "device-ip": "10.136.193.102",
      "vlan-id": null,
      "bridge-domain-id": 5
    },
    {
      "device-ip": "10.136.193.102",
      "vlan-id": null,
      "bridge-domain-id": 6
    },
    {
      "device-ip": "10.136.193.102",
      "vlan-id": null,
      "bridge-domain-id": 7
    },
    {
      "device-ip": "10.136.193.102",
      "vlan-id": null,
      "bridge-domain-id": 8
    },
    {
      "device-ip": "10.136.193.102",
      "vlan-id": null,
      "bridge-domain-id": 9
    },
    {
      "device-ip": "10.136.193.102",
      "vlan-id": null,
      "bridge-domain-id": 10
    },
    {
      "device-ip": "10.136.193.102",
      "vlan-id": null,
      "bridge-domain-id": 11
    }
  ],
  "nve": []
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=bdTable
component=Tag
path=/text
line=31
column=1
retryable=false
message=Tag.text expects string | number, but received null.
hint=Use a value compatible with string | number.
````

## 9. rw-140-Spine-ServerLeaf-BorderLeaf

- 来源: `2.json` 第 4 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 柱状图横坐标不显示。
- 标注覆盖结果: `MISSED`

### 输入 DSL

````openui
root = Stack([chartCard])

chartCard = Card([chartHeader, chart])
chartHeader = CardHeader("数据中心网络设备角色分布", "按角色统计设备数量")
labels = @Each(data.distributionData, "item", item.type)
values = @Each(data.distributionData, "item", item.count)
chart = BarChart(labels, [Series("设备数量", values)], "grouped", "设备角色", "数量")
````

### 输入 dataModel

````json
{
  "deployScene": "FabricDcn",
  "distributionData": [
    {
      "count": 23,
      "type": "ServerLeaf"
    },
    {
      "count": 4,
      "type": "Spine"
    },
    {
      "count": 2,
      "type": "AggregationRouter"
    },
    {
      "count": 2,
      "type": "BorderLeaf"
    },
    {
      "count": 1,
      "type": "FabricGateway"
    },
    {
      "count": 1,
      "type": "AccessRouter"
    },
    {
      "count": 1,
      "type": "Firewall"
    }
  ],
  "distributionTotal": 7
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 10. rw-149-Fabric-pod11-IP

- 来源: `2.json` 第 5 条
- 状态: `INVALID`
- 原始 Issue 数: 6
- 可操作 Issue 数: 6

- `error_detail.txt` 标注: 表格不完整。
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([header, summaryCard, typeTableCard, allDevicesCard], "column", "m")

header = CardHeader("Fabric " + data.fabricName + " 设备版本统计", "共 " + data.deviceTotal + " 台设备")

summaryCard = Card([summaryText], "clear")
summaryText = TextContent("当前 Fabric 包含 " + data.deviceTotal + " 台华为设备，全部在线。", "default")

typeTableCard = Card([typeTableHeader, typeTable], "card")
typeTableHeader = CardHeader("按设备型号分组统计", "型号、数量、版本及明细")
typeTable = Table([typeCol, countCol, versionCol, nameListCol], data.groupByType)
typeCol = Col("设备型号", "neType")
countCol = Col("设备数量", "count")
versionCol = Col("软件版本", "neVersion", {cell: @Render("v", "row", Stack(@Each(row.devices, "d", versionTagTpl), "row", "s", "start", false))})
versionTagTpl = Tag(d.neVersion, "info", null, "sm")
nameListCol = Col("设备名称列表", "neName", {cell: @Render("v", "row", Stack(@Each(row.devices, "d", nameTagTpl), "row", "s", "start", true))})
nameTagTpl = Tag(d.neName, "neutral", null, "sm")

allDevicesCard = Card([allDevicesHeader, allDevicesTable], "card")
allDevicesHeader = CardHeader("全部设备明细", "详细配置与状态")
allDevicesTable = Table([nameCol, ipCol, modelCol, roleCol, verCol, patchCol, statusCol], data.allDevices)
nameCol = Col("设备名称", "neName")
ipCol = Col("IP地址", "neIp")
modelCol = Col("设备型号", "neType")
roleCol = Col("设备角色", "neRole", {cell: @Render("v", @Switch(v, {"Spine": Tag("Spine", "info"), "Leaf": Tag("Leaf", "success"), "ServerLeaf": Tag("ServerLeaf", "success"), "BorderLeaf": Tag("BorderLeaf", "warning"), "AccessRouter": Tag("AccessRouter", "neutral"), "FabricGateway": Tag("FabricGateway", "danger")}, Tag(v, "neutral")))})
verCol = Col("软件版本", "neVersion")
patchCol = Col("版本补丁", "patchFileVersion", {cell: @Render("v", v ?? "无")})
statusCol = Col("状态", "neState", {cell: @Render("v", Tag("在线", "success"))})
````

### 输入 dataModel

````json
{
  "deviceTotal": 9,
  "groupByType": [
    {
      "devices": [
        {
          "neRole": "Spine",
          "neName": "Spine1",
          "neIp": "10.136.193.102",
          "neVersion": "8.22 V200R022C00"
        }
      ],
      "count": 1,
      "neType": "CE6863-48S6CQ"
    },
    {
      "devices": [
        {
          "neRole": "AccessRouter",
          "neName": "PE-10.136.193.112",
          "neIp": "10.136.193.112",
          "neVersion": "8.19 V200R019C10"
        },
        {
          "neRole": "FabricGateway",
          "neName": "pod11-DCIleaf2-1",
          "neIp": "10.136.193.111",
          "neVersion": "8.23 V200R023C00SPC100"
        }
      ],
      "count": 2,
      "neType": "CE6870-48S6CQ-EI"
    },
    {
      "devices": [
        {
          "neRole": "ServerLeaf",
          "neName": "serverleaf3",
          "neIp": "10.136.193.104",
          "neVersion": "8.24 V200R024C00SPC500"
        },
        {
          "neRole": "BorderLeaf",
          "neName": "borderleaf2",
          "neIp": "10.136.193.110",
          "neVersion": "8.24 V200R024C00SPC500"
        },
        {
          "neRole": "BorderLeaf",
          "neName": "borderleaf1",
          "neIp": "10.136.193.109",
          "neVersion": "8.24 V200R024C00SPC500"
        }
      ],
      "count": 3,
      "neType": "CE6881-48S6CQ"
    },
    {
      "devices": [
        {
          "neRole": "ServerLeaf",
          "neName": "serverleaf1",
          "neIp": "10.136.193.107",
          "neVersion": "8.25 V200R025C00SPC500"
        },
        {
          "neRole": "ServerLeaf",
          "neName": "serverleaf2",
          "neIp": "10.136.193.108",
          "neVersion": "8.25 V200R025C00SPC500"
        }
      ],
      "count": 2,
      "neType": "CE6857-48S6CQ-EI"
    },
    {
      "devices": [
        {
          "neRole": "Firewall",
          "neName": "pod11-FW6655F",
          "neIp": "10.136.193.116",
          "neVersion": "1.22 V600R022C00SPC100"
        }
      ],
      "count": 1,
      "neType": "USG6655F"
    }
  ],
  "allDevices": [
    {
      "patchFileVersion": null,
      "neState": "online",
      "neVendor": "Huawei",
      "neRole": "Spine",
      "neName": "Spine1",
      "neIp": "10.136.193.102",
      "neType": "CE6863-48S6CQ",
      "neVersion": "8.22 V200R022C00"
    },
    {
      "patchFileVersion": null,
      "neState": "online",
      "neVendor": "Huawei",
      "neRole": "AccessRouter",
      "neName": "PE-10.136.193.112",
      "neIp": "10.136.193.112",
      "neType": "CE6870-48S6CQ-EI",
      "neVersion": "8.19 V200R019C10"
    },
    {
      "patchFileVersion": "V200R024SPH151",
      "neState": "online",
      "neVendor": "Huawei",
      "neRole": "ServerLeaf",
      "neName": "serverleaf3",
      "neIp": "10.136.193.104",
      "neType": "CE6881-48S6CQ",
      "neVersion": "8.24 V200R024C00SPC500"
    },
    {
      "patchFileVersion": null,
      "neState": "online",
      "neVendor": "Huawei",
      "neRole": "ServerLeaf",
      "neName": "serverleaf1",
      "neIp": "10.136.193.107",
      "neType": "CE6857-48S6CQ-EI",
      "neVersion": "8.25 V200R025C00SPC500"
    },
    {
      "patchFileVersion": "V200R024SPH151",
      "neState": "online",
      "neVendor": "Huawei",
      "neRole": "BorderLeaf",
      "neName": "borderleaf2",
      "neIp": "10.136.193.110",
      "neType": "CE6881-48S6CQ",
      "neVersion": "8.24 V200R024C00SPC500"
    },
    {
      "patchFileVersion": null,
      "neState": "online",
      "neVendor": "Huawei",
      "neRole": "FabricGateway",
      "neName": "pod11-DCIleaf2-1",
      "neIp": "10.136.193.111",
      "neType": "CE6870-48S6CQ-EI",
      "neVersion": "8.23 V200R023C00SPC100"
    },
    {
      "patchFileVersion": "V200R024SPH151",
      "neState": "online",
      "neVendor": "Huawei",
      "neRole": "BorderLeaf",
      "neName": "borderleaf1",
      "neIp": "10.136.193.109",
      "neType": "CE6881-48S6CQ",
      "neVersion": "8.24 V200R024C00SPC500"
    },
    {
      "patchFileVersion": "",
      "neState": "online",
      "neVendor": "Huawei",
      "neRole": "Firewall",
      "neName": "pod11-FW6655F",
      "neIp": "10.136.193.116",
      "neType": "USG6655F",
      "neVersion": "1.22 V600R022C00SPC100"
    },
    {
      "patchFileVersion": "",
      "neState": "online",
      "neVendor": "Huawei",
      "neRole": "ServerLeaf",
      "neName": "serverleaf2",
      "neIp": "10.136.193.108",
      "neType": "CE6857-48S6CQ-EI",
      "neVersion": "8.25 V200R025C00SPC500"
    }
  ],
  "fabricName": "pod11"
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=versionCol
component=Stack
path=/justify
line=13
column=1
retryable=false
message=Stack.justify expects "around" | "between" | "center" | "end" | "evenly" | "start", but received boolean.
hint=Use a value compatible with "around" | "between" | "center" | "end" | "evenly" | "start".

issue[1]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=nameListCol
component=Stack
path=/justify
line=15
column=1
retryable=false
message=Stack.justify expects "around" | "between" | "center" | "end" | "evenly" | "start", but received boolean.
hint=Use a value compatible with "around" | "between" | "center" | "end" | "evenly" | "start".

issue[2]
code=type-table-column-missing
severity=ERROR
source=type
statementId=typeTable
component=Table
path=/columns/neVersion
line=10
column=1
retryable=false
message=Table column field "neVersion" is absent from the proven row shape.
hint=Use a field present in the Table rows.

issue[3]
code=type-table-column-missing
severity=ERROR
source=type
statementId=typeTable
component=Table
path=/columns/neName
line=10
column=1
retryable=false
message=Table column field "neName" is absent from the proven row shape.
hint=Use a field present in the Table rows.

issue[4]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=versionTagTpl
component=Tag
path=/icon
line=14
column=1
retryable=false
message=Tag.icon expects string, but received null.
hint=Use a value compatible with string.

issue[5]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=nameTagTpl
component=Tag
path=/icon
line=16
column=1
retryable=false
message=Tag.icon expects string, but received null.
hint=Use a value compatible with string.
````

## 11. rw-156-serverleaf3-BD-BD

- 来源: `2.json` 第 6 条
- 状态: `INVALID`
- 原始 Issue 数: 1
- 可操作 Issue 数: 1

- `error_detail.txt` 标注: VLAN ID位在表。
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([header, kpiRow, mappingCard, orphanCard], "column", "m")

header = CardHeader(data.device.neName + " BD广播域总览", data.device.neIp + " · " + data.device.fabricName)

kpiRow = Stack([totalCard, withVlanCard, withSubCard, withoutSubCard], "row", "m", "stretch", "start", true)
totalCard = Card([TextContent("BD总数", "small"), TextContent("" + data.bd_count, "large-heavy")])
withVlanCard = Card([TextContent("有关联VLAN的BD", "small"), TextContent("" + @Count(data.bd_list_with_vlan), "large-heavy")])
withSubCard = Card([TextContent("有子接口的BD", "small"), TextContent("" + @Count(data.bd_subinterface_mapping), "large-heavy")])
withoutSubCard = Card([TextContent("无子接口的BD", "small"), TextContent("" + @Count(data.bd_without_subinterface), "large-heavy")])

mappingCard = Card([mappingHeader, mappingTable])
mappingHeader = CardHeader("BD-VLAN-子接口映射表", "展示BD ID、关联VLAN ID及子接口名称列表")
mappingTable = Table([bdIdCol, vlanIdCol, subIfCol], data.bd_subinterface_mapping)
bdIdCol = Col("BD ID", "bd_id")
vlanIdCol = Col("关联VLAN ID", "vlan_id", {cell: @Render("v", @Each(v, "vid", Tag("" + vid, "info")))})
subIfCol = Col("子接口名称", "subinterfaces", {cell: @Render("v", @Each(v, "sub", Tag(sub.interface_name, "neutral")))})

orphanCard = Card([orphanHeader, orphanTable])
orphanHeader = CardHeader("无BD关联的孤立子接口", "未绑定到任何BD的子接口列表")
orphanTable = Table([ifNameCol], data.subinterface_without_bd)
ifNameCol = Col("子接口名称", "interface_name", {cell: @Render("v", Tag(v, "warning"))})
````

### 输入 dataModel

````json
{
  "bd_count": 23,
  "subinterface_without_bd": [
    {
      "vlan_id": [],
      "interface_name": "10GE2/0/44.300"
    },
    {
      "vlan_id": [],
      "interface_name": "Eth-Trunk200.2004"
    },
    {
      "vlan_id": [],
      "interface_name": "10GE2/0/34.1001"
    },
    {
      "vlan_id": [],
      "interface_name": "10GE2/0/34.1002"
    },
    {
      "vlan_id": [
        2065
      ],
      "interface_name": "10GE2/0/34.2001"
    }
  ],
  "bd_with_bd_level_vlan": [
    {
      "note": "BD级别的VLAN和子接口VLAN不同，BD 3011未找到对应子接口",
      "vlan_id": 1011,
      "bd_id": 3011
    },
    {
      "note": "BD级别的VLAN和子接口VLAN不同，BD 756未找到对应子接口",
      "vlan_id": 756,
      "bd_id": 756
    }
  ],
  "bd_list_with_vlan": [
    {
      "vlan_id": 1011,
      "bd_id": 3011
    },
    {
      "vlan_id": 756,
      "bd_id": 756
    }
  ],
  "bd_without_subinterface": [
    8801
  ],
  "device": {
    "neRole": "ServerLeaf",
    "neName": "serverleaf3",
    "neIp": "10.136.193.104",
    "fabricName": "pod11"
  },
  "bd_subinterface_mapping": [
    {
      "bd_id": 10,
      "subinterfaces": [
        {
          "vlan_id": [
            4085
          ],
          "interface_name": "Eth-Trunk200.1001"
        }
      ]
    },
    {
      "bd_id": 3999,
      "subinterfaces": [
        {
          "vlan_id": [
            96
          ],
          "interface_name": "Eth-Trunk200.3999"
        }
      ]
    },
    {
      "bd_id": 5001,
      "subinterfaces": [
        {
          "vlan_id": [
            1994
          ],
          "interface_name": "Eth-Trunk200.2000"
        }
      ]
    },
    {
      "bd_id": 5006,
      "subinterfaces": [
        {
          "vlan_id": [
            1315
          ],
          "interface_name": "Eth-Trunk200.2010"
        },
        {
          "vlan_id": [
            1324
          ],
          "interface_name": "Eth-Trunk200.2001"
        }
      ]
    },
    {
      "bd_id": 5013,
      "subinterfaces": [
        {
          "vlan_id": [
            1095
          ],
          "interface_name": "Eth-Trunk200.2003"
        }
      ]
    },
    {
      "bd_id": 5015,
      "subinterfaces": [
        {
          "vlan_id": [
            1084
          ],
          "interface_name": "Eth-Trunk200.3011"
        }
      ]
    },
    {
      "bd_id": 5017,
      "subinterfaces": [
        {
          "vlan_id": [
            584
          ],
          "interface_name": "Eth-Trunk200.2005"
        }
      ]
    },
    {
      "bd_id": 5020,
      "subinterfaces": [
        {
          "vlan_id": [
            1094
          ],
          "interface_name": "Eth-Trunk200.3001"
        }
      ]
    },
    {
      "bd_id": 5021,
      "subinterfaces": [
        {
          "vlan_id": [
            2072
          ],
          "interface_name": "100GE1/0/1.2000"
        }
      ]
    },
    {
      "bd_id": 5022,
      "subinterfaces": [
        {
          "vlan_id": [
            2081
          ],
          "interface_name": "Eth-Trunk200.2014"
        }
      ]
    },
    {
      "bd_id": 5036,
      "subinterfaces": [
        {
          "vlan_id": [
            430
          ],
          "interface_name": "Eth-Trunk200.3665"
        }
      ]
    },
    {
      "bd_id": 5040,
      "subinterfaces": [
        {
          "vlan_id": [
            428
          ],
          "interface_name": "Eth-Trunk200.2667"
        }
      ]
    },
    {
      "bd_id": 5222,
      "subinterfaces": [
        {
          "vlan_id": [
            1873
          ],
          "interface_name": "Eth-Trunk200.2222"
        }
      ]
    },
    {
      "bd_id": 5301,
      "subinterfaces": [
        {
          "vlan_id": [
            1795
          ],
          "interface_name": "Eth-Trunk200.2300"
        },
        {
          "vlan_id": [
            795
          ],
          "interface_name": "Eth-Trunk200.3300"
        }
      ]
    },
    {
      "bd_id": 5401,
      "subinterfaces": [
        {
          "vlan_id": [
            1695
          ],
          "interface_name": "Eth-Trunk200.2400"
        }
      ]
    },
    {
      "bd_id": 5433,
      "subinterfaces": [
        {
          "vlan_id": [
            762
          ],
          "interface_name": "10GE2/0/19.1001"
        },
        {
          "vlan_id": [
            762
          ],
          "interface_name": "10GE2/0/20.1001"
        },
        {
          "vlan_id": [
            762
          ],
          "interface_name": "Eth-Trunk200.3333"
        },
        {
          "vlan_id": [
            762
          ],
          "interface_name": "10GE2/0/44.1001"
        }
      ]
    },
    {
      "bd_id": 6000,
      "subinterfaces": [
        {
          "vlan_id": [
            1772
          ],
          "interface_name": "Eth-Trunk200.6000"
        }
      ]
    },
    {
      "bd_id": 7169,
      "subinterfaces": [
        {
          "vlan_id": [
            926
          ],
          "interface_name": "Eth-Trunk200.3069"
        },
        {
          "vlan_id": [
            1026
          ],
          "interface_name": "10GE2/0/19.3069"
        },
        {
          "vlan_id": [
            926
          ],
          "interface_name": "10GE2/0/20.3069"
        },
        {
          "vlan_id": [
            1026
          ],
          "interface_name": "10GE2/0/44.3069"
        }
      ]
    },
    {
      "bd_id": 7301,
      "subinterfaces": [
        {
          "vlan_id": [
            695
          ],
          "interface_name": "Eth-Trunk200.3400"
        }
      ]
    },
    {
      "bd_id": 5030,
      "subinterfaces": [
        {
          "vlan_id": [
            2079
          ],
          "interface_name": "Eth-Trunk200.2002"
        }
      ]
    },
    {
      "bd_id": 8801,
      "subinterfaces": []
    }
  ]
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-table-column-missing
severity=ERROR
source=type
statementId=mappingTable
component=Table
path=/columns/vlan_id
line=13
column=1
retryable=false
message=Table column field "vlan_id" is absent from the proven row shape.
hint=Use a field present in the Table rows.
````

## 12. rw-158-POD6-Spine166-IP-Fabric

- 来源: `2.json` 第 7 条
- 状态: `INVALID`
- 原始 Issue 数: 2
- 可操作 Issue 数: 2

- `error_detail.txt` 标注: 表格中空白列。
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([deviceCard, portStatsRow, portTableCard, interfaceStatsRow, interfaceTableCard], "column", "m")

deviceCard = Card([deviceHeader, deviceDesc], "card")
deviceHeader = CardHeader(data.deviceInfo.neName, data.deviceInfo.fabricName)
deviceDesc = Descriptions([
  DescField("设备名", data.deviceInfo.neName),
  DescField("IP地址", data.deviceInfo.neIp),
  DescField("角色", data.deviceInfo.neRole),
  DescField("Fabric", data.deviceInfo.fabricName)
])

portStatsRow = Stack([portTotalCard, portAdminUpCard, portAdminDownCard, portOperUpCard, portOperDownCard], "row", "m", "stretch", "start", true)
portTotalCard = Card([TextContent("端口总数", "small"), TextContent("" + data.portSummary.totalCount, "large-heavy")])
portAdminUpCard = Card([TextContent("管理状态 Up", "small"), TextContent("" + data.portSummary.adminActiveCount, "large-heavy")])
portAdminDownCard = Card([TextContent("管理状态 Down", "small"), TextContent("" + data.portSummary.adminInactiveCount, "large-heavy")])
portOperUpCard = Card([TextContent("运行状态 Up", "small"), TextContent("" + data.portSummary.operActiveCount, "large-heavy")])
portOperDownCard = Card([TextContent("运行状态 Down", "small"), TextContent("" + data.portSummary.operInactiveCount, "large-heavy")])

portTableCard = Card([portTableHeader, portTable], "card")
portTableHeader = CardHeader("端口详情", "共 " + data.portSummary.totalCount + " 个端口")
portTable = Table([portNameCol, portIpCol, portAdminCol, portOperCol, portSpeedCol, portAliasCol], data.portSummary.ports)
portNameCol = Col("端口名称", "ifName")
portIpCol = Col("IP地址", "ifIp", {cell: @Render("v", v != "--" ? Link("http://" + v, v) : TextContent(v))})
portAdminCol = Col("管理状态", "adminStatus", {cell: @Render("v", v == "active" ? Tag("Up", "success") : Tag("Down", "danger"))})
portOperCol = Col("运行状态", "operStatus", {cell: @Render("v", v == "active" ? Tag("Up", "success") : Tag("Down", "danger"))})
portSpeedCol = Col("速率", "ifSpeed")
portAliasCol = Col("别名", "ifAlias", {cell: @Render("v", v ? TextContent(v) : TextContent("-"))})

interfaceStatsRow = Stack([interfaceTotalCard, interfaceOperUpCard, interfaceOperDownCard], "row", "m", "stretch", "start", true)
interfaceTotalCard = Card([TextContent("逻辑接口总数", "small"), TextContent("" + data.interfaceSummary.totalCount, "large-heavy")])
interfaceOperUpCard = Card([TextContent("运行状态 Up", "small"), TextContent("" + data.interfaceSummary.operActiveCount, "large-heavy")])
interfaceOperDownCard = Card([TextContent("运行状态 Down", "small"), TextContent("" + data.interfaceSummary.operInactiveCount, "large-heavy")])

interfaceTableCard = Card([interfaceTableHeader, interfaceTable], "card")
interfaceTableHeader = CardHeader("逻辑接口列表", "共 " + data.interfaceSummary.totalCount + " 个接口")
interfaceTable = Table([interfaceNameCol, interfaceTypeCol, interfaceAdminCol, interfaceOperCol, interfaceAliasCol], data.interfaceSummary.logicalInterfaces)
interfaceNameCol = Col("接口名称", "ifName")
interfaceTypeCol = Col("类型", "ifType")
interfaceAdminCol = Col("管理状态", "adminStatus", {cell: @Render("v", v == "active" ? Tag("Up", "success") : Tag("Down", "danger"))})
interfaceOperCol = Col("运行状态", "operStatus", {cell: @Render("v", v == "active" ? Tag("Up", "success") : Tag("Down", "danger"))})
interfaceAliasCol = Col("别名", "ifAlias", {cell: @Render("v", v ? TextContent(v) : TextContent("-"))})
````

### 输入 dataModel

````json
{
  "portSummary": {
    "operActiveCount": 16,
    "adminActiveCount": 37,
    "adminInactiveCount": 0,
    "totalCount": 37,
    "ports": [
      {
        "ifIp": "10.136.242.166",
        "ifName": "MEth0/0/0",
        "adminStatus": "active",
        "operStatus": "active",
        "ifSpeed": "1Gbps"
      },
      {
        "ifIp": "192.167.167.38",
        "ifName": "100GE1/0/1",
        "adminStatus": "active",
        "operStatus": "active",
        "ifSpeed": "40Gbps"
      },
      {
        "ifAlias": "TO_leaf164",
        "ifIp": "10.74.33.13",
        "ifName": "100GE1/0/2",
        "adminStatus": "active",
        "operStatus": "active",
        "ifSpeed": "40Gbps"
      },
      {
        "ifIp": "192.167.167.46",
        "ifName": "100GE1/0/3",
        "adminStatus": "active",
        "operStatus": "active",
        "ifSpeed": "40Gbps"
      },
      {
        "ifAlias": "TO_leaf73",
        "ifIp": "10.74.33.21",
        "ifName": "100GE1/0/4",
        "adminStatus": "active",
        "operStatus": "active",
        "ifSpeed": "40Gbps"
      },
      {
        "ifIp": "192.167.167.6",
        "ifName": "100GE1/0/5",
        "adminStatus": "active",
        "operStatus": "active",
        "ifSpeed": "40Gbps"
      },
      {
        "ifIp": "192.167.167.30",
        "ifName": "100GE1/0/6",
        "adminStatus": "active",
        "operStatus": "active",
        "ifSpeed": "40Gbps"
      },
      {
        "ifIp": "fec1:0000:0000:0101:0200:0000:c055:0101",
        "ifName": "100GE1/0/7",
        "adminStatus": "active",
        "operStatus": "active",
        "ifSpeed": "40Gbps"
      },
      {
        "ifIp": "192.167.167.26",
        "ifName": "100GE1/0/8",
        "adminStatus": "active",
        "operStatus": "active",
        "ifSpeed": "40Gbps"
      },
      {
        "ifIp": "192.167.167.70",
        "ifName": "100GE1/0/9",
        "adminStatus": "active",
        "operStatus": "active",
        "ifSpeed": "40Gbps"
      },
      {
        "ifIp": "192.167.167.66",
        "ifName": "100GE1/0/10",
        "adminStatus": "active",
        "operStatus": "active",
        "ifSpeed": "40Gbps"
      },
      {
        "ifIp": "60.6.6.1",
        "ifName": "100GE1/0/11",
        "adminStatus": "active",
        "operStatus": "active",
        "ifSpeed": "40Gbps"
      },
      {
        "ifIp": "192.167.167.14",
        "ifName": "100GE1/0/14",
        "adminStatus": "active",
        "operStatus": "active",
        "ifSpeed": "40Gbps"
      },
      {
        "ifIp": "192.169.169.9",
        "ifName": "100GE1/0/21",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "40Gbps"
      },
      {
        "ifIp": "192.169.169.13",
        "ifName": "100GE1/0/22",
        "adminStatus": "active",
        "operStatus": "active",
        "ifSpeed": "40Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/12",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/13",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/15",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/16",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/17",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/18",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/19",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/20",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/23",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/24",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/25",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/26",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/27",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/28",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/29",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/30",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/31",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/32",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/33",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/34",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/35",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      },
      {
        "ifIp": "--",
        "ifName": "100GE1/0/36",
        "adminStatus": "active",
        "operStatus": "inactive",
        "ifSpeed": "100Gbps"
      }
    ],
    "operInactiveCount": 21
  },
  "interfaceSummary": {
    "operActiveCount": 20,
    "adminActiveCount": 46,
    "adminInactiveCount": 0,
    "logicalInterfaces": [
      {
        "ifType": "逻辑接口",
        "ifName": "NULL0",
        "adminStatus": "active",
        "operStatus": "active"
      },
      {
        "ifType": "LoopBack",
        "ifName": "InLoopBack0",
        "adminStatus": "active",
        "operStatus": "active"
      },
      {
        "ifType": "LoopBack",
        "ifName": "LoopBack0",
        "adminStatus": "active",
        "operStatus": "active"
      },
      {
        "ifAlias": "ROUTER-ID",
        "ifType": "LoopBack",
        "ifName": "LoopBack1",
        "adminStatus": "active",
        "operStatus": "active"
      },
      {
        "ifType": "Management",
        "ifName": "MEth0/0/0",
        "adminStatus": "active",
        "operStatus": "active"
      },
      {
        "ifType": "NVE",
        "ifName": "Nve1",
        "adminStatus": "active",
        "operStatus": "active"
      },
      {
        "ifType": "VBDIF",
        "ifName": "Vbdif5000",
        "adminStatus": "active",
        "operStatus": "inactive"
      },
      {
        "ifType": "Vlanif",
        "ifName": "Vlanif1615",
        "adminStatus": "active",
        "operStatus": "inactive"
      },
      {
        "ifType": "Eth-Trunk",
        "ifName": "Eth-Trunk100",
        "adminStatus": "active",
        "operStatus": "inactive"
      },
      {
        "ifType": "Eth-Trunk子接口",
        "ifName": "Eth-Trunk100.2000",
        "adminStatus": "active",
        "operStatus": "inactive"
      }
    ],
    "totalCount": 46,
    "operInactiveCount": 26
  },
  "deviceInfo": {
    "neRole": "Spine",
    "neName": "POD6-Spine166",
    "neIp": "10.136.242.166",
    "fabricName": "AI鹰眼"
  }
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-table-column-missing
severity=ERROR
source=type
statementId=portTable
component=Table
path=/columns/ifAlias
line=21
column=1
retryable=false
message=Table column field "ifAlias" is absent from some rows.
hint=Use a field present in the Table rows.

issue[1]
code=type-table-column-missing
severity=ERROR
source=type
statementId=interfaceTable
component=Table
path=/columns/ifAlias
line=36
column=1
retryable=false
message=Table column field "ifAlias" is absent from some rows.
hint=Use a field present in the Table rows.
````

## 13. rw-159-POD6-Spine166-IP-Fabric

- 来源: `2.json` 第 8 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 表格中空白列未显示横杠。
- 标注覆盖结果: `MISSED`

### 输入 DSL

````openui
root = Stack([deviceCard, summaryRow, linkTableCard], "column", "m")

deviceCard = Card([deviceHeader, deviceDesc], "card")
deviceHeader = CardHeader(data.device.name, data.device.type)
deviceDesc = Descriptions([
  DescField("IP", data.device.ip),
  DescField("款型", data.device.type),
  DescField("角色", data.device.role),
  DescField("Fabric", data.device.fabric)
])

summaryRow = Stack([totalCard, activeCard, inactiveCard], "row", "m", "stretch")
totalCard = Card([TextContent("总链路数", "small"), TextContent("" + data.summary.total_links, "large-heavy")])
activeCard = Card([TextContent("活跃链路", "small"), TextContent("" + data.summary.active_links, "large-heavy")])
inactiveCard = Card([TextContent("非活跃链路", "small"), TextContent("" + data.summary.inactive_links, "large-heavy")])

linkTableCard = Card([linkTableHeader, linkTable], "card")
linkTableHeader = CardHeader("链路详情", "共 " + @Count(data.links) + " 条链路")
linkTable = Table([
  localPortCol,
  remoteDeviceCol,
  remoteIpCol,
  remotePortCol,
  linkStatusCol,
  linkTypeCol,
  localOpticalCol,
  remoteOpticalCol
], @Sort(data.links, "link_id", "asc"))

localPortCol = Col("本地端口", "local_port")
remoteDeviceCol = Col("对端设备", "remote_device")
remoteIpCol = Col("对端IP", "remote_ip")
remotePortCol = Col("对端端口", "remote_port")
linkStatusCol = Col("链路状态", "link_status", {cell: @Render("v", @Switch(v, {"active": Tag("active", "success"), "inactive": Tag("inactive", "warning"), "无链路": Tag("无链路", "neutral")}, Tag(v, "neutral")))})
linkTypeCol = Col("链路类型", "link_type")
localOpticalCol = Col("本端光模块", "local_optical", {cell: @Render("v", v ? TextContent(v.sn + " / " + v.trans_type) : TextContent("-"))})
remoteOpticalCol = Col("对端光模块", "remote_optical", {cell: @Render("v", v ? TextContent(v.sn + " / " + v.trans_type) : TextContent("-"))})
````

### 输入 dataModel

````json
{
  "summary": {
    "total_links": 10,
    "inactive_links": 0,
    "active_links": 10
  },
  "links": [
    {
      "link_type": "二层链路",
      "remote_optical": {
        "vendor": "HUAWEI",
        "sn": "M5764B00471",
        "trans_type": "40GBASE_SR4",
        "status": "active"
      },
      "remote_ip": "10.136.243.110",
      "local_optical": {
        "vendor": "HUAWEI",
        "sn": "M5763B07013",
        "trans_type": "40GBASE_SR4",
        "status": "active"
      },
      "remote_device": "POD6-Mlag3-1-110",
      "local_port": "100GE1/0/1",
      "remote_port": "100GE1/0/1",
      "link_status": "active",
      "link_id": 1
    },
    {
      "link_type": "二层链路",
      "remote_optical": {
        "vendor": "HUAWEI",
        "sn": "BP183900930293",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_ip": "10.136.242.164",
      "local_optical": {
        "vendor": "HUAWEI",
        "sn": "R3387014511",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_device": "POD6-Leaf164",
      "local_port": "100GE1/0/2",
      "remote_port": "100GE1/0/1",
      "link_status": "active",
      "link_id": 2
    },
    {
      "link_type": "二层链路",
      "remote_optical": {
        "vendor": "HUAWEI",
        "sn": "R3387014218",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_ip": "10.136.243.111",
      "local_optical": {
        "vendor": "HUAWEI",
        "sn": "BP184102870740",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_device": "POD6-Mlag3-2-111",
      "local_port": "100GE1/0/3",
      "remote_port": "100GE1/0/1",
      "link_status": "active",
      "link_id": 3
    },
    {
      "link_type": "二层链路",
      "remote_optical": {
        "vendor": "HUAWEI",
        "sn": "BP184103481273",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_ip": "10.136.242.73",
      "local_optical": {
        "vendor": "HUAWEI",
        "sn": "BP184603480464",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_device": "POD6-Leaf73",
      "local_port": "100GE1/0/4",
      "remote_port": "100GE1/0/1",
      "link_status": "active",
      "link_id": 4
    },
    {
      "link_type": "二层链路",
      "remote_optical": {
        "vendor": "HUAWEI",
        "sn": "BP184601931184",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_ip": "10.136.242.14",
      "local_optical": {
        "vendor": "HUAWEI",
        "sn": "BP184603480703",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_device": "POD6-Mlag2-1-14",
      "local_port": "100GE1/0/5",
      "remote_port": "100GE1/0/3",
      "link_status": "active",
      "link_id": 5
    },
    {
      "link_type": "二层链路",
      "remote_optical": {
        "vendor": "HUAWEI",
        "sn": "BP203000450138",
        "trans_type": "40GBASE_SR4",
        "status": "active"
      },
      "remote_ip": "10.136.246.106",
      "local_optical": {
        "vendor": "HUAWEI",
        "sn": "BP184102870369",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_device": "POD6-Mlag1-1-106",
      "local_port": "100GE1/0/6",
      "remote_port": "100GE1/0/1",
      "link_status": "active",
      "link_id": 6
    },
    {
      "link_type": "-",
      "remote_optical": null,
      "remote_ip": "-",
      "local_optical": {
        "vendor": "HUAWEI",
        "sn": "BP183903460984",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_device": "-",
      "local_port": "100GE1/0/7",
      "remote_port": "-",
      "link_status": "无链路",
      "link_id": 7
    },
    {
      "link_type": "二层链路",
      "remote_optical": {
        "vendor": "HUAWEI",
        "sn": "BP184503481207",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_ip": "10.136.246.10",
      "local_optical": {
        "vendor": "HUAWEI",
        "sn": "BP184103480402",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_device": "POD6-Mlag1-2-10",
      "local_port": "100GE1/0/8",
      "remote_port": "100GE1/0/2",
      "link_status": "active",
      "link_id": 8
    },
    {
      "link_type": "二层链路",
      "remote_optical": {
        "vendor": "HUAWEI",
        "sn": "M5763B06572",
        "trans_type": "40GBASE_SR4",
        "status": "active"
      },
      "remote_ip": "10.136.242.17",
      "local_optical": {
        "vendor": "HUAWEI",
        "sn": "M5763B06541",
        "trans_type": "40GBASE_SR4",
        "status": "active"
      },
      "remote_device": "POD6-PE17",
      "local_port": "100GE1/0/9",
      "remote_port": "100GE1/0/1",
      "link_status": "active",
      "link_id": 9
    },
    {
      "link_type": "二层链路",
      "remote_optical": {
        "vendor": "HUAWEI",
        "sn": "BP203100150710",
        "trans_type": "40GBASE_SR4",
        "status": "active"
      },
      "remote_ip": "10.136.242.16",
      "local_optical": {
        "vendor": "HUAWEI",
        "sn": "BP184801161178",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_device": "POD6-PE16",
      "local_port": "100GE1/0/10",
      "remote_port": "100GE1/0/2",
      "link_status": "active",
      "link_id": 10
    },
    {
      "link_type": "-",
      "remote_optical": null,
      "remote_ip": "-",
      "local_optical": {
        "vendor": "HUAWEI",
        "sn": "INFBB1640463",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_device": "-",
      "local_port": "100GE1/0/11",
      "remote_port": "-",
      "link_status": "无链路",
      "link_id": 11
    },
    {
      "link_type": "二层链路",
      "remote_optical": {
        "vendor": "HUAWEI",
        "sn": "BP184102872514",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_ip": "10.136.242.15",
      "local_optical": {
        "vendor": "HUAWEI",
        "sn": "R3387014676",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_device": "POD6-Mlag2-2-15",
      "local_port": "100GE1/0/14",
      "remote_port": "100GE1/0/2",
      "link_status": "active",
      "link_id": 12
    },
    {
      "link_type": "-",
      "remote_optical": null,
      "remote_ip": "-",
      "local_optical": {
        "vendor": "HUAWEI",
        "sn": "BP184103480722",
        "trans_type": "40GBASE_eSR4",
        "status": "inactive"
      },
      "remote_device": "-",
      "local_port": "100GE1/0/21",
      "remote_port": "-",
      "link_status": "inactive",
      "link_id": 13
    },
    {
      "link_type": "-",
      "remote_optical": null,
      "remote_ip": "-",
      "local_optical": {
        "vendor": "HUAWEI",
        "sn": "BP184801481226",
        "trans_type": "40GBASE_eSR4",
        "status": "active"
      },
      "remote_device": "-",
      "local_port": "100GE1/0/22",
      "remote_port": "-",
      "link_status": "无链路",
      "link_id": 14
    },
    {
      "link_type": "-",
      "remote_optical": null,
      "remote_ip": "-",
      "local_optical": {
        "vendor": "HUAWEI",
        "sn": "INJBX3980550",
        "trans_type": "100GBASE_SR4",
        "status": "inactive"
      },
      "remote_device": "-",
      "local_port": "100GE1/0/36",
      "remote_port": "-",
      "link_status": "inactive",
      "link_id": 15
    }
  ],
  "device": {
    "role": "Spine",
    "fabric": "AI鹰眼",
    "ip": "10.136.242.166",
    "name": "POD6-Spine166",
    "type": "CE8855-32CQ4BQ"
  }
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 14. rw-170-IP-Fabric-active

- 来源: `2.json` 第 9 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: Chat图X轴信息展示不全。
- 标注覆盖结果: `MISSED`

### 输入 DSL

````openui
root = Stack([deviceCard, healthCard, kpiRow, chartsRow, crossTable], "column", "m", "start", "start", false)

deviceCard = Card([deviceHeader, deviceDesc], "card")
deviceHeader = CardHeader("设备光模块统计分析报告", "O05-17F-Leaf2-Simu6-0001")
deviceDesc = Descriptions([nameField, ipField, fabricField], "设备基本信息")
nameField = DescField("设备名称", data.device.name)
ipField = DescField("IP 地址", data.device.ip)
fabricField = DescField("Fabric", data.device.fabric)

healthCard = Card([healthHeader, healthContent], "card")
healthHeader = CardHeader("健康评级", "较差")
healthContent = Stack([healthTag, healthRateText], "row", "m", "center", "start", false)
healthTag = Tag("较差", "danger")
healthRateText = TextContent("健康率: " + @FormatPercent(data.summary.healthRate / 100, 1))

kpiRow = Stack([totalCard, activeCard, inactiveCard], "row", "m", "stretch", "start", true)
totalCard = Card([totalLabel, totalValue], "card")
totalLabel = TextContent("光模块总数", "small")
totalValue = TextContent("" + data.summary.totalModules, "large-heavy")
activeCard = Card([activeLabel, activeValue], "card")
activeLabel = TextContent("运行正常", "small")
activeValue = TextContent("" + data.summary.activeCount, "large-heavy")
inactiveCard = Card([inactiveLabel, inactiveValue], "card")
inactiveLabel = TextContent("运行异常", "small")
inactiveValue = TextContent("" + data.summary.inactiveCount, "large-heavy")

chartsRow = Stack([statusPieCard, operBarCard, adminBarCard, vendorBarCard, typeBarCard, interfaceBarCard], "row", "m", "stretch", "start", true)

statusPieCard = Card([statusPieHeader, statusPieChart], "card")
statusPieHeader = CardHeader("运行正常率", "光模块运行状态占比")
statusPieChart = PieChart(["运行正常", "运行异常"], [data.summary.activeCount, data.summary.inactiveCount], "donut")

operBarCard = Card([operBarHeader, operBarChart], "card")
operBarHeader = CardHeader("运行状态分布", "Active vs Inactive")
operBarLabels = @Each(data.operStatus, "item", item.status)
operBarValues = @Each(data.operStatus, "item", item.count)
operBarChart = BarChart(operBarLabels, [Series("数量", operBarValues)], "grouped", "状态", "数量")

adminBarCard = Card([adminBarHeader, adminBarChart], "card")
adminBarHeader = CardHeader("管理状态分布", "Active vs Inactive")
adminBarLabels = @Each(data.adminStatus, "item", item.status)
adminBarValues = @Each(data.adminStatus, "item", item.count)
adminBarChart = BarChart(adminBarLabels, [Series("数量", adminBarValues)], "grouped", "状态", "数量")

vendorBarCard = Card([vendorBarHeader, vendorBarChart], "card")
vendorBarHeader = CardHeader("光模块厂商分布", "按厂商统计")
vendorBarLabels = @Each(data.vendor, "item", item.vendor)
vendorBarValues = @Each(data.vendor, "item", item.count)
vendorBarChart = BarChart(vendorBarLabels, [Series("数量", vendorBarValues)], "grouped", "厂商", "数量")

typeBarCard = Card([typeBarHeader, typeBarChart], "card")
typeBarHeader = CardHeader("光模块类型分布", "400GBASE_SR4 vs 400GBASE_FR4")
typeBarLabels = @Each(data.transType, "item", item.type)
typeBarValues = @Each(data.transType, "item", item.count)
typeBarChart = BarChart(typeBarLabels, [Series("数量", typeBarValues)], "grouped", "类型", "数量")

interfaceBarCard = Card([interfaceBarHeader, interfaceBarChart], "card")
interfaceBarHeader = CardHeader("接口类型分布", "按接口前缀统计")
interfaceBarLabels = @Each(data.interfacePrefix, "item", item.prefix)
interfaceBarValues = @Each(data.interfacePrefix, "item", item.count)
interfaceBarChart = BarChart(interfaceBarLabels, [Series("数量", interfaceBarValues)], "grouped", "接口类型", "数量")

crossTable = Card([crossHeader, crossTableContent], "card")
crossHeader = CardHeader("交叉分析", "管理状态 x 运行状态")
crossTableContent = Table([adminCol, operCol, countCol], data.crossAnalysis)
adminCol = Col("管理状态", "admin", {cell: @Render("v", @Switch(v, {"active": Tag("Active", "success"), "inactive": Tag("Inactive", "warning")}, Tag(v)))})
operCol = Col("运行状态", "oper", {cell: @Render("v", @Switch(v, {"active": Tag("Active", "success"), "inactive": Tag("Inactive", "danger")}, Tag(v)))})
countCol = Col("数量", "count")
````

### 输入 dataModel

````json
{
  "summary": {
    "activeCount": 80,
    "healthLevel": "较差",
    "healthRate": 46.8,
    "inactiveCount": 91,
    "totalModules": 171
  },
  "transType": [
    {
      "count": 154,
      "type": "400GBASE_SR4"
    },
    {
      "count": 17,
      "type": "400GBASE_FR4"
    }
  ],
  "vendor": [
    {
      "vendor": "HUAWEI",
      "count": 171
    }
  ],
  "adminStatus": [
    {
      "count": 155,
      "status": "active"
    },
    {
      "count": 16,
      "status": "inactive"
    }
  ],
  "interfacePrefix": [
    {
      "prefix": "400GE",
      "count": 169
    },
    {
      "prefix": "10GE",
      "count": 2
    }
  ],
  "operStatus": [
    {
      "count": 91,
      "status": "inactive"
    },
    {
      "count": 80,
      "status": "active"
    }
  ],
  "crossAnalysis": [
    {
      "count": 75,
      "admin": "active",
      "oper": "inactive"
    },
    {
      "count": 80,
      "admin": "active",
      "oper": "active"
    },
    {
      "count": 16,
      "admin": "inactive",
      "oper": "inactive"
    }
  ],
  "device": {
    "fabric": "default1",
    "ip": "197.197.5.1",
    "name": "O05-17F-Leaf2-Simu6-0001"
  }
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 15. rw-178-Offline-neName-IP

- 来源: `2.json` 第 10 条
- 状态: `INVALID`
- 原始 Issue 数: 3
- 可操作 Issue 数: 3

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([emptyCard])

emptyCard = Card([emptyHeader, emptyText], "column", "m", "center", "center")
emptyHeader = CardHeader("设备列表", "查询条件: " + data.queryCondition)
emptyText = TextContent("暂无Offline状态设备")
````

### 输入 dataModel

````json
{
  "total": 0,
  "queryCondition": "neState = Offline（全网范围）",
  "devices": []
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=emptyCard
component=Card
path=/variant
line=3
column=1
retryable=false
message=Card.variant expects "card" | "clear" | "sunk", but received string.
hint=Use a value compatible with "card" | "clear" | "sunk".

issue[1]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=emptyCard
component=Card
path=/direction
line=3
column=1
retryable=false
message=Card.direction expects "column" | "row", but received string.
hint=Use a value compatible with "column" | "row".

issue[2]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=emptyCard
component=Card
path=/gap
line=3
column=1
retryable=false
message=Card.gap expects "2xl" | "l" | "m" | "none" | "s" | "xl" | "xs", but received string.
hint=Use a value compatible with "2xl" | "l" | "m" | "none" | "s" | "xl" | "xs".
````

## 16. rw-181-IP-Emergency-Serious

- 来源: `2.json` 第 11 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([headerCard, chartsRow, logTableCard], "column", "m")

headerCard = Card([headerContent], "card", "column", "none")
headerContent = Stack([ipNameRow, rangeCountRow], "column", "xs")
ipNameRow = Stack([ipTag, nameTag], "row", "m")
ipTag = Tag("IP: " + data.deviceIp, "info")
nameTag = Tag("设备: " + data.deviceName, "neutral")
rangeCountRow = Stack([rangeText, countText], "row", "l", "center", "between")
rangeText = TextContent("时间范围: " + data.timeRange, "small")
countText = TextContent("日志总数: " + data.totalLogs, "small-heavy")

chartsRow = Stack([severityChartCard, topBriefsChartCard], "row", "m", "stretch")

severityChartCard = Card([severityHeader, severityChart], "card", "column", "s")
severityHeader = CardHeader("日志级别分布", "")
severityEntries = @ObjectEntries(data.severitySummary)
severityLabels = @Each(severityEntries, "e", e.key)
severityValues = @Each(severityEntries, "e", e.value)
severityChart = BarChart(severityLabels, [Series("数量", severityValues)], "grouped", "级别", "数量")

topBriefsChartCard = Card([topBriefsHeader, topBriefsChart], "card", "column", "s")
topBriefsHeader = CardHeader("Top 5 日志类型排行", "")
topBriefsLabels = @Each(data.topBriefs, "b", b.brief)
topBriefsValues = @Each(data.topBriefs, "b", b.count)
topBriefsChart = HorizontalBarChart(topBriefsLabels, [Series("次数", topBriefsValues)], "grouped", "次数", "日志类型")

logTableCard = Card([logTableHeader, logTable], "card", "column", "s")
logTableHeader = CardHeader("最近日志明细", "")
logTable = Table([timeCol, severityCol, briefCol, detailCol], data.logs)
timeCol = Col("时间", "time", {cell: @Render("v", TextContent(@FormatDate(v, "dateTime")))})
severityCol = Col("级别", "severity", {cell: @Render("v", @Switch(v, {"Emergency": Tag("Emergency", "danger"), "Serious": Tag("Serious", "warning"), "Warning": Tag("Warning", "info")}, Tag(v, "neutral")))})
briefCol = Col("摘要", "brief")
detailCol = Col("详情", "detail")
````

### 输入 dataModel

````json
{
  "totalLogs": 519,
  "deviceIp": "197.197.5.1",
  "topBriefs": [
    {
      "brief": "hwBoardResThresholdExceed_active",
      "severity": "Serious",
      "count": 86
    },
    {
      "brief": "hwStorageUtilizationRisingAlarm_active",
      "severity": "Serious",
      "count": 72
    },
    {
      "brief": "linkDown_active",
      "severity": "Serious",
      "count": 54
    },
    {
      "brief": "hwCPUUtilizationRisingAlarm_active",
      "severity": "Emergency",
      "count": 43
    },
    {
      "brief": "hwGtlDefaultValue_active",
      "severity": "Emergency",
      "count": 43
    }
  ],
  "severitySummary": {
    "Serious": 328,
    "Warning": 105,
    "Emergency": 86
  },
  "deviceName": "O05-17F-Leaf2-Simu6-0001",
  "logs": [
    {
      "severity": "Serious",
      "brief": "hwARPHostConflict_active",
      "time": "2026-08-08T19:07:08+08:00",
      "detail": "host access interface frequently changed. (LocalIPAddress=43.1.16.96, LocalMAC=c8a7-76b9-2692, RemoteIPAddress=10.1.0.2, RemoteMAC=c8a7-76b9-2692, LocalInterface=400GE1/0/100, RemoteInterface=400GE1/0/101)"
    },
    {
      "severity": "Serious",
      "brief": "hwBoardResThresholdExceed_active",
      "time": "2026-08-08T19:06:47+08:00",
      "detail": "The reached of forwarding resources reaches the alarm threshold. (Slot = 1, Threshold = 90, Description: The reached of used IPv4 FIB entries exceeded the alarm threshold.)"
    },
    {
      "severity": "Emergency",
      "brief": "hwCPUUtilizationRisingAlarm_active",
      "time": "2026-08-08T19:06:47+08:00",
      "detail": "CPU usage exceeded the pre-set overload threshold. (CpuUsageThreshold=90)"
    },
    {
      "severity": "Serious",
      "brief": "IPv4VxlanTunnelDown_active",
      "time": "2026-08-08T19:06:47+08:00",
      "detail": "IPv4 vxlan tunnel status changes. (SourceIpAddress=10.1.1.1, DestinationIpAddress=10.100.1.1, TunnelStatus=DOWN)"
    },
    {
      "severity": "Warning",
      "brief": "LACP_STATE_DOWN",
      "time": "2026-08-08T19:06:47+08:00",
      "detail": "LACP state is down. (PortName=400GE1/0/100, TrunkName=Eth-Trunk101, Reason=The interface went down physically or flapped to down.)"
    },
    {
      "severity": "Serious",
      "brief": "linkDown_active",
      "time": "2026-08-08T19:06:47+08:00",
      "detail": "interface status changes. (ifName=400GE1/0/100, AdminStatus=UP, OperStatus=DOWN, Reason=Interface physical link is down)"
    },
    {
      "severity": "Serious",
      "brief": "hwStorageUtilizationRisingAlarm_active",
      "time": "2026-08-08T19:06:47+08:00",
      "detail": "The storage usage exceeded the pre-set overload threshold. (UsageThreshold=90)"
    },
    {
      "severity": "Serious",
      "brief": "hwBoardResThresholdExceed_active",
      "time": "2026-08-08T19:06:47+08:00",
      "detail": "The reached of forwarding resources reaches the alarm threshold. (Slot = 1, Threshold = 90, Description: The reached of used ARP entries exceeded the alarm threshold.)"
    },
    {
      "severity": "Serious",
      "brief": "hwStorageUtilizationRisingAlarm_active",
      "time": "2026-08-08T19:06:47+08:00",
      "detail": "The storage usage exceeded the pre-set overload threshold. (UsageThreshold=90)"
    },
    {
      "severity": "Emergency",
      "brief": "hwGtlDefaultValue_active",
      "time": "2026-08-08T19:06:47+08:00",
      "detail": "Current license value is default, the reason is License is expired. (SlotID=1)"
    }
  ],
  "timeRange": "2026-08-08T18:29:59+08:00 ~ 2026-08-08T19:29:59+08:00"
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 17. rw-182-Fabric-active-inactive

- 来源: `2.json` 第 12 条
- 状态: `INVALID`
- 原始 Issue 数: 1
- 可操作 Issue 数: 1

- `error_detail.txt` 标注: 柱状图数据渲染失败。
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([infoCard, chartsRow, topModulesCard], "column", "m")

infoCard = Card([infoHeader, infoDesc], "card")
infoHeader = CardHeader("设备光模块统计", data.device_ip)
infoDesc = Descriptions([
  DescField("设备名", data.device_name),
  DescField("Fabric", data.fabric),
  DescField("光模块总数", data.total_optical_modules)
])

chartsRow = Stack([operStatusCard, adminStatusCard, transTypeCard], "row", "m", "stretch")

operStatusCard = Card([operHeader, operChart])
operHeader = CardHeader("运行状态分布", "")
operChart = PieChart(["active", "inactive"], [data.oper_status_summary.active, data.oper_status_summary.inactive], "donut")

adminStatusCard = Card([adminHeader, adminChart])
adminHeader = CardHeader("管理状态分布", "")
adminChart = PieChart(["active", "inactive"], [data.admin_status_summary.active, data.admin_status_summary.inactive], "donut")

transTypeCard = Card([transHeader, transChart])
transHeader = CardHeader("传输类型分布", "")
transChart = BarChart(["400GBASE_SR4", "400GBASE_FR4"], [data.trans_type_summary["400GBASE_SR4"], data.trans_type_summary["400GBASE_FR4"]])

topModulesCard = Card([topHeader, topTable])
topHeader = CardHeader("前10个活跃光模块", "")
topTable = Table([ifNameCol, operStatusCol, transTypeCol, vendorCol], data.top_active_modules)

ifNameCol = Col("接口名", "ifName")
operStatusCol = Col("运行状态", "operStatus", {cell: @Render("v", v == "active" ? Tag("Active", "success") : Tag("Inactive", "danger"))})
transTypeCol = Col("传输类型", "transType")
vendorCol = Col("厂商", "opticalVendor")
````

### 输入 dataModel

````json
{
  "device_ip": "197.197.5.1",
  "admin_status_summary": {
    "inactive": 16,
    "active": 155
  },
  "vendor_summary": {
    "HUAWEI": 171
  },
  "total_optical_modules": 171,
  "device_name": "O05-17F-Leaf2-Simu6-0001",
  "trans_type_summary": {
    "400GBASE_FR4": 17,
    "400GBASE_SR4": 154
  },
  "oper_status_summary": {
    "inactive": 91,
    "active": 80
  },
  "fabric": "default1",
  "top_active_modules": [
    {
      "transType": "400GBASE_SR4",
      "opticalVendor": "HUAWEI",
      "ifName": "400GE1/0/37:2",
      "operStatus": "active"
    },
    {
      "transType": "400GBASE_SR4",
      "opticalVendor": "HUAWEI",
      "ifName": "400GE1/0/58:1",
      "operStatus": "active"
    },
    {
      "transType": "400GBASE_SR4",
      "opticalVendor": "HUAWEI",
      "ifName": "400GE1/0/7:2",
      "operStatus": "active"
    },
    {
      "transType": "400GBASE_SR4",
      "opticalVendor": "HUAWEI",
      "ifName": "400GE1/0/3:2",
      "operStatus": "active"
    },
    {
      "transType": "400GBASE_SR4",
      "opticalVendor": "HUAWEI",
      "ifName": "400GE1/0/37:1",
      "operStatus": "active"
    },
    {
      "transType": "400GBASE_SR4",
      "opticalVendor": "HUAWEI",
      "ifName": "400GE1/0/17:2",
      "operStatus": "active"
    },
    {
      "transType": "400GBASE_SR4",
      "opticalVendor": "HUAWEI",
      "ifName": "400GE1/0/21:2",
      "operStatus": "active"
    },
    {
      "transType": "400GBASE_SR4",
      "opticalVendor": "HUAWEI",
      "ifName": "400GE1/0/57:1",
      "operStatus": "active"
    },
    {
      "transType": "400GBASE_SR4",
      "opticalVendor": "HUAWEI",
      "ifName": "400GE1/0/31:1",
      "operStatus": "active"
    },
    {
      "transType": "400GBASE_SR4",
      "opticalVendor": "HUAWEI",
      "ifName": "400GE1/0/52:1",
      "operStatus": "active"
    }
  ]
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=component-slot-type-mismatch
severity=ERROR
source=type
statementId=transChart
component=BarChart
path=/series
line=23
column=1
retryable=false
message=BarChart.series expects Component<Series>[], but received number[].
hint=Use a value compatible with Component<Series>[].
````

## 18. rw-183-O05-17F-Leaf2-Simu6-0001-DOWN-ifName

- 来源: `2.json` 第 13 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([header, tableCard], "column", "m")

header = CardHeader(data.device + " DOWN状态端口明细", "共 " + data.down_port_count + " 个端口")

tableCard = Card([portTable])

portTable = Table([nameCol, aliasCol, adminCol, operCol, speedCol, ipCol], @Sort(data.down_ports, "ifName", "asc"))

nameCol = Col("端口名称", "ifName")
aliasCol = Col("端口描述", "ifAlias")
adminCol = Col("管理状态", "adminStatus", {cell: @Render("v", @Switch(v, {"active": Tag("Active", "success"), "inactive": Tag("Inactive", "warning")}, Tag(v)))})
operCol = Col("运行状态", "operStatus", {cell: @Render("v", @Switch(v, {"active": Tag("Active", "success"), "inactive": Tag("Inactive", "danger")}, Tag(v)))})
speedCol = Col("速率", "ifHighSpeed")
ipCol = Col("IP地址", "ifIp")
````

### 输入 dataModel

````json
{
  "down_ports": [
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/42",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.82.1",
      "ifName": "400GE1/0/50",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/125",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/97",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "To_Spine-CE9866-6_400GE1/0/102",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.42.1",
      "ifName": "400GE1/0/78",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/46",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/20",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/41",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/89",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/102",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/118",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/88",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/19",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "To_Spine-CE9866-5_400GE1/0/53",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.8.1",
      "ifName": "400GE1/0/69",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/31",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/121",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/110",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/101",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "To_Spine-CE9866-5_400GE1/0/55",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.12.1",
      "ifName": "400GE1/0/71",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "To_Spine-CE9866-6_400GE1/0/103",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.44.1",
      "ifName": "400GE1/0/79",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "10G",
      "ifIp": "--",
      "ifName": "10GE1/0/2",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/117",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/100",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/91",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/114",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/86",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.86.1",
      "ifName": "400GE1/0/52",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/98",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.92.1",
      "ifName": "400GE1/0/55",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/99",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/28",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/93",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.84.1",
      "ifName": "400GE1/0/51",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/44",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/92",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/94",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/26",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/109",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/59",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "To_Spine-CE9866-6_400GE1/0/101",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.40.1",
      "ifName": "400GE1/0/77",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/106",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/23",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/27",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/128",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/126",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/22",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/90",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/116",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/30",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/111",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/24",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/17",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/127",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/120",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/29",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/32",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/112",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/18",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/115",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.90.1",
      "ifName": "400GE1/0/54",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "To_Spine-CE9866-6_400GE1/0/104",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.46.1",
      "ifName": "400GE1/0/80",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/124",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/62",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/104",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "200G",
      "ifIp": "--",
      "ifName": "400GE1/0/25:2",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/95",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/48",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "10G",
      "ifIp": "--",
      "ifName": "10GE1/0/1",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/123",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/108",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "To_Spine-CE9866-5_400GE1/0/54",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.10.1",
      "ifName": "400GE1/0/70",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "200G",
      "ifIp": "--",
      "ifName": "400GE1/0/25:1",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.94.1",
      "ifName": "400GE1/0/56",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/96",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/105",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/113",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/45",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.80.1",
      "ifName": "400GE1/0/49",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/119",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/85",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/87",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/47",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.88.1",
      "ifName": "400GE1/0/53",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "To_Spine-CE9866-5_400GE1/0/56",
      "ifHighSpeed": "400G",
      "ifIp": "150.1.14.1",
      "ifName": "400GE1/0/72",
      "adminStatus": "inactive",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/21",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/122",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/60",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/103",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/61",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/107",
      "adminStatus": "active",
      "operStatus": "inactive"
    },
    {
      "ifAlias": "",
      "ifHighSpeed": "400G",
      "ifIp": "--",
      "ifName": "400GE1/0/43",
      "adminStatus": "active",
      "operStatus": "inactive"
    }
  ],
  "down_port_count": 91,
  "device": "O05-17F-Leaf2-Simu6-0001"
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 19. rw-188-O05-17F-Leaf2-Simu6-0001-GBASE_SR4-GBASE

- 来源: `2.json` 第 14 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([summaryCard, chartCard], "column", "m")

summaryCard = Card([summaryText], "card")
summaryText = TextContent("设备 **" + data.deviceName + "** 的光模块中，用户关注的 **400GBASE_SR4** 传输类型共有 **" + data.targetCount + "** 个，占总光模块数（" + data.totalOpticalModules + "个）的 " + @FormatPercent(data.targetCount / data.totalOpticalModules, 1) + "。", "default")

chartCard = Card([chartHeader, chartBody], "card")
chartHeader = CardHeader("传输类型分布", "光模块传输类型占比")
chartBody = Stack([pieChart, legendStack], "column", "m", "center", "center")

pieLabels = @Each(data.transTypeDistribution, "item", item.transType)
pieValues = @Each(data.transTypeDistribution, "item", item.count)
pieChart = PieChart(pieLabels, pieValues, "donut")

legendStack = Stack(@Each(data.transTypeDistribution, "item", legendItemTpl), "row", "m", "center", "center")
legendItemTpl = Stack([legendDot, legendText], "row", "none", "center", "center")
legendDot = Card([], "card") // Placeholder for color dot, relying on chart colors
legendText = TextContent(item.transType + ": " + item.count + "个", "small")
````

### 输入 dataModel

````json
{
  "totalOpticalModules": 171,
  "transTypeDistribution": [
    {
      "transType": "400GBASE_SR4",
      "count": 154
    },
    {
      "transType": "400GBASE_FR4",
      "count": 17
    }
  ],
  "deviceName": "O05-17F-Leaf2-Simu6-0001",
  "targetCount": 154
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 20. rw-204-IP

- 来源: `2.json` 第 15 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 序号列不正确。
- 标注覆盖结果: `MISSED`

### 输入 DSL

````openui
root = Stack([header, tableCard])

header = CardHeader(data.title, data.timeRange)

tableCard = Card([dropTable])

dropTable = Table([rankCol, deviceCol, ipCol, ifaceCol, recvCol, sendCol, totalCol], data.data)

rankCol = IndexCol("排名")
deviceCol = Col("设备名称", "neName")
ipCol = Col("设备IP", "neIp")
ifaceCol = Col("接口名称", "ifName")
recvCol = Col("接收丢包数", "recvDrop")
sendCol = Col("发送丢包数", "sendDrop")
totalCol = Col("总丢包数", "totalDrop")
````

### 输入 dataModel

````json
{
  "data": [
    {
      "totalDrop": 6912,
      "sendDrop": 3456,
      "ifName": "400GE1/0/100",
      "rank": 1,
      "neName": "O05-17F-Leaf2-Simu6-0001",
      "neIp": "197.197.5.1",
      "recvDrop": 3456
    },
    {
      "totalDrop": 6912,
      "sendDrop": 3456,
      "ifName": "400GE1/0/101",
      "rank": 2,
      "neName": "O05-17F-Leaf2-Simu6-0001",
      "neIp": "197.197.5.1",
      "recvDrop": 3456
    },
    {
      "totalDrop": 6912,
      "sendDrop": 3456,
      "ifName": "400GE1/0/102",
      "rank": 3,
      "neName": "O05-17F-Leaf2-Simu6-0001",
      "neIp": "197.197.5.1",
      "recvDrop": 3456
    },
    {
      "totalDrop": 6912,
      "sendDrop": 3456,
      "ifName": "400GE1/0/103",
      "rank": 4,
      "neName": "O05-17F-Leaf2-Simu6-0001",
      "neIp": "197.197.5.1",
      "recvDrop": 3456
    },
    {
      "totalDrop": 6912,
      "sendDrop": 3456,
      "ifName": "400GE1/0/104",
      "rank": 5,
      "neName": "O05-17F-Leaf2-Simu6-0001",
      "neIp": "197.197.5.1",
      "recvDrop": 3456
    }
  ],
  "title": "近一小时丢包最多的5个接口",
  "timeRange": "2026-08-08T19:16:35+08:00 ~ 2026-08-08T20:16:35+08:00"
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 21. rw-209-IP

- 来源: `3.json` 第 1 条
- 状态: `INVALID`
- 原始 Issue 数: 3
- 可操作 Issue 数: 3

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([alertCallout, infoCard, statsCard, tableCard], "column", "m")

alertCallout = TextCallout("danger", "严重异常：所有接口丢包率均为 33.33%", "检测到全部 " + data.summary.total_interfaces + " 个接口的接收/发送丢包率均为 33.33%，属于严重网络故障，请立即排查。")

infoCard = Card([infoHeader, infoDesc], "card")
infoHeader = CardHeader("设备基本信息", null)
infoDesc = Descriptions([DescField("设备名称", data.summary.device_name), DescField("设备 IP", data.summary.device_ip), DescField("时间范围", data.summary.time_range), DescField("接口总数", data.summary.total_interfaces)], null, 2, true)

statsCard = Card([statsHeader, statsRow], "card")
statsHeader = CardHeader("丢包率统计概览", null)
statsRow = Stack([recvStats, sendStats], "row", "m", "stretch")
recvStats = Card([TextContent("接收丢包率 (%)", "small-heavy"), Stack([TextContent("平均: " + data.summary.recv_drop_packet_rate.avg, "default"), TextContent("最小: " + data.summary.recv_drop_packet_rate.min, "default"), TextContent("最大: " + data.summary.recv_drop_packet_rate.max, "default")], "column", "none")])
sendStats = Card([TextContent("发送丢包率 (%)", "small-heavy"), Stack([TextContent("平均: " + data.summary.send_drop_packet_rate.avg, "default"), TextContent("最小: " + data.summary.send_drop_packet_rate.min, "default"), TextContent("最大: " + data.summary.send_drop_packet_rate.max, "default")], "column", "none")])

tableCard = Card([tableHeader, interfaceTable], "card")
tableHeader = CardHeader("接口丢包率明细", "按接口名称排序")
sortedInterfaces = @Sort(data.interfaces, "ifName", "asc")
interfaceTable = Table([nameCol, recvCol, sendCol], sortedInterfaces)
nameCol = Col("接口名称", "ifName")
recvCol = Col("接收丢包率 (%)", "recv_drop_rate", {cell: @Render("v", v > 0 ? Tag(v + "%", "danger") : TextContent(v + "%"))})
sendCol = Col("发送丢包率 (%)", "send_drop_rate", {cell: @Render("v", v > 0 ? Tag(v + "%", "danger") : TextContent(v + "%"))})
````

### 输入 dataModel

````json
{
  "summary": {
    "device_ip": "197.197.5.1",
    "device_name": "O05-17F-Leaf2-Simu6-0001",
    "time_range": "2026-08-08 20:01:06 ~ 20:31:06 (近30分钟)",
    "recv_drop_packet_rate": {
      "unit": "%",
      "avg": 33.33,
      "min": 33.33,
      "max": 33.33
    },
    "total_interfaces": 64,
    "send_drop_packet_rate": {
      "unit": "%",
      "avg": 33.33,
      "min": 33.33,
      "max": 33.33
    }
  },
  "interfaces": [
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/17",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/18",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/19",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/20",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/21",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/22",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/23",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/24",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/26",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/27",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/28",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/29",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/30",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/31",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/32",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/41",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/42",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/43",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/44",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/45",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/46",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/47",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/48",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/49",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/50",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/51",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/52",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/53",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/54",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/55",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/56",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/59",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/60",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/61",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/62",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/69",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/70",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/71",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/72",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/77",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/78",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/79",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/80",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/85",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/86",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/87",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/88",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/89",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/90",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/91",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/92",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/93",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/94",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/95",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/96",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/97",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/98",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/99",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/100",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/101",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/102",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/103",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/104",
      "send_drop_rate": 33.33
    },
    {
      "recv_drop_rate": 33.33,
      "ifName": "400GE1/0/105",
      "send_drop_rate": 33.33
    }
  ]
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=infoHeader
component=CardHeader
path=/subtitle
line=6
column=1
retryable=false
message=CardHeader.subtitle expects string, but received null.
hint=Use a value compatible with string.

issue[1]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=infoDesc
component=Descriptions
path=/title
line=7
column=1
retryable=false
message=Descriptions.title expects string, but received null.
hint=Use a value compatible with string.

issue[2]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=statsHeader
component=CardHeader
path=/subtitle
line=10
column=1
retryable=false
message=CardHeader.subtitle expects string, but received null.
hint=Use a value compatible with string.
````

## 22. rw-211-bps-Top5-bps

- 来源: `3.json` 第 2 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([noteCard, infoCard, inTableCard, outTableCard], "column", "m")

noteCard = TextCallout("info", "查询说明", data.note)

infoCard = Descriptions([DescField("设备名称", data.device.neName), DescField("设备IP", data.device.neIp), DescField("接口总数", data.total_interfaces), DescField("统计时间", data.timeRange)], "设备与查询信息")

inTableCard = Card([inHeader, inTable])
inHeader = CardHeader("接收速率 (bps) Top5", "")
inTable = Table([inRankCol, inIfNameCol, inRateCol], data.input_byte_rate_top5)
inRankCol = Col("排名", "rank")
inIfNameCol = Col("接口名称", "ifName")
inRateCol = Col("接收速率 (bps)", "input_byte_rate")

outTableCard = Card([outHeader, outTable])
outHeader = CardHeader("发送速率 (bps) Top5", "")
outTable = Table([outRankCol, outIfNameCol, outRateCol], data.output_byte_rate_top5)
outRankCol = Col("排名", "rank")
outIfNameCol = Col("接口名称", "ifName")
outRateCol = Col("发送速率 (bps)", "output_byte_rate")
````

### 输入 dataModel

````json
{
  "note": "该设备全部64个接口的接收速率和发送速率完全一致，均为 8.53 bps（极低流量），Top5仅为排序前5项展示。",
  "input_byte_rate_top5": [
    {
      "unit": "bps",
      "ifName": "400GE1/0/100",
      "rank": 1,
      "input_byte_rate": 8.53
    },
    {
      "unit": "bps",
      "ifName": "400GE1/0/101",
      "rank": 2,
      "input_byte_rate": 8.53
    },
    {
      "unit": "bps",
      "ifName": "400GE1/0/102",
      "rank": 3,
      "input_byte_rate": 8.53
    },
    {
      "unit": "bps",
      "ifName": "400GE1/0/103",
      "rank": 4,
      "input_byte_rate": 8.53
    },
    {
      "unit": "bps",
      "ifName": "400GE1/0/104",
      "rank": 5,
      "input_byte_rate": 8.53
    }
  ],
  "output_byte_rate_top5": [
    {
      "output_byte_rate": 8.53,
      "unit": "bps",
      "ifName": "400GE1/0/100",
      "rank": 1
    },
    {
      "output_byte_rate": 8.53,
      "unit": "bps",
      "ifName": "400GE1/0/101",
      "rank": 2
    },
    {
      "output_byte_rate": 8.53,
      "unit": "bps",
      "ifName": "400GE1/0/102",
      "rank": 3
    },
    {
      "output_byte_rate": 8.53,
      "unit": "bps",
      "ifName": "400GE1/0/103",
      "rank": 4
    },
    {
      "output_byte_rate": 8.53,
      "unit": "bps",
      "ifName": "400GE1/0/104",
      "rank": 5
    }
  ],
  "device": {
    "neName": "O05-17F-Leaf2-Simu6-0001",
    "neIp": "197.197.5.1"
  },
  "total_interfaces": 64,
  "timeRange": "近30分钟 (2026-08-08T20:04:49 ~ 20:34:49+08:00)"
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 23. rw-215-O05-17F-Leaf2-Simu6-0001-IP-Fabric

- 来源: `3.json` 第 3 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([header, infoCard, slotCard, healthCard, chartsRow], "column", "m")
header = CardHeader("O05-17F-Leaf2-Simu6-0001", "设备全景信息仪表板")
infoCard = Card([infoHeader, infoDesc], "card")
infoHeader = CardHeader("设备基本信息", "")
infoDesc = Descriptions([nameField, ipField, stateField, typeField, roleField, vendorField, versionField, fabricField, macField, esnField], "设备详情", 2, true)
nameField = DescField("名称", data.device.neName)
ipField = DescField("IP 地址", data.device.neIp)
stateField = DescField("状态", data.device.neState == "Online" ? Tag("在线", "success") : Tag("离线", "danger"))
typeField = DescField("型号", data.device.neType)
roleField = DescField("角色", data.device.neRole)
vendorField = DescField("厂商", data.device.neVendor)
versionField = DescField("版本", data.device.neVersion)
fabricField = DescField("Fabric", data.device.fabricName)
macField = DescField("MAC 地址", data.device.neMac)
esnField = DescField("ESN", data.device.neEsn)

slotCard = Card([slotHeader, slotDesc], "card")
slotHeader = CardHeader("单板信息", "")
slotDesc = Descriptions([slotNameField, softVerField, hardVerField, slotAdminField, slotOperField], "单板详情", 2, true)
slotNameField = DescField("单板名称", data.slot.slotName)
softVerField = DescField("软件版本", data.slot.softVersion)
hardVerField = DescField("硬件版本", data.slot.hardVersion)
slotAdminField = DescField("管理状态", data.slot.adminStatus == "active" ? Tag("Active", "success") : Tag("Inactive", "warning"))
slotOperField = DescField("运行状态", data.slot.operStatus == "active" ? Tag("Active", "success") : Tag("Inactive", "warning"))

healthCard = Card([healthHeader, healthContent], "card")
healthHeader = CardHeader("设备健康概览", "")
healthContent = Stack([healthStatus, healthDesc], "column", "s")
healthStatus = Tag(data.healthSummary.deviceHealth == "Normal" ? "健康" : "异常", data.healthSummary.deviceHealth == "Normal" ? "success" : "danger")
healthDesc = TextContent(data.healthSummary.description)

chartsRow = Stack([portAdminPie, portOperPie, speedBar, opticAdminPie, opticOperPie], "row", "m", "start", "start", true)

portAdminPie = Card([portAdminHeader, portAdminChart], "card")
portAdminHeader = CardHeader("端口管理状态", "")
portAdminChart = PieChart(["Active", "Inactive"], [data.portStats.adminStatus.active, data.portStats.adminStatus.inactive], "donut")

portOperPie = Card([portOperHeader, portOperChart], "card")
portOperHeader = CardHeader("端口运行状态", "")
portOperChart = PieChart(["Active", "Inactive"], [data.portStats.operStatus.active, data.portStats.operStatus.inactive], "donut")

speedBar = Card([speedHeader, speedChart], "card")
speedHeader = CardHeader("端口速率分布", "")
speedChart = BarChart(["400GE", "200GE", "10GE", "1GE"], [Series("端口数", [data.portStats.speedDistribution["400GE"], data.portStats.speedDistribution["200GE"], data.portStats.speedDistribution["10GE"], data.portStats.speedDistribution["1GE"]])], "grouped", "速率", "数量")

opticAdminPie = Card([opticAdminHeader, opticAdminChart], "card")
opticAdminHeader = CardHeader("光模块管理状态", "")
opticAdminChart = PieChart(["Active", "Inactive"], [data.opticalStats.adminStatus.active, data.opticalStats.adminStatus.inactive], "donut")

opticOperPie = Card([opticOperHeader, opticOperChart], "card")
opticOperHeader = CardHeader("光模块运行状态", "")
opticOperChart = PieChart(["Active", "Inactive"], [data.opticalStats.operStatus.active, data.opticalStats.operStatus.inactive], "donut")
````

### 输入 dataModel

````json
{
  "opticalStats": {
    "vendorDistribution": {
      "HUAWEI": 171
    },
    "total": 171,
    "adminStatus": {
      "inactive": 16,
      "active": 155
    },
    "operStatus": {
      "inactive": 91,
      "active": 80
    }
  },
  "healthSummary": {
    "portsDown": 91,
    "opticsUp": 80,
    "description": "设备在线运行正常，81个端口运行中，90个端口未使用/待机",
    "deviceHealth": "Normal",
    "opticsDown": 91,
    "portsUp": 81
  },
  "slot": {
    "slotName": "CE9866-128DQ 1",
    "softVersion": "V300R026C00",
    "hardVersion": "CEM32DQP01 VER B",
    "adminStatus": "active",
    "operStatus": "active"
  },
  "device": {
    "neState": "Online",
    "neEsn": "2102350FMLN0H8023425",
    "neVendor": "Huawei",
    "neMac": "97-55-46-F9-00-01",
    "neRole": "ServerLeaf",
    "neName": "O05-17F-Leaf2-Simu6-0001",
    "neIp": "197.197.5.1",
    "neType": "CE9866-128DQ",
    "neVersion": "1.26 V300R026C00",
    "neResId": "0e85d60e-d01d-4290-9a86-ea4865a31be0",
    "neCategory": "EnterpriseSwitch",
    "fabricName": "default1"
  },
  "portStats": {
    "total": 172,
    "adminStatus": {
      "inactive": 16,
      "active": 156
    },
    "operStatus": {
      "inactive": 91,
      "active": 81
    },
    "speedDistribution": {
      "400GE": 87,
      "200GE": 82,
      "10GE": 2,
      "1GE": 1
    }
  }
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 24. rw-217-query

- 来源: `3.json` 第 4 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([headerCard, linkTableCard], "column", "m")

headerCard = Card([headerTitle, headerSubtitle])
headerTitle = TextContent("设备链路信息", "large-heavy")
headerSubtitle = TextContent("设备: " + data.device + " | IP: " + data.device_ip + " | 总链路数: " + data.total_links)

linkTableCard = Card([table])
table = Table([indexCol, localPortCol, localDeviceCol, remotePortCol, remoteDeviceCol, linkTypeCol, speedCol, adminStatusCol, operStatusCol], data.links)

indexCol = IndexCol("序号")
localPortCol = Col("本端端口", "local_port")
localDeviceCol = Col("本端设备", "local_device")
remotePortCol = Col("对端端口", "remote_port")
remoteDeviceCol = Col("对端设备", "remote_device")
linkTypeCol = Col("链路类型", "link_type")
speedCol = Col("速率", "speed")
adminStatusCol = Col("管理状态", "admin_status", {cell: @Render("v", @Switch(v, {"UP": Tag("UP", "success"), "DOWN": Tag("DOWN", "danger")}, Tag(v, "neutral")))})
operStatusCol = Col("运营状态", "oper_status", {cell: @Render("v", @Switch(v, {"UP": Tag("UP", "success"), "DOWN": Tag("DOWN", "danger")}, Tag(v, "neutral")))})
````

### 输入 dataModel

````json
{
  "device_ip": "197.197.5.1",
  "total_links": 9,
  "links": [
    {
      "local_device": "O05-17F-Leaf2-Simu6-0001",
      "link_type": "二层链路",
      "remote_device": "O05-17F-Leaf2-Simu6-0002",
      "local_port": "400GE1/0/65:1",
      "remote_port": "400GE1/0/37:1",
      "index": 1,
      "link_status": "unknown",
      "admin_status": "UP",
      "oper_status": "UP",
      "speed": "200G"
    },
    {
      "local_device": "O05-17F-Leaf2-Simu6-0001",
      "link_type": "二层链路",
      "remote_device": "O05-17F-Leaf2-Simu6-0002",
      "local_port": "400GE1/0/66:2",
      "remote_port": "400GE1/0/34:2",
      "index": 2,
      "link_status": "unknown",
      "admin_status": "UP",
      "oper_status": "UP",
      "speed": "200G"
    },
    {
      "local_device": "O05-17F-Leaf2-Simu6-0001",
      "link_type": "二层链路",
      "remote_device": "O05-17F-Leaf2-Simu6-0002",
      "local_port": "400GE1/0/67:2",
      "remote_port": "400GE1/0/35:2",
      "index": 3,
      "link_status": "unknown",
      "admin_status": "UP",
      "oper_status": "UP",
      "speed": "200G"
    },
    {
      "local_device": "O05-17F-Leaf2-Simu6-0001",
      "link_type": "二层链路",
      "remote_device": "O05-17F-Leaf2-Simu6-0002",
      "local_port": "400GE1/0/66:1",
      "remote_port": "400GE1/0/38:1",
      "index": 4,
      "link_status": "unknown",
      "admin_status": "UP",
      "oper_status": "UP",
      "speed": "200G"
    },
    {
      "local_device": "O05-17F-Leaf2-Simu6-0001",
      "link_type": "二层链路",
      "remote_device": "O05-17F-Leaf2-Simu6-0002",
      "local_port": "400GE1/0/65:2",
      "remote_port": "400GE1/0/37:2",
      "index": 5,
      "link_status": "unknown",
      "admin_status": "UP",
      "oper_status": "UP",
      "speed": "200G"
    },
    {
      "local_device": "O05-17F-Leaf2-Simu6-0001",
      "link_type": "二层链路",
      "remote_device": "O05-17F-Leaf2-Simu6-0002",
      "local_port": "400GE1/0/67:1",
      "remote_port": "400GE1/0/39:1",
      "index": 6,
      "link_status": "unknown",
      "admin_status": "UP",
      "oper_status": "UP",
      "speed": "200G"
    },
    {
      "local_device": "O05-17F-Leaf2-Simu6-0001",
      "link_type": "二层链路",
      "remote_device": "PE-10.136.193.112",
      "local_port": "400GE1/0/100",
      "remote_port": "100GE1/0/1",
      "index": 7,
      "link_status": "unknown",
      "admin_status": "UP",
      "oper_status": "DOWN",
      "speed": "100G"
    },
    {
      "local_device": "O05-17F-Leaf2-Simu6-0001",
      "link_type": "二层链路",
      "remote_device": "O05-17F-Leaf2-Simu6-0002",
      "local_port": "400GE1/0/68:1",
      "remote_port": "400GE1/0/36:1",
      "index": 8,
      "link_status": "unknown",
      "admin_status": "UP",
      "oper_status": "UP",
      "speed": "200G"
    },
    {
      "local_device": "O05-17F-Leaf2-Simu6-0001",
      "link_type": "二层链路",
      "remote_device": "O05-17F-Leaf2-Simu6-0002",
      "local_port": "400GE1/0/68:2",
      "remote_port": "400GE1/0/36:2",
      "index": 9,
      "link_status": "unknown",
      "admin_status": "UP",
      "oper_status": "UP",
      "speed": "200G"
    }
  ],
  "device": "O05-17F-Leaf2-Simu6-0001"
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 25. rw-220-IP-Emergency-Serious

- 来源: `3.json` 第 5 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([infoCard, chartRow, alertCard], "column", "m", "start", "start", false)

infoCard = Card([infoHeader, infoDesc], "card", "column", "m", "start", "start", false)
infoHeader = CardHeader("设备日志概览", data.device_name)
infoDesc = Descriptions([nameField, ipField, timeField, totalField], "基本信息", 2, true)
nameField = DescField("设备名", data.device_name)
ipField = DescField("IP 地址", data.device_ip)
timeField = DescField("时间范围", data.time_range)
totalField = DescField("总日志数", data.total)

chartRow = Stack([pieCard, barCard], "row", "m", "stretch", "start", false)

pieCard = Card([pieHeader, pieChart], "card", "column", "s", "start", "start", false)
pieHeader = CardHeader("日志级别分布", "")
pieChart = PieChart(pieLabels, pieValues, "donut")
pieLabels = @ObjectKeys(data.severity_distribution)
pieValues = @Each(@ObjectEntries(data.severity_distribution), "entry", entry.value)

barCard = Card([barHeader, barChart], "card", "column", "s", "start", "start", false)
barHeader = CardHeader("Top 10 日志摘要", "按数量降序")
barChart = HorizontalBarChart(barLabels, [Series("数量", barValues)], "grouped", "日志摘要", "数量")
sortedBrief = @Sort(data.brief_top10, "count", "desc")
barLabels = @Each(sortedBrief, "item", item.brief)
barValues = @Each(sortedBrief, "item", item.count)

alertCard = Card([alertHeader, alertTable], "card", "column", "m", "start", "start", false)
alertHeader = CardHeader("重点告警 (Emergency)", "需要立即关注的严重告警")
alertTable = Table([briefCol, countCol, descCol], emergencyRows)
emergencyRows = @Filter(data.brief_top10, "severity", "==", "Emergency")
briefCol = Col("摘要", "brief")
countCol = Col("数量", "count")
descCol = Col("描述", "description")
````

### 输入 dataModel

````json
{
  "device_ip": "197.197.5.1",
  "total": 518,
  "device_name": "O05-17F-Leaf2-Simu6-0001",
  "time_range": "2026-08-08 20:03:14 ~ 21:03:14 (CST)",
  "brief_top10": [
    {
      "brief": "hwBoardResThresholdExceed_active",
      "severity": "Serious",
      "count": 148,
      "description": "转发资源达到告警阈值（IPv4 FIB/IPv6 FIB/ND/ARP表项超限）"
    },
    {
      "brief": "hwStorageUtilizationRisingAlarm_active",
      "severity": "Serious",
      "count": 74,
      "description": "存储空间使用率超过阈值（≥90%）"
    },
    {
      "brief": "hwARPHostConflict_active",
      "severity": "Serious",
      "count": 37,
      "description": "主机访问接口频繁变化（ARP冲突）"
    },
    {
      "brief": "hwMacHashConflict",
      "severity": "Warning",
      "count": 37,
      "description": "MAC地址哈希冲突"
    },
    {
      "brief": "hwGtlDefaultValue_active",
      "severity": "Emergency",
      "count": 37,
      "description": "License已过期，License值恢复默认"
    },
    {
      "brief": "hwCPUUtilizationRisingAlarm_active",
      "severity": "Emergency",
      "count": 37,
      "description": "CPU使用率超过阈值（≥90%）"
    },
    {
      "brief": "IPv4VxlanTunnelDown_active",
      "severity": "Serious",
      "count": 37,
      "description": "VXLAN隧道状态变为DOWN（源10.1.1.1→目的10.100.1.1）"
    },
    {
      "brief": "LACP_STATE_DOWN",
      "severity": "Warning",
      "count": 37,
      "description": "LACP状态DOWN（端口400GE1/0/100，Eth-Trunk101）"
    },
    {
      "brief": "linkDown_active",
      "severity": "Serious",
      "count": 37,
      "description": "接口物理链路DOWN（400GE1/0/100）"
    },
    {
      "brief": "hwMacUsageRaisingInfo_active",
      "severity": "Warning",
      "count": 37,
      "description": "MAC使用率达到阈值（89%，阈值80%）"
    }
  ],
  "severity_distribution": {
    "Serious": 333,
    "Warning": 111,
    "Emergency": 74
  }
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 26. rw-225-spine189-bps-bps

- 来源: `3.json` 第 6 条
- 状态: `INVALID`
- 原始 Issue 数: 2
- 可操作 Issue 数: 1

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([header, tableCard])
header = CardHeader("spine189 接口收发速率", "近30分钟平均值 (bps)")
outputData = data.queryInterfaceMetrics[0].telemetryData
inputData = data.queryInterfaceMetrics[1].telemetryData
mergedRows = @Each(outputData, "out", @Each(inputData, "in", out.ifName == in.ifName && out.output_byte_rate > 0 && in.input_byte_rate > 0 ? {ifName: out.ifName, inputRate: in.input_byte_rate, outputRate: out.output_byte_rate} : null))
flatRows = mergedRows[0]
tableCard = Card([interfaceTable])
interfaceTable = Table([nameCol, inCol, outCol], flatRows)
nameCol = Col("接口名称", "ifName")
inCol = Col("接收速率(bps)", "inputRate", {cell: @Render("v", TextContent(@FormatNumber(v, 2)))})
outCol = Col("发送速率(bps)", "outputRate", {cell: @Render("v", TextContent(@FormatNumber(v, 2)))})
````

### 输入 dataModel

````json
{
  "queryInterfaceMetrics": [
    {
      "telemetryTotal": 54,
      "telemetryData": [
        {
          "output_byte_rate": 15187.165714285715,
          "ifName": "25GE1/0/11",
          "metricValue": 15187.165714285715,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 15187.165714285715,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/11",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 11867.405714285716,
          "ifName": "25GE1/0/18",
          "metricValue": 11867.405714285716,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 11867.405714285716,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/18",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 11866.64,
          "ifName": "25GE1/0/24",
          "metricValue": 11866.64,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 11866.64,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/24",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 8064.948571428572,
          "ifName": "25GE1/0/20",
          "metricValue": 8064.948571428572,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 8064.948571428572,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/20",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 914.6457142857142,
          "ifName": "25GE1/0/2",
          "metricValue": 914.6457142857142,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 914.6457142857142,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/2",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "100GE1/0/1",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|100GE1/0/1",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "100GE1/0/2",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|100GE1/0/2",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "100GE1/0/3",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|100GE1/0/3",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "100GE1/0/4",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|100GE1/0/4",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "100GE1/0/5",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|100GE1/0/5",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "100GE1/0/6",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|100GE1/0/6",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/1",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/1",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/10",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/10",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/12",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/12",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/13",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/13",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/14",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/14",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/15",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/15",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/16",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/16",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/17",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/17",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/19",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/19",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/21",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/21",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/22",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/22",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/23",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/23",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/25",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/25",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/26",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/26",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/27",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/27",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/28",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/28",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/29",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/29",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/3",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/3",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/30",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/30",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/31",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/31",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/32",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/32",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/33",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/33",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/34",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/34",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/35",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/35",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/36",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/36",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/37",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/37",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/38",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/38",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/39",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/39",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/4",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/4",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/40",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/40",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/41",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/41",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/42",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/42",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/43",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/43",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/44",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/44",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/45",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/45",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/46",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/46",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/47",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/47",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/48",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/48",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/5",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/5",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/6",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/6",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/7",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/7",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/8",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/8",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "output_byte_rate": 0,
          "ifName": "25GE1/0/9",
          "metricValue": 0,
          "output_bandwidth_effcnt": 140,
          "output_byte_rate_filter": 0,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/9",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        }
      ]
    },
    {
      "telemetryTotal": 54,
      "telemetryData": [
        {
          "ifName": "25GE1/0/11",
          "metricValue": 8357.651428571427,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 8357.651428571427,
          "input_byte_rate": 8357.651428571427,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/11",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/20",
          "metricValue": 3858.3657142857146,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 3858.3657142857146,
          "input_byte_rate": 3858.3657142857146,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/20",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/2",
          "metricValue": 1252.6857142857143,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 1252.6857142857143,
          "input_byte_rate": 1252.6857142857143,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/2",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "100GE1/0/1",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|100GE1/0/1",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "100GE1/0/2",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|100GE1/0/2",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "100GE1/0/3",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|100GE1/0/3",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "100GE1/0/4",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|100GE1/0/4",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "100GE1/0/5",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|100GE1/0/5",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "100GE1/0/6",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|100GE1/0/6",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/1",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/1",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/10",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/10",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/12",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/12",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/13",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/13",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/14",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/14",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/15",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/15",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/16",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/16",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/17",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/17",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/18",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/18",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/19",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/19",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/21",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/21",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/22",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/22",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/23",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/23",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/24",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/24",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/25",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/25",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/26",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/26",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/27",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/27",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/28",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/28",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/29",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/29",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/3",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/3",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/30",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/30",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/31",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/31",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/32",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/32",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/33",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/33",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/34",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/34",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/35",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/35",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/36",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/36",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/37",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/37",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/38",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/38",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/39",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/39",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/4",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/4",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/40",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/40",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/41",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/41",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/42",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/42",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/43",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/43",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/44",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/44",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/45",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/45",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/46",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/46",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/47",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/47",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/48",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/48",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/5",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/5",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/6",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/6",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/7",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/7",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/8",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/8",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        },
        {
          "ifName": "25GE1/0/9",
          "metricValue": 0,
          "input_bandwidth_effcnt": 140,
          "neName": "spine189",
          "neIp": "10.136.194.189",
          "mac": "6C-EB-B6-51-37-C1",
          "input_byte_rate_filter": 0,
          "input_byte_rate": 0,
          "if_query_id": "6C-EB-B6-51-37-C1|25GE1/0/9",
          "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
        }
      ]
    }
  ]
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-table-row-shape-mismatch
severity=ERROR
source=type
statementId=interfaceTable
component=Table
path=/rows
line=8
column=1
retryable=false
message=Table rows must be a flat object array, but received null | object[].
hint=Flatten nested @Each results before passing them to Table.
````

<details>
<summary>原始校验结果（含级联诊断）</summary>

````text
status=INVALID

issue[0]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=interfaceTable
component=Table
path=/rows
line=8
column=1
retryable=false
message=Table.rows expects object[], but received null | object[].
hint=Use a value compatible with object[].

issue[1]
code=type-table-row-shape-mismatch
severity=ERROR
source=type
statementId=interfaceTable
component=Table
path=/rows
line=8
column=1
retryable=false
message=Table rows must be a flat object array, but received null | object[].
hint=Flatten nested @Each results before passing them to Table.
````

</details>

## 27. rw-228-CPU-Top-IP

- 来源: `3.json` 第 7 条
- 状态: `INVALID`
- 原始 Issue 数: 2
- 可操作 Issue 数: 2

- `error_detail.txt` 标注: 表格渲染失败。
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([header, tableCard])

header = CardHeader("CPU利用率 Top 5 设备排名", "按CPU利用率降序排列")

sortedData = @Sort(data.queryNetworkDeviceMetrics.telemetryData, "cpu_usage", "desc")
top5Data = @Each(sortedData, "item", @If(@Index(sortedData, "item") < 5, item, null))

tableCard = Card([table])

table = Table([nameCol, ipCol, cpuCol], top5Data)

nameCol = Col("设备名称", "neName")
ipCol = Col("设备IP", "neIp")
cpuCol = Col("CPU利用率 (%)", "cpu_usage", {cell: @Render("v", TextContent(@FormatNumber(v, 2) + "%"))})
````

### 输入 dataModel

````json
{
  "queryNetworkDeviceMetrics": {
    "telemetryTotal": 6,
    "telemetryData": [
      {
        "cpu_usage_filter": 23.428571428571427,
        "metricValue": 23.428571428571427,
        "neName": "pod13_spine_193.163",
        "cpu_usage": 23.428571428571427,
        "cpu_effcnt": 28,
        "neIp": "10.136.193.163",
        "mac": "C8-A7-76-B9-26-91",
        "ne_dn": "dc0d76fd-e595-4752-8989-14e087873a38"
      },
      {
        "cpu_usage_filter": 19.080593849416754,
        "metricValue": 19.080593849416754,
        "neName": "pod13_serverleaf_161",
        "cpu_usage": 19.080593849416754,
        "cpu_effcnt": 943,
        "neIp": "10.136.193.161",
        "mac": "1C-20-DB-DE-3F-61",
        "ne_dn": "b9a61499-ff08-4d9d-aba8-db3bc4506352"
      },
      {
        "cpu_usage_filter": 17.185185185185187,
        "metricValue": 17.185185185185187,
        "neName": "serverleaf2",
        "cpu_usage": 17.185185185185187,
        "cpu_effcnt": 27,
        "neIp": "10.136.193.108",
        "mac": "70-C7-F2-7D-95-21",
        "ne_dn": "7ae1696e-c238-45e3-9c4e-11ed9985f73c"
      },
      {
        "cpu_usage_filter": 16.928571428571427,
        "metricValue": 16.928571428571427,
        "neName": "spine189",
        "cpu_usage": 16.928571428571427,
        "cpu_effcnt": 28,
        "neIp": "10.136.194.189",
        "mac": "6C-EB-B6-51-37-C1",
        "ne_dn": "5e10fa4c-9c38-4ece-949d-e6dd65a60c50"
      },
      {
        "cpu_usage_filter": 16.375,
        "metricValue": 16.375,
        "neName": "Serverleaf_193.160",
        "cpu_usage": 16.375,
        "cpu_effcnt": 16,
        "neIp": "10.136.193.160",
        "mac": "C8-A7-76-C8-4C-71",
        "ne_dn": "d566e9ff-1177-43f4-a1f2-1b673a22e9ca"
      },
      {
        "cpu_usage_filter": 13,
        "metricValue": 13,
        "neName": "Server Leaf188",
        "cpu_usage": 13,
        "cpu_effcnt": 13,
        "neIp": "10.136.194.188",
        "mac": "6C-EB-B6-51-35-91",
        "ne_dn": "6f2fd000-5060-44c2-a12c-c5c26ea10733"
      }
    ]
  }
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=unknown-component
severity=ERROR
source=contract
statementId=top5Data
component=If
path=
line=-1
column=-1
retryable=false
message=Unknown component "If" — not found in catalog or builtins
hint=<null>

issue[1]
code=unknown-component
severity=ERROR
source=contract
statementId=top5Data
component=Index
path=
line=-1
column=-1
retryable=false
message=Unknown component "Index" — not found in catalog or builtins
hint=<null>
````

## 28. rw-241-O05-17F-Leaf2-Simu6-0001-down-lane

- 来源: `3.json` 第 8 条
- 状态: `INVALID`
- 原始 Issue 数: 2
- 可操作 Issue 数: 1

- `error_detail.txt` 标注: 表格渲染失败。
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([summaryCard, moduleCard, laneCard], "column", "m")

summaryCard = Card([summaryText])
summaryText = TextContent(data.summary.description)

moduleCard = Card([moduleHeader, moduleTable])
moduleHeader = CardHeader("光模块维度指标", "端口与温度")
moduleTable = Table([modulePortCol, moduleTempCol], data.opticalModuleMetrics)
modulePortCol = Col("端口", "port")
moduleTempCol = Col("温度(℃)", "temperature", {cell: @Render("v", v != null ? TextContent("" + v) : TextContent("无数据"))})

laneRows = @Each(data.opticalModuleMetrics, "mod", @Each(mod.lanes, "lane", {port: mod.port, laneId: lane.laneId, tx_power_dbm: lane.tx_power_dbm, rx_power_dbm: lane.rx_power_dbm, bias_cur: lane.bias_cur}))

laneCard = Card([laneHeader, laneTable])
laneHeader = CardHeader("通道维度指标", "端口、laneId、发送功率、接收功率、偏置电流")
laneTable = Table([lanePortCol, laneIdCol, laneTxCol, laneRxCol, laneBiasCol], laneRows)
lanePortCol = Col("端口", "port")
laneIdCol = Col("laneId", "laneId")
laneTxCol = Col("发送功率(dBm)", "tx_power_dbm")
laneRxCol = Col("接收功率(dBm)", "rx_power_dbm")
laneBiasCol = Col("偏置电流(mA)", "bias_cur")
````

### 输入 dataModel

````json
{
  "summary": {
    "description": "设备 O05-17F-Leaf2-Simu6-0001 上 7 个运行状态为 down 的光模块性能指标详情，查询时间为近 30 分钟。6 个端口有完整数据，400GE1/0/124 无性能数据上报。",
    "metrics": [
      "温度(℃)",
      "发送功率(dBm)",
      "接收功率(dBm)",
      "偏置电流(mA)"
    ]
  },
  "opticalModuleMetrics": [
    {
      "port": "400GE1/0/104",
      "temperature": 37.62,
      "lanes": [
        {
          "laneId": 0,
          "tx_power_dbm": -3,
          "bias_cur": 12.05,
          "rx_power_dbm": -3
        },
        {
          "laneId": 1,
          "tx_power_dbm": -3.1,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.1
        },
        {
          "laneId": 2,
          "tx_power_dbm": -3.5,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.5
        },
        {
          "laneId": 3,
          "tx_power_dbm": -3.6,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.6
        }
      ]
    },
    {
      "port": "400GE1/0/60",
      "temperature": 37.62,
      "lanes": [
        {
          "laneId": 0,
          "tx_power_dbm": -3,
          "bias_cur": 12.05,
          "rx_power_dbm": -3
        },
        {
          "laneId": 1,
          "tx_power_dbm": -3.1,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.1
        },
        {
          "laneId": 2,
          "tx_power_dbm": -3.5,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.5
        },
        {
          "laneId": 3,
          "tx_power_dbm": -3.6,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.6
        }
      ]
    },
    {
      "port": "400GE1/0/46",
      "temperature": 37.62,
      "lanes": [
        {
          "laneId": 0,
          "tx_power_dbm": -3,
          "bias_cur": 12.05,
          "rx_power_dbm": -3
        },
        {
          "laneId": 1,
          "tx_power_dbm": -3.1,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.1
        },
        {
          "laneId": 2,
          "tx_power_dbm": -3.5,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.5
        },
        {
          "laneId": 3,
          "tx_power_dbm": -3.6,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.6
        }
      ]
    },
    {
      "port": "400GE1/0/49",
      "temperature": 37.62,
      "lanes": [
        {
          "laneId": 0,
          "tx_power_dbm": -3,
          "bias_cur": 12.05,
          "rx_power_dbm": -3
        },
        {
          "laneId": 1,
          "tx_power_dbm": -3.1,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.1
        },
        {
          "laneId": 2,
          "tx_power_dbm": -3.5,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.5
        },
        {
          "laneId": 3,
          "tx_power_dbm": -3.6,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.6
        }
      ]
    },
    {
      "port": "400GE1/0/71",
      "temperature": 37.62,
      "lanes": [
        {
          "laneId": 0,
          "tx_power_dbm": -3,
          "bias_cur": 12.05,
          "rx_power_dbm": -3
        },
        {
          "laneId": 1,
          "tx_power_dbm": -3.1,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.1
        },
        {
          "laneId": 2,
          "tx_power_dbm": -3.5,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.5
        },
        {
          "laneId": 3,
          "tx_power_dbm": -3.6,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.6
        }
      ]
    },
    {
      "port": "400GE1/0/100",
      "temperature": 37.62,
      "lanes": [
        {
          "laneId": 0,
          "tx_power_dbm": -3,
          "bias_cur": 12.05,
          "rx_power_dbm": -3
        },
        {
          "laneId": 1,
          "tx_power_dbm": -3.1,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.1
        },
        {
          "laneId": 2,
          "tx_power_dbm": -3.5,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.5
        },
        {
          "laneId": 3,
          "tx_power_dbm": -3.6,
          "bias_cur": 12.05,
          "rx_power_dbm": -3.6
        }
      ]
    },
    {
      "note": "无性能数据上报",
      "port": "400GE1/0/124",
      "temperature": null,
      "lanes": []
    }
  ]
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-table-row-shape-mismatch
severity=ERROR
source=type
statementId=laneTable
component=Table
path=/rows
line=16
column=1
retryable=false
message=Table rows must be a flat object array, but received object[][].
hint=Flatten nested @Each results before passing them to Table.
````

<details>
<summary>原始校验结果（含级联诊断）</summary>

````text
status=INVALID

issue[0]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=laneTable
component=Table
path=/rows
line=16
column=1
retryable=false
message=Table.rows expects object[], but received object[][].
hint=Use a value compatible with object[].

issue[1]
code=type-table-row-shape-mismatch
severity=ERROR
source=type
statementId=laneTable
component=Table
path=/rows
line=16
column=1
retryable=false
message=Table rows must be a flat object array, but received object[][].
hint=Flatten nested @Each results before passing them to Table.
````

</details>

## 29. rw-246-O05-17F-Leaf2-Simu6-0001-CPU-CPU

- 来源: `3.json` 第 9 条
- 状态: `INVALID`
- 原始 Issue 数: 1
- 可操作 Issue 数: 1

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([gaugeCard])

gaugeCard = Card([gaugeHeader, gaugeChart])
gaugeHeader = CardHeader(data.title, data.subtitle)
gaugeChart = GaugeChart([gaugeReading], 0, 100)
gaugeReading = {name: "CPU利用率", value: data.data.cpuUsage}
````

### 输入 dataModel

````json
{
  "data": {
    "deviceIp": "197.197.5.1",
    "cpuUsage": 70,
    "unit": "%",
    "deviceName": "O05-17F-Leaf2-Simu6-0001",
    "timeRange": "近30分钟平均值"
  },
  "subtitle": "设备：O05-17F-Leaf2-Simu6-0001 | 时间范围：近30分钟",
  "options": {
    "max": 100,
    "threshold": 80,
    "warningThreshold": 60
  },
  "title": "设备CPU利用率",
  "type": "gauge"
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=unknown-component
severity=ERROR
source=contract
statementId=gaugeChart
component=GaugeChart
path=
line=-1
column=-1
retryable=false
message=Unknown component "GaugeChart" — not found in catalog or builtins
hint=<null>
````

## 30. rw-249-O05-17F-Leaf2-Simu6-0001-O05-17F-Leaf2-S

- 来源: `3.json` 第 10 条
- 状态: `INVALID`
- 原始 Issue 数: 4
- 可操作 Issue 数: 1

- `error_detail.txt` 标注: 设备名称未显示。
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([summaryCard, queryInfoCard, deviceCards])

summaryCard = Card([summaryHeader, summaryText])
summaryHeader = CardHeader("日志查询结果", "")
summaryText = TextContent(data.summary)

queryInfoCard = Card([queryInfo], variant="sunk")
queryInfo = Descriptions([
  DescField("查询类型", data.queryCondition.queryLogType),
  DescField("设备名称", @Each(data.queryCondition.deviceNames, "name", Tag(name, "neutral"))),
  DescField("时间范围", data.queryCondition.timeRange)
], "查询条件", 1, true)

deviceCards = Stack(@Each(data.devices, "device", deviceCardTpl), "column", "m")
deviceCardTpl = Card([deviceHeader, emptyText])
deviceHeader = CardHeader(device.neName, "日志数量: " + device.logCount)
emptyText = TextContent("暂无日志数据")
````

### 输入 dataModel

````json
{
  "summary": "在指定时间范围内，两台设备均未查询到日志数据。",
  "queryCondition": {
    "queryLogType": "all（所有日志）",
    "deviceNames": [
      "O05-17F-Leaf2-Simu6-0001",
      "O05-17F-Leaf2-Simu6-0002"
    ],
    "timeRange": "2026-08-12 15:03:02 ~ 16:03:02（近1小时）"
  },
  "devices": [
    {
      "logCount": 0,
      "neName": "O05-17F-Leaf2-Simu6-0001",
      "syslogResult": []
    },
    {
      "logCount": 0,
      "neName": "O05-17F-Leaf2-Simu6-0002",
      "syslogResult": []
    }
  ]
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=syntax-unexpected-token
severity=ERROR
source=syntax
statementId=queryInfoCard
component=<null>
path=<null>
line=7
column=42
retryable=false
message=Unexpected token EQUALS
hint=<null>
````

<details>
<summary>原始校验结果（含级联诊断）</summary>

````text
status=INVALID

issue[0]
code=syntax-unexpected-token
severity=ERROR
source=syntax
statementId=queryInfoCard
component=<null>
path=<null>
line=7
column=42
retryable=false
message=Unexpected token EQUALS
hint=<null>

issue[1]
code=unresolved-ref
severity=ERROR
source=reference
statementId=queryInfoCard
component=<null>
path=<null>
line=7
column=1
retryable=false
message=unresolved reference "variant"
hint=define a statement named "variant" earlier in the document

issue[2]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=queryInfoCard
component=Card
path=/direction
line=7
column=1
retryable=false
message=Card.direction expects "column" | "row", but received null.
hint=Use a value compatible with "column" | "row".

issue[3]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=queryInfoCard
component=Card
path=/gap
line=7
column=1
retryable=false
message=Card.gap expects "2xl" | "l" | "m" | "none" | "s" | "xl" | "xs", but received string.
hint=Use a value compatible with "2xl" | "l" | "m" | "none" | "s" | "xl" | "xs".
````

</details>

## 31. rw-283-CPU-CPU

- 来源: `3.json` 第 11 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([titleCard, detailCard])

titleCard = Card([titleText, descText])
titleText = TextContent(data.title, "large-heavy")
descText = TextContent(data.description, "small")

detailCard = Card([descList])
descList = Descriptions([nameField, cpuField, timeField], "详细信息")
nameField = DescField("设备名称", data.deviceName)
cpuField = DescField("CPU利用率", data.cpuUsage + "%")
timeField = DescField("时间范围", data.timeRange)
````

### 输入 dataModel

````json
{
  "cpuUsage": 70,
  "description": "近30分钟平均CPU利用率",
  "title": "设备CPU利用率查询结果",
  "deviceName": "O05-17F-Leaf2-Simu6-0001",
  "timeRange": "2026-08-12 17:00:49 ~ 2026-08-12 17:30:49 (UTC+8)"
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 32. rw-290-default1-Fabric-CPU

- 来源: `3.json` 第 12 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([summaryCard, deviceTableCard])

summaryCard = Card([summaryHeader, summaryContent])
summaryHeader = CardHeader("Fabric CPU利用率统计", data.fabricName + " · " + data.timeRange)
summaryContent = Stack([summaryRow], "row", "m", "start", "start", true)
summaryRow = Stack([avgCard, countCard], "row", "m", "center", "center")
avgCard = Card([TextContent("平均CPU利用率", "small"), TextContent(data.averageCpuUsage + "%", "large-heavy")])
countCard = Card([TextContent("设备数量", "small"), TextContent("" + data.deviceCount, "large-heavy")])

deviceTableCard = Card([tableHeader, deviceTable])
tableHeader = CardHeader("设备列表", "共 " + data.deviceCount + " 台设备")
deviceTable = Table([nameCol, ipCol, cpuCol], data.deviceList)
nameCol = Col("设备名称", "neName")
ipCol = Col("IP地址", "neIp")
cpuCol = Col("CPU利用率", "cpu_usage", {cell: @Render("v", TextContent(v + "%"))})
````

### 输入 dataModel

````json
{
  "metricName": "CPU利用率",
  "averageCpuUsage": 70,
  "deviceCount": 10,
  "deviceList": [
    {
      "neName": "O05-17F-Leaf2-Simu6-0001",
      "cpu_usage": 70,
      "neIp": "197.197.5.1"
    },
    {
      "neName": "O05-17F-Leaf2-Simu6-0002",
      "cpu_usage": 70,
      "neIp": "197.197.5.2"
    },
    {
      "neName": "O05-17F-Leaf2-Simu6-0003",
      "cpu_usage": 70,
      "neIp": "197.197.5.3"
    },
    {
      "neName": "O05-17F-Leaf2-Simu6-0004",
      "cpu_usage": 70,
      "neIp": "197.197.5.4"
    },
    {
      "neName": "O05-17F-Leaf2-Simu6-0005",
      "cpu_usage": 70,
      "neIp": "197.197.5.5"
    },
    {
      "neName": "O05-17F-Leaf2-Simu6-0006",
      "cpu_usage": 70,
      "neIp": "197.197.5.6"
    },
    {
      "neName": "O05-17F-Leaf2-Simu6-0007",
      "cpu_usage": 70,
      "neIp": "197.197.5.7"
    },
    {
      "neName": "O05-17F-Leaf2-Simu6-0008",
      "cpu_usage": 70,
      "neIp": "197.197.5.8"
    },
    {
      "neName": "O05-17F-Leaf2-Simu6-0009",
      "cpu_usage": 70,
      "neIp": "197.197.5.9"
    },
    {
      "neName": "O05-17F-Leaf2-Simu6-0010",
      "cpu_usage": 70,
      "neIp": "197.197.5.10"
    }
  ],
  "fabricName": "default1",
  "metricUnit": "%",
  "timeRange": "近30分钟 (2026-08-12 17:20 ~ 17:50)"
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 33. rw-297-O05-17F-Leaf2-Simu6-0001-CPU-CPU

- 来源: `3.json` 第 13 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([chartCard])
chartCard = Card([chartHeader, cpuChart])
chartHeader = CardHeader("设备CPU利用率趋势", "197.197.5.1 (O05-17F-Leaf2-Simu6-0001) · 近1小时")
timeLabels = @Each(data.data, "item", item.timestamp)
cpuValues = @Each(data.data, "item", item.value)
cpuSeries = Series("CPU利用率", cpuValues)
cpuChart = LineChart(timeLabels, [cpuSeries], "smooth", "时间", "CPU利用率(%)")
````

### 输入 dataModel

````json
{
  "summary": "CPU利用率保持在70.0%",
  "deviceIp": "197.197.5.1",
  "unit": "%",
  "data": [
    {
      "value": 70,
      "timestamp": "2026-08-12 17:05:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:06:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:07:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:08:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:09:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:12:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:13:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:14:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:15:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:16:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:17:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:18:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:19:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:20:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:21:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:22:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:23:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:24:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:28:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:29:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:30:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:31:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:32:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:33:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:34:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:35:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:36:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:37:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:38:00"
    },
    {
      "value": 70,
      "timestamp": "2026-08-12 17:39:00"
    }
  ],
  "metric": "cpu_usage",
  "chartType": "trend_chart",
  "title": "设备CPU利用率趋势",
  "deviceName": "O05-17F-Leaf2-Simu6-0001",
  "timeRange": "近1小时"
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 34. rw-299-CPU-IP-MAC

- 来源: `3.json` 第 14 条
- 状态: `INVALID`
- 原始 Issue 数: 8
- 可操作 Issue 数: 2

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([titleCard, infoCard, metricCard, timeCard], "column", "m")

titleCard = Card([titleText])
titleText = TextContent(data.title, "large-heavy")

infoCard = Card([infoHeader, infoDescriptions])
infoHeader = CardHeader("设备信息")
infoDescriptions = Descriptions([nameField, ipField, macField], columns=1)
nameField = DescField("设备名称", data.deviceInfo.neName ?? "无数据")
ipField = DescField("设备IP", data.deviceInfo.neIp ?? "无数据")
macField = DescField("MAC地址", data.deviceInfo.mac ?? "无数据")

metricCard = Card([metricHeader, metricValue])
metricHeader = CardHeader("CPU利用率")
metricValue = TextContent(data.metricData.metricValue + data.metricData.unit, "large-heavy")

timeCard = Card([timeHeader, timeDescriptions])
timeHeader = CardHeader("查询时间范围")
timeDescriptions = Descriptions([rangeField, startField, endField], columns=1)
rangeField = DescField("时间范围", data.queryCondition.timeRange ?? "无数据")
startField = DescField("开始时间", @FormatDate(data.queryCondition.startTime, "dateTime"))
endField = DescField("结束时间", @FormatDate(data.queryCondition.endTime, "dateTime"))
````

### 输入 dataModel

````json
{
  "queryCondition": {
    "startTime": 1786527542000,
    "endTime": 1786529342000,
    "timeRange": "近30分钟"
  },
  "title": "设备CPU利用率查询结果",
  "deviceInfo": {
    "neName": "O05-17F-Leaf2-Simu6-0001",
    "neIp": "197.197.5.1",
    "mac": "97-55-46-F9-00-01"
  },
  "metricData": {
    "unit": "%",
    "aggregationType": "平均值",
    "metricName": "CPU利用率",
    "metricValue": 70
  }
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=syntax-unexpected-token
severity=ERROR
source=syntax
statementId=infoDescriptions
component=<null>
path=<null>
line=8
column=72
retryable=false
message=Unexpected token EQUALS
hint=<null>

issue[1]
code=syntax-unexpected-token
severity=ERROR
source=syntax
statementId=timeDescriptions
component=<null>
path=<null>
line=19
column=76
retryable=false
message=Unexpected token EQUALS
hint=<null>
````

<details>
<summary>原始校验结果（含级联诊断）</summary>

````text
status=INVALID

issue[0]
code=syntax-unexpected-token
severity=ERROR
source=syntax
statementId=infoDescriptions
component=<null>
path=<null>
line=8
column=72
retryable=false
message=Unexpected token EQUALS
hint=<null>

issue[1]
code=syntax-unexpected-token
severity=ERROR
source=syntax
statementId=timeDescriptions
component=<null>
path=<null>
line=19
column=76
retryable=false
message=Unexpected token EQUALS
hint=<null>

issue[2]
code=unresolved-ref
severity=ERROR
source=reference
statementId=infoDescriptions
component=<null>
path=<null>
line=8
column=1
retryable=false
message=unresolved reference "columns"
hint=define a statement named "columns" earlier in the document

issue[3]
code=unresolved-ref
severity=ERROR
source=reference
statementId=timeDescriptions
component=<null>
path=<null>
line=19
column=1
retryable=false
message=unresolved reference "columns"
hint=define a statement named "columns" earlier in the document

issue[4]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=infoDescriptions
component=Descriptions
path=/columns
line=8
column=1
retryable=false
message=Descriptions.columns expects number, but received null.
hint=Use a value compatible with number.

issue[5]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=infoDescriptions
component=Descriptions
path=/border
line=8
column=1
retryable=false
message=Descriptions.border expects boolean, but received number.
hint=Use a value compatible with boolean.

issue[6]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=timeDescriptions
component=Descriptions
path=/columns
line=19
column=1
retryable=false
message=Descriptions.columns expects number, but received null.
hint=Use a value compatible with number.

issue[7]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=timeDescriptions
component=Descriptions
path=/border
line=19
column=1
retryable=false
message=Descriptions.border expects boolean, but received number.
hint=Use a value compatible with boolean.
````

</details>

## 35. rw-302-Fabric-default1

- 来源: `3.json` 第 15 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([summaryCard, roleCard, vendorCard, stateCard, deviceTableCard], "column", "m")

summaryCard = Card([summaryHeader, summaryContent])
summaryHeader = CardHeader("Fabric 设备统计", "Fabric: " + data.fabricName)
summaryContent = Stack([totalDevices], "row", "m", "start", "start")
totalDevices = Card([TextContent("设备总数", "small"), TextContent("" + data.total, "large-heavy")])

roleCard = Card([roleHeader, roleChart])
roleHeader = CardHeader("角色分布", "")
roleLabels = @Each(data.devices, "d", d.role)
roleChart = PieChart(roleLabels, @Each(data.devices, "d", 1), "donut")

vendorCard = Card([vendorHeader, vendorChart])
vendorHeader = CardHeader("厂商分布", "")
vendorLabels = @Each(data.devices, "d", d.vendor)
vendorChart = PieChart(vendorLabels, @Each(data.devices, "d", 1), "donut")

stateCard = Card([stateHeader, stateChart])
stateHeader = CardHeader("状态分布", "")
stateLabels = @Each(data.devices, "d", d.state)
stateChart = PieChart(stateLabels, @Each(data.devices, "d", 1), "donut")

deviceTableCard = Card([deviceTableHeader, deviceTable])
deviceTableHeader = CardHeader("设备列表", "")
deviceTable = Table([nameCol, roleCol, vendorCol, typeCol, ipCol, stateCol], data.devices)

nameCol = Col("设备名称", "name")
roleCol = Col("角色", "role", {cell: @Render("v", Tag(v, "info"))})
vendorCol = Col("厂商", "vendor")
typeCol = Col("型号", "type")
ipCol = Col("IP 地址", "ip")
stateCol = Col("状态", "state", {cell: @Render("v", @Switch(v, {"在线": Tag("在线", "success"), "离线": Tag("离线", "danger")}, Tag(v, "neutral")))})
````

### 输入 dataModel

````json
{
  "total": 10,
  "devices": [
    {
      "role": "ServerLeaf",
      "vendor": "Huawei",
      "ip": "197.197.5.1",
      "name": "O05-17F-Leaf2-Simu6-0001",
      "state": "在线",
      "type": "CE9866-128DQ"
    },
    {
      "role": "ServerLeaf",
      "vendor": "Huawei",
      "ip": "197.197.5.2",
      "name": "O05-17F-Leaf2-Simu6-0002",
      "state": "在线",
      "type": "CE9866-128DQ"
    },
    {
      "role": "ServerLeaf",
      "vendor": "Huawei",
      "ip": "197.197.5.3",
      "name": "O05-17F-Leaf2-Simu6-0003",
      "state": "在线",
      "type": "CE9866-128DQ"
    },
    {
      "role": "ServerLeaf",
      "vendor": "Huawei",
      "ip": "197.197.5.4",
      "name": "O05-17F-Leaf2-Simu6-0004",
      "state": "在线",
      "type": "CE9866-128DQ"
    },
    {
      "role": "ServerLeaf",
      "vendor": "Huawei",
      "ip": "197.197.5.5",
      "name": "O05-17F-Leaf2-Simu6-0005",
      "state": "在线",
      "type": "CE9866-128DQ"
    },
    {
      "role": "ServerLeaf",
      "vendor": "Huawei",
      "ip": "197.197.5.6",
      "name": "O05-17F-Leaf2-Simu6-0006",
      "state": "在线",
      "type": "CE9866-128DQ"
    },
    {
      "role": "ServerLeaf",
      "vendor": "Huawei",
      "ip": "197.197.5.7",
      "name": "O05-17F-Leaf2-Simu6-0007",
      "state": "在线",
      "type": "CE9866-128DQ"
    },
    {
      "role": "ServerLeaf",
      "vendor": "Huawei",
      "ip": "197.197.5.8",
      "name": "O05-17F-Leaf2-Simu6-0008",
      "state": "在线",
      "type": "CE9866-128DQ"
    },
    {
      "role": "ServerLeaf",
      "vendor": "Huawei",
      "ip": "197.197.5.9",
      "name": "O05-17F-Leaf2-Simu6-0009",
      "state": "在线",
      "type": "CE9866-128DQ"
    },
    {
      "role": "ServerLeaf",
      "vendor": "Huawei",
      "ip": "197.197.5.10",
      "name": "O05-17F-Leaf2-Simu6-0010",
      "state": "在线",
      "type": "CE9866-128DQ"
    }
  ],
  "fabricName": "default1"
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 36. rw-303-O05-17F-Leaf2-Simu6-0001-CPU

- 来源: `4.json` 第 1 条
- 状态: `INVALID`
- 原始 Issue 数: 2
- 可操作 Issue 数: 2

- `error_detail.txt` 标注: CPU利用率没有渲染出来。
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([infoCard, cpuGaugeCard])

infoCard = Card([infoHeader, infoDescriptions])
infoHeader = CardHeader(data.title, null)
infoDescriptions = Descriptions([ipField, nameField, rangeField], "设备信息")
ipField = DescField("设备 IP", data.data.device_ip)
nameField = DescField("设备名称", data.data.device_name)
rangeField = DescField("时间范围", data.data.time_range)

cpuGaugeCard = Card([cpuHeader, cpuGauge])
cpuHeader = CardHeader("CPU 利用率", "当前负载情况")
cpuGauge = GaugeChart([cpuReading], 0, 100)
cpuReading = {name: "CPU", value: data.data.cpu_usage}
````

### 输入 dataModel

````json
{
  "data": {
    "device_ip": "197.197.5.1",
    "device_name": "O05-17F-Leaf2-Simu6-0001",
    "unit": "%",
    "time_range": "近30分钟（最新值)",
    "cpu_usage": 70
  },
  "title": "设备 CPU 利用率查询结果"
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=unknown-component
severity=ERROR
source=contract
statementId=cpuGauge
component=GaugeChart
path=
line=-1
column=-1
retryable=false
message=Unknown component "GaugeChart" — not found in catalog or builtins
hint=<null>

issue[1]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=infoHeader
component=CardHeader
path=/subtitle
line=4
column=1
retryable=false
message=CardHeader.subtitle expects string, but received null.
hint=Use a value compatible with string.
````

## 37. rw-313-serverleaf1-IP-Server

- 来源: `4.json` 第 2 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: （无说明）
- 标注覆盖结果: `UNLABELED`

### 输入 DSL

````openui
root = Stack([infoCard, chartRow, timeInfo], "column", "m", "start", "start", false)
infoCard = Card([infoHeader, infoDesc], "card", "column", "m", "start", "start", false)
infoHeader = CardHeader("设备基本信息", "serverleaf1")
infoDesc = Descriptions([nameField, ipField, statusField, roleField, typeField, vendorField, fabricField, esnField, macField, versionField, categoryField], "设备详情", 2, true)
nameField = DescField("设备名称", data.deviceInfo.neName)
ipField = DescField("IP地址", data.deviceInfo.neIp)
statusField = DescField("运行状态", data.deviceInfo.neState)
roleField = DescField("角色", data.deviceInfo.neRole)
typeField = DescField("设备款型", data.deviceInfo.neType)
vendorField = DescField("厂商", data.deviceInfo.neVendor)
fabricField = DescField("所属Fabric", data.deviceInfo.fabricName)
esnField = DescField("ESN", data.deviceInfo.neEsn)
macField = DescField("MAC地址", data.deviceInfo.neMac)
versionField = DescField("版本", data.deviceInfo.neVersion)
categoryField = DescField("类别", data.deviceInfo.neCategory)
chartRow = Stack([pieCard, barCard], "row", "m", "stretch", "start", false)
pieCard = Card([pieHeader, pieChart], "card", "column", "s", "start", "start", false)
pieHeader = CardHeader("异常日志级别分布", "总计: " + data.logSummary.totalLogs + " 条")
pieChart = PieChart(@Each(data.logSummary.severityStats, "item", item.name), @Each(data.logSummary.severityStats, "item", item.count), "donut")
barCard = Card([barHeader, barChart], "card", "column", "s", "start", "start", false)
barHeader = CardHeader("异常类型 TOP10", "按日志摘要统计")
barChart = HorizontalBarChart(@Each(data.top10Briefs, "item", item.name), [Series("数量", @Each(data.top10Briefs, "item", item.count))], "grouped", "数量", "日志摘要")
timeInfo = TextContent("数据说明：查询时间为 " + data.logSummary.timeRange, "small")
````

### 输入 dataModel

````json
{
  "logSummary": {
    "totalLogs": 394,
    "severityStats": [
      {
        "color": "#F5A623",
        "name": "Warning",
        "count": 351
      },
      {
        "color": "#E04B4B",
        "name": "Error",
        "count": 25
      },
      {
        "color": "#7B68EE",
        "name": "Serious",
        "count": 18
      }
    ],
    "timeRange": "2026-08-12 06:38:20 ~ 18:38:20 (近12小时)"
  },
  "top10Briefs": [
    {
      "name": "ospfIfConfigError",
      "count": 300
    },
    {
      "name": "ifconfigerror",
      "count": 25
    },
    {
      "name": "OSPFV3_CONFLICT_ROUTERID_INTF",
      "count": 17
    },
    {
      "name": "linkDown_clear",
      "count": 8
    },
    {
      "name": "hwLldpInterfaceRemTablesChange",
      "count": 5
    },
    {
      "name": "hwLldpInterfaceNeighborChange",
      "count": 5
    },
    {
      "name": "hwStormSuppressAlarm_clear",
      "count": 3
    },
    {
      "name": "hwStormSuppressAlarm_active",
      "count": 3
    },
    {
      "name": "hwIfMonitorOutputRateRising_active",
      "count": 3
    },
    {
      "name": "hwMstpiPortStateForwarding",
      "count": 3
    }
  ],
  "deviceInfo": {
    "neState": "在线",
    "neEsn": "2102352CHS10K1000023",
    "neVendor": "华为",
    "neMac": "70-C7-F2-7D-95-41",
    "neRole": "Server Leaf",
    "neName": "serverleaf1",
    "neIp": "10.136.193.107",
    "neType": "CE6857-48S6CQ-EI",
    "neVersion": "8.25 V200R025C00SPC500",
    "neCategory": "交换机",
    "fabricName": "pod11"
  }
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 38. rw-321-default1-ServerLeaf

- 来源: `4.json` 第 3 条
- 状态: `INVALID`
- 原始 Issue 数: 3
- 可操作 Issue 数: 2

- `error_detail.txt` 标注: 渲染不出来
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root
根 ```

。

Root
````

### 输入 dataModel

````json
{
  "total": 10,
  "data": [
    {
      "name": "CE9866-128DQ",
      "value": 10
    }
  ],
  "scope": "Fabric: default1，角色: ServerLeaf",
  "title": "default1域 ServerLeaf 设备款型分布"
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=syntax-missing-assignment
severity=ERROR
source=syntax
statementId=root
component=<null>
path=<null>
line=1
column=1
retryable=false
message=Statement 'root' is missing '='
hint=<null>

issue[1]
code=syntax-missing-assignment
severity=ERROR
source=syntax
statementId=Root
component=<null>
path=<null>
line=6
column=1
retryable=false
message=Statement 'Root' is missing '='
hint=<null>
````

<details>
<summary>原始校验结果（含级联诊断）</summary>

````text
status=INVALID

issue[0]
code=syntax-missing-assignment
severity=ERROR
source=syntax
statementId=root
component=<null>
path=<null>
line=1
column=1
retryable=false
message=Statement 'root' is missing '='
hint=<null>

issue[1]
code=syntax-missing-assignment
severity=ERROR
source=syntax
statementId=Root
component=<null>
path=<null>
line=6
column=1
retryable=false
message=Statement 'Root' is missing '='
hint=<null>

issue[2]
code=root-missing
severity=ERROR
source=root
statementId=<null>
component=<null>
path=<null>
line=-1
column=-1
retryable=false
message=no renderable root component (no statements produced a root component)
hint=the program must contain at least one renderable component as its entry
````

</details>

## 39. mae-preva-check-002

- 来源: `4.json` 第 4 条
- 状态: `INVALID`
- 原始 Issue 数: 6
- 可操作 Issue 数: 6

- `error_detail.txt` 标注: 审核结果中，Pass没有显示未通过，但是表格中显示未通过。
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([summaryCard, detailsCard, downloadCard], "column", "m")

summaryCard = Card([summaryHeader, summaryBody], "column", "s")
summaryHeader = CardHeader("金桥区参数核查结果", "核查时间: " + data.data.checkTime)
summaryBody = Stack([statusTag, regionTag], "row", "m", "center", "start")
statusTag = Tag(data.data.checkResult, data.data.checkResult == "PASS" ? "success" : "danger")
regionTag = Tag("区域: " + data.data.regionName, "info")

detailsCard = Card([detailsHeader, detailsTable], "column", "s")
detailsHeader = CardHeader("核查详情", "共 " + @Count(data.data.details) + " 项参数")
detailsTable = Table([paramCol, currentCol, expectedCol, statusCol], data.data.details)
paramCol = Col("参数名称", "paramName")
currentCol = Col("当前值", "currentValue")
expectedCol = Col("期望值", "expectedValue")
statusCol = Col("状态", "status", {cell: @Render("v", @Switch(v, {"PASS": Tag("通过", "success"), "FAIL": Tag("失败", "danger")}, Tag(v, "neutral")))})

downloadCard = Card([downloadBtn], "column", "s")
downloadBtn = Button("下载核查报告", Action([@OpenUrl(data.data.resultURL)]), "primary")
````

### 输入 dataModel

````json
{
  "code": "0",
  "message": "success",
  "data": {
    "intentInstanceId": 301,
    "regionId": 100,
    "regionName": "金桥区",
    "checkTime": "2026-07-14 02:00:00",
    "checkResult": "PASS",
    "details": [
      {
        "paramName": "concertStartTime",
        "currentValue": "2026-07-14 19:00:00",
        "expectedValue": "2026-07-14 19:00:00",
        "status": "PASS"
      },
      {
        "paramName": "concertEndTime",
        "currentValue": "2026-07-14 22:00:00",
        "expectedValue": "2026-07-14 22:00:00",
        "status": "PASS"
      },
      {
        "paramName": "occupancy",
        "currentValue": 50000,
        "expectedValue": 50000,
        "status": "PASS"
      }
    ],
    "resultURL": "/files/intent_301_result.zip"
  }
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=summaryCard
component=Card
path=/variant
line=3
column=1
retryable=false
message=Card.variant expects "card" | "clear" | "sunk", but received string.
hint=Use a value compatible with "card" | "clear" | "sunk".

issue[1]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=summaryCard
component=Card
path=/direction
line=3
column=1
retryable=false
message=Card.direction expects "column" | "row", but received string.
hint=Use a value compatible with "column" | "row".

issue[2]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=detailsCard
component=Card
path=/variant
line=9
column=1
retryable=false
message=Card.variant expects "card" | "clear" | "sunk", but received string.
hint=Use a value compatible with "card" | "clear" | "sunk".

issue[3]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=detailsCard
component=Card
path=/direction
line=9
column=1
retryable=false
message=Card.direction expects "column" | "row", but received string.
hint=Use a value compatible with "column" | "row".

issue[4]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=downloadCard
component=Card
path=/variant
line=17
column=1
retryable=false
message=Card.variant expects "card" | "clear" | "sunk", but received string.
hint=Use a value compatible with "card" | "clear" | "sunk".

issue[5]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=downloadCard
component=Card
path=/direction
line=17
column=1
retryable=false
message=Card.direction expects "column" | "row", but received string.
hint=Use a value compatible with "column" | "row".
````

## 40. mae-valuearea-activation-004

- 来源: `4.json` 第 5 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([statusCard, capacityCard, detailCard], "column", "m")

statusCard = Card([statusHeader, statusContent], "card", "column", "m", "start", "start")
statusHeader = CardHeader("金桥区价值区域评估", "开通状态查询")
statusContent = Stack([statusRow, licenseRow], "column", "s")
statusRow = Stack([statusLabel, statusValue], "row", "m", "center")
statusLabel = TextContent("是否可以开通", "default")
statusValue = Tag("可以开通", "success")
licenseRow = Stack([licenseLabel, licenseValue], "row", "m", "center")
licenseLabel = TextContent("是否拥有License", "default")
licenseValue = Tag("是", "success")

capacityCard = Card([capacityHeader, capacityGrid], "card", "column", "m", "start", "start")
capacityHeader = CardHeader("容量概览", "当前资源使用情况")
capacityGrid = Stack([totalCapCard, availCapCard, maxCellCard, availCellCard], "row", "m", "stretch", "start", true)
totalCapCard = Card([totalCapLabel, totalCapValue], "card", "column", "none", "center", "center")
totalCapLabel = TextContent("总License容量", "small")
totalCapValue = TextContent("" + data.data.totalLicenseCapacity, "large-heavy")
availCapCard = Card([availCapLabel, availCapValue], "card", "column", "none", "center", "center")
availCapLabel = TextContent("可用License容量", "small")
availCapValue = TextContent("" + data.data.avaliableLicenseCapacity, "large-heavy")
maxCellCard = Card([maxCellLabel, maxCellValue], "card", "column", "none", "center", "center")
maxCellLabel = TextContent("最大激活小区容量", "small")
maxCellValue = TextContent("" + data.data.maxActivateCellCapacity, "large-heavy")
availCellCard = Card([availCellLabel, availCellValue], "card", "column", "none", "center", "center")
availCellLabel = TextContent("可用激活小区容量", "small")
availCellValue = TextContent("" + data.data.avaliableActivateCellCapaticy, "large-heavy")

detailCard = Card([detailHeader, detailList], "card", "column", "m", "start", "start")
detailHeader = CardHeader("详细信息", "区域ID与具体参数")
detailList = Descriptions([regionIdField, totalField, availField, maxField, availCellField], "区域详情", 1, true)
regionIdField = DescField("区域ID", data.data.regionId)
totalField = DescField("总License容量", data.data.totalLicenseCapacity)
availField = DescField("可用License容量", data.data.avaliableLicenseCapacity)
maxField = DescField("最大激活小区容量", data.data.maxActivateCellCapacity)
availCellField = DescField("可用激活小区容量", data.data.avaliableActivateCellCapaticy)
````

### 输入 dataModel

````json
{
  "code": "0",
  "message": "success",
  "data": {
    "regionId": 100,
    "canActivate": true,
    "hasLicense": true,
    "totalLicenseCapacity": 10000,
    "avaliableLicenseCapacity": 5000,
    "maxActivateCellCapacity": 10000,
    "avaliableActivateCellCapaticy": 5000
  }
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 41. mae-valuearea-activation-006

- 来源: `4.json` 第 6 条
- 状态: `INVALID`
- 原始 Issue 数: 2
- 可操作 Issue 数: 2

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([statusCard, capacityCard, licenseCard], "column", "m")

statusCard = Card([statusHeader, statusContent], "card", "column", "m", "start", "start")
statusHeader = CardHeader("青浦区价值区域评估", "开通状态")
statusContent = Stack([statusRow], "row", "m", "center", "start")
statusRow = Stack([statusLabel, statusTag], "row", "s", "center", "start")
statusLabel = TextContent("当前状态", "default")
statusTag = Tag("可以开通", "success")

capacityCard = Card([capacityHeader, capacityBody], "card", "column", "m", "start", "start")
capacityHeader = CardHeader("容量信息", "当前可用资源")
capacityBody = Descriptions([
  DescField("区域ID", data.data.regionId),
  DescField("最大激活小区容量", data.data.maxActivateCellCapacity),
  DescField("可用激活小区容量", data.data.avaliableActivateCellCapaticy)
], null, 1, true)

licenseCard = Card([licenseHeader, licenseBody], "card", "column", "m", "start", "start")
licenseHeader = CardHeader("License 信息", "许可证使用情况")
licenseBody = Descriptions([
  DescField("是否持有 License", data.data.hasLicense ? "是" : "否"),
  DescField("总 License 容量", data.data.totalLicenseCapacity),
  DescField("可用 License 容量", data.data.avaliableLicenseCapacity)
], null, 1, true)
````

### 输入 dataModel

````json
{
  "code": "0",
  "message": "success",
  "data": {
    "regionId": 200,
    "canActivate": true,
    "hasLicense": true,
    "totalLicenseCapacity": 10000,
    "avaliableLicenseCapacity": 4000,
    "maxActivateCellCapacity": 8000,
    "avaliableActivateCellCapaticy": 3000
  }
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=capacityBody
component=Descriptions
path=/title
line=12
column=1
retryable=false
message=Descriptions.title expects string, but received null.
hint=Use a value compatible with string.

issue[1]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=licenseBody
component=Descriptions
path=/title
line=20
column=1
retryable=false
message=Descriptions.title expects string, but received null.
hint=Use a value compatible with string.
````

## 42. tc-010-campus-client-apis-6

- 来源: `4.json` 第 7 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([kpiRow, chartCard, tableCard])

kpiRow = Stack([totalCountCard, topReasonCard], "row", "m", "stretch", "start", false)
totalCountCard = Card([TextContent("总离线次数", "small"), TextContent("" + @Sum(data.data.num), "large-heavy")])
topReasonItem = @First(@Sort(data.data, "num", "desc"))
topReasonCard = Card([TextContent("主要离线原因", "small"), TextContent(topReasonItem.errMsg, "large-heavy")])

chartCard = Card([chartHeader, chart])
chartHeader = CardHeader("离线原因分布", "按数量排序")
chartLabels = @Each(@Sort(data.data, "num", "desc"), "item", item.errMsg)
chartValues = @Each(@Sort(data.data, "num", "desc"), "item", item.num)
chart = HorizontalBarChart(chartLabels, [Series("次数", chartValues)], "grouped", "离线原因", "次数")

tableCard = Card([tableHeader, table])
tableHeader = CardHeader("离线原因详情", "错误码与具体原因统计")
table = Table([errCodeCol, errMsgCol, numCol], data.data)
errCodeCol = Col("错误码", "errCode")
errMsgCol = Col("错误原因", "errMsg")
numCol = Col("数量", "num", {cell: @Render("v", TextContent(@FormatNumber(v)))})
````

### 输入 dataModel

````json
{
  "resultCode": 0,
  "errorDes": "Operation successful.",
  "errorReson": "Success",
  "errorDetail": "The offline statistics data was retrieved successfully.",
  "errorAdvice": "None",
  "data": [
    {
      "errCode": "0",
      "errMsg": "正常下线",
      "num": 3420
    },
    {
      "errCode": "201001",
      "errMsg": "认证失败",
      "num": 125
    },
    {
      "errCode": "201002",
      "errMsg": "DHCP获取超时",
      "num": 45
    },
    {
      "errCode": "201003",
      "errMsg": "信号强度弱断开",
      "num": 89
    },
    {
      "errCode": "201004",
      "errMsg": "IP地址冲突",
      "num": 12
    },
    {
      "errCode": "201005",
      "errMsg": "认证服务器无响应",
      "num": 8
    },
    {
      "errCode": "201006",
      "errMsg": "用户手动下线",
      "num": 567
    },
    {
      "errCode": "201007",
      "errMsg": "AP设备重启",
      "num": 30
    }
  ]
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 43. tc-031-throughput-quality

- 来源: `4.json` 第 8 条
- 状态: `INVALID`
- 原始 Issue 数: 19
- 可操作 Issue 数: 2

- `error_detail.txt` 标注: 区域排名详情数据丢失。
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([header, summaryCard, kpiRow, trendCard, rankCard, subCard])

header = CardHeader("区域吞吐质量指标", "综合健康分: " + data.healthScore)

summaryCard = TextContent(data.summary)

kpiRow = Stack([rootValueCard, rankCardSmall], "row", "m", "stretch", "start", false)
rootValueCard = Card([TextContent("当前吞吐量", "small"), TextContent("" + data.data[0].value.rootValue + " Mbps", "large-heavy")])
rankCardSmall = Card([TextContent("区域排名", "small"), TextContent("第 " + data.data[0].value.rank + " 名", "large-heavy")])

trendCard = Card([trendHeader, trendChart])
trendHeader = CardHeader("吞吐量趋势", "Baseline vs Essential")
trendLabels = @Each(data.data[0].value.baseline, "item", @FormatDate(item.timestamp, "dateTime"))
baselineSeries = Series("Baseline", @Each(data.data[0].value.baseline, "item", item.value))
essentialSeries = Series("Essential", @Each(data.data[0].value.essential, "item", item.value))
trendChart = LineChart(trendLabels, [baselineSeries, essentialSeries], "smooth", "时间", "Mbps")

rankCard = Card([rankHeader, rankTable])
rankHeader = CardHeader("区域排名详情", "按吞吐量排序")
rankTable = Table([Col("区域ID", "regionId"), Col("区域名称", "regionName"), Col("吞吐量 (Mbps)", "value", {cell: @Render("v", TextContent("" + v + " Mbps")})], data.data[0].value.rankList)

subCard = Card([subHeader, subTable])
subHeader = CardHeader("细分指标", "峰值、平均及最小吞吐量")
subTable = Table([Col("指标", "key"), Col("数值", "value")], data.data[0].value.subValues)
````

### 输入 dataModel

````json
{
  "resultCode": 200,
  "errorDes": "操作成功",
  "errorReson": "请求处理完成",
  "errorDetail": "无异常",
  "errorAdvice": "无需操作",
  "detailUrl": "/rest/campuswlanqualityservice/v1/intent/detail/throughput/20240101",
  "healthScore": 85.6,
  "data": [
    {
      "key": "throughput",
      "value": {
        "rootValue": 120.5,
        "rank": 1,
        "rankList": [
          {
            "regionId": "RGN-001",
            "regionName": "主教学楼",
            "value": 150.3
          },
          {
            "regionId": "RGN-002",
            "regionName": "实验楼",
            "value": 135.7
          },
          {
            "regionId": "RGN-003",
            "regionName": "图书馆",
            "value": 118.2
          }
        ],
        "subValues": [
          {
            "key": "peakThroughput",
            "value": "180.4 Mbps"
          },
          {
            "key": "averageThroughput",
            "value": "132.1 Mbps"
          },
          {
            "key": "minThroughput",
            "value": "95.8 Mbps"
          }
        ],
        "baseline": [
          {
            "timestamp": 1709116800,
            "value": 115.2
          },
          {
            "timestamp": 1709203200,
            "value": 118.7
          },
          {
            "timestamp": 1709289600,
            "value": 122.4
          }
        ],
        "essential": [
          {
            "timestamp": 1709116800,
            "value": 112.5
          },
          {
            "timestamp": 1709203200,
            "value": 119.3
          },
          {
            "timestamp": 1709289600,
            "value": 125.8
          }
        ]
      }
    }
  ],
  "charts": [
    {
      "en": "{\"title\":{\"text\":\"Throughput Trend\"},\"xAxis\":{\"type\":\"time\"},\"yAxis\":{\"type\":\"value\"},\"series\":[{\"data\":[[1709116800000,115.2],[1709203200000,118.7],[1709289600000,122.4]],\"type\":\"line\"}]}",
      "zh": "{\"title\":{\"text\":\"吞吐量趋势\"},\"xAxis\":{\"type\":\"time\"},\"yAxis\":{\"type\":\"value\"},\"series\":[{\"data\":[[1709116800000,115.2],[1709203200000,118.7],[1709289600000,122.4]],\"type\":\"line\"}]}"
    }
  ],
  "summary": "区域吞吐量整体表现良好，主教学楼区域吞吐量最高达150.3 Mbps，建议持续监控实验楼区域网络负载。"
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=syntax-unexpected-token
severity=ERROR
source=syntax
statementId=rankTable
component=<null>
path=<null>
line=20
column=148
retryable=false
message=Unexpected token R_BRACE
hint=<null>

issue[1]
code=unresolved-ref
severity=ERROR
source=reference
statementId=root
component=<null>
path=<null>
line=1
column=1
retryable=false
message=unresolved reference "subCard"
hint=define a statement named "subCard" earlier in the document
````

<details>
<summary>原始校验结果（含级联诊断）</summary>

````text
status=INVALID

issue[0]
code=syntax-unexpected-token
severity=ERROR
source=syntax
statementId=rankTable
component=<null>
path=<null>
line=20
column=148
retryable=false
message=Unexpected token R_BRACE
hint=<null>

issue[1]
code=syntax-unexpected-token
severity=ERROR
source=syntax
statementId=rankTable
component=<null>
path=<null>
line=20
column=151
retryable=false
message=Unexpected token COMMA
hint=<null>

issue[2]
code=syntax-unexpected-token
severity=ERROR
source=syntax
statementId=rankTable
component=<null>
path=<null>
line=20
column=157
retryable=false
message=Unexpected token DOT
hint=<null>

issue[3]
code=syntax-unexpected-token
severity=ERROR
source=syntax
statementId=rankTable
component=<null>
path=<null>
line=23
column=11
retryable=false
message=Unexpected token EQUALS
hint=<null>

issue[4]
code=syntax-unclosed-bracket
severity=ERROR
source=syntax
statementId=rankTable
component=<null>
path=<null>
line=23
column=23
retryable=false
message=Unclosed '('
hint=<null>

issue[5]
code=syntax-unexpected-token
severity=ERROR
source=syntax
statementId=rankTable
component=<null>
path=<null>
line=23
column=45
retryable=false
message=Unexpected token R_PAREN
hint=<null>

issue[6]
code=syntax-unexpected-token
severity=ERROR
source=syntax
statementId=rankTable
component=<null>
path=<null>
line=24
column=10
retryable=false
message=Unexpected token EQUALS
hint=<null>

issue[7]
code=syntax-unclosed-bracket
severity=ERROR
source=syntax
statementId=rankTable
component=<null>
path=<null>
line=24
column=17
retryable=false
message=Unclosed '('
hint=<null>

issue[8]
code=syntax-unexpected-token
severity=ERROR
source=syntax
statementId=rankTable
component=<null>
path=<null>
line=24
column=62
retryable=false
message=Unexpected token DOT
hint=<null>

issue[9]
code=syntax-unclosed-bracket
severity=ERROR
source=syntax
statementId=rankTable
component=<null>
path=<null>
line=20
column=99
retryable=false
message=Unclosed '{'
hint=<null>

issue[10]
code=syntax-unclosed-bracket
severity=ERROR
source=syntax
statementId=rankTable
component=<null>
path=<null>
line=20
column=75
retryable=false
message=Unclosed '('
hint=<null>

issue[11]
code=syntax-unclosed-bracket
severity=ERROR
source=syntax
statementId=rankTable
component=<null>
path=<null>
line=20
column=19
retryable=false
message=Unclosed '['
hint=<null>

issue[12]
code=syntax-unclosed-bracket
severity=ERROR
source=syntax
statementId=rankTable
component=<null>
path=<null>
line=20
column=18
retryable=false
message=Unclosed '('
hint=<null>

issue[13]
code=missing-required
severity=ERROR
source=contract
statementId=rankTable
component=Table
path=/rows
line=-1
column=-1
retryable=false
message=missing required field "rows"
hint=<null>

issue[14]
code=unresolved-ref
severity=ERROR
source=reference
statementId=rankTable
component=<null>
path=<null>
line=20
column=1
retryable=false
message=unresolved reference "subCard"
hint=define a statement named "subCard" earlier in the document

issue[15]
code=unresolved-ref
severity=ERROR
source=reference
statementId=rankTable
component=<null>
path=<null>
line=20
column=1
retryable=false
message=unresolved reference "subHeader"
hint=define a statement named "subHeader" earlier in the document

issue[16]
code=unresolved-ref
severity=ERROR
source=reference
statementId=rankTable
component=<null>
path=<null>
line=20
column=1
retryable=false
message=unresolved reference "subTable"
hint=define a statement named "subTable" earlier in the document

issue[17]
code=unresolved-ref
severity=ERROR
source=reference
statementId=root
component=<null>
path=<null>
line=1
column=1
retryable=false
message=unresolved reference "subCard"
hint=define a statement named "subCard" earlier in the document

issue[18]
code=builtin-argument-type-mismatch
severity=ERROR
source=type
statementId=rankTable
component=<null>
path=/args/1
line=20
column=1
retryable=false
message=@Render argument 1 expects binder string literal, but received Component<TextContent>.
hint=Use a value compatible with binder string literal.
````

</details>

## 44. tc-035-device-info-4

- 来源: `4.json` 第 9 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([header, summaryRow, chartCard, tableCard], "column", "m")

header = CardHeader("设备分布概览", data.deployScene)

summaryRow = Stack([totalCard, typesCard], "row", "m", "stretch")
totalCard = Card([TextContent("设备总数", "small"), TextContent("" + data.distributionTotal, "large-heavy")])
typesCard = Card([TextContent("分布项数", "small"), TextContent("" + @Count(data.distributionData), "large-heavy")])

chartCard = Card([chartHeader, chart])
chartHeader = CardHeader("分布详情", "按型号、状态、角色统计")
chartLabels = @Each(data.distributionData, "item", item.type)
chartValues = @Each(data.distributionData, "item", item.count)
chart = HorizontalBarChart(chartLabels, [Series("数量", chartValues)], "grouped", "类型", "数量")

tableCard = Card([tableHeader, table])
tableHeader = CardHeader("详细列表", "")
table = Table([typeCol, countCol], data.distributionData)
typeCol = Col("类型", "type")
countCol = Col("数量", "count", {cell: @Render("v", TextContent(@FormatNumber(v)))})
````

### 输入 dataModel

````json
{
  "status_code": 200,
  "error_code": 0,
  "error_msg": "Successful.",
  "distributionTotal": 258,
  "distributionData": [
    {
      "count": 85,
      "type": "华为 S5735-L24P4S-A"
    },
    {
      "count": 60,
      "type": "思科 Catalyst 9280X"
    },
    {
      "count": 45,
      "type": "华三 S6850-64Q-G4C"
    },
    {
      "count": 32,
      "type": "状态-在线"
    },
    {
      "count": 30,
      "type": "状态-离线"
    },
    {
      "count": 7,
      "type": "角色-核心交换机"
    },
    {
      "count": 9,
      "type": "角色-接入交换机"
    }
  ],
  "deployScene": "数据中心网络部署"
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 45. tc-037-device-info-6

- 来源: `4.json` 第 10 条
- 状态: `VALID`
- 原始 Issue 数: 0
- 可操作 Issue 数: 0

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([statusCard, countCard, tableCard], "column", "m")

statusCard = Card([statusHeader, statusContent], "card")
statusHeader = CardHeader("查询结果", "站点或区域列表")
statusContent = Stack([statusCodeRow, errorMsgRow], "column", "s")
statusCodeRow = Stack([statusLabel, statusValue], "row", "m", "center")
statusLabel = TextContent("状态码:", "small")
statusValue = Tag("" + data.status_code, "success")
errorMsgRow = Stack([errorLabel, errorValue], "row", "m", "center")
errorLabel = TextContent("消息:", "small")
errorValue = TextContent(data.error_msg, "default")

countCard = Card([countContent], "card")
countContent = Stack([countLabel, countValue], "row", "m", "center")
countLabel = TextContent("总数:", "small")
countValue = TextContent("" + data.page_count, "large-heavy")

tableCard = Card([tableHeader, positionTable], "card")
tableHeader = CardHeader("区域详情", "共 " + data.page_count + " 条记录")
positionTable = Table([idCol, nameCol, typeCol, parentCol, floorCol, pathCol, remarkCol], data.data)

idCol = Col("ID", "id", {cell: @Render("v", TextContent(v, "small"))})
nameCol = Col("名称", "name", {cell: @Render("v", TextContent(v, "default"))})
typeCol = Col("类型", "type", {cell: @Render("v", @Switch(v, {"site": Tag("站点", "info"), "region": Tag("区域", "success"), "building": Tag("楼宇", "warning"), "floor": Tag("楼层", "neutral")}, Tag(v, "neutral")))})
parentCol = Col("父节点", "parent_name", {cell: @Render("v", TextContent(v ?? "无", "small"))})
floorCol = Col("楼层", "floor_no", {cell: @Render("v", TextContent(v ?? "无", "small"))})
pathCol = Col("全路径", "fullPath", {cell: @Render("v", TextContent(v, "small"))})
remarkCol = Col("备注", "remark", {cell: @Render("v", TextContent(v ?? "无", "small"))})
````

### 输入 dataModel

````json
{
  "status_code": 200,
  "error_code": 0,
  "error_msg": "Successful.",
  "page_count": 5,
  "data": [
    {
      "id": "a1b2c3d4-e5f6-7890-g1h2-i3j4k5l6m7n8",
      "name": "总部大楼",
      "parent_id": "00000000-0000-0000-0000-000000000000",
      "parent_name": "根节点",
      "parent_type": "site",
      "type": "building",
      "remark": "公司总部主楼",
      "floor_no": "1F",
      "fullPath": "总部/总部大楼",
      "position_id_path": [
        "00000000-0000-0000-0000-000000000000",
        "a1b2c3d4-e5f6-7890-g1h2-i3j4k5l6m7n8"
      ],
      "latitude": "40.7128",
      "longitude": "-74.0060"
    },
    {
      "id": "b2c3d4e5-f6g7-8901-h2i3-j4k5l6m7n8o9",
      "name": "研发中心",
      "parent_id": "a1b2c3d4-e5f6-7890-g1h2-i3j4k5l6m7n8",
      "parent_name": "总部大楼",
      "parent_type": "building",
      "type": "region",
      "remark": "研发部门办公区",
      "floor_no": "3F",
      "fullPath": "总部/总部大楼/研发中心",
      "position_id_path": [
        "00000000-0000-0000-0000-000000000000",
        "a1b2c3d4-e5f6-7890-g1h2-i3j4k5l6m7n8",
        "b2c3d4e5-f6g7-8901-h2i3-j4k5l6m7n8o9"
      ],
      "latitude": "40.7135",
      "longitude": "-74.0055"
    },
    {
      "id": "c3d4e5f6-g7h8-9012-i3j4-k5l6m7n8o9p0",
      "name": "3F实验室",
      "parent_id": "b2c3d4e5-f6g7-8901-h2i3-j4k5l6m7n8o9",
      "parent_name": "研发中心",
      "parent_type": "region",
      "type": "floor",
      "remark": "AI实验室所在楼层",
      "floor_no": "3F-2",
      "fullPath": "总部/总部大楼/研发中心/3F实验室",
      "position_id_path": [
        "00000000-0000-0000-0000-000000000000",
        "a1b2c3d4-e5f6-7890-g1h2-i3j4k5l6m7n8",
        "b2c3d4e5-f6g7-8901-h2i3-j4k5l6m7n8o9",
        "c3d4e5f6-g7h8-9012-i3j4-k5l6m7n8o9p0"
      ],
      "latitude": "40.7137",
      "longitude": "-74.0053"
    },
    {
      "id": "d4e5f6g7-h8i9-0123-j4k5-l6m7n8o9p0q1",
      "name": "数据中心",
      "parent_id": "a1b2c3d4-e5f6-7890-g1h2-i3j4k5l6m7n8",
      "parent_name": "总部大楼",
      "parent_type": "building",
      "type": "room",
      "remark": "服务器机房",
      "floor_no": "B1",
      "fullPath": "总部/总部大楼/数据中心",
      "position_id_path": [
        "00000000-0000-0000-0000-000000000000",
        "a1b2c3d4-e5f6-7890-g1h2-i3j4k5l6m7n8",
        "d4e5f6g7-h8i9-0123-j4k5-l6m7n8o9p0q1"
      ],
      "latitude": "40.7125",
      "longitude": "-74.0065"
    },
    {
      "id": "e5f6g7h8-i9j0-1234-k5l6-m7n8o9p0q1r2",
      "name": "会议室A",
      "parent_id": "c3d4e5f6-g7h8-9012-i3j4-k5l6m7n8o9p0",
      "parent_name": "3F实验室",
      "parent_type": "floor",
      "type": "room",
      "remark": "容纳20人的多功能会议室",
      "floor_no": "3F-2",
      "fullPath": "总部/总部大楼/研发中心/3F实验室/会议室A",
      "position_id_path": [
        "00000000-0000-0000-0000-000000000000",
        "a1b2c3d4-e5f6-7890-g1h2-i3j4k5l6m7n8",
        "b2c3d4e5-f6g7-8901-h2i3-j4k5l6m7n8o9",
        "c3d4e5f6-g7h8-9012-i3j4-k5l6m7n8o9p0",
        "e5f6g7h8-i9j0-1234-k5l6-m7n8o9p0q1r2"
      ],
      "latitude": "40.7136",
      "longitude": "-74.0054"
    }
  ]
}
````

### 可操作校验结果

````text
status=VALID
issues=[]
````

## 46. ncet-003

- 来源: `4.json` 第 11 条
- 状态: `INVALID`
- 原始 Issue 数: 8
- 可操作 Issue 数: 8

- `error_detail.txt` 标注: 没有趋势折线图。
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([header, powerCard, tableCard], "column", "m")

header = CardHeader("历史光功率查询结果", "NE: " + neName + " | 端口: " + portName)

neName = data.externalAttributes.historyOpticalPower[0].neName
portName = data.externalAttributes.historyOpticalPower[0].portName
powerData = data.externalAttributes.historyOpticalPower[0].historyOpticalPowerEvents[0].eventValues

powerCard = Card([chartHeader, powerChart], "column", "m")
chartHeader = CardHeader("光功率趋势 (dBm)", "时间范围: " + startTimeStr + " 至 " + endTimeStr)

startTimeStr = @FormatDate(data.externalAttributes.historyOpticalPower[0].startTime, "dateTime")
endTimeStr = @FormatDate(data.externalAttributes.historyOpticalPower[0].endTime, "dateTime")

timeLabels = @Each(powerData, "item", @FormatDate(item.endTime, "dateTime"))
powerValues = @Each(powerData, "item", @parseFloat(item.value))
powerSeries = Series("光功率 (dBm)", powerValues)
powerChart = LineChart(timeLabels, [powerSeries], "smooth", "时间", "光功率 (dBm)")

tableCard = Card([tableHeader, powerTable], "column", "m")
tableHeader = CardHeader("详细数据", "包含最大值、最小值和当前值")
powerTable = Table([timeCol, valueCol, maxCol, minCol], powerData)

timeCol = Col("时间", "endTime", {cell: @Render("v", TextContent(@FormatDate(v, "dateTime")))})
valueCol = Col("当前值 (dBm)", "value", {cell: @Render("v", v ? TextContent(v) : TextContent("无数据"))})
maxCol = Col("最大值 (dBm)", "maxValue", {cell: @Render("v", v ? TextContent(v) : TextContent("无数据"))})
minCol = Col("最小值 (dBm)", "minValue", {cell: @Render("v", v ? TextContent(v) : TextContent("无数据"))})
````

### 输入 dataModel

````json
{
  "errors": {
    "error": [
      {
        "errorInfo": {
          "errorCode": "0",
          "errorParas": []
        },
        "errorMessage": ""
      }
    ]
  },
  "externalAttributes": {
    "historyOpticalPower": [
      {
        "endTime": "1784268900000",
        "historyOpticalPowerEvents": [
          {
            "eventId": 15882,
            "eventName": "信道光功率当前值(dBm)【NE(20-211)-子架0(subrack)-3-15OPM8-1(IN1)-OCH:1】",
            "eventValues": [
              {
                "endTime": "1784259900000",
                "maxValue": "-60.00",
                "minValue": "-60.00",
                "value": "-60.00"
              },
              {
                "endTime": "1784260800000",
                "maxValue": "-60.00",
                "minValue": "-60.00",
                "value": "-60.00"
              },
              {
                "endTime": "1784261700000",
                "maxValue": "-60.00",
                "minValue": "-60.00",
                "value": "-60.00"
              },
              {
                "endTime": "1784262600000",
                "maxValue": "-60.00",
                "minValue": "-60.00",
                "value": "-60.00"
              },
              {
                "endTime": "1784263500000"
              },
              {
                "endTime": "1784264400000",
                "maxValue": "-60.00",
                "minValue": "-60.00",
                "value": "-60.00"
              },
              {
                "endTime": "1784265300000",
                "maxValue": "-60.00",
                "minValue": "-60.00",
                "value": "-60.00"
              },
              {
                "endTime": "1784266200000",
                "maxValue": "-60.00",
                "minValue": "-60.00",
                "value": "-60.00"
              },
              {
                "endTime": "1784267100000",
                "maxValue": "-60.00",
                "minValue": "-60.00",
                "value": "-60.00"
              },
              {
                "endTime": "1784268000000",
                "maxValue": "-60.00",
                "minValue": "-60.00",
                "value": "-60.00"
              },
              {
                "endTime": "1784268900000",
                "maxValue": "-60.00",
                "minValue": "-60.00",
                "value": "-60.00"
              }
            ],
            "range": "[-60.0, -23.7]"
          }
        ],
        "neId": "167772507",
        "neName": "NE(20-211)",
        "portId": "1",
        "portName": "Shelf0-3-15OPM8-1(IN1)",
        "shelfId": "0",
        "slotId": "3",
        "startTime": "1784259900000"
      }
    ]
  }
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=unknown-component
severity=ERROR
source=contract
statementId=powerValues
component=parseFloat
path=
line=-1
column=-1
retryable=false
message=Unknown component "parseFloat" — not found in catalog or builtins
hint=<null>

issue[1]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=powerCard
component=Card
path=/variant
line=9
column=1
retryable=false
message=Card.variant expects "card" | "clear" | "sunk", but received string.
hint=Use a value compatible with "card" | "clear" | "sunk".

issue[2]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=powerCard
component=Card
path=/direction
line=9
column=1
retryable=false
message=Card.direction expects "column" | "row", but received string.
hint=Use a value compatible with "column" | "row".

issue[3]
code=type-table-column-missing
severity=ERROR
source=type
statementId=powerTable
component=Table
path=/columns/value
line=22
column=1
retryable=false
message=Table column field "value" is absent from some rows.
hint=Use a field present in the Table rows.

issue[4]
code=type-table-column-missing
severity=ERROR
source=type
statementId=powerTable
component=Table
path=/columns/maxValue
line=22
column=1
retryable=false
message=Table column field "maxValue" is absent from some rows.
hint=Use a field present in the Table rows.

issue[5]
code=type-table-column-missing
severity=ERROR
source=type
statementId=powerTable
component=Table
path=/columns/minValue
line=22
column=1
retryable=false
message=Table column field "minValue" is absent from some rows.
hint=Use a field present in the Table rows.

issue[6]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=tableCard
component=Card
path=/variant
line=20
column=1
retryable=false
message=Card.variant expects "card" | "clear" | "sunk", but received string.
hint=Use a value compatible with "card" | "clear" | "sunk".

issue[7]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=tableCard
component=Card
path=/direction
line=20
column=1
retryable=false
message=Card.direction expects "column" | "row", but received string.
hint=Use a value compatible with "column" | "row".
````

## 47. ncet-014

- 来源: `4.json` 第 12 条
- 状态: `INVALID`
- 原始 Issue 数: 4
- 可操作 Issue 数: 3

- `error_detail.txt` 标注: 渲染不出来失败
- 标注覆盖结果: `DETECTED`

### 输入 DSL

````openui
root = Stack([summaryCard, alarmTableCard])

summaryCard = Card([summaryHeader, summaryStats], "column", "s")
summaryHeader = CardHeader("业务告警概览", "当前活动告警统计")
summaryStats = Stack([totalCountStat, criticalCountStat], "row", "m", "start", "start", true)
totalCountStat = Card([TextContent("总告警数", "small"), TextContent("" + @Count(allAlarms), "large-heavy")])
criticalCountStat = Card([TextContent("紧急告警", "small"), TextContent("" + @Count(@Filter(allAlarms, "severity", "==", "紧急")), "large-heavy")])

alarmTableCard = Card([alarmTableHeader, alarmTable])
alarmTableHeader = CardHeader("告警详情", "按服务分组的当前告警列表")
alarmTable = Table([serviceCol, alarmNameCol, severityCol, locationCol, occurTimeCol, statusCol, causeCol], allAlarms)

serviceCol = Col("服务名称", "serviceName", {cell: @Render("v", TextContent(v))})
alarmNameCol = Col("告警名称", "alarmName", {cell: @Render("v", TextContent(v))})
severityCol = Col("严重级别", "severity", {cell: @Render("v", @Switch(v, {"紧急": Tag("紧急", "danger"), "重要": Tag("重要", "warning"), "次要": Tag("次要", "info"), "提示": Tag("提示", "neutral")}, Tag(v, "neutral")))})
locationCol = Col("位置信息", "locationInfo", {cell: @Render("v", TextContent(v))})
occurTimeCol = Col("发生时间", "occurUtc", {cell: @Render("v", TextContent(v))})
statusCol = Col("状态", "cleared", {cell: @Render("v", @Switch(v, {"未清除": Tag("未清除", "danger"), "已清除": Tag("已清除", "success")}, Tag(v, "neutral")))})
causeCol = Col("可能原因", "probableCause", {cell: @Render("v", TextContent(v))})

allAlarms = @Each(data.externalAttributes.serviceAlarmDetails, "service", @Each(service.alarmUOs, "alarm", {serviceName: service.serviceName, alarmName: alarm.alarmName, severity: alarm.severity, locationInfo: alarm.locationInfo, occurUtc: alarm.occurUtc, cleared: alarm.cleared, probableCause: alarm.probableCause}))
````

### 输入 dataModel

````json
{
  "errors": {
    "error": [
      {
        "errorInfo": {
          "errorCode": "0",
          "errorParas": []
        }
      }
    ]
  },
  "externalAttributes": {
    "serviceAlarmDetails": [
      {
        "serviceUuid": "c11d2b50-1fa9-425b-887a-07d4e278c21a",
        "serviceName": "广州增城区专线FE0160KA",
        "alarmUOs": [
          {
            "alarmId": "12345",
            "alarmCsn": "67890",
            "incidentCsn": [
              "100001"
            ],
            "alarmSource": "NE(9-208)",
            "locationInfo": "Shelf0-16-PORT-2",
            "alarmName": "LOS",
            "severity": "紧急",
            "severityValue": "1",
            "clearUtc": "",
            "occurUtc": "2025-06-15 10:30:00",
            "lastOccurUtc": "2025-06-15 10:30:00",
            "cleared": "未清除",
            "acknowledged": "未确认",
            "additionalInformation": "光信号丢失",
            "arrivedOn": "2025-06-15 10:30:05",
            "probableCause": "光纤中断",
            "alarmCount": "1",
            "hasBoard": "true",
            "alarmDataType": "CURRENT",
            "productName": "OTN"
          }
        ]
      }
    ],
    "isTruncated": "false"
  }
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=summaryCard
component=Card
path=/variant
line=3
column=1
retryable=false
message=Card.variant expects "card" | "clear" | "sunk", but received string.
hint=Use a value compatible with "card" | "clear" | "sunk".

issue[1]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=summaryCard
component=Card
path=/direction
line=3
column=1
retryable=false
message=Card.direction expects "column" | "row", but received string.
hint=Use a value compatible with "column" | "row".

issue[2]
code=type-table-row-shape-mismatch
severity=ERROR
source=type
statementId=alarmTable
component=Table
path=/rows
line=11
column=1
retryable=false
message=Table rows must be a flat object array, but received object[][].
hint=Flatten nested @Each results before passing them to Table.
````

<details>
<summary>原始校验结果（含级联诊断）</summary>

````text
status=INVALID

issue[0]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=summaryCard
component=Card
path=/variant
line=3
column=1
retryable=false
message=Card.variant expects "card" | "clear" | "sunk", but received string.
hint=Use a value compatible with "card" | "clear" | "sunk".

issue[1]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=summaryCard
component=Card
path=/direction
line=3
column=1
retryable=false
message=Card.direction expects "column" | "row", but received string.
hint=Use a value compatible with "column" | "row".

issue[2]
code=type-prop-mismatch
severity=ERROR
source=type
statementId=alarmTable
component=Table
path=/rows
line=11
column=1
retryable=false
message=Table.rows expects object[], but received object[][].
hint=Use a value compatible with object[].

issue[3]
code=type-table-row-shape-mismatch
severity=ERROR
source=type
statementId=alarmTable
component=Table
path=/rows
line=11
column=1
retryable=false
message=Table rows must be a flat object array, but received object[][].
hint=Flatten nested @Each results before passing them to Table.
````

</details>

## 48. rw-003-grouped

- 来源: `4.json` 第 13 条
- 状态: `INVALID`
- 原始 Issue 数: 2
- 可操作 Issue 数: 2

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([summaryCard, modelTabs], "column", "m")

summaryCard = Card([summaryHeader, summaryContent], "card", "column", "m", "center", "center")
summaryHeader = CardHeader("Online Devices Summary", "Fabric: " + data.fabric)
summaryContent = TextContent("Total Online Devices: " + data.total_count, "large-heavy")

modelTabs = Tabs(@Each(data.grouped_by_model, "group", modelTabTpl))

modelTabTpl = TabItem(group.model, group.model + " (" + group.count + " devices)", [modelTable])

modelTable = Table([modelCol, countCol, versionCol, nameCol, ipCol, vendorCol], group.devices)

modelCol = Col("Model", "model")
countCol = Col("Count", "count")
versionCol = Col("Version", "version")
nameCol = Col("Device Name", "name")
ipCol = Col("IP", "ip")
vendorCol = Col("Vendor", "vendor")
````

### 输入 dataModel

````json
{
  "total_count": 7,
  "fabric": "pod1",
  "grouped_by_model": [
    {
      "devices": [
        {
          "vendor": "Huawei",
          "ip": "10.137.16.139",
          "name": "DC1-spine-02",
          "version": "8.25 V200R025C00SPC500"
        },
        {
          "vendor": "Huawei",
          "ip": "10.137.16.138",
          "name": "DC1-spine-01",
          "version": "8.25 V200R025C00SPC500"
        }
      ],
      "count": 2,
      "model": "CE12804S"
    },
    {
      "devices": [
        {
          "vendor": "Huawei",
          "ip": "10.137.16.137",
          "name": "serverleaf02_1",
          "version": "8.23 V200R023C00SPC500"
        }
      ],
      "count": 1,
      "model": "CE6865-48S8CQ-EI"
    },
    {
      "devices": [
        {
          "vendor": "Huawei",
          "ip": "10.137.16.135",
          "name": "serverleaf01_1",
          "version": "8.25 V200R025C00SPC500"
        },
        {
          "vendor": "Huawei",
          "ip": "10.137.16.136",
          "name": "serverleaf01_2",
          "version": "8.25 V200R025C00SPC500"
        }
      ],
      "count": 2,
      "model": "CE6857-48S6CQ-EI"
    },
    {
      "devices": [
        {
          "vendor": "Huawei",
          "ip": "10.137.16.140",
          "name": "borderleaf01_1",
          "version": "8.25 V200R025C00SPC500"
        },
        {
          "vendor": "Huawei",
          "ip": "10.137.16.141",
          "name": "borderleaf01_2",
          "version": "8.25 V200R025C00SPC500"
        }
      ],
      "count": 2,
      "model": "CE8861-4C-EI"
    }
  ]
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=type-table-column-missing
severity=ERROR
source=type
statementId=modelTable
component=Table
path=/columns/model
line=11
column=1
retryable=false
message=Table column field "model" is absent from the proven row shape.
hint=Use a field present in the Table rows.

issue[1]
code=type-table-column-missing
severity=ERROR
source=type
statementId=modelTable
component=Table
path=/columns/count
line=11
column=1
retryable=false
message=Table column field "count" is absent from the proven row shape.
hint=Use a field present in the Table rows.
````

## 49. rw-246-O05-17F-Leaf2-Simu6-0001-CPU-CPU

- 来源: `4.json` 第 14 条
- 状态: `INVALID`
- 原始 Issue 数: 1
- 可操作 Issue 数: 1

- `error_detail.txt` 标注: 不用关注
- 标注覆盖结果: `IGNORED`

### 输入 DSL

````openui
root = Stack([gaugeCard])

gaugeCard = Card([gaugeHeader, gaugeChart])
gaugeHeader = CardHeader(data.title, data.subtitle)
gaugeChart = GaugeChart([gaugeReading], 0, 100)
gaugeReading = {name: "CPU利用率", value: data.data.cpuUsage}
````

### 输入 dataModel

````json
{
  "data": {
    "deviceIp": "197.197.5.1",
    "cpuUsage": 70,
    "unit": "%",
    "deviceName": "O05-17F-Leaf2-Simu6-0001",
    "timeRange": "近30分钟平均值"
  },
  "subtitle": "设备：O05-17F-Leaf2-Simu6-0001 | 时间范围：近30分钟",
  "options": {
    "max": 100,
    "threshold": 80,
    "warningThreshold": 60
  },
  "title": "设备CPU利用率",
  "type": "gauge"
}
````

### 可操作校验结果

````text
status=INVALID

issue[0]
code=unknown-component
severity=ERROR
source=contract
statementId=gaugeChart
component=GaugeChart
path=
line=-1
column=-1
retryable=false
message=Unknown component "GaugeChart" — not found in catalog or builtins
hint=<null>
````
