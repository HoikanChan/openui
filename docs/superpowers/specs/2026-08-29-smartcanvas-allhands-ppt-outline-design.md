# 生成式 UI 技术趋势与 SmartCanvas 工程实践：全员技术分享大纲

## 分享定位

- 听众：公司全员，默认具备基本 AI 产品认知，但不要求了解前端 DSL、Agent 协议或 SmartCanvas 实现。
- 时长：30 分钟，其中正文约 27.5 分钟，讨论约 2.5 分钟。
- 目标：让听众理解生成式 UI 的技术趋势，并认可 SmartCanvas 将 OpenUI 工程化、产品化的价值。
- 核心结论：生成式 UI 的竞争重点正在从“模型能否生成页面”转向“企业能否稳定、低成本、可治理地交付动态界面”；SmartCanvas 的价值是补齐从开源生成框架到企业级交付之间的工程鸿沟。

## 叙事结构

1. 行业趋势：生成式 UI 是什么，业界形成了哪些路线。
2. 技术选型：为什么企业场景更适合声明式生成，为什么选择 OpenUI。
3. 商用实践：SmartCanvas 如何解决业务扩展、性能、大数据和质量问题。
4. 后续演进：从生成引擎升级为可验证、可修复、可观测、可降级的可信运行时。

## 逐页内容

### 第 1 页：从“回答问题”到“生成可操作界面”

- 时间：1 分钟。
- 本页结论：大模型的输出形态正在从文本答案扩展为可浏览、可筛选、可操作的动态界面。
- 页面内容：
  - 标题：生成式 UI：从技术趋势到 SmartCanvas 工程实践。
  - 副标题：如何把模型生成的界面变成可商用能力。
- 讲述重点：不要从项目架构开场，先让所有听众理解为什么这件事值得讨论。
- 转场：如果模型可以生成界面，业界究竟在生成什么，又通过什么方式交付？

### 第 2 页：Generative UI 改变的不只是回答格式

- 时间：2 分钟。
- 本页结论：文本适合解释，界面适合比较、探索和行动；生成式 UI 的价值是缩短“理解结果—采取行动”的路径。
- 页面内容：
  - 同一个业务任务的两种结果：一段文字与一个包含图表、表格、筛选和操作的界面。
  - 三类增量价值：信息密度更高、数据关系更清楚、结果可以继续操作。
- 讲述重点：用业务体验建立共识，不把 GenUI 描述成“AI 自动写前端代码”。
- 转场：要看清技术趋势，需要先把“界面出现在哪里”和“模型生成什么”分开。

### 第 3 页：生成式 UI 已经形成两层技术栈

- 时间：2 分钟。
- 本页结论：Transport 和 Generation 是两项独立选择，讨论 GenUI 时不应混为一谈。
- 页面内容：
  - Transport：在自有前端中交付可关注 AG-UI；在第三方宿主中交付可关注 MCP Apps。
  - Generation：Static、Declarative、Open-ended。
  - SmartCanvas 的主要问题位于 Generation 层，同时运行在公司自有产品界面中。
- 讲述重点：只解释判断框架，不展开协议细节。
- 依据：`docs/prototypes/openui-blog.md`。
- 转场：在生成层，三条路线实际上是控制力与创造力之间的不同取舍。

### 第 4 页：三种生成路线，本质是控制力与自由度的取舍

- 时间：2 分钟。
- 本页结论：声明式生成是多数企业场景在可控性与动态性之间的平衡点。
- 页面内容：
  - Static：从预制组件中选择，稳定但新增场景依赖研发。
  - Declarative：模型组合受控组件，兼顾动态布局和企业设计体系。
  - Open-ended：模型生成 HTML、CSS、JS，自由但带来安全、风格、时延和稳定性成本。
- 讲述重点：三种路线没有绝对优劣，应按场景选择；SmartCanvas 的默认路径是 Declarative，未来可考虑在受控沙箱中提供局部 Open-ended 逃生口。
- 转场：先把三条路线逐一展开，再回到 SmartCanvas 的技术选型。

### 新增第 5 页：Static 用预制组件换取确定性

- 时间：1.5 分钟。
- 本页结论：Static 让模型只选择已经注册的组件并填写 Props，因此视觉、安全和行为都由宿主预先确定。
- 页面内容：
  - 单一运行链路：用户意图 → Tool Call → 组件注册表 → Props 校验 → 预制组件。
  - 代码锚点：`packages/react-headless/src/stream/processStreamedMessage.ts` 对 `TOOL_CALL_START` 与 `TOOL_CALL_ARGS` 的增量处理。
  - 适用边界：客服卡片、金融看板等答案形态有限且需要强一致性的场景。
  - 主要代价：每一种新答案形态都需要新增组件，能力目录随研发迭代而增长。
- 视觉：左侧画运行链路，右侧放精简事件处理代码；不使用多卡片布局。
- 转场：Static 只能“选择一个组件”，Declarative 则允许模型用同一套组件目录组织完整界面。

### 新增第 6 页：Declarative 的三种格式共享架构，取舍不同

- 时间：2 分钟。
- 本页结论：OpenUI、A2UI 与 json-render 都让模型组合受控组件，但分别优化紧凑表达、跨端协商和多目标渲染。
- 页面内容：按文章顺序，每种格式独占一行。
  - `OpenUI Lang`：行式 DSL，`id = Component(args)`；支持前向引用和骨架渐进显示；组件 Schema 可生成模型 Prompt。
  - `A2UI`：通过 `createSurface`、`updateComponents`、`updateDataModel` 分离结构与数据；强调 Catalog 协商以及 Flutter、Angular、Lit 等客户端。
  - `json-render`：对扁平元素树流式发送 JSON Patch；突出 Web、Email、PDF、Terminal 等渲染目标覆盖。
- 代码：每行只保留一条最能代表 wire format 的示例，避免三段完整代码造成拥挤。
- 页尾判断：共同安全边界是“模型只能调用 Catalog 已注册的能力”；SmartCanvas 选择 OpenUI，重点在紧凑输出与流式首屏。
- 外部依据：OpenUI《The State of Generative UI in 2026》Generation / Declarative 章节。
- 转场：当 Catalog 无法覆盖一次性创意界面时，才进入 Open-ended。

### 新增第 7 页：Open-ended 自由度最高，也最考验首屏时延

- 时间：1.5 分钟。
- 本页结论：模型直接生成 HTML、CSS 与 JavaScript，适合一次性、探索式或创意输出，但不应让用户等待完整页面生成后才看到结果。
- 页面内容：
  - 左侧预留 16:9 截图框，标注“案例截图待补：Claude Artifact / 灵光”，不生成或伪造产品截图。
  - 右侧展示链路：Prompt → HTML/CSS/JS → Sandbox iframe → Interactive artifact。
  - 明确代价：输出 Token 多、完整页面生成慢、运行结果波动、无法自然继承宿主设计 Token，并需要严格沙箱隔离。
  - 工程红线：`3 秒内出现首屏`。首屏以可感知的壳、骨架或首个可用区域为准，不等待所有脚本和内容完成。
  - 达成方式：预构建 Artifact Shell；HTML 分段/流式到达；首屏关键结构优先；非关键脚本与数据延后；超时则显示可操作降级界面。
- 说明：本页不绑定当前仓库实现，待演讲者确定 Claude Artifact 或灵光案例后替换截图占位框。
- 外部依据：OpenUI 文章对 Open-ended 的延迟、风格一致性、沙箱和运行波动分析；`3 秒`为本次分享提出的产品体验目标，不表述为文章数据或当前实测成绩。
- 转场：自由度越高，延迟与治理成本越大；这进一步解释 SmartCanvas 为什么把 Declarative 作为默认路径。

> 插页后，原第 5–14 页顺延为第 8–17 页；其内容和相对顺序保持不变。

### 第 5 页：声明式方案相似，格式效率却会产生真实成本

- 时间：2 分钟。
- 本页结论：模型逐 Token 输出结构时，协议冗余会同时放大时延和成本。
- 页面内容：
  - 比较 OpenUI、A2UI、json-render 的共同点：组件目录、结构化输出、流式渲染。
  - 关键差异：表达格式、流式模型、生态覆盖和扩展方式。
  - JSON 的重复键、引号和 Patch 包装是用户不可见但持续付费的开销。
- 讲述重点：不把比较讲成“谁全面胜出”，而是说明 SmartCanvas 的约束条件更重视紧凑输出、流式体验和组件扩展。
- 转场：这正是我们选择 OpenUI 的原因，但 Token 并不是唯一理由。

### 第 6 页：我们选择 OpenUI，因为它同时满足三项约束

- 时间：2 分钟。
- 本页结论：OpenUI 为 SmartCanvas 提供了紧凑、流式、可扩展的声明式基础。
- 页面内容：
  1. 语法紧凑：七类基准场景合计 4,800 tokens；json-render 为 10,180，减少 52.8%。
  2. 流式优先：允许结构先出现、后续声明逐步填充，适合边生成边渲染。
  3. 契约可扩展：组件、工具、示例和业务规则可以进入模型可见的能力目录。
- 讲述重点：把“Token 少”翻译为用户价值——更快看到结果、更低推理成本、更少格式错误机会。
- 依据：`docs/content/docs/openui-lang/benchmarks.mdx`、OpenUI 官方仓库。
- 转场：OpenUI 解决了语言和运行时基础，却没有自动解决企业交付问题。

### 第 7 页：开源框架只是起点，商用化还有四道门槛

- 时间：1.5 分钟。
- 本页结论：SmartCanvas 的工程价值集中在 OpenUI 之上的企业能力层。
- 页面内容：
  - 下游业务扩展：不同业务有不同组件、工具、规则和数据模型。
  - 性能：流式时延、重复生成和模型调用成本。
  - 大数据：模型无法直接消费万级行或万级点的完整数据。
  - 质量：能解析不等于业务表达正确，更不等于界面可用。
- 讲述重点：本页承担从“选型介绍”到“项目实践”的明确转折。
- 转场：SmartCanvas 的总体设计，就是围绕这四个问题建立一条可治理链路。

### 第 8 页：SmartCanvas 把生成、协议和渲染解耦

- 时间：2 分钟。
- 本页结论：生成侧不渲染，渲染侧不生成，双方通过 OpenUI DSL 与组件契约解耦。
- 页面内容：
  - 上游：业务意图、领域数据、领域扩展。
  - 后端 Java Generation SDK：契约注册、Prompt 组装、数据特征化、模型适配。
  - 中间协议：OpenUI DSL 流式输出。
  - 前端 DSL Engine：解析、数据与算子、组件封装、画布编排和渲染。
  - Eval：独立质量平面，复用生产生成路径并反馈到 Prompt、引擎和组件。
- 讲述重点：架构图只讲主链路和边界，不逐个朗读模块。
- 依据：`docs/prototypes/smartcanvas-architecture-prototype.html`、`docs/SYSTEM-DESIGN.md`。
- 转场：有了稳定边界，业务扩展才能从定制页面变成可注册能力。

### 第 9 页：业务接入从“开发页面”变成“注册领域能力”

- 时间：2 分钟。
- 本页结论：同一个业务拓展包通过后端契约注册和前端 PIU 实现注册两条链路接入 SmartCanvas，分别解决“模型能生成什么”和“前端如何渲染”。
- 页面内容：
  - 后端生成链路：业务将组件契约、工具、模板、示例和规则组成的拓展包注册到 NetGraph Pub 服务；SmartCanvas 后台按 `extensionId` 读取契约并组织 Prompt，让模型知道可使用哪些领域能力。
  - 前端运行链路：拓展包声明 `sourcePiu`，SmartCanvas 拉起业务 PIU；业务 PIU 通过 `smart-canvas:extend` 事件 emit 组件和工具的前端实现，注册到 SmartCanvas 运行时。
  - 模型输出的 OpenUI DSL 只携带组件名、字段映射和数据引用；Renderer 再按同名组件找到 PIU 注册的真实实现。
  - 两条链路以 `extensionId`、组件名和契约版本对齐，缺少任意一侧都不能形成可用的业务扩展。
- 简图：采用上下双泳道，避免画成严格串行流程。

```text
                         后端：让模型知道“能生成什么”
业务拓展包 ─ 契约 / 工具 / 规则 ─→ NetGraph Pub ─→ SmartCanvas 后台 ─→ Prompt / LLM ─→ OpenUI DSL
    │                                                                                         │
    │                    extensionId · 组件名 · 契约版本保持一致                              │
    │                                                                                         ↓
    └─ sourcePiu / 前端实现 ─→ 业务 PIU ── emit smart-canvas:extend ─→ SmartCanvas 运行库 ─→ Renderer
                         前端：让画布知道“组件如何渲染”
```

- PPT 绘制建议：只使用两条水平箭头和六个节点；中间用一条浅色横带标注 `extensionId / component name / version`，OpenUI DSL 到 Renderer 使用一条向下连接线。
- 讲述重点：后台不保存 React 实现，前端也不负责组织模型 Prompt；前后端通过同源契约协作而不是彼此耦合。
- 已有依据：Extension JSON、Java SDK 注册、`sourcePiu`、PIU `smart-canvas:extend` 运行时扩展链路。
- 后续风险：需要继续建设契约版本、前后端兼容性检查和拓展包发布治理。
- 转场：扩展能力解决“能生成什么”，大数据问题则决定模型“需要看到什么”。

### 第 10 页：大数据场景的关键是让模型看结构，让运行时拿全量

- 时间：2.5 分钟。
- 本页结论：模型生成布局不需要读取全部数据，Prompt 数据与渲染数据应走两条通路。
- 页面内容：
  - Render Data Model：完整数据进入运行时，保证最终界面可绑定全量结果。
  - Prompt Data Model：保留少量样本，并生成 TypeScript 类型 Sidecar。
  - Sidecar 保存真实数组数量、完整低基数枚举和字段结构，避免采样导致认知错误。
  - 代表性测试：1 万行宽表的 Prompt 副本约为原始大小的 0.05%，1 万点数值序列约为 0.06%。
- 讲述重点：这不是简单截断，而是确定性的、无模型参与的数据特征化过程。
- 依据：`packages/genui-java-sdk/README.md`、CharacterizationEffectTest。
- 转场：减少输入解决了规模问题，但模型输出依然可能在语法、契约或业务表达上犯错。

### 第 11 页：一张 Table，体现 DSL 如何从“传数据”走向“声明意图”

- 时间：2 分钟。
- 本页结论：可商用 DSL 不应让模型重复生成全量数据和底层组件配置，而应让模型声明数据引用、字段映射和受控的渲染意图。
- 页面内容：
  - 原来的问题：Table 生成同时携带完整数据、列配置和底层渲染细节，导致 Token 随数据量增长，模型容易写错配置，也难以安全表达自定义单元格。
  - 第一步改造——数据引用：Table 直接绑定运行时数据 `data.orders`，全量数据不再复制到生成结果。
  - 第二步改造——字段映射：每个 `Col` 只声明列名和字段路径，运行时负责把 DSL 映射为真实 Table 配置。
  - 第三步改造——受控自定义渲染：`@Render` 把当前值 `v` 和当前行 `row` 绑定到一个受控组件表达式，不允许模型注入任意 JavaScript。
- 页面中的真实示例：

```openui
ordersTable = Table([idCol, statusCol], data.orders)
idCol = Col("订单号", "id")
statusCol = Col("状态", "status", {
  cell: @Render("v", "row", Tag(v, v == "paid" ? "success" : "warning"))
})
```

- 示例拆解：
  - `data.orders`：数据仍由业务运行时提供，模型只建立引用。
  - `Col("订单号", "id")`：用字段映射替代完整列对象和数据搬运。
  - `@Render("v", "row", ...)`：在明确的变量作用域内组合已注册组件，可实现状态标签、格式化、链接、小趋势图等定制单元格。
- 讲述重点：
  - 这个改造同时降低 Token、稳定 Schema，并保留业务所需的表现力。
  - 表现力来自 DSL 算子和组件契约，而不是重新开放任意代码执行。
  - 它也带来新的正确性要求：字段路径必须存在、`@Render` 变量作用域必须正确、返回组件必须符合契约。
- 已有依据：Table / Col 组件式 DSL、DataModel 绑定、`@Render` lazy builtin 与单元格渲染 E2E Fixture。
- 转场：这类改造是否真的让结果更正确、更完整、更易用，不能只看 Parser 通过，需要用最终渲染结果做 Eval。

### 第 12 页：Eval 把主观体验变成可回归的工程指标

- 时间：2.5 分钟。
- 本页结论：GenUI 质量必须评测最终生成与渲染结果，而不是只测 Parser 或组件代码。
- 页面内容：
  - 流程：业务 Fixture → 生产同源生成 → DSL 解析与渲染 → 截图 → 多模态裁判 → 失败模式 → 修改后全量验证。
  - 维度：组件选择、数据完整性、格式质量、布局连贯性。
  - 经验：
    - Eval 生成必须走与生产一致的 Java SDK 链路。
    - 总分用于看方向，失败模式用于指导修改，Delta 用于阻止回归。
    - Judge 需要人工校准，避免把裁判偏好误当成产品质量。
    - Prompt、引擎和组件库需要在同一闭环中迭代。
- 依据：`packages/react-ui-dsl/README.md`、`openspec/specs/genui-eval-loop/spec.md`、`docs/adr/0005-eval-generation-via-java-sdk.md`。
- 转场：Eval 告诉我们哪里出错；下一阶段要让运行时在结果到达用户前自动发现并处理错误。

### 第 13 页：下一阶段，从生成引擎升级为可信运行时

- 时间：3 分钟。
- 本页结论：未来重点不是单纯更换更大的模型，而是建立约束、验证、修复、降级和观测组成的可靠性闭环。
- 页面内容：
  1. 生成前约束：组件白名单、契约 Prompt；评估语法约束解码，降低不可解析输出。
  2. 确定性后置校验：对 AST、组件、参数、引用、数据路径、工具权限和复杂度预算做可执行检查。
  3. 定向修复：输出结构化错误，只重写失败语句或子树，避免整页重生成。
  4. 有界反思：仅对语义类错误启用，最多一至两次，并设置 Token、时延和错误类型门槛。
  5. 降级策略：使用 Extension Template、Generated Template Cache、静态组件或文本结果兜底。
  6. 线上治理：记录模型、Prompt、契约版本、校验错误、修复次数、最终结果和用户反馈。
- 讲述重点：
  - “形式化后置校验”在当前阶段应定义为确定性、可重复、可测试的契约检查，而不是定理证明。
  - 约束解码可以保证结构合法，但无法保证组件选择、数据表达和业务语义正确，因此仍需要后置验证与 Eval。
  - OpenUI Cloud 已将输出校验与纠正、Provider Fallback、持久化、可观测性和审计列为生产能力，可作为路线参考，而不是直接照搬其产品形态。
- 外部依据：OpenUI Cloud 官方说明、OpenAI Structured Outputs、Spring AI Schema Validation。
- 转场：回到开场，真正有价值的不是模型偶尔生成一个漂亮页面，而是系统持续交付可信交互。

### 第 14 页：生成式 UI 的终点是可信地交付交互

- 时间：1 分钟。
- 本页结论：SmartCanvas 不是一个页面生成 Demo，而是一套把动态生成能力带入企业业务的工程体系。
- 页面内容：
  - 趋势：AI 输出正在从文本走向动态界面。
  - 选型：OpenUI 提供紧凑、流式、可扩展的声明式基础。
  - 实践：SmartCanvas 补齐业务扩展、企业组件、大数据和质量工程。
  - 未来：让每次生成都可验证、可修复、可观测、可降级。
- 收束句：我们要交付的不是“模型生成了一段 DSL”，而是“用户获得了一次可信、可操作的业务体验”。

## 时间分配

| 部分 | 页码 | 时间 |
| --- | --- | ---: |
| 开场与行业趋势 | 1–4 | 7 分钟 |
| 技术选型 | 5–7 | 5.5 分钟 |
| SmartCanvas 商用实践 | 8–12 | 11 分钟 |
| 后续路线与收束 | 13–14 | 4 分钟 |
| 讨论 | — | 2.5 分钟 |

正文合计约 27.5 分钟。希望保留更充足的讨论时间时，可将第 3 页和第 5 页各压缩 30 秒，将讨论时间扩展至 3.5 分钟。

## 数据口径

- OpenUI Token 数据来自仓库可复现基准，可作为实测结果表达。
- Characterization 压缩比例来自 Java SDK 的代表性测试 Fixture，应明确是 Prompt 数据副本的压缩效果，不是业务数据本身被删除。
- `95%` 生成成功率、端到端 P95 `< 5s`、模板命中 P95 `< 200ms` 在 `docs/SYSTEM-DESIGN.md` 中属于目标或验收规格，不应作为已实现结果表达。
- 模板在线固化、完整服务端校验、Reflection 重生成和自动降级在系统设计中标记为后续生成侧能力；分享中应放在路线图或建设中能力，而不是已交付成绩。
- Eval 的 Fixture 数量会继续变化，正文建议使用“覆盖典型组件与业务场景”，不要写死数量。

## 主要资料

- 本地行业综述：`docs/prototypes/openui-blog.md`
- SmartCanvas 架构草图：`docs/prototypes/smartcanvas-architecture-prototype.html`
- SmartCanvas 系统设计：`docs/SYSTEM-DESIGN.md`
- OpenUI 基准：`docs/content/docs/openui-lang/benchmarks.mdx`
- OpenUI 扩展：`docs/content/docs/openui-lang/extending-openui.mdx`
- Java Generation SDK：`packages/genui-java-sdk/README.md`
- Eval Loop：`packages/react-ui-dsl/README.md`
- OpenUI 官方仓库：https://github.com/thesysdev/openui
- OpenUI Cloud：https://www.openui.com/cloud
- OpenAI Structured Outputs：https://openai.com/index/introducing-structured-outputs-in-the-api/
- Spring AI Schema Validation：https://docs.spring.io/spring-ai/reference/api/structured-output/validation.html

## 自检结论

- 大纲区分了行业事实、仓库已有能力、目标规格和后续路线。
- 每页只有一个主要结论，并通过转场形成连续叙事。
- 重点集中在趋势认知和 SmartCanvas 工程价值，没有扩展到具体 API 教程或代码演示。
- 30 分钟内可完成；如果现场演示实际产品，需要删除或合并一页以腾出演示时间。
