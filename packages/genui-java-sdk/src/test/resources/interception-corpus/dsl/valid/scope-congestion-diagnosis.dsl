root = Stack([reportHeader, phenomenonCard, rootCauseCard, bottleneckCard, repairCard], "column", "l")

reportHeader = CardHeader("网络拥塞诊断报告", "故障等级: " + data.congestion_diagnosis.fault_level)

phenomenonCard = Card([phenomHeader, phenomDesc], "card", "column", "m")
phenomHeader = CardHeader("故障现象", "")
phenomDesc = Descriptions([
  DescField("故障时间", data.congestion_diagnosis.fault_phenomenon.fault_time),
  DescField("拥塞设备", data.congestion_diagnosis.fault_phenomenon.congestion_device),
  DescField("拥塞接口", data.congestion_diagnosis.fault_phenomenon.congestion_interface),
  DescField("拥塞时长", data.congestion_diagnosis.fault_phenomenon.congestion_duration),
  DescField("平均带宽利用率", data.congestion_diagnosis.fault_phenomenon.bandwidth_utilization.average),
  DescField("峰值带宽利用率", data.congestion_diagnosis.fault_phenomenon.bandwidth_utilization.peak),
  DescField("峰值时间", data.congestion_diagnosis.fault_phenomenon.bandwidth_utilization.peak_time),
  DescField("丢包率", data.congestion_diagnosis.fault_phenomenon.loss_rate),
  DescField("最大时延", data.congestion_diagnosis.fault_phenomenon.max_delay),
  DescField("影响范围", data.congestion_diagnosis.fault_phenomenon.impact_scope)
], "故障详情", 2, true)

rootCauseCard = Card([rcHeader, rcContent], "card", "column", "m")
rcHeader = CardHeader("根因分析", "置信度: " + data.congestion_diagnosis.root_cause.confidence)
rcContent = Stack([rcType, rcDesc, rcEvidence], "column", "m")
rcType = TextContent("类型: " + data.congestion_diagnosis.root_cause.type, "default")
rcDesc = TextContent(data.congestion_diagnosis.root_cause.description, "default")
rcEvidence = Stack([evidenceHeader, evidenceList], "column", "s")
evidenceHeader = TextContent("证据链:", "small-heavy")
evidenceList = Stack(@Each(data.congestion_diagnosis.root_cause.evidence, "item", evidenceItemTpl), "column", "s")
evidenceItemTpl = TextContent(item, "small")

bottleneckCard = Card([bnHeader, bnDesc], "card", "column", "m")
bnHeader = CardHeader("瓶颈分析", "")
bnDesc = Descriptions([
  DescField("设备", data.congestion_diagnosis.bottleneck_analysis.device),
  DescField("入方向接口", data.congestion_diagnosis.bottleneck_analysis.in_interface),
  DescField("出方向接口", data.congestion_diagnosis.bottleneck_analysis.out_interface),
  DescField("带宽比例", data.congestion_diagnosis.bottleneck_analysis.bandwidth_ratio),
  DescField("拥塞类型", data.congestion_diagnosis.bottleneck_analysis.congestion_type),
  DescField("受影响流", data.congestion_diagnosis.bottleneck_analysis.affected_flows)
], "瓶颈详情", 2, true)

repairCard = Card([repairHeader, repairContent], "card", "column", "m")
repairHeader = CardHeader("修复建议", "")
repairContent = Stack([primaryActionCard, tempActionsCard, longTermActionsCard], "column", "m")

primaryActionCard = Card([paHeader, paDesc], "card", "column", "s")
paHeader = CardHeader("主要行动", "紧急程度: " + data.repair_recommendations.primary_action.urgency)
paDesc = Descriptions([
  DescField("行动", data.repair_recommendations.primary_action.action),
  DescField("目标", data.repair_recommendations.primary_action.target),
  DescField("描述", data.repair_recommendations.primary_action.description),
  DescField("预期效果", data.repair_recommendations.primary_action.expected_effect)
], "", 1, false)

tempActionsCard = Card([taHeader, taList], "card", "column", "s")
taHeader = CardHeader("临时措施", "")
taList = Stack(@Each(data.repair_recommendations.temporary_actions, "item", taItemTpl), "column", "m")
taItemTpl = Card([taAction, taTarget, taDesc, taUrgency], "card", "column", "none")
taAction = TextContent("行动: " + item.action, "small-heavy")
taTarget = TextContent("目标: " + item.target, "small")
taDesc = TextContent(item.description, "small")
taUrgency = TextContent("紧急程度: " + item.urgency, "small")

longTermActionsCard = Card([ltaHeader, ltaList], "card", "column", "s")
ltaHeader = CardHeader("长期措施", "")
ltaList = Stack(@Each(data.repair_recommendations.long_term_actions, "item", ltaItemTpl), "column", "m")
ltaItemTpl = Card([ltaAction, ltaDesc, ltaUrgency], "card", "column", "none")
ltaAction = TextContent("行动: " + item.action, "small-heavy")
ltaDesc = TextContent(item.description, "small")
ltaUrgency = TextContent("紧急程度: " + item.urgency, "small")
