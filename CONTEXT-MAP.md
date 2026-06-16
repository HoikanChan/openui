# Context Map

这是一个 monorepo,每个 package 是独立的子领域(bounded context)。各自有自己的术语表 `CONTEXT.md`。本文件只指路、说边界,不重复术语定义。

## 跨上下文核心概念

以下几个概念跨多个 package 出现,语义统一,各 package 各自展开:

- **OpenUI Lang / Stream IR** — 模型生成的、可流式的 UI 中间表示语言。在 [lang-core/CONTEXT.md](packages/lang-core/CONTEXT.md) 定义,被 react-lang / react-ui-dsl / openui-cli 复用。
- **Library** — 组件库定义(组件名 + props schema + 渲染实现 + 描述)。在 lang-core 定义无渲染版本,在 react-lang 扩展为带 React 渲染实现的版本。
- **Renderer** — 把 Stream IR 流式渲染成 React 树的运行时,定义在 [react-lang/CONTEXT.md](packages/react-lang/CONTEXT.md)。

## Package 边界

| Package | 角色 | 依赖方向 |
|---|---|---|
| [lang-core](packages/lang-core/CONTEXT.md) | 框架无关的 Lang 内核:parser / runtime / prompt 生成 / 校验 | 无上游 |
| [react-lang](packages/react-lang/CONTEXT.md) | React 渲染运行时:Renderer、hooks、表单校验 | → lang-core |
| [react-headless](packages/react-headless/CONTEXT.md) | 无头聊天状态层:thread、stream 适配器、消息格式 | 独立(不依赖 lang) |
| [react-ui](packages/react-ui/CONTEXT.md) | 预制 UI 组件库 + Shell/CopilotShell 布局 + genui-lib 标准库 | → react-lang, react-headless |
| [react-ui-dsl](packages/react-ui-dsl/CONTEXT.md) | 基于 Ant Design v5 + ECharts 的 DSL 组件库(供 NOE Mate / eview 场景) | → react-lang |
| [openui-cli](packages/openui-cli/CONTEXT.md) | 脚手架 + 从组件库导出 system prompt 的 CLI | → react-lang(template) |
| [genui-java-sdk](CONTEXT.md) | Java 后端 SDK:Generation Context 注册 + prompt 拼装(与 lang-core prompt 字节对齐);术语在系统词汇表 | 无上游(镜像 lang-core prompt 语义) |

## Examples 边界

- `examples/react-ui-dsl-demo` — react-ui-dsl 的前端演示(DSLEngine 消费方)。
- `examples/genui-service` — **GenUI Service**:genui-java-sdk 的 REST 参考实现(SmartCanvasService 契约的可运行模板),供公司内部仿照搭建;术语在[系统词汇表](CONTEXT.md)。

## 不在术语表里的东西

`CONTEXT.md` 是**纯词汇表**,不写:
- 实现细节、文件路径、API 签名(这些读代码即可)
- 路线图、待办、bug(用 issue 系统)
- 架构决策的理由(写 `docs/adr/`)

业务侧术语(如 NOE Mate 的 SmartCanvasService、AICOService、PIU、eview)**不属于** openui 本仓——它们是 [DESIGN.md](DESIGN.md) 描述的下游消费方,本仓 CONTEXT 只在确有边界引用时一句话带过,不展开。
