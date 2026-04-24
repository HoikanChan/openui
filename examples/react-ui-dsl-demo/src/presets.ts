export interface Preset {
  label: string;
  prompt: string;
  dataModel: Record<string, unknown>;
}

export const presets: Preset[] = [
  {
    label: "真实数据-折线图",
    prompt: "展示UI",
    dataModel: { "rows": [{ "deviceName": "NE-01-Core-Switch", "showName": "GigabitEthernet0/0/1", "time": 1717200000000, "PeakBandwidthUtilization": 45.7, "Traffic": 1234567.89, "portResId": "550e8400-e29b-41d4-a716-446655440001" }, { "deviceName": "NE-01-Core-Switch", "showName": "GigabitEthernet0/0/1", "time": 1717203600000, "PeakBandwidthUtilization": 52.3, "Traffic": 1345678.9, "portResId": "550e8400-e29b-41d4-a716-446655440001" }, { "deviceName": "NE-02-Access-Router", "showName": "Ethernet1/1", "time": 1717200000000, "PeakBandwidthUtilization": 18.2, "Traffic": 456789.12, "portResId": "550e8400-e29b-41d4-a716-446655440002" }, { "deviceName": "NE-02-Access-Router", "showName": "Ethernet1/1", "time": 1717203600000, "PeakBandwidthUtilization": 22.5, "Traffic": 512345.67, "portResId": "550e8400-e29b-41d4-a716-446655440002" }], "times": { "period": 60, "startTime": 1716595200000, "endTime": 1717200000000, "valid_period": 60, "valid_startTime": 1716595200000, "valid_endTime": 1717200000000 }, "statistics": [{ "portResId": "550e8400-e29b-41d4-a716-446655440001", "deviceName": "NE-01-Core-Switch", "showName": "GigabitEthernet0/0/1", "indicatorName": "Traffic", "max": 1345678.9, "min": 1234567.89, "avg": 1290123.395, "last": 1345678.9 }, { "portResId": "550e8400-e29b-41d4-a716-446655440002", "deviceName": "NE-02-Access-Router", "showName": "Ethernet1/1", "indicatorName": "Traffic", "max": 512345.67, "min": 456789.12, "avg": 484567.395, "last": 512345.67 }] }
  },
  {
    label: "设备列表",
    prompt: "展示设备清单表格，包含名称、IP、状态、类型，状态列用标签样式区分",
    dataModel: {
      devices: [
        { name: "Router-Core-01", ip: "192.168.1.1", status: "online", type: "路由器" },
        { name: "Switch-Access-02", ip: "192.168.1.2", status: "offline", type: "交换机" },
        { name: "Firewall-Edge-03", ip: "192.168.1.3", status: "warning", type: "防火墙" },
      ],
    },
  },
  {
    label: "设备详情卡片",
    prompt: "展示单个设备的详情卡片，包含设备名、IP、CPU使用率、内存使用率、端口数",
    dataModel: {
      device: {
        name: "Router-Core-01",
        ip: "192.168.1.1",
        cpu: 45,
        memory: 62,
        ports: 24,
      },
    },
  },
  {
    label: "设备健康雷达",
    prompt: "对比多台设备的健康指标雷达图，包含CPU、内存、磁盘、带宽、丢包率",
    dataModel: {
      labels: ["CPU %", "内存 %", "磁盘 %", "带宽 %", "丢包 %"],
      series: [
        { category: "Router-A", values: [65, 72, 45, 80, 2] },
        { category: "Router-B", values: [40, 55, 30, 60, 1] },
        { category: "Switch-C", values: [30, 48, 25, 90, 0.5] },
      ],
    },
  },
  {
    label: "设备拓扑图",
    prompt: "展示网络设备拓扑连接关系，核心路由器连接多台交换机和防火墙",
    dataModel: {
      nodes: [
        { id: "core", name: "核心路由", type: "router" },
        { id: "sw1", name: "接入交换机1", type: "switch" },
        { id: "sw2", name: "接入交换机2", type: "switch" },
        { id: "fw", name: "边界防火墙", type: "firewall" },
      ],
      links: [
        { source: "core", target: "sw1" },
        { source: "core", target: "sw2" },
        { source: "core", target: "fw" },
      ],
    },
  },
  {
    label: "设备配置变更",
    prompt: "展示设备配置变更时间线，包含变更时间、状态和描述",
    dataModel: {
      timeline: {
        title: "配置变更历史",
        items: [
          { status: "success", title: "端口配置更新", description: "G0/1 端口 VLAN 调整完成", time: "2026-04-20 10:30" },
          { status: "default", title: "ACL规则变更", description: "新增访问控制规则，待审批", time: "2026-04-19 14:00" },
          { status: "error", title: "固件升级失败", description: "升级过程异常中断", time: "2026-04-18 09:15" },
        ],
      },
    },
  },
  {
    label: "告警列表",
    prompt: "展示当前告警列表表格",
    dataModel: {
      alerts: [
        { id: "A001", level: "critical", source: "Router-Core-01", message: "CPU使用率超过90%", time: "2026-04-20 10:30" },
        { id: "A002", level: "warning", source: "Switch-Access-02", message: "端口G0/1流量异常", time: "2026-04-20 09:15" },
        { id: "A003", level: "info", source: "Firewall-Edge-03", message: "防火墙规则更新", time: "2026-04-20 08:00" },
      ],
    },
  },
  {
    label: "告警趋势",
    prompt: "展示24小时告警数量趋势折线图",
    dataModel: {
      labels: ["00:00", "04:00", "08:00", "12:00", "16:00", "20:00", "24:00"],
      series: [
        { category: "Critical", values: [2, 1, 3, 5, 8, 4, 2] },
        { category: "Warning", values: [5, 3, 7, 12, 15, 10, 6] },
        { category: "Info", values: [10, 8, 15, 20, 25, 18, 12] },
      ],
    },
  },
  {
    label: "告警热力图",
    prompt: "展示告警分布热力图，横轴为小时，纵轴为星期",
    dataModel: {
      xLabels: ["0h", "6h", "12h", "18h"],
      yLabels: ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"],
      values: [
        [2, 8, 12, 7],
        [1, 9, 11, 6],
        [3, 7, 9, 8],
        [4, 10, 14, 9],
        [5, 11, 15, 10],
        [1, 3, 5, 2],
        [0, 2, 3, 1],
      ],
    },
  },
  {
    label: "告警聚合饼图",
    prompt: "展示告警类型分布饼图",
    dataModel: {
      labels: ["CPU告警", "内存告警", "端口告警", "链路告警", "其他"],
      values: [35, 25, 20, 15, 5],
    },
  },
  {
    label: "接口流量排行",
    prompt: "展示Top接口流量横向条形图，按流量大小排序",
    dataModel: {
      labels: ["GigabitEthernet0/0", "GigabitEthernet0/1", "FastEthernet1/0", "GigabitEthernet0/2"],
      series: [{ category: "流量 (Mbps)", values: [850, 620, 340, 280] }],
    },
  },
  {
    label: "流量趋势",
    prompt: "展示接口流量趋势面积图，区分入站和出站流量",
    dataModel: {
      labels: ["00:00", "06:00", "12:00", "18:00", "24:00"],
      series: [
        { category: "入站 (Mbps)", values: [120, 200, 520, 380, 200] },
        { category: "出站 (Mbps)", values: [80, 150, 300, 250, 120] },
      ],
    },
  },
  {
    label: "延迟丢包散点",
    prompt: "展示核心路由延迟vs丢包散点图，x轴延迟，y轴丢包率",
    dataModel: {
      scatterSeries: {
        name: "核心路由",
        points: [
          { x: 5, y: 0.1 },
          { x: 8, y: 0.2 },
          { x: 12, y: 0.3 },
          { x: 15, y: 0.5 },
          { x: 20, y: 0.8 },
        ],
      },
      xLabel: "延迟 (ms)",
      yLabel: "丢包率 (%)",
    },
  },
  {
    label: "带宽分解树图",
    prompt: "展示带宽占用分解树图，按网段和接口分层",
    dataModel: {
      data: [
        { name: "eth0", value: 850, group: "网段 A" },
        { name: "eth1", value: 620, group: "网段 A" },
        { name: "eth2", value: 340, group: "网段 B" },
        { name: "eth3", value: 280, group: "网段 B" },
      ],
    },
  },
  {
    label: "KPI仪表盘",
    prompt: "展示系统健康KPI仪表盘，显示综合健康评分",
    dataModel: {
      readings: [{ name: "系统健康", value: 87 }],
    },
  },
  {
    label: "IP地址池",
    prompt: "展示IP地址池使用情况表格，包含已用、空闲、总数、利用率",
    dataModel: {
      ipPool: {
        segments: [
          { name: "192.168.1.0/24", used: 180, free: 74, total: 254, utilization: "71%" },
          { name: "192.168.2.0/24", used: 120, free: 134, total: 254, utilization: "47%" },
          { name: "10.0.0.0/16", used: 850, free: 64506, total: 65536, utilization: "1.3%" },
        ],
      },
    },
  },
  {
    label: "VLAN列表",
    prompt: "展示VLAN配置列表表格，包含VLAN ID、名称、关联端口",
    dataModel: {
      vlans: [
        { id: 10, name: "管理网络", ports: "G0/1, G0/2" },
        { id: 20, name: "业务网络", ports: "G0/3-G0/10" },
        { id: 30, name: "存储网络", ports: "G0/11-G0/15" },
        { id: 40, name: "访客网络", ports: "G0/16-G0/20" },
      ],
    },
  },
  {
    label: "端口状态",
    prompt: "展示端口状态表格，包含端口名、状态、速率、双工模式",
    dataModel: {
      ports: [
        { name: "GigabitEthernet0/0", status: "up", speed: "1000Mbps", duplex: "全双工" },
        { name: "GigabitEthernet0/1", status: "down", speed: "-", duplex: "-" },
        { name: "FastEthernet1/0", status: "up", speed: "100Mbps", duplex: "全双工" },
        { name: "GigabitEthernet0/2", status: "error", speed: "1000Mbps", duplex: "半双工" },
      ],
    },
  },
  {
    label: "业务拓扑",
    prompt: "展示业务系统与设备关联关系，业务应用依赖哪些网络设备",
    dataModel: {
      businessApps: [
        { id: "app1", name: "ERP系统", criticality: "高" },
        { id: "app2", name: "OA系统", criticality: "中" },
        { id: "app3", name: "监控系统", criticality: "低" },
      ],
      relations: [
        { app: "app1", devices: ["Router-Core-01", "Switch-Access-02"] },
        { app: "app2", devices: ["Switch-Access-02"] },
        { app: "app3", devices: ["Firewall-Edge-03"] },
      ],
    },
  },
  {
    label: "SLA仪表盘",
    prompt: "展示业务SLA达标率仪表盘，包含可用性、响应时间、故障恢复时间",
    dataModel: {
      slaMetrics: [
        { name: "可用性", value: 99.9 },
        { name: "响应时间", value: 85 },
        { name: "故障恢复", value: 92 },
      ],
    },
  },
  {
    label: "故障影响分析",
    prompt: "展示故障对业务影响的表格，包含故障设备、影响业务、影响程度",
    dataModel: {
      impacts: [
        { device: "Router-Core-01", business: "ERP系统", level: "严重", status: "已恢复" },
        { device: "Switch-Access-02", business: "OA系统", level: "中等", status: "处理中" },
        { device: "Firewall-Edge-03", business: "监控系统", level: "轻微", status: "待处理" },
      ],
    },
  },
];