# react-ui-dsl 术语表

`@cloudsop/openui-react-ui-dsl` — 基于 **Ant Design v5** + **ECharts** 实现的 OpenUI Lang 组件库。与 [react-ui](../react-ui/CONTEXT.md) 平级,定位不同:react-ui 是自带样式的开箱壳层,本包是接入 Ant Design 生态、面向内部业务(如 NOE Mate / eview)的 DSL 库。

整包 `export * from "@cloudsop/openui-react-lang"`,所以下游只装本包即可拿到 [Renderer 等运行时](../react-lang/CONTEXT.md)。

---

## 核心导出

**dslLibrary** — 本包的 `Library` 实例。已注册全部 DSL 组件、根组件 `Stack`、默认 prompt 规则与示例。直接喂给 Renderer 用。

**dslComponentGroups** — 组件分组定义(布局 / 数据展示 / 表单 / 图表 / 反馈 等),决定 prompt 中组件的组织方式。

**DEFAULT_PROMPT_ADDITIONAL_RULES** — 本组件库追加的 prompt 规则文本(对模型生成行为的补充约束)。

**DEFAULT_PROMPT_EXAMPLES** — prompt 中携带的少样本示例(few-shot)。

---

## 组件命名(按角色分组)

下列名字就是 Stream IR 中模型可以使用的组件名,与 [src/genui-lib/](src/genui-lib/) 下的文件同名。

**布局** — `Stack`(根 / 容器,纵横向布局)、`Separator`、`Tabs`、`TimeLine`

**文本与展示** — `TextContent`、`MarkDownRenderer`、`Tag`、`TagBlock`、`Link`、`Image`、`Card`、`CardHeader`、`Descriptions`(配 `DescGroup` / `DescField`)、`List`

**表单** — `Form`、`Input`、`Select`、`Button`

**表格** — `Table`(配 `Col` 子组件;Col 是 SubComponentOf Table)

**图表(ECharts 包装)** — `LineChart` / `BarChart` / `HorizontalBarChart` / `AreaChart` / `PieChart` / `ScatterChart`(配 `ScatterSeries`)/ `RadarChart` / `GaugeChart` / `HeatmapChart` / `TreeMapChart` / `MiniChart`;辅助:`Series`(系列)、`Point`(单点)

---

## 渲染目标(View Target)

**View Target** — 决定 DSL 组件最终渲染到哪套底层组件库的运行时切换机制。

**REACT_UI_DSL_VIEW_TARGET 环境变量** — 切换 view target 的开关:
- 缺省 — 渲染到 Ant Design v5 + ECharts(默认路径)
- `eview` — 渲染到内部 eview 组件库(对接 NOE Mate 业务侧)

适配的目的是:**模型只见到一套 DSL 组件名与 props**,实际像素由 view target 决定,业务侧可以替换底层 UI 库而不动 Stream IR 与 prompt。

---

## E2E 与 Fixtures

**Fixture** — 单个 e2e 用例的输入结构。位于 [src/__tests__/e2e/fixtures/](src/__tests__/e2e/fixtures/),包含 dataModel + 生成意图 + 期望 DSL。

**DSL Snapshot** — 模型针对某 fixture 生成的 OpenUI Lang 文本,以 `.dsl` 文件形式提交到仓库,作为渲染回归基线。位于 [src/__tests__/e2e/snapshots/](src/__tests__/e2e/snapshots/) 与 benchmark snapshots 目录。**手工不可改**——只能通过 `pnpm test:e2e:regen` 重新生成。

**E2E Report** — `pnpm test:e2e:report` 产出的带时间戳 HTML 报告,在 `src/__tests__/e2e/reports/<timestamp>/index.html`。

---

## 外部消费方(仅边界引用,不展开)

本包是 NOE Mate 业务侧 **SmartCanvasService** 生成、**eview** 渲染链路的客户端组件库。业务侧的术语(SmartCanvasService / AICOService / PIU / Reflection 重生成 / 模板在线固化 等)定义在仓库根的 [DESIGN.md](../../DESIGN.md),不在本术语表内重复。

---

## 相关上下文

- 协议与运行时:[../lang-core/CONTEXT.md](../lang-core/CONTEXT.md)、[../react-lang/CONTEXT.md](../react-lang/CONTEXT.md)
- 另一套面向自有设计的标准组件库:[../react-ui/CONTEXT.md](../react-ui/CONTEXT.md)
