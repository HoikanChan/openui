root = Stack([detailCard])
detailCard = Card([header, details])
header = CardHeader("故障定位详情", data.issueLocationPathName)
details = Descriptions([
  DescField("问题类型", data.problemType),
  DescField("告警名称", data.alarmName),
  DescField("是否告警", data.isAlarm),
  DescField("根因类型", data.alarmRootType),
  DescField("业务层级", data.trailLevel),
  DescField("业务名称", data.trailName),
  DescField("光纤名称", data.fiberName),
  DescField("故障定位网元", data.issueLocationNeName),
  DescField("故障定位路径", data.issueLocationPathName),
  DescField("诊断信息", data.diagnoseInfo)
], "详细信息")
