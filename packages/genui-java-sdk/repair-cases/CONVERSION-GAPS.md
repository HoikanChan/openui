# CONVERSION-GAPS — smartcanvas 语料 → repair-bench case 包

生成: 2026-10-15T12:12:03.602Z
脚本: packages/genui-java-sdk/scripts/md-to-repair-cases.mjs
语料: C:\workspace\smartcanvas-dataset/eval-failure-report/evaluations/llm-errors/
契约: src/main/resources/openui/base-contract.json(contractVersion @cloudsop/openui-react-ui-dsl@1.1.5)逐字节拷贝

## 总览

| 项 | 数 |
|---|---|
| 案例 | 25 |
| dataModel 完整 | 15 |
| dataModel 省略号最小修复 | 1 |
| dataModel 结构性跳过(按缺失) | 3 |
| dataModel 缺失(待内部回填) | 6 |
| intent 来自 md 原文 | 16 |
| intent LLM 反推(待核对) | 9 |
| intent 仍缺失 | 0 |
| issues.json 透传 | 19 |

## 缺 dataModel — 待内部回填

md 里没有渲染数据块(或不可解析),case.json 不带 dataModel:validation 时类型校验关闭,相关根因不会复现。

**回填进展(2026-10-16,人工编辑)**:GEN-00247(case-08)/ GEN-00232(case-17,结构跳过)/ GEN-00094(case-23)/ GEN-00088(case-25)/ GEN-00233(case-18,结构跳过)/ GEN-00349(case-19,结构跳过)/ GEN-00076(case-24)已回填;GEN-00088 取 gloden-dataset.md 同期 queryInterfaceMetrics 真值(effcnt=30),case-24 复用同族真值(GEN-00076 的 DSL 引用 recv_drop_pkts_cnt,真值中无此字段——这正是案例的幻觉面,行形状错配照常触发),case-17/18/19 用 queryDeviceInfo 真值(黄金 GEN-00022 结构,单台 Simu6 设备),其余为内部近似数据。回填后验证:GEN-00088/94/76 复现 type-table-row-shape-mismatch、GEN-00232/233/349 复现 type-data-path-missing,与 REPRODUCE-BASELINE 预期一致。注意:重跑 md-to-repair-cases.mjs 会按 md 重写这些 case.json 并清掉回填值(intent 字段有复用逻辑,dataModel 没有)。

| case | 目录 | md 来源 |
|---|---|---|
| GEN-00140 | case-20_GEN-00140 | 4b-数据路径幻觉-queryDeviceInfo.md — queryDevicePort 真值语料侧没有 |
| rw-333-group#1 | case-22_rw-333-group-1 | 6a-内置函数参数错误-ObjectEntries.md — data.data(fabricName/deviceType/count)真值语料侧没有 |

## 结构性 dataModel 跳过

md 标注（结构）,是示意不是真实数据,按缺失处理,同样待内部回填。

**回填进展(2026-10-16)**:GEN-00232(case-17)/ GEN-00233(case-18)/ GEN-00349(case-19)已全部回填 queryDeviceInfo 真值,三例均复现 type-data-path-missing。

(已清空——跳过的 3 例均已回填)

## dataModel 省略号最小修复

仅移除 `...` 及其残留逗号让 JSON 可解析,保留 md 中出现的行;真实行集待内部核对。

| case | 目录 | md 来源 |
|---|---|---|
| GEN-00124 | case-13_GEN-00124 | 3c-字段名幻觉-CPU内存指标.md |

## LLM 反推 intent — 待人工核对

| case | 目录 | 反推 intent |
|---|---|---|
| GEN-00056 | case-15_GEN-00056 | 查询设备的 Loopback 接口信息和 BGP 对端信息，并用两个表格分别展示设备 IP、接口名称以及对端 IP、接口名称。 |
| GEN-00066 | case-16_GEN-00066 | 帮我查询光模块性能指标，展示设备IP、设备名称、接口名称，并用表格列出电压和温度等指标。 |
| GEN-00232 | case-17_GEN-00232 | 查询设备的基本信息，并用卡片详情展示设备名称、IP、角色、状态（在线/离线标签）、款型、厂商、MAC、DN、ESN、版本和 Fabric 名称。 |
| GEN-00233 | case-18_GEN-00233 | 查询单个网络设备的基本信息，并以详情卡片和描述列表展示设备名称、IP地址、状态、角色、设备分类、款型、厂商、MAC地址、ESN、版本及所属Fabric。 |
| GEN-00349 | case-19_GEN-00349 | 我想查询设备的基本信息，并以详情卡片展示设备名称、IP地址、状态、款型、角色、厂商、版本、MAC地址、ESN、分类及所属Fabric。 |
| GEN-00140 | case-20_GEN-00140 | 查询设备端口信息，展示设备基本信息、端口列表及端口总数。 |
| rw-333-group#1 | case-22_rw-333-group-1 | 请根据 Fabric 设备库存数据，按 Fabric 分标签展示各设备类型及数量，并在顶部汇总总设备数、Fabric 数和设备类型数。 |
| GEN-00076 | case-24_GEN-00076 | 查询某设备的接口指标详情，并用表格展示设备名称、设备IP、接口名称、MAC地址、查询ID、接收丢包数、过滤丢包数和指标值。 |
| GEN-00088 | case-25_GEN-00088 | 帮我查询接口指标详情，并以指标名称和数值的表格展示设备、接口、MAC、查询ID、输出字节率等遥测数据。 |

## md issueCodes 为空(parser 静默崩溃)

原始记录 codes 为空,未写 issues.json;T3 对比时归入"无原始记录"。

| case | 目录 | md 来源 |
|---|---|---|
| GEN-00091#1 | case-01_GEN-00091-1 | 1a-幻觉内置函数-接口速率.md |
| GEN-00100#2 | case-02_GEN-00100-2 | 1a-幻觉内置函数-接口速率.md |

## md 未记录 issueCodes

小节没有 issueCodes 元数据(4b 四例),未写 issues.json;T3 对比时归入"无原始记录"。

| case | 目录 | md 来源 |
|---|---|---|
| GEN-00232 | case-17_GEN-00232 | 4b-数据路径幻觉-queryDeviceInfo.md |
| GEN-00233 | case-18_GEN-00233 | 4b-数据路径幻觉-queryDeviceInfo.md |
| GEN-00349 | case-19_GEN-00349 | 4b-数据路径幻觉-queryDeviceInfo.md |
| GEN-00140 | case-20_GEN-00140 | 4b-数据路径幻觉-queryDeviceInfo.md |

## 空分类文件 — 语料缺口,反馈内部

- 0-LLM错误汇总.md
- 1-幻觉内置函数.md
- 5-Unique返回类型误解.md
- 6b-内置函数参数错误-其他.md
- 7b-Each语义误解-设备指标.md
- 8a-语法错误-JSON包装与空输出.md
- 8b-语法错误-括号嵌套与运算符.md
- 9a-引用未解析-连字符字段.md
- 9b-引用未解析-其他.md

## 跨文件重复

- GEN-00109:首见 1b-幻觉内置函数-光模块指标.md,重复于 3a-字段名幻觉-光模块指标.md(内容相同,收录一次)
- GEN-00119:首见 1b-幻觉内置函数-光模块指标.md,重复于 3a-字段名幻觉-光模块指标.md(内容相同,收录一次)
- GEN-00122:首见 1b-幻觉内置函数-光模块指标.md,重复于 3a-字段名幻觉-光模块指标.md(内容相同,收录一次)
