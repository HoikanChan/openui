// Self-contained replay demo bundle. The Replay button loads this prompt + data
// model and streams the DSL below back as a simulated generation. It is kept
// intentionally standalone (not looked up from the preset list) so the showcase
// works even if the matching "网络运维总览" preset is edited or removed.
//
// Keep REPLAY_DATA_MODEL in sync with the `data.*` paths referenced in REPLAY_DSL.
export const REPLAY_PROMPT =
  "生成网络运维总览面板。顶部用一组卡片展示关键指标，每张卡片显示指标名称、当前数值和迷你趋势折线图：在线设备、平均CPU、平均内存、活跃告警。下方用标签页分两个视图：「流量趋势」用折线图展示入站和出站流量随时间的变化；「设备明细」用表格展示设备名、IP、状态、类型，状态列用标签样式区分。";

export const REPLAY_DATA_MODEL: Record<string, unknown> = {
  kpis: [
    { label: "在线设备", value: 42, spark: [40, 41, 39, 42, 43, 42, 42] },
    { label: "平均CPU", value: 53, spark: [48, 50, 55, 52, 58, 54, 53] },
    { label: "平均内存", value: 61, spark: [58, 60, 59, 62, 63, 61, 61] },
    { label: "活跃告警", value: 7, spark: [3, 4, 5, 6, 8, 7, 7] },
  ],
  trafficTrend: {
    labels: ["00:00", "04:00", "08:00", "12:00", "16:00", "20:00", "24:00"],
    series: [
      { category: "入站 (Mbps)", values: [120, 200, 520, 380, 460, 300, 200] },
      { category: "出站 (Mbps)", values: [80, 150, 300, 250, 320, 210, 120] },
    ],
  },
  devices: [
    { name: "Router-Core-01", ip: "192.168.1.1", status: "online", type: "路由器" },
    { name: "Switch-Access-02", ip: "192.168.1.2", status: "offline", type: "交换机" },
    { name: "Firewall-Edge-03", ip: "192.168.1.3", status: "warning", type: "防火墙" },
    { name: "Switch-Access-05", ip: "192.168.1.5", status: "online", type: "交换机" },
  ],
};

export const REPLAY_DSL = `root = Stack([header, kpiRow, chartTabs])
header = TextContent("网络运维总览", "large")

kpiRow = Stack([kpiCardOnline, kpiCardCpu, kpiCardMem, kpiCardAlert], "row", "m", "stretch")

kpiCardOnline = Card([kpiOnlineTitle, kpiOnlineValue, kpiOnlineChart], "card", "standard")
kpiOnlineTitle = TextContent("在线设备", "small")
kpiOnlineValue = TextContent(@FormatNumber(data.kpis[0].value) + " 台", "large")
kpiOnlineChart = MiniChart("line", data.kpis[0].spark)

kpiCardCpu = Card([kpiCpuTitle, kpiCpuValue, kpiCpuChart], "card", "standard")
kpiCpuTitle = TextContent("平均CPU", "small")
kpiCpuValue = TextContent(@FormatNumber(data.kpis[1].value) + " %", "large")
kpiCpuChart = MiniChart("line", data.kpis[1].spark)

kpiCardMem = Card([kpiMemTitle, kpiMemValue, kpiMemChart], "card", "standard")
kpiMemTitle = TextContent("平均内存", "small")
kpiMemValue = TextContent(@FormatNumber(data.kpis[2].value) + " %", "large")
kpiMemChart = MiniChart("line", data.kpis[2].spark)

kpiCardAlert = Card([kpiAlertTitle, kpiAlertValue, kpiAlertChart], "card", "standard")
kpiAlertTitle = TextContent("活跃告警", "small")
kpiAlertValue = TextContent(@FormatNumber(data.kpis[3].value) + " 条", "large")
kpiAlertChart = MiniChart("line", data.kpis[3].spark)

chartTabs = Tabs([trafficTab, deviceTab])

trafficTab = {value: "traffic", label: "流量趋势", content: [trafficChart]}
trafficChart = LineChart(data.trafficTrend.labels, [inSeries, outSeries], "smooth", "时间", "流量 (Mbps)")
inSeries = Series(data.trafficTrend.series[0].category, data.trafficTrend.series[0].values)
outSeries = Series(data.trafficTrend.series[1].category, data.trafficTrend.series[1].values)

deviceTab = {value: "devices", label: "设备明细", content: [deviceTable]}
deviceTable = Table([deviceNameCol, deviceIpCol, deviceStatusCol, deviceTypeCol], data.devices)
deviceNameCol = Col("设备名", "name")
deviceIpCol = Col("IP地址", "ip")
deviceStatusCol = Col("状态", "status", {cell: @Render("v", Tag(@Switch(v, {"online": "在线", "offline": "离线", "warning": "告警"}, v), @Switch(v, {"online": "success", "offline": "danger", "warning": "warning"}, "neutral")))})
deviceTypeCol = Col("类型", "type")`;
