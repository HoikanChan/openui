root = VLayout([
  summaryCard,
  statusGrid,
  trendSection
])

summaryCard = Card([cardTitle, kpiRows], "card", "standard")
cardTitle = Text("网络整体运行状况摘要", "large")
kpiRows = VLayout([
  kpiItem1,
  kpiItem2,
  kpiItem3,
  kpiItem4
], "m", "start", "between")

kpiItem1 = HLayout([
  Text("总设备数", "small"),
  Text(data.totalDevices.toString(), "large-heavy")
], "s", "center", "end")

kpiItem2 = HLayout([
  Text("活跃设备数", "small"),
  Text(data.activeDevices.toString(), "large-heavy")
], "s", "center", "end")

kpiItem3 = HLayout([
  Text("告警总数", "small"),
  Text(data.totalAlarms.toString(), "large-heavy")
], "s", "center", "end")

kpiItem4 = HLayout([
  Text("关键告警数", "small"),
  Text(data.criticalAlarms.toString(), "large-heavy")
], "s", "center", "end")

statusGrid = VLayout([
  deviceStatusRow,
  alarmStatusRow,
  performanceStatusRow
], "l", "start", "between")

deviceStatusRow = HLayout([
  Text("设备状态", "small"),
  Tag(
    data.activeDevices + "/" + data.totalDevices,
    @Switch(
      @Round(@Div(data.activeDevices, data.totalDevices), 2),
      {
        "1": "success",
        "0.9": "warning",
        "0.8": "warning",
        "0.7": "danger"
      },
      "danger"
    )
  )
], "m", "center", "start")

alarmStatusRow = HLayout([
  Text("告警状态", "small"),
  Tag(
    data.criticalAlarms > 0 ? "有关键告警" : "无关键告警",
    data.criticalAlarms > 0 ? "danger" : "success"
  )
], "m", "center", "start")

performanceStatusRow = HLayout([
  Text("可用率", "small"),
  Tag(
    @FormatPercent(data.availabilityRate, 2) + " (" + @FormatNumber(@Mul(@Div(data.activeDevices, data.totalDevices), 100), 1) + "%)",
    @Switch(
      data.availabilityRate,
      {
        "0.95": "danger",
        "0.9": "warning",
        "0.8": "warning",
        "0.7": "danger"
      },
      "success"
    )
  )
], "m", "center", "start")

trendSection = VLayout([
  Text("性能趋势", "large"),
  cpuTrendChart,
  memTrendChart
], "l", "start", "start")

cpuTrendChart = MiniChart("line", [data.avgCpuUtil], 96)
memTrendChart = MiniChart("line", [data.avgMemUtil], 96)