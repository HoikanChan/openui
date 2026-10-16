# 模型生成 openui-lang 的错误行为目录

> 用途：作为 prompt 优化的依据。汇总 temp=0.7 压力实验中 qwen3-30b-a3b-instruct-2507 被 gate 拦截的全部错误模式，每条附**真实错误原文**与地址。
> 语料来源：29 个真实失败现场，来自 `packages/genui-java-sdk/target/benchmark-out/t07/`（修复前）与 `t07-after/`（导入完整性契约+定向示例后）。
> 每个案例的单条被拦语句原文在 reask 请求体里：`target/benchmark-out/{t07,t07-after}/round{1,2}/<id>.llm-calls.json` → `calls[1].request.messages[1].content` 的 "Invalid next statement" 段。会话内另有干净副本 `scratchpad/reask-loop/cases/<key>/invalid.dsl`（会话级临时，可能已失效，故原文已内联于下）。

判定口径：FINAL 校验 VALID 且显式定义 `root`，`data` 视为已知外部引用（与线上 gate 一致，见 `ValidateCli.java`）。

---

## 按频次排序的错误模式

### 1. JS 数组方法 + 箭头函数 `.map(x => x.f)` —— 最高频
模型把对象数组取字段写成 JavaScript。触发 `syntax-unclosed-bracket` + `syntax-unexpected-token`（`=>` 打断 parser）。**几乎每个 tc-* 用例都犯。**

```
// ❌ 原文 (pre-round1-tc-008-capacity-quality, pre-round1-tc-010, tc-007, tc-015 …)
capacityKPI = Card([CardHeader("容量质量", ...), MiniChart("line", data.data[0].value.baseline.map(item => item.value), 64)], "sunk", "standard")
trendMiniChart = MiniChart("line", data.data[0].value.baseline.map(item => item.value), 48)

// ✅ 正确：数组 pluck 或 @Each
MiniChart("line", data.data[0].value.baseline.value, 48)
MiniChart("line", @Each(data.data[0].value.baseline, "item", item.value), 48)
```
地址：`t07-after/round1/tc-010-coverage-quality.llm-calls.json`、`t07/round1/tc-008-capacity-quality.llm-calls.json` 等。

### 2. 编造小写组件 `cardHeader(...) / cardValue(...) / miniChart(...)`
模型把重复结构当成可调用的辅助函数发明出来。触发 `unknown-component`（或被当作调用时 `syntax`）。

```
// ❌ 原文 (pre-round1-tc-009-common-quality) —— 同时含 .map + 把表达式包成字符串
throughputCard = Card([cardHeader("吞吐量"), cardValue("@FormatPercent(data.data[0].value.rootValue / 100, 1)"), miniChart("line", data.data[0].value.essential.map(item => item.value))], "card", "standard")
// ❌ (pre-round1-tc-015) metricHeader/metricValue/metricTrend 同类
throughputCard = Card([metricHeader("吞吐量", "throughput"), metricValue(...), metricTrend(...)], ...)

// ✅ 正确：只用已注册组件；表达式不要加引号
cardHeaderRef = CardHeader("吞吐量", @FormatPercent(data.data[0].value.rootValue / 100, 1))
```
地址：`t07/round1/tc-009-common-quality.llm-calls.json`、`t07/round1/tc-019-wired-metric-list.llm-calls.json`（unknown-component）。

### 3. JS 严格等于 `===` / `!==`
触发 `syntax-unexpected-token`（Unexpected token EQUALS）。

```
// ❌ 原文 (pre-round1-tc-014-roam-quality) —— 同时含 .includes()
valueCol = Col("数值", "value", {cell: @Render("v", "row", TextContent(row.value + (["roam_success_rate", "roam_compliance_rate"].includes(row.key) ? "%" : (row.key === "avg_roam_time" ? "ms" : ""))))})

// ✅ 正确：== / !=；成员判断用 || 展开
row.key == "avg_roam_time"
row.key == "roam_success_rate" || row.key == "roam_compliance_rate"
```
地址：`t07/round1/tc-014-roam-quality.llm-calls.json`。

### 4. JS 字符串/数字方法 `.toString() / .toFixed() / .join() / .includes()`
触发 `syntax`。

```
// ❌ 原文 (pre-round2-tc-002-app-fault-analysis)
poorFlowCol = Col("异常流数", "poorFlowCount", {cell: @Render("v", TextContent(v.toString()))})

// ✅ 正确
@Render("v", TextContent("" + v))            // toString → 拼接
@Render("v", TextContent(@FormatNumber(v, 1)))  // toFixed → @FormatNumber
```
地址：`t07/round2/tc-002-app-fault-analysis.llm-calls.json`。

### 5. `@Render` 参数数量混淆（多塞一个位置参数）
`@Render` 的双 binder 形式是 `@Render("v", "row", expr)`——第 2 参必须是**字符串 binder 名**。模型误塞了第 3 个非法参数，导致 binder 作用域坏掉 → `unresolved-ref "v"`。

```
// ❌ 原文 (pre-round1-paginated-list, prefix 第 11 行) —— 末尾多了 ", 2"
clearTimeCol = Col("Clear Time", "clearTime", {cell: @Render("v", TextContent(v ? @FormatDate(v, "dateTime") : "Never") , 2)})

// ✅ 正确：@Render("v", expr) 或 @Render("v", "row", expr)
clearTimeCol = Col("Clear Time", "clearTime", {cell: @Render("v", TextContent(v ? @FormatDate(v, "dateTime") : "Never"))})
```
地址：`t07/round1/paginated-list.llm-calls.json`（错误在 prefix 里的 clearTimeCol，最终扣在 root 上报 unresolved-ref "v"）。

### 6. `@Each` 模板里写块语句（赋值语句）
`@Each` 的模板必须是**单个表达式**，模型却写成了 JS 代码块（多条 `name = value` + 尾部对象）。触发 `syntax-unexpected-token`（EQUALS）。

```
// ❌ 原文 (sparse-nullable, 原始 t07)
deviceRows = @Each(data.devices, "d", {
  deviceId = d.deviceId
  name = d.name
  statusTag = Tag(d.status ?? "未知", ...)
  { deviceId: deviceId, name: name, status: statusTag, ... }
})

// ✅ 正确：模板直接是对象字面量表达式，内联逻辑
deviceRows = @Each(data.devices, "d", {deviceId: d.deviceId, name: d.name, status: Tag(d.status ?? "未知", ...)})
```
地址：`t07/round*/sparse-nullable.llm-calls.json`（如已被覆盖，原文见上）。

### 7. excess-args（组件参数超量）—— 三轮反馈仍不改的硬核
即使反馈逐字给出 "Card takes 3 arg(s), got 4"，模型仍过量供参。属**模型能力墙**，prompt 难解。

```
// ❌ 残留原文 (pre-round2-current-vs-previous-kpi, out-ITER/full-3.dsl)
totalAlarmsCard = Card([cardHeader, cardValue, cardTrend], "card", "standard")   // 另一处 Card 为 4 参
// 且多张卡复用同名 cardHeader/cardValue → 引用坍塌 unresolved-ref

// ✅ 正确：严格对齐签名 Card(children?, variant?, width?) 三参；每张卡用独立命名
```
地址：`t07/round2/current-vs-previous-kpi.llm-calls.json`；三轮迭代残留 `scratchpad/reask-loop/cases/pre-round2-current-vs-previous-kpi/out-ITER/full-3.dsl`。

### 8. 深层嵌套导致括号失衡
单条语句嵌套 3 层以上 `@Render`/`@Switch`/三元，模型托不住括号配平。触发 `syntax-unclosed-bracket`。

```
// ❌ 原文 (pre-round1-actual-target-gap)
statusCol = Col("Status", "gap", {cell: @Render("v", "row", Tag(@Switch(v < 0 ? "below" : v > 0 ? "above" : "on", {"below": "Below", ...}, ...), ...))})
// ❌ (post-round2-single-record-list) 同类深嵌套

// ✅ 正确：拆成多条命名语句再引用，缩短单条复杂度
```
地址：`t07/round1/actual-target-gap.llm-calls.json`、`t07-after/round2/single-record-list.llm-calls.json`。

### 9. 续写截断 → unresolved-ref（机理 A，已被完整性契约缓解）
模型修好被拦语句后中途停笔，引用了从未定义的下游变量。完整性契约（已导入）针对此，迭代 pass2-3 可救回，但单轮仍偶发。

```
// ❌ 现象 (tc-010)：改对了 .map，但没写完 roamCard/coverageCard/throughputCard 就停
// 终局：[unresolved-ref] unresolved reference "roamCard","coverageCard","throughputCard"
```
地址：`t07-after/round2/tc-010-coverage-quality.llm-calls.json`。

---

## 汇总：错误码频次（初始拦截，temp=0.7 两轮 116 次生成）
| 错误码 | 次数 | 主因 |
|---|---|---|
| `syntax-unexpected-token` | 31 | `.map`/`=>`/`===`/块语句/`.toString` |
| `syntax-unclosed-bracket` | 18 | 深嵌套 + JS 表达式 |
| `unresolved-ref` | 7 | @Render 三参 / 续写截断 |
| `unknown-component` | 4 | 编造小写组件 |
| `excess-args` | 1+ | 过量供参（模型墙） |
| `null-required` | 1 | 必填参给 null |

## prompt 优化的可攻克性分层
- **可攻克（机理 A/JS 泄漏，占比最大）**：1/2/3/4/6 —— 都是 JS 惯性，定向 WRONG→RIGHT 示例 + NEVER 规则有效。当前 `ReaskPromptBuilder` 已覆盖 `.map`/`.toString`，**未覆盖 `===`/`!==`/`.includes`/块语句/编造组件的完整清单**——这是下一步 prompt 优化的主战场。
- **模型能力墙（prompt 难解）**：5/7/8 —— @Render 三参、excess-args、深嵌套括号。三轮完美反馈仍不改，靠 prompt 收益递减（离线实验 V3 加更多示例反降）。
- 主生成 prompt（非 reask）加固更值得：temp=0 时拦截率仅 2.6%，把上述 NEVER 规则前移到生成端，能从源头减少触发。
