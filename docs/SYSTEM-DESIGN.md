# FUNC002026050925327669 数据驱动UI生成

## 6.2 功能模块

### 6.2.1 功能概述

**业务背景**

在 NOE Mate 业务场景中，统一 portal 是基础能力，需要兼容传统 UI、驾舱界面以及 Skill 输出结果的可视化呈现。随着 Agents/Skills 输出的数据类型和交互形态增多，单纯返回文本或固定页面难以满足用户对结果浏览、筛选、钻取、跳转和叠加展示的要求。

**要解决的问题**

当前 Skill 输出与前端展示之间缺少统一的 UI 生成协议和渲染链路，导致不同场景需要定制页面或定制组件接入，复用度低、交付周期长。对于数据量较大、交互要求较高或组件能力需要扩展的场景，还需要统一处理生成质量、流式渲染、异常兜底和组件适配问题。

**目标能力**

本功能面向 Agents/Skills 输出结果，提供数据驱动的 UI 生成能力：根据用户意图、业务数据和生成建议生成 Stream IR，并由前端 Renderer 流式解析、绑定数据、调用组件库完成可视化渲染。

**本功能在 NOE Mate / Skill 输出可视化里的价值**

通过统一的生成协议、渲染运行时和组件适配方式，将 Skill 输出从文本或静态结构升级为可交互、可扩展、可降级的界面结果，降低业务场景接入成本。

### 6.2.2 实现思路

本功能采用端到端生成与渲染链路实现：

1. 前置 Agents/Skills 根据用户意图生成数据，并产生UI生成建议，包括组件布局，扩展组件、UI交互等信息
2. AICOService 透传或编排生成请求，将用户意图、业务数据和生成建议转发给 SmartCanvasService。
3. SmartCanvasService 组装生成上下文和提示词，约束大模型输出 Stream IR。
4. 前端 Renderer 接收 Stream IR 流式内容，通过 parser 解析、dataModel 绑定和组件库映射完成增量渲染。
5. 当生成、解析或渲染失败时，通过错误反馈、重试、降级展示等机制兜底，确保用户仍可获得可读结果。

### 6.2.3 功能规格

**表 6-1 功能规格**

| 规格名称 | 描述 | 分类 | 规格值 | 引入版本 | 支持领域 | 假设与约束 | 备注 |
|---|---|---|---|---|---|---|---|
| AI 生成 UI IR | 支持通过提示词生成 UI 界面的 IR 中间表示 | 目标规格 | 支持生成通用 UI 组件、交互事件的 IR | NetGraph-Smart V100R027C10 | | | 当前已有 parser / Renderer / react-ui-dsl 基础 |
| 扩展组件提示词长度 | 单个扩展组件的描述长度上限 | 目标规格 | 100 token | NetGraph-Smart V100R027C10 | | 含名称、描述、propsSchema | 后续开发 |
| 支持扩展组件数量 | 单个 Generation Extension 中扩展组件数量上限 | 目标规格 | 30 | NetGraph-Smart V100R027C10 | | 扩展组件通过 Extension 注册接口注册 | 后续开发 |
| UI 生成成功率 | 生成的 IR 通过语法与类型校验，且端侧成功渲染的比例 | 目标规格 | 95% | NetGraph-Smart V100R027C10 | | 基于内部测试集统计 | 作为验收目标统计 |
| 端到端生成时延 | 从接收请求到返回完整 IR 的耗时（P95） | 目标规格 | < 5s | NetGraph-Smart V100R027C10 | | 不含模板命中场景 | 作为验收目标统计 |
| 模板命中时延 | 模板命中场景下接口响应时延（P95） | 目标规格 | < 200ms | NetGraph-Smart V100R027C10 | | | 后续开发 |

### 6.2.4 实现设计

#### 6.2.4.1 总体架构

本功能采用“生成侧 + 协议层 + 渲染侧 + 组件库”的分层设计。

| 层级 | 架构元素 | 设计职责 |
|---|---|---|
| 业务输入层 | Agents/Skills | 产生用户意图、业务数据和 UI 生成建议 |
| 编排接入层 | AICOService | 透传或编排 UI 生成请求，统一接入 SmartCanvasService |
| 生成侧 | SmartCanvasService | 组装 prompt、调用大模型、约束生成 Stream IR，并提供校验、重试、降级、模板能力 |
| 协议层 | Stream IR | 作为服务侧与前端之间的 UI 中间表示，描述组件、数据绑定、状态、表达式和交互 |
| 渲染侧 | Renderer / parser / dataModel | 流式接收 Stream IR，解析为可渲染结构，完成数据绑定、表达式求值和错误隔离 |
| 组件库层 | react-ui-dsl / eview | 管控组件能力、组件提示信息、组件属性映射和 eview 适配 |

整体链路中，SmartCanvasService 不直接渲染 UI，只负责生成和治理 Stream IR；前端 Renderer 不参与大模型生成，只负责解释协议、绑定运行时数据并调用组件库渲染。

#### 6.2.4.2 生成侧设计：SmartCanvasService

SmartCanvasService 是后续开发的生成侧服务，负责将用户意图、业务数据和 UI 生成建议转换为可被前端消费的 Stream IR。其核心能力包括 拓展能力注册、prompt 组装、上下文压缩、模板在线固化、生成结果校验、Reflection 重生成和异常降级。

全链路统一使用 `Extension ID` / `extensionId` 作为生成扩展配置的选择标识，不再使用 `Context ID` / `contextId` 作为公开概念或接口字段。原先的 Generation Context 概念统一收敛为 Generation Extension。

**1. 提示词管理**

将扩展组件描述、UI 建议信息拼装为完整 prompt，发送给大模型。生成内容支持：

UI 组件（内置组件 + 扩展组件）数据处理表达式（详见表 6-2）Action 动作（PIU 事件、跳转事件、点击事件等）

数据绑定语法：

{data.\*}：访问 DataModel 数据{$varName}：访问状态变量

支持组件

**2. 前置处理**

data使用前需要进行预处理，需要讲data中无用字段进行过滤。

计算数据 token 长度，超过 2K 时尝试进行采样压缩，缓解超大数据导致的 prompt 膨胀问题。

* 压缩范围：仅对数组结构进行采样压缩，嵌套对象与非数组结构不参与压缩
* 采样策略：保留数组头部、尾部及中间均匀采样的元素，保证数据分布特征
* 恢复机制：大模型输出 Stream IR 后，对 data 部分进行数据恢复，将完整数据回填至最终响应
* 约束：模型仅可对采样数据建立绑定引用关系（{data.\*}），不可对数据本身做变换

**3. 扩展组件注入**

Skill 输出的生成建议中包含扩展组件信息，包括扩展组件名称、描述和参数类型描述（propsDes）。扩展组件信息用于 prompt 注入，使大模型可选用扩展组件；同时用于后续类型校验，检查组件参数类型与取值范围。

**4. 校验反思**

对大模型生成的 Stream IR 进行多维校验，校验失败时通过**反思**机制将错误反馈给大模型重新生成。

**校验维度：**

语法校验：Stream IR 词法、语法结构是否合法类型校验：

组件 props 类型、表达式参数类型是否匹配

引用完整性：被引用的标识符（如 tbl = Table([col1, col2]) 中的 col1）是否已定义

**反思重生成**：

将校验错误信息作为上下文反馈给大模型，要求其修复后重新输出

重试策略：最多 N 次（N 可配置，建议默认 2 次），超过阈值进入降级流程

**5. 模板在线缓存**

以 (dataSource, intent, apiDoc) 三元组作为模板唯一标识，自动匹配已生成的模板，解决相同场景重复调用大模型导致的生成效率低问题。

**唯一标识：key = hash(dataSource + intent + extensionId + extensionVersion)**

dataSource：由 Skill 在生成建议中显式传入，标识数据来源（如 apiName 或自定义来源标识）

intent：用户意图，需做归一化处理（大小写、空白、标点统一）

extensionId / extensionVersion：本次所选的 UI 生成扩展配置及版本号（见本节 8），未携带 extensionId 时取空值

apiDoc：api文档，可选。

* 固化流程：大模型生成的 Stream IR 通过语法与类型校验后，自动以 key 固化入 Redis 缓存
* 命中流程：每次生成请求先查缓存，命中则跳过大模型调用，直接返回模板 Stream IR，并注入运行时数据
* 模板与数据解耦：固化内容为 Stream IR 结构与 {data.\*} 绑定关系，不固化具体数据值
* 扩展组件兼容性校验：命中时校验当前请求的扩展组件集合是否为模板生成时的超集，不兼容则视为未命中
* 失效策略：扩展组件 schema 变更或 Stream IR 语法版本升级时批量失效；提供手动失效接口作为运维兜底

**Redis 规格与容量设计**

* **实例规格**：256 MB，单节点（可按需升级为主从）
* **最大容量**：约 1 万条模板（单条 Stream IR 平均 ~20 KB，10000 × 20 KB = 200 MB，留 56 MB 作为键元数据与碎片缓冲）
* **键结构**：template:{hash} — 纯字符串键，hash 为 SHA-256 截取前 16 字节的十六进制串（32 字符），键本身极小，内存开销可忽略
* **值结构**：JSON 序列化的 Stream IR，含扩展组件 schema 指纹与 {data.\*} 绑定关系，不含具体数据值
* **淘汰策略**：allkeys-lru，Redis 内存压力时自动淘汰最久未使用的模板；业务层无需感知，下次请求重新生成并回填

**6 生成自定义拓展**

业务往往有自己的专用组件和专用操作——比如告警运维场景，有「告警级别标签」这种专用组件、「确认告警」这种专用操作。这些东西模型本来不认识，也就不会生成出来。

业务方**提前把自己的扩展组件和工具作为 UI 生成扩展配置等通过扩展注册接口进行注册**，之后这个业务的每次生成请求，只要报上 `extensionId`，服务就自动把对应的扩展能力带进提示词。

注意服务只记录「扩展描述」（给模型看、给服务校验用的元数据），组件的实际渲染代码仍在前端（见 6.2.4.5），两边用同一个组件名对齐。

**能扩展什么**

| 扩展什么 | 用来做什么 |
| --- | --- |
| 组件 | 新增模型能生成、前端能渲染的 UI 组件 |
| 工具 | 提供可被调用的 tool 函数，实现 UI 查询数据或发起变更请求；描述结构与 MCP Tool 定义保持兼容 |
| Extension Template | 业务手写、随 Generation Extension 注册的 Stream IR 模板，可通过 `templateId` 直接复用；与服务在线固化的 Generated Template Cache 是两类对象 |
| 提示词规则 | 约束生成风格、业务规则、组件取用偏好 |
| 生成示例 | UI范例，提升生成稳定性 |

**完整流程**

![](data:image/png;base64...)

**完整流程**

从前端定义组件，到最终在页面上渲染出来，整条链路如下：

| 步骤 | 谁做 | 做什么 |
| --- | --- | --- |
| 1 定义拓展能力 | 业务方前端 | 用 defineComponent 写清组件名、参数、说明和渲染代码；按 MCP Tool 兼容结构写清工具名、说明和输入输出 schema（见 6.2.4.5） |
| 2 注册前端组件和工具 | 业务方前端Piu | 给DSLEngine传入待拉起的业务piu，进行自动拉起。业务piu待拉起后，通过piu事件给DSLEngine注册前端组件和工具 |
| 3 导出组件规格 | 前端工具链 | 调用 DSLEngine 暴露的 `generateComponentSpecs`，从多个前端组件定义生成 `components` JSON 片段（见 6.2.4.5） |
| 4 注册扩展配置 | 业务方 | 业务方维护完整扩展配置 JSON，并把组件规格导出结果填入 `components` 后，通过 `/extensions` POST 接口注册（业务方直连，不经 AICOService） |
| 5 发起生成 | Skill / AICOService | 生成请求带上 extensionId 选好扩展配置，需要时再临时附带一次性工具或规则 |
| 6 渲染 | DSLEngine | 按组件名和工具名，找到对应注册好的拓展内容 |

拓展内容横跨两头：

* **描述**（步骤 3-4，让模型知道有这个组件、能生成、能校验）
* **渲染代码**（步骤 1-2，让前端能画出来）。

两头靠同一个组件名对齐，缺一边这个组件就会失效

**7. UI 生成接口**

**接口说明**：

| 说明项 | 内容 |
|---|---|
| 接口名称 | `SSE /rest/smartcanvas/v1/ui/generate` |
| 功能 | 生成 Stream IR |
| 影响 | LUI 对话框与 Canvas 画布入口需要适配流式接口返回内容 |
| 请求体 | `UIRequestDetail`，生成 UI 接口对应的请求体 |
| 返回类型 | SSE 流式返回 |
| SSE 事件 | `data`、`done`、`error` |

**Request 入参（UIRequestDetail）**：

| 字段名称 | 可选/必选 | 字段含义 | 字段规范 |
|---|---|---|---|
| `templateId` | 可选 | 指定 UI 模板 ID。指定后不进行 UI 生成，直接模板渲染；未指定时按正常UI生成链路处理。 | string，长度 0-64 |
| `renderPiu` | 可选 | 指定进行渲染的 PIU。指定后不进行 UI 生成，直接由 PIU 进行渲染；未指定时按UI生成链路处理。 | string，长度 0-64 |
| `iframeUrl` | 可选 | 使用 iframe 加载指定 URL 渲染界面；仅 iframe 直出场景需要。 | string，长度 0-1024；取值为 URL 或站内相对路径。 |
| `iframeTitle` | 可选 | iframe 页面标题；仅 `iframeUrl` 有值时需要，用于页面说明。 | string，长度 0-64。 |
| `scenario` | 可选 | 渲染场景。`LUI` 表示渲染到 AI 对话框中；`Canvas` 表示渲染到 LUI 左侧的画布中；未传时默认按 `LUI` 处理。 | string，枚举：`LUI`、`Canvas`；长度 3 或 6。 |
| `extensionId` | 可选 | UI 生成扩展配置标识。传入后使用第 8 节注册的拓展组件、工具、模板、示例和规则；不传时仅使用基础生成能力。 | string，长度 1-128。 |
| `userInput` | 可选 | 用户输入的自然语言，作为 intent 参与模板命中标识构造与 prompt 组装；非UI生成场景可不传。 | string，长度 1-4096。 |
| `source` | 可选 | 上游数据来源唯一标识，用于缓存UI生成结果进行复用；非UI生成场景可不传。 | string，长度 0-128。 |
| `request` | 可选 | 上游输入参数，如 Skill 工具调用入参或 API 请求参数；非UI生成场景可不传，用于辅助UI生成，如UI标题等。 | object，JSON 对象；不超过 64 KB。 |
| `response` | 必选 | 生成 UI 的主要数据源，根据数据进行UI生成 | object，JSON 对象；不超过 1 MB。 |
| `suggestion` | 可选 |  example或者是额外约束的提示词，用于补充默认生成建议；无额外组件约束或示例要求时可不传。 | string，长度 1-4096。 |

`source`、`request`、`response` 是通用上游数据模型，不限定数据必须来自 API；当上游是 Skill 时，`source` 表示 Skill 或场景标识，`request` 表示 Skill 入参，`response` 表示 Skill 输出结果。
`templateId`、`renderPiu`、`iframeUrl` 属于直出或降级渲染入口，通常不与生成式链路同时携带。

**完整 Request 示例**：
```json
{
  "scenario": "LUI",
  "extensionId": "alarm-genui-presets",
  "userInput": "查看告警列表",
  "source": "alarmSkill.queryAlarmList",
  "request": {
    "pageIndex": 1,
    "pageSize": 10,
    "status": "Active"
  },
  "response": {
    "data": [
      {
        "id": 1,
        "name": "Alice Johnson",
        "ne": "Finance",
        "status": "Active"
      }
    ],
    "total": 1
  },
  "suggestion": "优先使用表格展示告警列表，状态字段使用 Tag 组件呈现。"
}
```

`templateId`、`renderPiu` 和 `iframeUrl` 属于上游业务指定的直出分支。`templateId` 命中 Extension Template 时不调用 LLM，但仍需进行服务端校验；校验失败按 Extension Template 配置错误返回。`renderPiu` 和 `iframeUrl` 由 SmartCanvasService 通过统一响应消息体返回给 DSLEngine，DSLEngine 根据消息体类型选择 PIU 或 iframe 渲染方式。

**Response（SSE 流式）**：

```
event: data
data: {"type":"ir","content":"root = Stack([title])"}

event: data
data: {"type":"ir","content":"\ntitle = Text(\"Alarm Table\")"}

event: done
data: {"type":"done","traceId":"xxx","source":"llm"}
```

**SSE 事件类型**：
- `data`：统一承载 Render Stream Payload，通过 payload `type` 字段区分 openui-lang、Extension Template、PIU、iframe、done 或 error

Render Stream Payload 最小集合如下：

```ts
type RenderStreamPayload =
  | { type: "ir"; content: string }
  | { type: "extensionTemplate"; templateId: string; content: string }
  | { type: "piu"; renderPiu: string; payload?: object }
  | { type: "iframe"; iframeUrl: string; iframeTitle?: string; payload?: object }
  | { type: "done"; traceId: string; source: "llm" | "extensionTemplate" | "piu" | "iframe" | "generatedTemplateCache" }
  | { type: "error"; traceId: string; code: string; message: string; fallback?: object };
```

**8. Extension 注册接口**

Extension 注册接口用于注册 UI 生成扩展配置。业务方可以通过 `extensionId` 把组件、工具、Extension Template、示例和规则登记为一组可复用的生成扩展配置，后续 UI 生成请求只需携带该 `extensionId` 即可使用这些拓展能力。

MVP 阶段，`GenUIExtension` 注册 JSON 由业务方维护。DSLEngine 侧仅提供 `generateComponentSpecs`，用于从前端组件定义导出 `components` 字段所需的组件规格 JSON，避免业务方手写组件名、组件说明和 props 约束。`extensionId`、`version`、`sourcePiu`、`templates`、`componentGroups`、`tools`、`examples` 和 `additionalRules` 仍由业务方按业务场景声明式填写。

`sourcePiu` 可牵引 DSLEngine 拉起业务 PIU；业务 PIU 拉起后通过 PIU 事件向 DSLEngine 注册拓展组件和工具的前端实现。业务方可将同一批前端组件定义传给 `generateComponentSpecs`，生成多个组件组成的 `components` JSON 片段，并填入 Extension 注册接口请求体。

注册体中的 `templates` 表示业务手写的 Extension Template，不表示 SmartCanvasService 在线固化的 Generated Template Cache。Extension Template 是业务配置，Generated Template Cache 是运行期缓存产物，两者使用不同存储空间和失效策略。

**拓展组件规格导出方式（generateComponentSpecs）**：

```ts
import { defineComponent, generateComponentSpecs } from "@openuidev/react-ui-dsl";
import { z } from "zod";

const AlarmSeverityTag = defineComponent({
  name: "AlarmSeverityTag",
  props: z.object({
    severity: z.enum(["critical", "major", "minor", "warning"]),
    text: z.string().optional()
  }),
  description: "告警级别标签，按级别着色展示",
  component: AlarmSeverityTagView
});

const AlarmStatusCard = defineComponent({
  name: "AlarmStatusCard",
  props: z.object({
    title: z.string(),
    total: z.number(),
    critical: z.number().optional()
  }),
  description: "告警状态卡片，用于展示告警总数和严重告警数量",
  component: AlarmStatusCardView
});

export const alarmComponentSpecs = generateComponentSpecs([
  AlarmSeverityTag,
  AlarmStatusCard
]);
```

`generateComponentSpecs` 的输出是一个以组件名为 key 的 JSON 对象，可直接作为 `GenUIExtension.components` 字段值使用。输出内容只包含模型生成和服务侧校验所需的组件规格，不包含 React 渲染实现。

```json
{
  "AlarmSeverityTag": {
    "description": "告警级别标签，按级别着色展示",
    "propsSchema": {
      "type": "object",
      "properties": {
        "severity": {
          "type": "string",
          "enum": ["critical", "major", "minor", "warning"]
        },
        "text": { "type": "string" }
      },
      "required": ["severity"]
    }
  },
  "AlarmStatusCard": {
    "description": "告警状态卡片，用于展示告警总数和严重告警数量",
    "propsSchema": {
      "type": "object",
      "properties": {
        "title": { "type": "string" },
        "total": { "type": "number" },
        "critical": { "type": "number" }
      },
      "required": ["title", "total"]
    }
  }
}
```

`generateComponentSpecs` 支持一次导出多个组件。生成时应校验组件名重复、props schema 可序列化和组件描述是否为空；校验失败时导出失败，业务方修复组件定义后重新导出。

Component Contract 统一由组件说明和 `propsSchema` 表达，不再以 prompt-only `signature` 作为核心契约。`propsSchema` 是 OpenUI 受控 JSON Schema 子集，用于注册期基础校验和生成后组件 props 校验，不要求支持完整 JSON Schema。Stream IR 组件调用仍采用位置参数；`propsSchema.properties` 的声明顺序即位置参数顺序，所有 required 参数必须排在 optional 参数之前。注册期应拒绝不满足该顺序约束的组件契约。

**接口说明**：

| 说明项 | 内容 |
|---|---|
| 接口名称 | `POST /rest/smartcanvas/v1/extensions` |
| 功能 | 注册或覆盖 UI 生成扩展配置 |
| 请求体 | `GenUIExtension`，业务方维护；其中 `components` 可由 DSLEngine 的 `generateComponentSpecs` 导出 |
| 调用方 | 业务方或发布流水线直连，不经 AICOService |
| 生效方式 | 注册成功后，生成请求通过 `extensionId` 选择该扩展配置 |
| 返回类型 | JSON 注册摘要 |

**Request 入参（GenUIExtension）**：

| 字段名称 | 可选/必选 | 字段含义 | 字段规范 |
|---|---|---|---|
| `extensionId` | 必选 | 扩展配置唯一标识。生成请求通过该值选择要使用的拓展能力。 | string，长度 1-128。 |
| `version` | 必选 | 扩展配置版本。组件、工具、模板、示例或规则发生变化时需要更新。 | string，长度 1-64；可使用字母、数字、`.`、`-`、`_`；示例：`1.0.0`、`2026.06.1`。 |
| `sourcePiu` | 可选 | 需要拉起的业务 PIU 列表。DSLEngine 根据该列表拉起业务 PIU；业务 PIU 通过 PIU 事件注册拓展对应的前端实现。 | array，元素为 string；建议不超过 30 个。 |
| `templates` | 可选 | 可复用 UI 模板集合。key 为模板 ID，对应生成接口的 `templateId`；value 为模板内容，通常是 Stream IR 模板文本。没有模板沉淀时可不传。 | object，JSON 对象；key 为 string；value 为 string；建议不超过 256 KB。 |
| `components` | 可选 | 拓展组件描述，包含组件说明和 props 约束。MVP 阶段由 DSLEngine `generateComponentSpecs` 从多个前端组件定义导出，见上文“拓展组件规格导出方式”。没有拓展组件时可不传。 | object，JSON 对象；key 为组件名；建议不超过 30 个组件。 |
| `componentGroups` | 可选 | 组件分组和使用说明，用于提示词组织；没有分组诉求时可不传。 | array，数组项为 JSON 对象。 |
| `tools` | 可选 | 拓展工具描述，供 Query/Mutation 或 Action 调用；采用 MCP Tool 兼容结构，见上文“拓展工具定义”。没有拓展工具时可不传。 | array，数组项为 JSON 对象；建议不超过 30 个工具。 |
| `examples` | 可选 | 生成示例，用于牵引模型稳定使用拓展组件和工具；没有示例时可不传。 | array，元素为 string；单条建议不超过 4096 字符。 |
| `additionalRules` | 可选 | 附加生成规则，用于约束生成风格、组件使用偏好或业务规则；没有额外约束时可不传。 | array，元素为 string；单条建议不超过 512 字符。 |

MVP 阶段仅 `components` 字段提供导出能力，`tools`、`templates`、`examples` 和 `additionalRules` 由业务方手写维护；其中 `tools` 应按 MCP Tool 兼容结构维护。重复注册同一个 `extensionId` 时按整包覆盖处理；覆盖成功后应使该扩展配置关联的旧模板缓存失效。

**完整 Request 示例**：

```json
{
  "extensionId": "alarm-genui-presets",
  "version": "1.0.0",
  "sourcePiu": ["alarmPiu", "topoPiu"],
  "templates": {
    "alarmList": "root = Stack([title])\ntitle = Text(\"Alarm Table\")"
  },
  "components": {
    "AlarmSeverityTag": {
      "description": "告警级别标签，按级别（critical/major/minor/warning）着色展示",
      "propsSchema": {
        "type": "object",
        "properties": {
          "severity": {
            "type": "string",
            "enum": ["critical", "major", "minor", "warning"],
            "description": "告警级别"
          },
          "text": { "type": "string", "description": "标签文本，缺省时按级别取默认中文文案" }
        },
        "required": ["severity"]
      },
      "examples": ["sev = AlarmSeverityTag(\"critical\", \"严重\")"]
    },
    "AlarmStatusCard": {
      "description": "告警状态卡片，用于展示告警总数和严重告警数量",
      "propsSchema": {
        "type": "object",
        "properties": {
          "title": { "type": "string", "description": "卡片标题" },
          "total": { "type": "number", "description": "告警总数" },
          "critical": { "type": "number", "description": "严重告警数量" }
        },
        "required": ["title", "total"]
      },
      "examples": ["statusCard = AlarmStatusCard(\"当前告警\", 128, 12)"]
    }
  },
  "componentGroups": [
    {
      "name": "business",
      "components": ["AlarmSeverityTag", "AlarmStatusCard"],
      "notes": ["告警业务专用组件，渲染告警级别时优先使用 AlarmSeverityTag，展示告警汇总时优先使用 AlarmStatusCard"]
    }
  ],
  "tools": [
    {
      "name": "acknowledgeAlarm",
      "title": "确认告警",
      "description": "确认（ack）一条告警，返回是否成功",
      "inputSchema": {
        "type": "object",
        "properties": {
          "alarmId": { "type": "string", "description": "告警 ID" }
        },
        "required": ["alarmId"]
      },
      "outputSchema": {
        "type": "object",
        "properties": {
          "success": { "type": "boolean" }
        }
      },
      "annotations": {
        "readOnlyHint": false,
        "destructiveHint": false,
        "idempotentHint": true
      }
    }
  ],
  "examples": [
    "root = VLayout([sevTag])\nsevTag = AlarmSeverityTag(\"major\", \"重要\")",
    "sevCol = Col(\"级别\", \"severity\", {cell: @Render(\"v\", AlarmSeverityTag(v))})"
  ],
  "additionalRules": [
    "告警级别列统一使用 AlarmSeverityTag 组件呈现，不要用纯文本"
  ]
}
```

**Response 示例**：

```json
{
  "extensionId": "alarm-genui-presets",
  "version": "1.0.0",
  "componentCount": 2,
  "toolCount": 1,
  "templateCount": 1,
  "traceId": "xxx"
}
```

#### 6.2.4.3 协议设计：Stream IR

Stream IR 是服务侧与前端渲染侧之间的 UI 中间表示，采用按语句声明的文本结构描述组件、数据、状态、查询、变更和表达式。

Stream IR 设计目标：

- **可流式渲染**：根节点和组件声明可随模型输出分批到达，前端可增量解析并更新界面
- **可绑定变量**：通过 `{data.*}` 绑定运行时 DataModel，通过 `{$varName}` 绑定状态变量
- **可约束**：组件名、props、表达式和事件动作可由组件库与协议规则统一约束
- **可扩展**：支持内置组件、扩展组件、Query/Mutation、数据表达式和 Action 动作

示例：

```
root = VLayout([header, chart])
header = Text("Quarterly Revenue Comparison", "large")
seriesA = Series(data.series[0].category, data.series[0].values)
seriesB = Series(data.series[1].category, data.series[1].values)
chart = BarChart(data.labels, [seriesA, seriesB], "grouped", "Quarter", "Revenue (USD)")
```

协议中的表达式能力用于完成数据过滤、聚合、格式化等处理。

**表 6-2 支持表达式**

表达式用于在 Stream IR 内完成轻量数据处理和展示格式化，避免 SmartCanvasService 在生成阶段改写业务数据。生成侧只允许模型引用 DataModel 字段并调用受控表达式；渲染侧在受控运行时中求值，并将结果传入组件 props。表达式主要覆盖数组统计、过滤排序、对象映射、数值计算、日期/字节/百分比等展示型处理，不承载复杂业务计算。

| 表达式 | 说明 | 表达式 | 说明 |
|---|---|---|---|
| @Count | 返回数组长度 | @ObjectEntries | 对象转数组 |
| @First | 返回数组第一个元素 | @ObjectKeys | 对象 key 数组 |
| @Last | 返回数组最后一个元素 | @Round | 四舍五入 |
| @Sum | 数组求和 | @Abs | 绝对值 |
| @Avg | 数组求平均值 | @Floor | 向下取整 |
| @Min | 数组最小值 | @Ceil | 向上取整 |
| @Max | 数组最大值 | @FormatDate | 格式化日期 |
| @Sort | 数组排序 | @FormatBytes | 字节格式化 |
| @Filter | 数组过滤，支持 ==, !=, >, <, >=, <=, contains | @FormatNumber | 格式化数字 |

**变量绑定**

Stream IR 支持通过 `{data.*}` 和 `$varName` 两类变量完成运行时绑定。`{data.*}` 用于读取业务 DataModel，承载 Skill 或接口返回的数据；`$varName` 用于声明和引用前端状态变量，承载筛选条件、选中项、表单输入、弹窗开关等交互状态。

变量绑定规则如下：

| 绑定类型 | 说明 | 示例 |
|---|---|---|
| DataModel 绑定 | 读取业务数据，只读，不在端侧修改 | `Table([nameCol], data.rows)` |
| 状态变量绑定 | 读取端侧状态，可由 Action 或表单组件更新 | `selected = Text($selectedId)` |
| 表单字段绑定 | 组件通过 formName/name 接入运行时 store | `Select("status", ["open", "closed"], $status)` |
| 表达式绑定 | 表达式读取 data 或状态变量后生成组件 props | `filteredRows = @Filter(data.rows, "status", "==", $status)` |

**Action 支持**

Action 用于描述用户交互动作，由支持 ActionExpression 的组件 props 触发。`Action([@steps...])` 按顺序执行多个动作步骤，常用于按钮点击、行操作、表单提交、跳转和对话续写。

| Action 能力 | 说明 | 示例 |
|---|---|---|
| 状态更新 | 使用 `@Set($varName, value)` 更新状态变量 | `Action([@Set($selectedId, row.id)])` |
| 状态重置 | 使用 `@Reset($varName)` 恢复状态变量默认值 | `Action([@Reset($selectedId)])` |
| 对话续写 | 使用 `@ToAssistant(message, context)` 将用户动作转成后续对话输入 | `Action([@ToAssistant("分析该告警", row.id)])` |
| 打开链接 | 使用 `@OpenUrl(url)` 触发外部链接打开 | `Action([@OpenUrl(data.detailUrl)])` |
| 工具调用 | 使用 `@Run(queryOrMutationRef)` 触发 Query/Mutation 运行 | `Action([@Run(refreshAlarm)])` |

Query/Mutation 通过 Renderer 的 `toolProvider` 接入外部工具或后端接口。`@Run(queryRef)` 用于重新拉取 Query，`@Run(mutationRef)` 用于执行 Mutation；Mutation 执行失败时后续步骤停止，避免错误状态继续传播。

**二次渲染**

二次渲染指用户交互、状态变化、Query 刷新或 Mutation 回写后，Renderer 在不重新生成 Stream IR 的情况下重新计算渲染结果。二次渲染由运行时 store、query snapshot、表达式求值和组件 props 映射共同完成。

二次渲染触发场景包括：

| 触发场景 | 处理方式 |
|---|---|
| `@Set` 更新状态变量 | Renderer 更新 store，重新求值引用该变量的表达式和组件 props |
| `@Reset` 重置状态变量 | Renderer 恢复变量默认值，并刷新关联组件 |
| 表单输入变化 | 表单组件写入运行时 store，依赖该字段的展示区域重新渲染 |
| Query 刷新 | query snapshot 更新后，引用 Query 结果的组件重新渲染 |
| Mutation 成功 | 按 Action 步骤触发 Query 刷新或状态更新，驱动界面二次渲染 |

二次渲染不改变原始 Stream IR 结构，只更新运行时数据和表达式结果；如果二次渲染过程中出现表达式异常或组件异常，应隔离到对应组件并保留其他区域正常展示。

#### 6.2.4.4 渲染侧设计：Renderer / parser / dataModel

渲染侧负责接收 Stream IR 流式内容，解析为可渲染节点，并结合 dataModel、组件库和工具能力完成 UI 渲染。当前项目已有 Renderer、useOpenUIState、parser、dataModel、Query/Mutation 语句和部分内置表达式基础，后续需要围绕错误反馈、降级展示和业务 Action 能力继续补齐。

**1. parser 转换**

parser 将 Stream IR 转换成 AST 与可渲染节点，方便后续渲染。

**1.1 词法分析（Lexer）**

- 输入：Stream IR 文本字符串
- 输出：Token 流

Token 类型定义：
```
enum T {
  Ident,    // 标识符: Table, root
  String,   // 字符串: "hello"
  Number,   // 数字: 42
  StateVar, // 状态变量: $count
  LParen, RParen, LBrack, RBrack, // 括号
  Dot, Comma, Eq, // 运算符
  // ...
}
```

**1.2 表达式解析（Pratt Parser）**

- 输入：Token 流
- 输出：AST 节点（ASTNode）

**1.3 语句分类**

将表达式分类为不同类型的 Statement：
```
type Statement =
  | { kind: "value"; id: string; expr: ASTNode }       // root = VLayout([...])
  | { kind: "state"; id: string; init: ASTNode }       // $count = 0
  | { kind: "query"; id: string; call: CallNode; expr: ASTNode; deps?: string[] }
  | { kind: "mutation"; id: string; call: CallNode; expr: ASTNode }
```

**1.4 Materialization（核心转换）**

将 AST 转换为 ElementNode，完成引用展开、绑定关系建立。

**2. Renderer 流式渲染**

Renderer 接收 response、library、isStreaming、dataModel、toolProvider、onError 等运行时输入，通过流式 parser 逐行处理 Stream IR。解析过程中支持根节点优先渲染、后续节点增量补齐、解析错误聚合和局部错误提示。

**3. 数据绑定**

解析数据绑定关系，完成组件属性赋值：
- `{data.*}` 访问 DataModel 数据
- `{$varName}` 访问状态变量

字段访问需做原型链防护，禁止访问 `__proto__`、`constructor` 等内置属性。

**4. Query / Mutation / Action**

Stream IR 中的 Query/Mutation 语句用于描述数据查询和状态变更能力。Action 事件用于完成 PIU 事件、路由事件、点击事件等交互绑定。PIU 拉起失败时降级为静态展示，并记录日志用于排查。

**5. 表达式求值**

表达式求值用于支持数据过滤、聚合、格式化等场景。表达式求值在受控环境中执行，禁用 `eval` / `new Function`，防止注入风险。

#### 6.2.4.5 组件库设计：react-ui-dsl / eview

组件库层负责控制模型可生成的组件集合、组件说明、props 约束和目标 UI 组件适配。

**1. 组件库管控**

react-ui-dsl 负责向生成侧和渲染侧提供一致的组件能力，包括组件名称、组件描述、props 说明、示例和运行时渲染实现。通过统一的组件库管控，可以避免模型生成未定义组件或错误 props。

**2. 扩展组件注册**

后续扩展组件通过组件描述和实现注册进入组件库，供 prompt 注入、类型校验和运行时渲染使用。

组件定义采用 `defineComponent` 描述组件名称、props schema、组件说明和 React 渲染实现；内置组件集合通过 `createLibrary` 汇总后提供给 Renderer 和 prompt 生成流程。扩展配置不要求业务方先创建完整 `Library`，MVP 阶段由 `generateComponentSpecs` 从多个拓展组件定义中导出 `components` JSON 片段，其他注册字段由业务方维护。示例如下：

```ts
import { createLibrary, defineComponent, generateComponentSpecs } from "@openuidev/react-ui-dsl";
import { z } from "zod";

const Button = defineComponent({
  name: "Button",
  props: z.object({
    text: z.string(),
    type: z.enum(["primary", "default"]).optional()
  }),
  description: "按钮组件",
  component: ({ props }) => <ButtonView text={props.text} type={props.type} />
});

export const library = createLibrary({
  root: "VLayout",
  components: [Button]
});

export const componentSpecs = generateComponentSpecs([Button]);
```

`library` 是 Renderer 的运行时组件库，也是生成 prompt 和 JSON Schema 的来源。`componentSpecs` 是可填入 `GenUIExtension.components` 的组件规格 JSON，不包含 React 渲染实现。SmartCanvasService 在拼装 prompt 时应使用与前端组件定义同源的组件描述；前端 Renderer 在渲染时根据 Stream IR 中的组件名查找 library 或 PIU 注册的组件实现。

MVP 阶段仅 `components` schema 由 DSLEngine 提供的 `generateComponentSpecs` 导出：从 `defineComponent` 的 props（zod）推导组件约束和模型可见描述，序列化为组件名到组件规格的 JSON 对象。`tools`、`templates`、`componentGroups`、`examples` 和 `additionalRules` 由业务方在 `GenUIExtension` 注册 JSON 中维护；其中 `tools` 采用 MCP Tool 兼容结构，便于后续映射到 MCP `tools/call` 或现有 PIU/toolProvider 执行通道。业务方把 `components` 导出结果填入注册 JSON 后，通过 `POST /rest/smartcanvas/v1/extensions` 注册。

**3. eview 适配**

eview 作为目标 UI 组件库，通过 react-ui-dsl 的 view target 机制适配。适配层负责将 Stream IR 组件语义和 props 映射到 eview 组件实现，优先覆盖布局、文本、表格、表单、图表和业务高频组件。对 eview 暂不支持的能力，采用占位提示、静态展示或降级组件兜底。

#### 6.2.4.6 异常处理与降级

异常处理采用生成侧与渲染侧分层兜底策略。

| 异常场景 | 处理策略 |
|---|---|
| 注册契约名称碰撞 | 注册接口返回 409，原 Generation Extension 保持不变 |
| 生成请求 extensionId 未注册 | 拼装前校验失败，返回错误事件，不静默回退仅 base contract |
| Overlay 工具名与已注册工具碰撞 | 返回错误事件，不产生拼装结果 |
| Stream IR 生成失败 | SmartCanvasService 捕获异常，返回错误事件或降级 markdown |
| 语法校验失败 | 通过 Reflection 反馈错误并触发重生成，超过阈值后降级 |
| 模型超大输出 | 服务侧检查输出 token，超过阈值进入错误处理或降级 |
| SSE 流式中断 | 前端保留已渲染内容，提示用户重试 |
| parser 解析失败 | Renderer 聚合错误，局部展示错误提示 |
| 表达式求值异常 | 隔离到单组件或单表达式，不影响整体页面 |
| 扩展组件未注册 | 渲染未知组件占位提示，不阻塞其他组件 |
| PIU 拉起失败 | 降级为静态展示，并记录日志 |

全链路通过 traceId 串联生成请求、大模型调用、Stream IR 校验、SSE 返回和前端渲染。关键观测指标包括模板命中率、生成时延、首字节时延、生成成功率、token 用量、校验失败率、降级率、端侧渲染错误率，以及 Generation Extension 注册数与注册失败率。

### 6.2.5 增量SR清单

#### 6.2.5.1 SR20260509977042：数据驱动UI生成

##### 6.2.5.1.1 SR描述

**需求背景**

在 NOE Mate 的业务场景中，统一 portal 是其中的基本能力，因此需要与传统的 UI 和驾舱界面进行兼容，需要 UI 的跳转和叠加能力。同时，对于各 SKILL 的输出，需要 UI 生成能力进行可视化渲染。因此需要构建此能力。

**需求价值**

为 NOE MATE 场景中提供 UI 生成的基础能力。

**需求详情**

1. 界面交互增强，支持按钮点击、组件更新等常用交互事件
2. 支持流式渲染，渲染结果分批次返回
3. 支持生成数据表达式处理数据
4. 提供接口支持产品调整提示词、扩展组件
5. DFX 增强，数据过长进行上下文压缩
6. 支持模板在线固化提高生成效率
7. 自研 Stream IR 与 Renderer 渲染框架，提升 UI 生成框架稳定性和易扩展性，eview 组件对接新框架

##### 6.2.5.1.2 SR实现思路

本 SR 在基线能力上新增数据驱动 UI 生成链路：AICOService 将 Agents/Skills 产生的用户意图、业务数据和生成建议转发至 SmartCanvasService；SmartCanvasService 生成 Stream IR，并通过 SSE 返回前端；前端 Renderer 基于 parser、dataModel 和 react-ui-dsl/eview 组件库完成流式渲染。

落地重点包括：服务侧约束大模型输出稳定的 Stream IR；前端侧保证 Stream IR 可以边到达边解析、边渲染；组件库侧通过 react-ui-dsl 管控组件范围并适配 eview；异常场景下通过校验、Reflection、重试和降级展示保障用户体验。

##### 6.2.5.1.3 功能实现刷新

本 SR 相比基线新增/刷新以下内容：

1. **SmartCanvas 生成服务能力**：新增 UI 生成服务，接收意图、数据和生成建议，输出 Stream IR。
2. **prompt 组装**：将用户意图、业务数据、组件描述、扩展组件说明、交互建议和生成约束组装为模型输入。
3. **Stream IR 生成约束**：约束模型输出组件声明、数据绑定、状态、表达式和 Action，避免生成未定义组件和不可解析结构。
4. **Renderer 流式渲染接入**：前端接收 SSE 流式内容，通过 Renderer 增量解析并渲染。
5. **表达式与数据绑定**：支持 `{data.*}`、`{$varName}`、数据表达式、Query/Mutation 和 Action 事件绑定。
6. **eview 组件适配**：通过 react-ui-dsl 管控组件能力，并完成 eview 目标组件属性映射和渲染适配。
7. **降级能力**：生成失败、校验失败、流式中断、未知组件、表达式异常和 PIU 拉起失败时提供分层兜底。
8. **Extension 注册接口**：扩展组件与工具的注册式管理（PUT/GET extensions、替换语义、碰撞拒绝）、Redis 持久化、模板失效联动，以及生成请求的 extensionId 选择与 Request Overlay。

##### 6.2.5.1.4 DFX分析

1. **Stream IR 解析失败**：渲染错误提示，单组件解析失败不影响整体页面其他部分渲染
2. **接口超时错误**：渲染错误提示，前端展示重试入口
3. **LLM 限流/熔断**：服务侧捕获限流异常，降级返回 markdown 格式数据
4. **语法校验反复失败**：Reflection 重试超过阈值后降级返回 markdown 格式数据
5. **流式中断**：SSE 连接中断时，前端已渲染部分保留，提示用户重试
6. **扩展组件未注册**：Renderer 检测到未知组件时渲染占位提示，不阻塞其他组件渲染
7. **PIU 拉起失败**：捕获异常并降级为静态展示，记录日志用于排查
8. **表达式求值异常**：单表达式异常隔离至该组件，不影响其他组件
9. **可观测性**：全链路 traceId 贯穿（生成请求 → LLM 调用 → Stream IR 校验 → 前端渲染），关键指标埋点（模板命中率、生成时延、首字节时延、成功率、token 用量、降级率）

##### 6.2.5.1.5 架构元素影响列表

| 架构元素 | 影响类型 | 新增/修改/复用 | 说明 |
|---|---|---|---|
| AICOService | 接入编排 | 修改 | 增加 UI 生成请求透传与结果接收能力，将用户意图、业务数据和生成建议转发至 SmartCanvasService |
| SmartCanvasService | 生成服务 | 新增 | 承担 Stream IR 生成、提示词组装、Extension 注册、模板在线固化、上下文压缩、校验与 Reflection 重生成等能力 |
| Java Generation SDK | 生成内核 | 复用 | SmartCanvasService 集成 SDK 完成 base contract 加载、Extension Registration 与 prompt 拼装（与前端 library prompt 字节对齐）；SDK 保持纯内存语义 |
| Redis 模板与注册存储 | 缓存能力 | 新增 | 存储经校验通过的模板 Stream IR 与已注册的 Generation Extension 扩展契约（分 key 空间），支撑模板命中/失效与注册持久化、启动加载 |
| 大模型服务 | 生成依赖 | 复用 | 根据提示词和生成约束输出 Stream IR，并在 Reflection 流程中根据错误反馈重新生成 |
| Stream IR | 协议能力 | 复用并扩展 | 作为服务侧与前端之间的 UI 中间表示，承载组件、数据绑定、状态、表达式和交互 |
| Renderer / parser / dataModel | 前端渲染运行时 | 复用并扩展 | 负责 Stream IR 解析、表达式求值、数据绑定、Action 事件绑定与流式渲染 |
| react-ui-dsl | 组件库管控 | 复用并扩展 | 管控组件名称、组件说明、props 约束、示例和运行时实现 |
| eview 组件适配 | 目标组件适配 | 修改 | 对接 Stream IR 组件属性映射，补齐 eview 基础组件适配和扩展组件注册能力 |
| PIU 集成 | 业务交互 | 修改 | 支持由 Stream IR Action 触发 PIU 拉起、跳转或叠加渲染事件 |
| 观测日志 | 可观测性 | 新增 | 增加 traceId、生成耗时、模板命中率、校验失败率、降级率、token 用量等指标 |

##### 6.2.5.1.6 AR设计

**AR-01：采用 Stream IR 作为生成协议**

采用 Stream IR 作为服务侧与前端之间的 UI 中间表示。Stream IR 采用按语句声明的文本结构，便于大模型生成、服务侧校验和前端流式解析；同时可表达组件、状态、Query/Mutation、数据绑定、表达式和 Action，适合数据驱动 UI 生成场景。

**AR-02：前端采用 Renderer 流式渲染**

前端采用 Renderer 接收 Stream IR 流式内容，并结合 parser、dataModel 和组件库完成增量渲染。该方式可以降低首屏等待时间，支持模型边生成边展示，并在流式中断或局部解析失败时保留已渲染内容。

**AR-03：组件库通过 react-ui-dsl 管控**

组件能力通过 react-ui-dsl 统一管控，避免生成侧和渲染侧对组件能力理解不一致。react-ui-dsl 同时承载组件说明、props 约束、示例和运行时实现，便于 prompt 注入、生成约束、类型校验和 Renderer 运行时渲染复用同一套组件定义。

**AR-04：eview 采用 view target 方式适配**

eview 作为目标 UI 组件库，通过 react-ui-dsl 的 view target 机制进行适配。适配层负责将 Stream IR 组件语义和 props 映射到 eview 组件实现；当 eview 暂不支持某类能力时，通过占位、静态展示或降级组件兜底。

**AR-05：SmartCanvas 与 OpenUI 的职责边界**

SmartCanvas 负责生成侧能力，包括 prompt 组装、大模型调用、Stream IR 生成约束、校验、Reflection、模板固化和服务侧降级。OpenUI 负责协议解析和前端渲染能力，包括 Stream IR parser、Renderer、dataModel、表达式求值、组件库映射和端侧错误隔离。两者通过 Stream IR 解耦，避免生成服务直接依赖具体前端实现，也避免前端承担大模型生成职责。

**AR-06：扩展组件采用注册式 Generation Extension 管理**

扩展组件与扩展工具通过 Extension Registration 预注册为以 extensionId 隔离的 Generation Extension，生成请求仅携带 extensionId 与一次性 Request Overlay（动态 tools 与 extraRules），不在请求中内嵌组件契约。该模型与 Java Generation SDK（genui-java-sdk）及 GenUI Service 参考实现（examples/genui-service）保持一致，收益包括：每次生成请求节省扩展描述 token（单 extension 上限 30 个组件 × 100 token）；模板固化 key 纳入 extensionId 与 Contract Version，实现确定性命中并取代命中后兼容性校验；契约名称碰撞在注册期即被拒绝，而非生成期才暴露。备选「每次请求内嵌扩展组件描述」被否：请求体膨胀、模板固化需逐次做扩展组件超集校验、且与 SDK/参考实现的契约模型脱节。注册契约由 Redis 持久化并在启动时加载（服务层职责），SDK 保持纯内存语义；未注册 extensionId 的生成请求在服务层报错，不沿用 SDK 内部的静默回退行为。
