# Generated DSL 校验与反思修复分析报告设计

## 目标

重做 `openspec/changes/add-generated-dsl-validation/reask-fix-ab-report.html`，形成一份面向形式化验证专家的中文技术分析报告。报告必须回答四个核心问题：

1. 当前模型具体产生了哪些错误；
2. 系统如何检测错误并构造反思请求；
3. 模型反思后的真实结果是什么；
4. 反思为什么成功或失败。

报告只分析现有机制与证据，不增加形式化验证升级路线，不修改生产代码、测试或实验数据。

## 论证原则

报告采用“证据链式”结构，所有结论按证据强度分级：

- **确定性属性**：由实现约束和当前测试直接支持；
- **经验性结果**：由特定模型、样本、温度和运行轮次支持；
- **已知反例**：存在真实或固定 corpus 反例；
- **证据缺口**：当前代码尚无匹配版本的 live eval。

不得把单元测试通过率表述为形式化证明，不得把离线 corpus 命中率外推为真实模型分布，不得把历史 prompt 的运行结果归因于当前未重新评测的 prompt。

## 报告结构

1. **执行摘要**：用一句话区分 Gate 的结构安全价值与 Reflection Repair 的未收敛状态。
2. **系统边界与属性矩阵**：列出 syntax safety、contract safety、reference closure、data-path correctness、visual quality 的当前状态与证据。
3. **错误分类**：按失效层归类，而非只罗列错误码。
4. **反思机制**：展示 `INVALID → diagnostics → prompt assembly → reask → revalidation → VALID/ERROR` 数据流。
5. **真实运行结果**：展示 116 次 eligible run 的前后汇总、39 fixture 的 Gate A/B，以及当前 corpus/SDK 测试数据。
6. **成功轨迹**：完整展示 `actual-target-gap` 的无效语句、issues、反思原句、模型输出和最终 `VALID`。
7. **失败轨迹**：完整展示 `tc-010-coverage-quality` 的局部语法修复成功、文档续写提前停止和最终 unresolved refs。
8. **失败机理**：区分诊断失败、局部改写失败、全局完整性失败、模型能力墙、accepted-prefix 限制和证据噪声。
9. **已知漏检与证据限制**：列出 data path、字段拼写、单参数 JS 方法、顶层数组跳过、采样方差、judge 噪声和缺失 eval artifacts。
10. **证据台账**：记录文件、运行目录、fixture、模型、温度、样本量和证据强度。

## 错误分类内容

正式报告至少覆盖以下真实错误族，每类包含原始 DSL、validator issues、反思策略和结果解释：

1. `.map(x => x.f)` 等 JavaScript 数组方法与箭头函数；
2. `===`、`!==`、`.includes()` 等 JavaScript 操作符和方法；
3. `.toString()`、`.toFixed()`、`.join()` 等转换方法；
4. `Math`、`Date`、`String` 等 JavaScript 全局对象；
5. 编造或使用过时组件，如小写 helper、`VLayout`、`Text`、`MiniChart`；
6. `@Render` 参数数量与 binder 作用域错误；
7. `@Each` 模板内使用多条赋值语句；
8. 组件 `excess-args`；
9. 深层嵌套括号失衡；
10. 续写截断导致全局引用不闭合；
11. validator 漏检的数据路径、字段拼写和单参数方法调用。

## 反思语句展示规则

报告同时展示两类反思内容，但必须明确区分来源：

- **历史真实运行请求**：直接来自 `target/benchmark-out/**/**/*.llm-calls.json`，可与具体运行结果关联；
- **当前 prompt 构造结果**：来自当前 `ReaskPromptBuilder` 的本地实际执行或 `interception-corpus-dump.md`，只说明当前会发送什么，不宣称已有 live 模型效果。

重点展示的当前语句包括：

- 完整性契约：同类错误全部修复、所有引用最终定义、必须生成 `root`、不得修完当前语句即停止；
- 紧凑语法规则；
- syntax repair hints；
- 按当前无效语句触发的 JavaScript rewrite；
- 结构化 issues、statement id、line/column 和组件签名。

## 真实数据口径

报告使用并标明以下数据集：

- `t07` 两轮：116 eligible，102 VALID，16 次 reask，2 次修复，修复率 12.5%，平均耗时 5095ms，平均首 DSL 3084ms；
- `t07-after` 两轮：116 eligible，104 VALID，14 次 reask，2 次修复，修复率 14.3%，平均耗时 4702ms，平均首 DSL 2689ms；
- Gate A/B：39 个经 Java 管线的 fixture，坏 DSL 交付 1 → 0，平均延迟 +5.3%，首 DSL +526ms，P95 +2359ms；
- 当前 corpus：69 个 valid、46 个 intercepted、3 个 known miss；
- 当前 Java SDK：455 tests，0 failure，0 error。

提交信息中记录的 21% → 58% 与 89.7% → 94.8% 仅作为“后续实验记录”展示，并注明当前仓库缺少与之同等完整的版本化报告，证据强度低于可直接复核的 12.5% → 14.3%。

## 视觉与交互

采用已批准的“A · 验证案卷”视觉方向：

- 暖纸张底色、深墨文字、红色批注与绿色通过状态；
- 衬线正文配等宽证据文本；
- 桌面端固定目录，移动端折叠为单列；
- 首页显示核心结论、关键指标和证据强度；
- 案例使用四段式轨迹：原始错误、诊断、反思、终局；
- 长代码和 prompt 使用可滚动代码块；
- 支持打印样式，打印时隐藏交互控件并保持章节分页可读；
- 单文件 HTML，不依赖外部字体、脚本、CDN 或图片。

页面可以使用少量原生 JavaScript 实现目录高亮、案例折叠和“展开完整 prompt”，但报告正文在 JavaScript 禁用时仍必须完整可读。

## 数据与错误处理

- 缺失的历史 eval run、截图或响应正文必须显示为证据缺口，不得补写或推断；
- 日志只保存请求而未保存模型响应时，使用最终 DSL 与终局 metrics 重建可证事实，并明确不能恢复逐 token 响应；
- 历史 prompt 与当前 prompt 必须有不同标签；
- 所有百分比同时显示分子和分母；
- 所有成功/失败结论必须能追溯到 fixture 或 corpus id。

## 验证方式

完成 HTML 后执行：

1. 搜索占位符、错误编码和失效本地链接；
2. 用浏览器打开 HTML，检查桌面和窄屏布局；
3. 检查目录跳转、折叠、打印样式和无 JavaScript 可读性；
4. 逐项核对报告数值与 `metrics-on.json`、benchmark 报告和 corpus manifest；
5. 确认真实运行 prompt 与当前构造 prompt 的标签没有混淆；
6. 确认未修改生产代码、测试或实验原始数据。
