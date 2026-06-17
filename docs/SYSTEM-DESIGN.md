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

1. 前置 Agents/Skills 产生用户意图、业务数据和 UI 生成建议，包括组件布局、扩展上下文选择（contextId）、一次性工具与附加规则（Request Overlay）、UI 交互等信息；扩展组件与扩展工具由业务方通过 Context 拓展接口预先注册。
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
| 支持扩展组件数量 | 单个 Generation Context 中扩展组件数量上限 | 目标规格 | 30 | NetGraph-Smart V100R027C10 | | 扩展组件通过 Context 拓展接口注册 | 后续开发 |
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

SmartCanvasService 是后续开发的生成侧服务，负责将用户意图、业务数据和 UI 生成建议转换为可被前端消费的 Stream IR。其核心能力包括 Context 拓展注册、prompt 组装、上下文压缩、模板在线固化、生成结果校验、Reflection 重生成和异常降级。

**1. 模板在线固化**

以数据来源、用户意图与生成上下文共同构造模板唯一标识，自动匹配已生成的模板，解决相同场景重复调用大模型导致的生成效率低问题。

- **唯一标识**：`key = hash(dataSource + normalizedIntent + contextId + extensionVersion + overlayHash)`
  - `dataSource`：由 Skill 在生成建议中显式传入，标识数据来源（如 `apiName` 或自定义来源标识）
  - `intent`：用户意图，需做归一化处理（大小写、空白、标点统一）
  - `contextId` / `extensionVersion`：所选 Generation Context 及其契约版本（见本节 8），未携带 contextId 时取空值
  - `overlayHash`：Request Overlay（一次性 `tools` + `extraRules`）的规范化哈希，无 Overlay 时取空值；同一 Skill 对同一场景的建议通常稳定，命中率不受影响
- **固化流程**：大模型生成的 Stream IR 通过语法与类型校验后，自动以 key 固化入 Redis 缓存
- **命中流程**：每次生成请求先查缓存，命中则跳过大模型调用，直接返回模板 Stream IR，并注入运行时数据；不同 contextId / Overlay 的请求由 key 自然隔离，无需命中后二次兼容性校验
- **模板与数据解耦**：固化内容为 Stream IR 结构与 `{data.*}` 绑定关系，不固化具体数据值
- **失效策略**：Extension Registration 替换（同 contextId 重复注册）成功后批量失效该 contextId 下全部模板（防止注册方未递增 version 导致旧模板错误命中）；Stream IR 语法版本升级时全量失效；提供手动失效接口作为运维兜底
- **观测指标**：模板命中率、模板库规模、命中/未命中时延对比

**2. 扩展组件注入**

扩展组件与扩展工具不在生成请求中传递，而是由业务方通过 Context 拓展接口（见本节 8）预先注册为 Generation Context；生成请求通过 `contextId` 选择上下文。prompt 注入时由注册契约提供扩展组件名称、描述与 propsSchema 推导的签名，使大模型可选用扩展组件；propsSchema 同时用于后续类型校验，检查组件参数类型与取值范围。请求级的一次性工具与附加规则通过 Request Overlay（`tools` / `extraRules` 字段）传入，仅参与本次拼装，不写入注册契约。

**3. 上下文压缩**

计算数据 token 长度，超过 2K 时尝试进行采样压缩，缓解超大数据导致的 prompt 膨胀问题。

- **压缩范围**：仅对数组结构进行采样压缩，嵌套对象与非数组结构不参与压缩
- **采样策略**：保留数组头部、尾部及中间均匀采样的元素，保证数据分布特征
- **恢复机制**：大模型输出 Stream IR 后，对 data 部分进行数据恢复，将完整数据回填至最终响应
- **约束**：模型仅可对采样数据建立绑定引用关系（`{data.*}`），不可对数据本身做变换

**4. 提示词组装**

将扩展组件描述、UI 建议信息拼装为完整 prompt，发送给大模型。生成内容支持：
- UI 组件（内置组件 + 扩展组件）
- 数据处理表达式（详见表 6-2）
- Action 动作（PIU 事件、跳转事件、点击事件等）

数据绑定语法：
- `{data.*}`：访问 DataModel 数据
- `{$varName}`：访问状态变量

**5. 语法检查与 Reflection 重生成**

对大模型生成的 Stream IR 进行多维校验，校验失败时通过 Reflection 机制将错误反馈给大模型重新生成。

- **校验维度**：
  - 语法校验：Stream IR 词法、语法结构是否合法
  - 类型校验：组件 props 类型、表达式参数类型是否匹配
  - 引用完整性：被引用的标识符（如 `tbl = Table([col1, col2])` 中的 `col1`）是否已定义
- **Reflection 重生成**：将校验错误信息作为上下文反馈给大模型，要求其修复后重新输出
- **重试策略**：最多 N 次（N 可配置，建议默认 2 次），超过阈值进入降级流程

**6. 错误检查**

- 对模型超大输出（超过 4K token）等异常场景进行检查
- 重复生成失败则降级返回 markdown 格式数据，确保前端始终有可渲染内容

**7. UI 生成接口**

接口名称：SSE `/rest/smartcanvas/v1/generate/ui`

功能：生成 Stream IR

**Request 入参（UIRequestDetail）**：

```yaml
UIRequestDetail:
  description: 生成UI接口对应的请求体
  required:
    - apiRsp
  properties:
    scenario:
      description: 生成式UI使用场景，如LUI中渲染使用
      type: string
      pattern: 'LUI'
      maxLength: 128
    userInput:
      description: 用户输入的自然语言
      type: string
      maxLength: 4096
    apiName:
      description: api name
      type: string
      maxLength: 256
    apiUrl:
      description: api url
      type: string
      maxLength: 256
    apiMethod:
      description: api method
      type: string
      maxLength: 16
    apiReq:
      description: api请求参数
      type: object
    apiRsp:
      description: api响应报文
      type: object
    isAnswer:
      description: 是否为大模型回答部分，需要显示在右侧，默认在左侧
      type: boolean
      default: false
    contextId:
      description: Generation Context 标识，选择预注册的扩展上下文；缺省时仅使用 base contract
      type: string
      maxLength: 128
    tools:
      description: Request Overlay 动态工具，仅本次生成生效，不持久化；与已注册工具同名时拒绝
      type: array
      items:
        $ref: '#/definitions/UIToolDescriptor'
    extraRules:
      description: Request Overlay 附加生成规则，仅本次生成生效，拼装时追加到 prompt 约束
      type: array
      items:
        type: string
        maxLength: 512
```

**字段说明**：
- `userInput`：作为 intent，参与模板命中标识构造与 prompt 组装
- `apiName`：作为 dataSource，参与模板命中标识构造
- `apiRsp`：作为生成 UI 的数据源，参与上下文压缩与最终数据回填
- `scenario`：使用场景标识，控制渲染位置（如 LUI 左右栏）
- `isAnswer`：控制渲染位置，true 时显示在右侧，否则左侧
- `contextId`：选择预注册的 Generation Context（见本节 8）；携带未注册的 contextId 时服务在拼装前校验并返回 `error` 事件，不静默回退仅 base contract
- `tools` / `extraRules`：Request Overlay，仅参与本次 prompt 拼装，不写入注册契约；Overlay 工具名与所选上下文已注册工具碰撞时返回 `error` 事件

**Request 示例**：
```json
{
  "scenario": "LUI",
  "userInput": "查看告警列表",
  "apiName": "queryAlarmList",
  "apiUrl": "/rest/alarm/v1/list",
  "apiMethod": "GET",
  "apiReq": {},
  "apiRsp": {
    "data": [
      { "id": 1, "name": "Alice Johnson", "ne": "Finance", "status": "Active" }
    ]
  },
  "isAnswer": false,
  "contextId": "alarm-ops",
  "extraRules": ["告警级别列使用 Tag 组件呈现"]
}
```

**Response（SSE 流式）**：

```
event: data
data: root = VLayout([title, tbl])

event: data
data: title = Text("Alarm Table")

event: data
data: tbl = Table([col1, col2, col3, col4])

event: data
data: col1 = Col("ID", ids, "number")

event: data
data: col2 = Col("Name", names, "string")

event: data
data: col3 = Col("Ne", nes, "string")

event: data
data: col4 = Col("Status", statuses, "string")

event: data
data: ids = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]

event: data
data: names = ["Alice Johnson", "Bob Martinez", ...]

event: data
data: nes = ["Finance", "Engineering", ...]

event: data
data: statuses = ["Active", "Active", ...]

event: done
data: {"traceId": "xxx", "baseContractVersion": "1.0.0", "extensionVersion": "2026.06.1"}
```

**SSE 事件类型**：
- `data`：Stream IR 语句行，按声明顺序流式下发
- `done`：生成完成，携带 traceId 与拼装元数据（baseContractVersion、extensionVersion），便于排查当前生效的契约版本
- `error`：错误事件，携带错误码与降级内容（含 contextId 未注册、Overlay 工具名碰撞等校验失败场景）

**8. Context 拓展接口**

**要解决的问题**

大模型默认只会用一套内置的标准组件（表格、文本、图表等）。但具体业务往往有自己的专用组件和专用操作——比如告警运维场景，有「告警级别标签」这种专用组件、「确认告警」这种专用操作。这些东西模型本来不认识，也就不会生成出来。

**做法**

让业务方**提前把自己的扩展组件和工具登记到服务里**，打成一个带名字的包；之后这个业务的每次生成请求，只要报上这个名字（`contextId`），服务就自动把对应的扩展能力带进提示词。如果某次生成想临时多加一个工具或一条规则，可以在请求里一次性附带，用完即弃、不登记。

为什么是「登记一次、反复用」而不是「每次请求都带一遍组件说明」：一个业务的扩展组件可能有几十个、每个上百 token，每次都塞既费 token 又容易写错；登记一次后反复复用，还能把生成结果缓存成模板（见本节 1）。注意服务只登记「描述」（给模型看、给服务校验用的元数据），组件的实际渲染代码仍在前端（见 6.2.4.5），两边用同一个组件名对齐。

**几个说法**（下面接口和示例都会用到，括号内为对应英文术语）：

| 说法 | 通俗解释 | 术语 |
|---|---|---|
| 标准组件包 | 所有业务通用的内置组件清单，服务自带，业务方不用登记 | Base Contract |
| 业务扩展包 | 「标准组件 + 某业务自己的扩展」合成的一包，用一个 id 认领（`contextId`） | Generation Context |
| 登记扩展 | 业务方把自己的扩展组件/工具交给服务记下来这个动作 | Extension Registration |
| 临时追加 | 某次生成想额外加的工具或规则，只这一次有效、不登记 | Request Overlay |
| 重名拒绝 | 登记的组件/工具名和已有的撞了，直接拒绝、不覆盖 | Contract Name Collision |
| 扩展版本号 | 业务扩展包的版本，改了内容就该改号 | Contract Version |

**能扩展什么**

可扩展三类东西，按生效范围分「登记长期生效」和「随请求一次性」：

| 扩展什么 | 怎么生效 | 用来做什么 |
|---|---|---|
| 组件 | 只能登记（长期） | 新增模型能生成、前端能渲染的 UI 组件 |
| 工具 | 登记（长期）或随请求附带（一次性） | 提供可被调用的后端能力（查询/变更，对应 Query/Mutation） |
| 提示词规则 | 登记（长期）或随请求附带（一次性） | 约束生成风格、业务规则、组件取用偏好 |
| 生成示例 | 只能登记（长期） | 给模型几个范例，提升生成稳定性 |

组件必须登记——因为它在前端要有对应的渲染代码，没法只靠一次请求临时塞进来；工具和规则既能登记成长期能力，也能某次生成临时加。

**完整流程**

从前端定义组件，到最终在页面上渲染出来，整条链路如下：

| 步骤 | 谁做 | 做什么 |
|---|---|---|
| 1 定义组件 | 前端 | 用 `defineComponent` 写清组件名、参数、说明和渲染代码，`createLibrary` 汇总成组件库（见 6.2.4.5） |
| 2 导出描述 | 前端工具链 | 从组件库自动导出「扩展包描述」（JSON），不手写，保证描述和渲染代码一致（见 6.2.4.5） |
| 3 登记 | 业务方 | 把导出的描述 `PUT` 给服务登记（业务方直连，不经 AICOService） |
| 4 服务存档 | 服务 | 检查没有重名、引用合法后存入 Redis 并加载生效，同时清掉该业务的旧模板缓存 |
| 5 发起生成 | Skill / AICOService | 生成请求带上 `contextId` 选好业务扩展包，需要时再临时附带工具/规则 |
| 6 拼提示词 | 服务 | 把「标准组件 + 该业务扩展 + 临时追加」拼成提示词喂给模型 |
| 7 模型生成 | 大模型 | 输出用到这些扩展组件/工具的界面描述（Stream IR），校验通过后可缓存成模板 |
| 8 渲染 | 前端 | 按组件名找到渲染代码画出界面，工具调用接到后端 |

组件这件事横跨两头：**描述**（步骤 2-3，让模型知道有这个组件、能生成、能校验）和**渲染代码**（步骤 1、8，让前端能画出来）。两头靠同一个组件名对齐，缺一边这个组件就在那一边失效——描述缺了模型不会生成它，渲染代码缺了前端只能显示一个「未知组件」占位。

**接口定义**：

- `PUT /rest/smartcanvas/v1/contexts/{contextId}`：注册或整体替换一个 Generation Context 的扩展契约（替换语义，幂等）
- `GET /rest/smartcanvas/v1/contexts`：返回已注册 Generation Context 摘要列表（contextId、version、组件数、工具数）

**注册体（GenUIContextExtension）**：

```yaml
GenUIContextExtension:
  description: Generation Context 扩展契约
  required:
    - version
  properties:
    version:
      description: 扩展契约版本（Contract Version），由注册方维护，契约内容变更时应同步变更
      type: string
      maxLength: 64
    components:
      description: 扩展组件描述，key 为组件名，需与前端 library 中 defineComponent 注册名称一致
      type: object
      additionalProperties:
        $ref: '#/definitions/UIComponentDescriptor'
    componentGroups:
      description: 组件分组说明，引用的组件名必须存在于 base contract 与本扩展的合集中
      type: array
      items:
        type: object
        required:
          - name
          - components
        properties:
          name:
            description: 分组名称
            type: string
          components:
            description: 分组内组件名列表
            type: array
            items:
              type: string
          notes:
            description: 分组使用说明，用于 prompt 注入
            type: array
            items:
              type: string
    tools:
      description: 扩展工具描述（Query/Mutation 可调用的后端能力）
      type: array
      items:
        $ref: '#/definitions/UIToolDescriptor'
    examples:
      description: 生成示例，用于提升生成稳定性
      type: array
      items:
        type: string
    additionalRules:
      description: 附加生成规则，拼装时追加到 prompt 约束
      type: array
      items:
        type: string
  definitions:
    UIToolDescriptor:
      description: 扩展工具描述
      required:
        - name
        - description
        - inputSchema
      properties:
        name:
          description: 工具名称，同一 Generation Context 内唯一
          type: string
          maxLength: 128
        description:
          description: 工具用途说明，用于 prompt 注入
          type: string
          maxLength: 512
        inputSchema:
          description: 入参 JSON Schema
          type: object
        outputSchema:
          description: 出参 JSON Schema
          type: object
```

**扩展组件描述（UIComponentDescriptor）**：

```yaml
UIComponentDescriptor:
  description: 扩展组件描述
  required:
    - description
    - propsSchema
  properties:
    description:
      description: 组件用途说明，用于 prompt 注入
      type: string
      maxLength: 512
    propsSchema:
      description: 组件 props 的 JSON Schema 子集描述，用于推导 prompt 组件签名描述和服务侧类型校验
      type: object
      required:
        - type
        - properties
      properties:
        type:
          description: 固定为 object，表示组件 props 为对象结构
          type: string
          enum:
            - object
        properties:
          description: props 字段定义，key 为 props 名称，value 为字段 schema
          type: object
          additionalProperties:
            $ref: '#/definitions/PropSchema'
        required:
          description: 必填 props 名称列表
          type: array
          items:
            type: string
        additionalProperties:
          description: 是否允许未声明 props，默认 false
          type: boolean
          default: false
    examples:
      description: 组件调用示例，用于提升生成稳定性
      type: array
      items:
        type: string
  definitions:
    PropSchema:
      description: 单个 props 字段 schema
      type: object
      required:
        - type
      properties:
        type:
          description: 字段类型
          type: string
          enum:
            - string
            - number
            - integer
            - boolean
            - array
            - object
            - component
            - action
            - any
        description:
          description: 字段含义，用于 prompt 注入
          type: string
          maxLength: 512
        enum:
          description: 枚举取值，仅 string/number/integer 类型使用
          type: array
          items:
            type:
              - string
              - number
        items:
          description: 数组元素 schema，仅 array 类型使用
          $ref: '#/definitions/PropSchema'
        properties:
          description: 对象字段 schema，仅 object 类型使用
          type: object
          additionalProperties:
            $ref: '#/definitions/PropSchema'
        required:
          description: 对象类型内部必填字段列表
          type: array
          items:
            type: string
        default:
          description: 默认值，用于模型理解字段缺省行为
        examples:
          description: 字段取值示例
          type: array
```

`propsSchema` 与前端 `defineComponent({ name, props, description, component })` 的组件定义保持同源：前端组件库通过 `createLibrary()` 汇总组件并生成 prompt/schema，注册契约按相同信息组织（建议由前端扩展 library 工具链导出，禁止手写），确保模型可见描述、服务侧校验规则和前端渲染实现一致。

**登记的几条规矩**：

- **重复登记 = 整包覆盖**：同一个 id 再登记一次，就整包替换掉旧的（登记几次结果都一样，幂等）；想「下线」某个业务扩展，登记一个空包即可，不需要单独的删除接口
- **重名直接拒绝**：登记的组件名/工具名如果和标准组件包、或本扩展内部自己撞了，整次登记被拒（返回 409），原来已登记的不受影响；其他不合法输入返回 400
- **分组要指到真组件**：组件分组里引用的组件名，必须确实存在（标准组件包 + 本扩展里有），否则登记被拒
- **存 Redis、重启不丢**：登记内容写进 Redis，服务重启时重新加载生效（生成内核 SDK 本身只把契约放在内存里，"存盘"这件事由服务层负责）
- **登记后清掉旧模板**：覆盖登记成功后，自动清掉这个业务之前缓存的模板（见本节 1），避免旧模板套用新契约出错
- **谁能登记**：登记是「管理动作」，业务方在上线/变更时直接调服务完成（不走 AICOService）；生成请求才走 AICOService。登记接口接入产品统一鉴权，只有授权方能写

**和生成是怎么接上的**：

- **怎么进提示词**：登记时把组件描述喂进生成内核；每次生成时，把「标准组件 + 该业务扩展 + 本次临时追加」拼成提示词，且和前端组件库自己生成的提示词逐字节一致
- **顺带做类型校验**：组件的参数描述（propsSchema）还用来校验模型生成的界面里、组件参数填得对不对（见本节 5）
- **报了不存在的 id 会报错**：生成请求若带了一个没登记过的 `contextId`，服务直接报错（`error` 事件），不会「假装没扩展、只用标准组件」硬生成——否则会悄悄产出缺组件的错界面，还可能被缓存成错模板
- **结果里带版本号**：`done` 事件会带上本次用的标准组件包和扩展包各是什么版本，方便排查
- **不开「整段改提示词」的后门**：生产服务不提供「直接替换整段提示词」的调试旁路；要调提示词，用登记的长期规则（`additionalRules`）或请求里的临时规则（`extraRules`）。（参考实现 GenUI Service 作为实验环境保留了这个后门，定位不同）

**示例：扩展注册请求**

以告警运维场景为例，给一个叫 `alarm-ops` 的业务扩展包登记内容：一个扩展组件（告警级别标签）、一个扩展工具（确认告警），外加组件分组、示例和规则。下面这段 JSON 不是手写的，是前端组件库自动导出的（怎么导出见 6.2.4.5）。

`PUT /rest/smartcanvas/v1/contexts/alarm-ops`

```json
{
  "version": "2026.06.1",
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
    }
  },
  "componentGroups": [
    {
      "name": "business",
      "components": ["AlarmSeverityTag"],
      "notes": ["告警业务专用组件，渲染告警级别时优先使用"]
    }
  ],
  "tools": [
    {
      "name": "acknowledgeAlarm",
      "description": "确认（ack）一条告警，返回是否成功",
      "inputSchema": {
        "type": "object",
        "properties": { "alarmId": { "type": "string", "description": "告警 ID" } },
        "required": ["alarmId"]
      },
      "outputSchema": {
        "type": "object",
        "properties": { "success": { "type": "boolean" } }
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

注册成功返回注册摘要（contextId、version、组件数、工具数），并可在 `GET /rest/smartcanvas/v1/contexts` 中观察到 `alarm-ops` 生效。

**示例：使用扩展上下文的生成请求**

生成请求用 `contextId` 选中上面这个业务扩展包，再临时附带一个一次性工具和一条一次性规则（只这次有效，不会被登记下来）。完整请求字段见本节 7。

`POST /rest/smartcanvas/v1/generate/ui`

```json
{
  "scenario": "LUI",
  "userInput": "查看告警列表并支持确认操作",
  "apiName": "queryAlarmList",
  "apiUrl": "/rest/alarm/v1/list",
  "apiMethod": "GET",
  "apiReq": {},
  "apiRsp": {
    "data": [
      { "id": 1, "name": "链路中断", "severity": "critical", "status": "active" }
    ]
  },
  "isAnswer": false,
  "contextId": "alarm-ops",
  "tools": [
    {
      "name": "exportAlarmReport",
      "description": "导出当前告警列表为报表（仅本次会话临时启用）",
      "inputSchema": {
        "type": "object",
        "properties": { "format": { "type": "string", "enum": ["csv", "xlsx"] } },
        "required": ["format"]
      }
    }
  ],
  "extraRules": ["列表顶部展示告警总数"]
}
```

这次请求如果缓存成模板，模板的 key 会把数据来源、用户意图、`contextId`、扩展包版本、临时追加内容都算进去（见本节 1），所以不同业务、不同临时内容不会串用同一个模板；临时工具 `exportAlarmReport` 和已登记的 `acknowledgeAlarm` 不重名，校验通过。

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

组件定义采用 `defineComponent` 描述组件名称、props schema、组件说明和 React 渲染实现；组件集合通过 `createLibrary` 汇总后提供给 Renderer 和 prompt 生成流程。示例如下：

```ts
import { createLibrary, defineComponent } from "@openuidev/react-lang";
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
```

`library` 是 Renderer 的运行时组件库，也是生成 prompt 和 JSON Schema 的来源。SmartCanvasService 在拼装 prompt 时应使用与前端 library 同源的组件描述；前端 Renderer 在渲染时根据 Stream IR 中的组件名查找 library 中的组件实现。

扩展组件的注册契约（6.2.4.2-8 的 `components` / `tools` schema）即由 `library` 经工具链导出：从 `defineComponent` 的 props（zod）推导 `propsSchema`、组件签名与工具 schema，序列化为 `GenUIContextExtension` JSON。导出而非手写，保证「模型可见描述、服务侧校验规则、前端渲染实现」三者同源；业务方拿到导出产物后通过 `PUT /contexts/{contextId}` 注册。

**3. eview 适配**

eview 作为目标 UI 组件库，通过 react-ui-dsl 的 view target 机制适配。适配层负责将 Stream IR 组件语义和 props 映射到 eview 组件实现，优先覆盖布局、文本、表格、表单、图表和业务高频组件。对 eview 暂不支持的能力，采用占位提示、静态展示或降级组件兜底。

#### 6.2.4.6 异常处理与降级

异常处理采用生成侧与渲染侧分层兜底策略。

| 异常场景 | 处理策略 |
|---|---|
| 注册契约名称碰撞 | 注册接口返回 409，原 Generation Context 保持不变 |
| 生成请求 contextId 未注册 | 拼装前校验失败，返回错误事件，不静默回退仅 base contract |
| Overlay 工具名与已注册工具碰撞 | 返回错误事件，不产生拼装结果 |
| Stream IR 生成失败 | SmartCanvasService 捕获异常，返回错误事件或降级 markdown |
| 语法校验失败 | 通过 Reflection 反馈错误并触发重生成，超过阈值后降级 |
| 模型超大输出 | 服务侧检查输出 token，超过阈值进入错误处理或降级 |
| SSE 流式中断 | 前端保留已渲染内容，提示用户重试 |
| parser 解析失败 | Renderer 聚合错误，局部展示错误提示 |
| 表达式求值异常 | 隔离到单组件或单表达式，不影响整体页面 |
| 扩展组件未注册 | 渲染未知组件占位提示，不阻塞其他组件 |
| PIU 拉起失败 | 降级为静态展示，并记录日志 |

全链路通过 traceId 串联生成请求、大模型调用、Stream IR 校验、SSE 返回和前端渲染。关键观测指标包括模板命中率、生成时延、首字节时延、生成成功率、token 用量、校验失败率、降级率、端侧渲染错误率，以及 Generation Context 注册数与注册失败率。


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
8. **Context 拓展接口**：扩展组件与工具的注册式管理（PUT/GET contexts、替换语义、碰撞拒绝）、Redis 持久化、模板失效联动，以及生成请求的 contextId 选择与 Request Overlay。

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
| SmartCanvasService | 生成服务 | 新增 | 承担 Stream IR 生成、提示词组装、Context 拓展注册、模板在线固化、上下文压缩、校验与 Reflection 重生成等能力 |
| Java Generation SDK | 生成内核 | 复用 | SmartCanvasService 集成 SDK 完成 base contract 加载、Extension Registration 与 prompt 拼装（与前端 library prompt 字节对齐）；SDK 保持纯内存语义 |
| Redis 模板与注册存储 | 缓存能力 | 新增 | 存储经校验通过的模板 Stream IR 与已注册的 Generation Context 扩展契约（分 key 空间），支撑模板命中/失效与注册持久化、启动加载 |
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

**AR-06：扩展组件采用注册式 Generation Context 管理**

扩展组件与扩展工具通过 Extension Registration 预注册为以 contextId 隔离的 Generation Context，生成请求仅携带 contextId 与一次性 Request Overlay（动态 tools 与 extraRules），不在请求中内嵌组件契约。该模型与 Java Generation SDK（genui-java-sdk）及 GenUI Service 参考实现（examples/genui-service）保持一致，收益包括：每次生成请求节省扩展描述 token（单 context 上限 30 个组件 × 100 token）；模板固化 key 纳入 contextId 与 Contract Version，实现确定性命中并取代命中后兼容性校验；契约名称碰撞在注册期即被拒绝，而非生成期才暴露。备选「每次请求内嵌扩展组件描述」被否：请求体膨胀、模板固化需逐次做扩展组件超集校验、且与 SDK/参考实现的契约模型脱节。注册契约由 Redis 持久化并在启动时加载（服务层职责），SDK 保持纯内存语义；未注册 contextId 的生成请求在服务层报错，不沿用 SDK 内部的静默回退行为。
