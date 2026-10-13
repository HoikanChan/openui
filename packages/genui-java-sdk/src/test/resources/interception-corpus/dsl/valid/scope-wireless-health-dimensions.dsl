root = Stack([reportHeader, dimCards])
reportHeader = CardHeader("无线健康度报告", "综合健康分: " + data.healthScore)
dimCards = Stack(@Each(data.data, "dim", dimTpl))
dimTpl = Card([CardHeader(dim.key, "得分: " + dim.value.rootValue + " · 排名: " + dim.value.rank), LineChart(@Each(dim.value.baseline, "b", @FormatDate(b.timestamp, "date")), [Series("Baseline", dim.value.baseline.value), Series("Essential", dim.value.essential.value)], "smooth", "时间", "数值"), Stack([Table([Col("区域", "regionName"), Col("数值", "value"), Col("层级", "level")], dim.value.rankList), Table([Col("子指标", "key"), Col("数值", "value")], dim.value.subValues)], "row", "m")])
