# OpenUI 智能画布功能设计文档

**文档编号**: OPENUI-CANVAS-001  
**版本**: v1.0  
**状态**: 设计评审  
**作者**: OpenUI Team  
**日期**: 2026-05-15

---

## 文档目录

- [第一章 功能规格](#第一章-功能规格)
- [第二章 功能实现设计](#第二章-功能实现设计)
- [第三章 DFX设计](#第三章-dfx设计)
- [第四章 附录](#第四章-附录)

---

# 第一章 功能规格

## 1.1 功能概述

### 1.1.1 功能名称

智能画布（Intelligent Canvas）

### 1.1.2 功能定位

为OpenUI DSL生成的UI组件提供独立的可视化工作区，支持仪表盘卡片展示、全页预览和外部HTML集成，与LUI对话区协同呈现LLM响应内容。

### 1.1.3 功能价值

| 维度 | 价值描述 |
|------|----------|
| 用户价值 | 可视化内容持久展示，不随对话滚动消失；支持多维度仪表盘布局 |
| 业务价值 | 提升LLM响应的呈现能力，支持复杂可视化场景 |
| 技术价值 | 解耦UI渲染目标，提升系统扩展性 |

## 1.2 用户角色与场景

### 1.2.1 用户角色

| 角色 | 描述 | 核心诉求 |
|------|------|----------|
| 终端用户 | 使用AI对话系统的业务人员 | 直观查看可视化内容，无需在对话流中翻找图表 |
| 业务开发者 | 基于OpenUI构建应用的技术人员 | 简单集成画布组件，灵活控制渲染策略 |
| LLM Skill开发者 | 编写Prompt和DSL规则的AI工程师 | 精准控制哪些内容渲染到画布 |

### 1.2.2 用户场景

#### 场景1：系统监控仪表盘

**触发条件**: 用户询问系统运行状态，LLM调用工具获取指标数据

**场景流程**:
```
用户: "查看服务器CPU和内存使用情况"
LLM: 调用监控工具获取实时数据
    → 生成DSL:
       - LUI: Descriptions显示摘要信息
       - Canvas: DashboardCard包含LineChart展示趋势
用户: 在LUI看到摘要，在Canvas Tab看到实时趋势图
      可继续对话，图表持续显示
```

**预期结果**:
- LUI对话区显示文字摘要和操作按钮
- Canvas DashboardTab显示监控卡片，网格布局
- 用户可随时查看图表，不受对话滚动影响

#### 场景2：报表预览

**触发条件**: 用户请求生成报表或文档

**场景流程**:
```
用户: "生成本月销售报表"
LLM: 调用报表工具获取数据
    → 生成DSL:
       - LUI: Text确认报表已生成
       - Canvas: PreviewCard包含完整报表内容
用户: 点击Canvas "本月销售报表" Tab查看完整报表
```

**预期结果**:
- LUI显示简要确认信息
- Canvas新增PreviewTab，标题为报表名称
- Tab内整页展示报表内容（表格、图表、文字）

#### 场景3：外部应用集成

**触发条件**: 用户需要查看外部系统界面或第三方仪表盘

**场景流程**:
```
用户: "打开Grafana监控面板"
Skill: 解析用户意图，提供Grafana URL
LLM: 生成DSL:
     - LUI: Text提示外部应用已加载
     - Canvas: HtmlTab嵌入Grafana URL
用户: Canvas新增Tab显示Grafana完整界面
```

**预期结果**:
- LUI显示简要提示
- Canvas新增HtmlTab，iframe嵌入外部应用
- 用户可在Canvas中操作外部应用界面

#### 场景4：混合内容展示

**触发条件**: 用户请求复杂信息展示

**场景流程**:
```
用户: "分析上周流量数据，并对比上周和本周趋势"
LLM: 获取数据，生成多个可视化
    → 生成DSL:
       - LUI: Descriptions + Table显示详细数据
       - Canvas DashboardTab: 多个DashboardCard
         - Card1: LineChart上周趋势
         - Card2: LineChart本周趋势
         - Card3: BarChart对比分析
用户: LUI查看数据明细，Canvas对比多个图表
```

**预期结果**:
- LUI展示可交互的数据表格
- Canvas DashboardTab显示多个卡片（网格排列）
- 用户可同时对比多个可视化维度

## 1.3 功能需求列表

### 1.3.1 DSL层需求

| 需求ID | 需求描述 | 优先级 | 验收标准 |
|--------|----------|--------|----------|
| FR-DSL-001 | 新增DashboardCard组件 | P0 | 组件可被DSL Parser识别和解析 |
| FR-DSL-002 | DashboardCard支持children、title、tab、size、cardId属性 | P0 | 属性解析正确，类型校验通过 |
| FR-DSL-003 | 新增PreviewCard组件 | P0 | 组件可被DSL Parser识别和解析 |
| FR-DSL-004 | PreviewCard支持children、title、cardId属性 | P0 | title必填，属性解析正确 |
| FR-DSL-005 | 新增HtmlTab组件 | P0 | 组件可被DSL Parser识别和解析 |
| FR-DSL-006 | HtmlTab支持url、title、cardId属性 | P0 | url和title必填，属性解析正确 |
| FR-DSL-007 | Canvas组件从LUI树剔除 | P0 | LUI渲染不含Canvas组件节点 |
| FR-DSL-008 | Canvas组件提取为CanvasItem数组 | P0 | Parser返回canvasItems字段 |

### 1.3.2 解析层需求

| 求ID | 需求描述 | 优先级 | 验收标准 |
|--------|----------|--------|----------|
| FR-PARSE-001 | Parser识别Canvas组件类型 | P0 | 通过typeName区分三种组件 |
| FR-PARSE-002 | 提取Canvas组件并剔除LUI树 | P0 | LUI树不含Canvas节点 |
| FR-PARSE-003 | 流式解析支持Canvas组件提取 | P1 | 流式更新时canvasItems增量更新 |
| FR-PARSE-004 | CanvasItem类型定义和类型守卫 | P0 | 类型检查正确，TypeScript编译通过 |

### 1.3.3 渲染层需求

| 需求ID | 需求描述 | 优先级 | 验收标准 |
|--------|----------|--------|----------|
| FR-RENDER-001 | Renderer新增onCanvasItems回调 | P0 | 回调在ParseResult变化时触发 |
| FR-RENDER-002 | CanvasItem数组正确传递 | P0 | 回调参数包含完整CanvasItem列表 |
| FR-RENDER-003 | LUI和Canvas共享dataModel | P0 | dataModel对两边均可访问 |
| FR-RENDER-004 | 导出renderElementNode函数 | P0 | Canvas可渲染ElementNode树 |

### 1.3.4 前端组件需求

| 需求ID | 需求描述 | 优先级 | 验收标准 |
|--------|----------|--------|----------|
| FR-COMP-001 | CanvasTabs容器组件 | P0 | 支持多Tab渲染和管理 |
| FR-COMP-002 | DashboardGrid网格布局组件 | P0 | 12列固定网格，严格顺序排列 |
| FR-COMP-003 | PreviewContent整页渲染组件 | P0 | 内容占满Tab区域 |
| FR-COMP-004 | HtmlEmbed iframe嵌入组件 | P0 | iframe正确加载URL |
| FR-COMP-005 | DashboardCard按tab属性分组 | P0 | 同tab名称的卡片归入同一Tab |
| FR-COMP-006 | PreviewCard独占Tab | P0 | 每个PreviewCard创建独立Tab |
| FR-COMP-007 | HtmlTab独占Tab | P0 | 每个HtmlTab创建独立Tab |
| FR-COMP-008 | 清空画布按钮 | P1 | 用户点击可清空CanvasItems |
| FR-COMP-009 | Tab切换交互 | P0 | 用户可切换查看不同Tab |
| FR-COMP-010 | 垂直滚动支持 | P1 | DashboardGrid内容超出时滚动 |

### 1.3.5 Prompt层需求

| 需求ID | 需求描述 | 优先级 | 验收标准 |
|--------|----------|--------|----------|
| FR-PROMPT-001 | Canvas组件签名自动包含 | P0 | dslLibrary.prompt()输出组件签名 |
| FR-PROMPT-002 | 业务层可通过additionalRules扩展 | P0 | 业务层添加使用规则生效 |
| FR-PROMPT-003 | 业务层可通过examples扩展 | P0 | 业务层添加示例生效 |
| FR-PROMPT-004 | 卡片数量建议上限提示 | P2 | Prompt含建议上限警告 |

## 1.4 非功能需求

### 1.4.1 性能需求

| 需求ID | 需求描述 | 目标值 |
|--------|----------|--------|
| NFR-PERF-001 | DSL解析含Canvas组件的响应时间 | ≤现有解析时间+5% |
| NFR-PERF-002 | Canvas组件首次渲染时间 | ≤200ms |
| NFR-PERF-003 | Tab切换响应时间 | ≤100ms |
| NFR-PERF-004 | DashboardGrid滚动流畅度 | FPS≥30 |

### 1.4.2 可靠性需求

| 需求ID | 需求描述 | 目标值 |
|--------|----------|--------|
| NFR-REL-001 | Canvas组件解析成功率 | ≥99.9% |
| NFR-REL-002 | iframe加载失败处理 | 显示错误提示，不崩溃 |
| NFR-REL-003 | 流式解析Canvas组件稳定性 | 无内存泄漏 |

### 1.4.3 可维护性需求

| 需求ID | 需求描述 |
|--------|----------|
| NFR-MAIN-001 | 组件代码覆盖率≥80% |
| NFR-MAIN-002 | 类型定义完整，无any类型滥用 |
| NFR-MAIN-003 | 文档与代码同步更新 |

## 1.5 界面原型

### 1.5.1 布局结构

```
┌──────────────────────────────────────────────────────────────────┐
│                        页面整体布局                                │
├──────────────────────────────┬───────────────────────────────────┤
│                              │                                   │
│     LUI Dialog Area          │      Canvas Area                  │
│     (对话流)                  │      (智能画布)                   │
│                              │                                   │
│  ┌────────────────────┐      │  ┌─────────────────────────────┐  │
│  │ User Message       │      │  │ [清空画布]                   │  │
│  ├────────────────────┤      │  ├─────────────────────────────┤  │
│  │ LLM Response       │      │  │ ┌─Tab Bar─────────────────┐ │  │
│  │                    │      │  │ │ [Dashboard][Preview][...]│ │  │
│  │ - Text             │      │  │ └─────────────────────────┘ │  │
│  │ - Table            │      │  ├─────────────────────────────┤  │
│  │ - Descriptions     │      │  │                             │  │
│  │ - Button           │      │  │  DashboardGrid              │  │
│  │                    │      │  │                             │  │
│  │                    │      │  │  ┌─────────┐ ┌─────────────┐│  │
│  │                    │      │  │  │Card1    │ │Card2        ││  │
│  │                    │      │  │  │LineChart│ │BarChart     ││  │
│  │                    │      │  │  │6 cols   │ │6 cols       ││  │
│  │                    │      │  │  └─────────┘ └─────────────┘│  │
│  │                    │      │  │                             │  │
│  │                    │      │  │  ┌───────────────────────┐  │  │
│  │                    │      │  │  │Card3 (8 cols)         │  │  │
│  │                    │      │  │  │PieChart               │  │  │
│  │                    │      │  │  └───────────────────────┘  │  │
│  │                    │      │  │                             │  │
│  └────────────────────┘      │  └─────────────────────────────┘  │
│                              │                                   │
│                              │  (垂直滚动条)                      │
│                              │                                   │
└──────────────────────────────┴───────────────────────────────────┘
```

### 1.5.2 DashboardGrid网格布局

```
12列网格示意:
┌────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┐
│ 1  │ 2  │ 3  │ 4  │ 5  │ 6  │ 7  │ 8  │ 9  │ 10 │ 11 │ 12 │
└────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┘

卡片排列示例 (size.w默认6):

DSL顺序:
  cardA = DashboardCard(..., {size: {w: 6}})
  cardB = DashboardCard(..., {size: {w: 8}})
  cardC = DashboardCard(..., {size: {w: 4}})
  cardD = DashboardCard(...)  // 默认w=6

排列结果:
Row 1:
┌──────────────────────┐┌──────────────────────┐
│    cardA (w=6)       ││    空白 (剩余6列)     │
└──────────────────────┘└──────────────────────┘
                         cardB(w=8)无法放入，换行

Row 2:
┌────────────────────────────────────────┐┌────────────────┐
│             cardB (w=8)                ││   空白 (4列)   │
└────────────────────────────────────────┘└────────────────┘

Row 3:
┌───────────────────┐┌──────────────────────┐
│  cardC (w=4)      ││    cardD (w=6)       │
└───────────────────┘└──────────────────────┘
```

### 1.5.3 PreviewTab和HtmlTab布局

```
PreviewTab (整页):
┌─────────────────────────────────────────────────┐
│                                                 │
│  ┌───────────────────────────────────────────┐  │
│  │                                           │  │
│  │         PreviewCard Content               │  │
│  │                                           │  │
│  │  - Text, Table, Charts...                 │  │
│  │                                           │  │
│  │                                           │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
└─────────────────────────────────────────────────┘


HtmlTab (iframe):
┌─────────────────────────────────────────────────┐
│                                                 │
│  ┌───────────────────────────────────────────┐  │
│  │                                           │  │
│  │         iframe(url)                       │  │
│  │                                           │  │
│  │  外部应用完整界面                          │  │
│  │                                           │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
└─────────────────────────────────────────────────┘
```

---

# 第二章 功能实现设计

## 2.1 技术架构

### 2.1.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              应用层                                      │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                     ChatPage / App Shell                           │  │
│  │  ┌─────────────────┐                    ┌─────────────────────────┐│  │
│  │  │   LUI Area      │                    │     Canvas Area         ││  │
│  │  │  <Renderer />   │                    │  <CanvasTabs />         ││  │
│  │  └─────────────────┘                    └─────────────────────────┘│  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ onCanvasItems
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                            组件层                                        │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    @openuidev/react-ui-dsl                         │  │
│  │                                                                     │  │
│  │  ┌─────────────────┐ ┌──────────────┐ ┌──────────────────────────┐ │  │
│  │  │  DashboardCard  │ │ PreviewCard  │ │        HtmlTab           │ │  │
│  │  │  Schema/Comp    │ │ Schema/Comp  │ │     Schema/Comp          │ │  │
│  │  └─────────────────┘ └──────────────┘ └──────────────────────────┘ │  │
│  │                                                                     │  │
│  │  ┌─────────────────────────────────────────────────────────────┐   │  │
│  │  │                    CanvasTabs                                │   │  │
│  │  │  ┌─────────────┐ ┌──────────────┐ ┌─────────────────────────┐│   │  │
│  │  │  │DashboardGrid│ │PreviewContent│ │      HtmlEmbed          ││   │  │
│  │  │  └─────────────┘ └──────────────┘ └─────────────────────────┘│   │  │
│  │  └─────────────────────────────────────────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ renderElementNode
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           渲染层                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    @openuidev/react-lang                           │  │
│  │                                                                     │  │
│  │  ┌─────────────────────────────────────────────────────────────┐   │  │
│  │  │                    Renderer                                  │   │  │
│  │  │  Props:                                                       │   │  │
│  │  │    - response: DSL string                                    │   │  │
│  │  │    - library: Component library                               │   │  │
│  │  │    - onCanvasItems: (items: CanvasItem[]) => void            │   │  │
│  │  │    - dataModel: Record<string, unknown>                      │   │  │
│  │  └─────────────────────────────────────────────────────────────┘   │  │
│  │                                                                     │  │
│  │  ┌─────────────────────────────────────────────────────────────┐   │  │
│  │  │                 renderElementNode                             │   │  │
│  │  │  (node, library, dataModel) => React.ReactNode               │   │  │
│  │  └─────────────────────────────────────────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ ParseResult.canvasItems
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           解析层                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    @openuidev/lang-core                            │  │
│  │                                                                     │  │
│  │  ┌─────────────────────────────────────────────────────────────┐   │  │
│  │  │                    Parser                                     │   │  │
│  │  │  Input: DSL text                                              │   │  │
│  │  │  Output: ParseResult                                          │   │  │
│  │  │    - root: ElementNode (LUI tree, Canvas removed)             │   │  │
│  │  │    - canvasItems: CanvasItem[]                                │   │  │
│  │  │    - ...other fields                                          │   │  │
│  │  └─────────────────────────────────────────────────────────────┘   │  │
│  │                                                                     │  │
│  │  ┌─────────────────────────────────────────────────────────────┐   │  │
│  │  │                    Types                                      │   │  │
│  │  │  - DashboardCardNode                                          │   │  │
│  │  │  - PreviewCardNode                                            │   │  │
│  │  │  - HtmlTabNode                                                │   │  │
│  │  │  - CanvasItem (union)                                         │   │  │
│  │  │  - Type guards                                                │   │  │
│  │  └─────────────────────────────────────────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ DSL generation
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                            Prompt层                                      │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    dslLibrary.prompt()                             │  │
│  │                                                                     │  │
│  │  Output: System Prompt for LLM                                     │  │
│  │    - Component signatures (DashboardCard, PreviewCard, HtmlTab)    │  │
│  │    - Business layer additionalRules                                │  │
│  │    - Business layer examples                                       │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.1.2 模块划分

| 层级 | 模块 | 职责 | 包名 |
|------|------|------|------|
| DSL层 | Canvas组件定义 | 定义DashboardCard/PreviewCard/HtmlTab schema和组件 | @openuidev/react-ui-dsl |
| 解析层 | Parser扩展 | 提取Canvas组件，剔除LUI树 | @openuidev/lang-core |
| 解析层 | 类型定义 | CanvasItem类型及类型守卫 | @openuidev/lang-core |
| 渲染层 | Renderer扩展 | onCanvasItems回调，renderElementNode导出 | @openuidev/react-lang |
| 组件层 | CanvasTabs | Tab容器，管理多Tab渲染 | @openuidev/react-ui-dsl |
| 组件层 | DashboardGrid | 12列网格布局 | @openuidev/react-ui-dsl |
| 组件层 | PreviewContent | 整页内容渲染 | @openuidev/react-ui-dsl |
| 组件层 | HtmlEmbed | iframe嵌入 | @openuidev/react-ui-dsl |
| Prompt层 | Prompt生成 | Canvas组件签名生成 | @openuidev/react-ui-dsl |

## 2.2 数据流设计

### 2.2.1 DSL生成数据流

```
┌─────────────┐
│  用户请求   │
└─────────────┘
      │
      ▼
┌─────────────┐
│   Skill层   │ ─────────────────────────────────────┐
│  意图解析   │                                      │
└─────────────┘                                      │
      │                                              │
      ▼                                              ▼
┌─────────────┐                          ┌─────────────────────────┐
│  Tool调用   │                          │  Business Prompt配置    │
│  数据获取   │                          │  - additionalRules      │
└─────────────┘                          │  - examples             │
      │                                  └─────────────────────────┘
      ▼                                              │
┌─────────────┐                                     │
│  LLM推理    │                                     │
│  DSL生成    │ ◄───────────────────────────────────┘
└─────────────┘
      │
      │ 生成DSL:
      │   root = VLayout([...])
      │   cpuCard = DashboardCard([chart], "CPU", {...})
      │   preview = PreviewCard([content], "Report")
      │   html = HtmlTab("url", "Title")
      │
      ▼
┌─────────────────────────────────────────────────────────────────┐
│                        DSL Response String                       │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2.2 DSL解析数据流

```
┌─────────────────────────────────────────────────────────────────┐
│                        DSL Response String                       │
│  root = VLayout([table])                                         │
│  cpuCard = DashboardCard([chart], "CPU", {tab: "Monitor"})       │
│  preview1 = PreviewCard([content], "Report")                     │
│  html1 = HtmlTab("https://app.com", "External")                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼ parse(text, schema)
┌─────────────────────────────────────────────────────────────────┐
│                           Parser                                 │
│  1. Lexer: tokenize DSL text                                     │
│  2. Parser: build AST tree                                       │
│  3. Materialize: AST → ElementNode tree                          │
│  4. extractCanvasItems():                                        │
│     - Traverse tree                                              │
│     - Identify typeName: "DashboardCard" | "PreviewCard" | "HtmlTab" │
│     - Collect into canvasItems[]                                 │
│     - Remove from LUI tree (return null)                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        ParseResult                               │
│  {                                                               │
│    root: ElementNode (LUI tree),                                 │
│    canvasItems: [                                                │
│      DashboardCardNode {typeName: "DashboardCard", props: {...}},│
│      PreviewCardNode {typeName: "PreviewCard", props: {...}},    │
│      HtmlTabNode {typeName: "HtmlTab", props: {...}}             │
│    ],                                                            │
│    meta: {...},                                                  │
│    stateDeclarations: {...},                                     │
│    queryStatements: [...],                                       │
│    mutationStatements: [...]                                     │
│  }                                                               │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
┌─────────────────────────┐   ┌─────────────────────────────────┐
│      LUI Root           │   │        Canvas Items              │
│  VLayout([table])       │   │  [                              │
│  (不含Canvas组件)       │   │    DashboardCardNode,           │
│                         │   │    PreviewCardNode,             │
│                         │   │    HtmlTabNode                  │
│                         │   │  ]                              │
└─────────────────────────┘   └─────────────────────────────────┘
```

### 2.2.3 渲染数据流

```
┌─────────────────────────────────────────────────────────────────┐
│                        ParseResult                               │
└─────────────────────────────────────────────────────────────────┘
              │                               │
              ▼                               ▼
┌─────────────────────────────┐   ┌─────────────────────────────┐
│      Renderer (LUI)         │   │      onCanvasItems          │
│                             │   │      Callback               │
│  Props:                     │   │                             │
│    response: DSL            │   │  onCanvasItems(canvasItems) │
│    library: dslLibrary      │   │                             │
│    dataModel: {...}         │   │  → setCanvasItems(items)    │
│                             │   │                             │
│  Internal:                  │   │                             │
│    useOpenUIState()         │   │                             │
│      - parseResult          │   │                             │
│      - canvasItems          │   │                             │
│                             │   │                             │
│  Render:                    │   │                             │
│    <RenderNode node={root} />│   │                             │
│    → LUI DOM                │   │                             │
└─────────────────────────────┘   └─────────────────────────────┘
                                              │
                                              ▼
                              ┌─────────────────────────────┐
                              │      Application State      │
                              │      canvasItems: CanvasItem[]│
                              └─────────────────────────────┘
                                              │
                                              ▼
                              ┌─────────────────────────────┐
                              │       CanvasTabs            │
                              │                             │
                              │  Props:                     │
                              │    items: canvasItems       │
                              │    library: dslLibrary      │
                              │    dataModel: {...}         │
                              │                             │
                              │  Internal:                  │
                              │    - Group by typeName      │
                              │    - Group DashboardCard    │
                              │      by tab prop            │
                              │                             │
                              │  Render:                    │
                              │    <Tabs items={tabItems}> │
                              │      - DashboardTab        │
                              │      - PreviewTab          │
                              │      - HtmlTab             │
                              └─────────────────────────────┘
```

### 2.2.4 CanvasTabs内部数据流

```
┌─────────────────────────────────────────────────────────────────┐
│                    CanvasItems Input                             │
│  [                                                              │
│    {typeName: "DashboardCard", props: {tab: "Dashboard", ...}}, │
│    {typeName: "DashboardCard", props: {tab: "Monitor", ...}},   │
│    {typeName: "DashboardCard", props: {tab: "Dashboard", ...}}, │
│    {typeName: "PreviewCard", props: {title: "Report", ...}},    │
│    {typeName: "HtmlTab", props: {title: "External", ...}}       │
│  ]                                                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼ useMemo分离
┌─────────────────────────────────────────────────────────────────┐
│                    分离后的数据                                   │
│  dashboardCards: [DashboardCardNode, DashboardCardNode, ...]    │
│  previewCards: [PreviewCardNode]                                │
│  htmlTabs: [HtmlTabNode]                                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼ dashboardCards按tab分组
┌─────────────────────────────────────────────────────────────────┐
│                  DashboardTab分组                                │
│  {                                                              │
│    "Dashboard": [Card1, Card3],                                 │
│    "Monitor": [Card2]                                           │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼ 构建tabItems
┌─────────────────────────────────────────────────────────────────┐
│                    Tab Items                                     │
│  [                                                              │
│    {                                                            │
│      key: "dashboard-Dashboard",                                │
│      label: "Dashboard",                                        │
│      children: <DashboardGrid cards={[Card1, Card3]} />        │
│    },                                                           │
│    {                                                            │
│      key: "dashboard-Monitor",                                  │
│      label: "Monitor",                                          │
│      children: <DashboardGrid cards={[Card2]} />               │
│    },                                                           │
│    {                                                            │
│      key: "preview-Report",                                     │
│      label: "Report",                                           │
│      children: <PreviewContent content={...} />                 │
│    },                                                           │
│    {                                                            │
│      key: "html-External",                                      │
│      label: "External",                                         │
│      children: <HtmlEmbed url="..." />                          │
│    }                                                            │
│  ]                                                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    <Tabs items={tabItems} />                     │
└─────────────────────────────────────────────────────────────────┘
```

## 2.3 接口设计

### 2.3.1 DSL组件接口

#### DashboardCard Schema

```typescript
interface DashboardCardProps {
  children?: unknown[];              // 子组件数组，可选
  title?: string;                    // 卡片标题，可选
  tab?: string;                      // DashboardTab归属，默认"Dashboard"
  size?: {                           // 网格尺寸，可选
    w?: number;                      // 列跨度(1-12)，默认6
    h?: number;                      // 行高度(可选，自适应)
  };
  cardId?: string;                   // 卡片ID，可选，自动生成
}
```

#### PreviewCard Schema

```typescript
interface PreviewCardProps {
  children?: unknown[];              // 子组件数组，可选
  title: string;                     // Tab名称，必填
  cardId?: string;                   // 卡片ID，可选，自动生成
}
```

#### HtmlTab Schema

```typescript
interface HtmlTabProps {
  url: string;                       // iframe URL，必填
  title: string;                     // Tab名称，必填
  cardId?: string;                   // 卡片ID，可选，自动生成
}
```

### 2.3.2 Parser接口

#### ParseResult扩展

```typescript
interface ParseResult {
  root: ElementNode | null;          // LUI树(Canvas组件已剔除)
  meta: {                            // 元信息
    incomplete: boolean;
    unresolved: string[];
    orphaned: string[];
    statementCount: number;
    errors: ValidationError[];
  };
  stateDeclarations: Record<string, unknown>;
  queryStatements: QueryStatementInfo[];
  mutationStatements: MutationStatementInfo[];
  
  // NEW: Canvas组件提取结果
  canvasItems: CanvasItem[];
}
```

#### CanvasItem类型

```typescript
type CanvasItem = DashboardCardNode | PreviewCardNode | HtmlTabNode;

interface DashboardCardNode extends ElementNode {
  typeName: "DashboardCard";
  props: DashboardCardProps;
}

interface PreviewCardNode extends ElementNode {
  typeName: "PreviewCard";
  props: PreviewCardProps;
}

interface HtmlTabNode extends ElementNode {
  typeName: "HtmlTab";
  props: HtmlTabProps;
}
```

#### Type Guards

```typescript
function isDashboardCardNode(node: ElementNode): node is DashboardCardNode;
function isPreviewCardNode(node: ElementNode): node is PreviewCardNode;
function isHtmlTabNode(node: ElementNode): node is HtmlTabNode;
function isCanvasItem(node: ElementNode): node is CanvasItem;
```

### 2.3.3 Renderer接口

#### RendererProps扩展

```typescript
interface RendererProps {
  response: string | null;           // DSL文本
  library: Library;                  // 组件库
  isStreaming?: boolean;             // 流式状态
  onAction?: (event: ActionEvent) => void;
  onStateUpdate?: (state: Record<string, unknown>) => void;
  initialState?: Record<string, any>;
  onParseResult?: (result: ParseResult | null) => void;
  dataModel?: Record<string, unknown>;
  locale?: string;
  toolProvider?: ToolProvider | null;
  queryLoader?: React.ReactNode;
  onError?: (errors: OpenUIError[]) => void;
  
  // NEW: Canvas组件回调
  onCanvasItems?: (items: CanvasItem[]) => void;
}
```

#### renderElementNode导出

```typescript
/**
 * 渲染ElementNode树为React节点
 * @param node ElementNode或数组
 * @param library 组件库
 * @param dataModel 数据模型
 * @returns React.ReactNode
 */
function renderElementNode(
  node: ElementNode | ElementNode[] | unknown,
  library: Library,
  dataModel?: Record<string, unknown>
): React.ReactNode;
```

### 2.3.4 CanvasTabs组件接口

```typescript
interface CanvasTabsProps {
  items: CanvasItem[];               // Canvas组件数组
  library: Library;                  // 组件库(用于渲染children)
  dataModel?: Record<string, unknown>; // 数据模型
  showClearButton?: boolean;         // 显示清空按钮，默认true
  onClear?: () => void;              // 清空回调
}
```

### 2.3.5 Prompt接口

```typescript
interface PromptOptions {
  preamble?: string;
  additionalRules?: string[];        // 业务层添加规则
  examples?: string[];               // 业务层添加示例
  tools?: ToolDefinition[];
  toolCalls?: boolean;
  bindings?: boolean;
}

// dslLibrary.prompt()自动包含Canvas组件签名
function prompt(options?: PromptOptions): string;
```

## 2.4 模块详细设计

### 2.4.1 Parser extractCanvasItems模块

**职责**: 从ElementNode树中提取Canvas组件，剔除LUI树

**算法**:
```
function extractCanvasItems(root: ElementNode | null): {
  luiRoot: ElementNode | null,
  canvasItems: CanvasItem[]
}

算法流程:
1. 初始化canvasItems = []
2. traverse(node):
   - if node is ElementNode:
     - if isCanvasItem(node):
       - push to canvasItems
       - return null (剔除)
     - else:
       - traverse每个prop值
       - return 修改后的node
   - if node is Array:
     - traverse每个元素
     - filter掉null值
     - return 修改后的数组
   - else: return node
3. luiRoot = traverse(root)
4. return {luiRoot, canvasItems}
```

**边界处理**:
- root为null: 返回{luiRoot: null, canvasItems: []}
- 空数组children: 返回null，不保留空数组
- 嵌套Canvas组件: 理论上不应出现（Prompt禁止）

### 2.4.2 DashboardGrid布局模块

**职责**: 将DashboardCard数组排列为12列网格

**算法**:
```
function layoutCards(cards: DashboardCardNode[]): LayoutRow[]

算法流程(严格顺序填充):
1. 初始化rows = [], currentRow = [], currentCol = 0
2. for each card in cards:
   - width = card.props.size?.w ?? 6
   - if currentCol + width <= 12:
     - push {card, startCol: currentCol, width} to currentRow
     - currentCol += width
   - else:
     - push currentRow to rows
     - reset currentRow = [{card, startCol: 0, width}]
     - currentCol = width
3. if currentRow非空: push to rows
4. return rows

LayoutRow结构:
{
  card: DashboardCardNode,
  startCol: number (0-11),
  width: number (1-12)
}[]
```

**边界处理**:
- cards为空数组: 返回空rows
- card size.w超过12: schema校验拦截(max=12)
- card size.w为undefined: 默认6列

### 2.4.3 CanvasTabs分组模块

**职责**: 按类型和属性分组Canvas组件

**算法**:
```
function groupCanvasItems(items: CanvasItem[]): {
  dashboardGroups: Record<string, DashboardCardNode[]>,
  previewCards: PreviewCardNode[],
  htmlTabs: HtmlTabNode[]
}

算法流程:
1. 初始化groups = {}, previews = [], htmls = []
2. for each item in items:
   - switch item.typeName:
     - "DashboardCard":
       - tabName = item.props.tab ?? "Dashboard"
       - if !groups[tabName]: groups[tabName] = []
       - push item to groups[tabName]
     - "PreviewCard":
       - push item to previews
     - "HtmlTab":
       - push item to htmls
3. return {dashboardGroups: groups, previewCards: previews, htmlTabs: htmls}
```

**分组规则**:
- DashboardCard: 按props.tab分组，默认"Dashboard"
- PreviewCard: 每个独立成Tab，不分组
- HtmlTab: 每个独立成Tab，不分组

## 2.5 状态管理设计

### 2.5.1 Application State

```typescript
interface AppState {
  // DSL响应
  dslResponse: string;
  
  // LUI状态
  luiRenderResult: React.ReactNode;
  
  // Canvas状态
  canvasItems: CanvasItem[];
  
  // 共享数据模型
  dataModel: Record<string, unknown>;
  
  // 流式状态
  isStreaming: boolean;
}
```

### 2.5.2 Renderer内部状态

```typescript
interface RendererInternalState {
  // 解析结果
  parseResult: ParseResult | null;
  
  // 评估后的渲染树
  evaluatedRoot: React.ReactNode;
  
  // Query加载状态
  isQueryLoading: boolean;
  
  // Context值
  contextValue: OpenUIContextValue;
}
```

### 2.5.3 CanvasTabs内部状态

```typescript
interface CanvasTabsInternalState {
  // 当前活跃Tab
  activeKey: string;  // 默认"dashboard-Dashboard"
  
  // 分组后的数据(useMemo)
  dashboardGroups: Record<string, DashboardCardNode[]>;
  previewCards: PreviewCardNode[];
  htmlTabs: HtmlTabNode[];
  
  // Tab列表(useMemo)
  tabItems: TabItem[];
}
```

## 2.6 错误处理设计

### 2.6.1 DSL解析错误

| 错误场景 | 处理方式 | 错误码 |
|----------|----------|--------|
| DashboardCard props.title类型错误 | schema校验，报告missing-required或invalid-prop | validation-error |
| PreviewCard缺少title | 报告missing-required | missing-required |
| HtmlTab缺少url或title | 报告missing-required | missing-required |
| size.w超过12 | schema校验拦截(max=12) | invalid-prop |

### 2.6.2 渲染错误

| 错误场景 | 处理方式 |
|----------|----------|
| Canvas组件children渲染失败 | ErrorBoundary捕获，显示fallback |
| iframe加载失败(HtmlTab) | 显示错误提示："外部内容加载失败" |
| DashboardGrid空cards | 不渲染，返回null |

### 2.6.3 状态错误

| 错误场景 | 处理方式 |
|----------|----------|
| onCanvasItems未提供 | Canvas组件不渲染，LUI正常 |
| library未包含Canvas组件 | CanvasItem被识别但渲染时跳过 |
| dataModel未提供 | Canvas渲染时children无数据 |

---

# 第三章 DFX设计

## 3.1 性能设计

### 3.1.1 解析性能

**目标**: DSL解析含Canvas组件时，响应时间不超过现有解析时间+5%

**设计要点**:

| 优化项 | 实现方式 |
|----------|----------|
| 单次遍历提取 | extractCanvasItems与树构建合并，避免二次遍历 |
| useMemo缓存 | CanvasTabs分组和布局计算使用useMemo |
| 流式增量 | 流式解析时仅更新变化的canvasItems |

**性能模型**:
```
解析时间 = Lexer时间 + Parser时间 + Materialize时间 + Extract时间

Extract时间 = O(N) * C
  N: ElementNode数量
  C: 单节点判断常数时间(≈1μs)

估算:
  N=100节点 → Extract时间≈0.1ms
  现有解析时间≈5ms → 新增0.1ms ≈ 2%增量
```

### 3.1.2 渲染性能

**目标**: Canvas首次渲染≤200ms，Tab切换≤100ms

**设计要点**:

| 优化项 | 实现方式 |
|----------|----------|
| React.memo | DashboardGrid、PreviewContent、HtmlEmbed使用memo |
| 惰性渲染 | Tab切换时仅渲染活跃Tab内容 |
| 虚拟滚动 | DashboardGrid卡片过多时考虑虚拟滚动(可选) |
| iframe懒加载 | HtmlTab iframe仅在Tab激活时加载 |

**渲染模型**:
```
CanvasTabs渲染时间 = 分组计算 + Tab渲染

分组计算: useMemo, 仅items变化时计算
Tab渲染:
  - DashboardGrid: cards.map → Card渲染
  - PreviewContent: renderElementNode(children)
  - HtmlEmbed: iframe加载(异步)

估算:
  10个DashboardCard → 分组+布局≈5ms
  1个PreviewCard(children=10节点) → renderElementNode≈10ms
  Tab切换: React diff + commit ≈ 50ms
```

### 3.1.3 滚动性能

**目标**: DashboardGrid滚动FPS≥30

**设计要点**:

| 优化项 | 实现方式 |
|----------|----------|
| CSS overflow | 使用原生overflow-y: auto，避免自定义滚动 |
| 防抖渲染 | 滚动时不触发重新渲染 |
| 固定高度容器 | Canvas容器固定高度，避免动态调整 |

### 3.1.4 内存性能

**目标**: 无内存泄漏，长时间运行内存稳定

**设计要点**:

| 优化项 | 实现方式 |
|----------|----------|
| useEffect清理 | Renderer清理onCanvasItems回调引用 |
| iframe卸载 | Tab切换时HtmlEmbed iframe可卸载(可选) |
| 缓存清理 | canvasItems状态重置时清理引用 |

## 3.2 可靠性设计

### 3.2.1 解析可靠性

**目标**: Canvas组件解析成功率≥99.9%

**设计要点**:

| 防护项 | 实现方式 |
|----------|----------|
| Schema严格校验 | z.strict()防止多余属性 |
| 必填字段校验 | PreviewCard.title、HtmlTab.url/title必填 |
| 类型守卫 | isCanvasItem判断前检查typeName存在 |
| 默认值兜底 | DashboardCard.tab默认"Dashboard"，size.w默认6 |

### 3.2.2 渲染可靠性

**目标**: iframe加载失败不崩溃，显示错误提示

**设计要点**:

| 防护项 | 实现方式 |
|----------|----------|
| ErrorBoundary | CanvasTabs外层包裹ErrorBoundary |
| iframe错误处理 | onError回调，显示"外部内容加载失败" |
| 空状态处理 | canvasItems为空时返回null |
| children为空 | DashboardCard/PreviewCard children可选，空数组渲染null |

### 3.2.3 流式解析可靠性

**目标**: 流式更新时canvasItems稳定，无重复或丢失

**设计要点**:

| 防护项 | 实现方式 |
|----------|----------|
| 增量合并 | ParseResult每次全新，canvasItems数组重新生成 |
| 去重处理 | 相同statementId的CanvasItem不重复(Parser保证) |
| 时序控制 | onCanvasItems仅在ParseResult变化时触发 |

## 3.3 可维护性设计

### 3.3.1 代码结构

**目标**: 模块职责清晰，易于定位和修改

**目录结构**:
```
packages/
├── lang-core/
│   └── src/
│       ├── parser/
│       │   ├── types.ts          # CanvasItem类型定义
│       │   ├── parser.ts         # extractCanvasItems逻辑
│       │   └── extractCanvas.ts  # 提取模块(可选独立文件)
│       └── runtime/
│           └── evaluator.ts      # Canvas组件评估(可选)
│
├── react-lang/
│   └── src/
│       ├── Renderer.tsx          # onCanvasItems回调
│       ├── renderElementNode.ts  # 导出函数
│       ├── index.ts              # 导出CanvasItem类型
│       └── context.ts            # Context扩展(可选)
│
├── react-ui-dsl/
│   └── src/
│       ├── genui-lib/
│       │   ├── DashboardCard/
│       │   │   ├── schema.ts
│       │   │   ├── index.tsx
│       │   │   └── view.tsx      # 可选视图层
│       │   ├── PreviewCard/
│       │   │   ├── schema.ts
│       │   │   ├── index.tsx
│       │   │   └── view.tsx
│       │   ├── HtmlTab/
│       │   │   ├── schema.ts
│       │   │   ├── index.tsx
│       │   │   └── view.tsx
│       │   └── dslLibrary.tsx    # 注册Canvas组件
│       │
│       └── components/
│           └── CanvasTabs/
│               ├── CanvasTabs.tsx
│               ├── DashboardGrid.tsx
│               ├── PreviewContent.tsx
│               ├── HtmlEmbed.tsx
│               └── index.ts      # 导出
│
└── docs/
    └── adr/
        ├── 0001-canvas-component-design.md
        ├── 0002-canvas-grid-layout.md
        ├── 0003-canvas-tab-implementation.md
        └── canvas-feature-design.md
```

### 3.3.2 类型安全

**目标**: 无any类型滥用，TypeScript严格模式编译通过

**设计要点**:

| 类型安全项 | 实现方式 |
|----------|----------|
| CanvasItem联合类型 | DashboardCardNode | PreviewCardNode | HtmlTabNode |
| 类型守卫返回类型 | 函数返回明确类型断言 |
| Props类型推断 | z.infer<typeof Schema> |
| 严格null检查 | nullable字段明确标注optional |

### 3.3.3 测试覆盖

**目标**: 组件代码覆盖率≥80%

**测试目录**:
```
packages/
├── lang-core/
│   └── src/__tests__/
│       ├── parser.test.ts          # extractCanvasItems测试
│       └── types.test.ts           # 类型守卫测试
│
├── react-lang/
│   └── src/__tests__/
│       ├── Renderer.test.tsx       # onCanvasItems测试
│       └── renderElementNode.test.tsx
│
├── react-ui-dsl/
│   └── src/__tests__/
│       ├── CanvasTabs.test.tsx     # 分组、布局、渲染测试
│       ├── DashboardGrid.test.tsx  # 网格算法测试
│       └── e2e/
│           ├── fixtures.ts         # Canvas组件fixtures
│           └── snapshots/
│               ├── dashboard.dsl
│               ├── preview.dsl
│               └── html.dsl
```

### 3.3.4 文档同步

**目标**: 文档与代码同步，无滞后或冲突

**文档清单**:

| 文档 | 维护时机 |
|------|----------|
| CONTEXT.md | 新增术语时立即更新 |
| ADR-0001~0003 | 设计决策变更时更新 |
| canvas-feature-design.md | 功能变更时更新 |
| README.md | 接口变更时更新 |

## 3.4 可扩展性设计

### 3.4.1 新Canvas组件扩展

**目标**: 未来新增Canvas组件类型时，改动最小化

**扩展步骤**:
```
新增Canvas组件(如VideoTab):
1. lang-core/types.ts:
   - 新增VideoTabNode接口
   - 新增isVideoTabNode类型守卫
   - CanvasItem类型添加VideoTabNode
   - isCanvasItem添加"VideoTab"判断

2. react-ui-dsl/genui-lib/:
   - 新增VideoTab/schema.ts, index.tsx

3. react-ui-dsl/dslLibrary.tsx:
   - 注册VideoTab组件

4. react-ui-dsl/components/CanvasTabs:
   - useMemo分离添加videoTabs
   - tabItems构建添加VideoTab渲染
   - 新增VideoEmbed组件

5. Prompt:
   - dslLibrary.prompt()自动包含签名
```

**扩展成本**: 约5个文件，20行代码增量

### 3.4.2 布局算法扩展

**目标**: 未来支持不同布局策略时，切换成本低

**设计要点**:
```
当前: 严格顺序填充
未来: 可能支持
  - 紧凑填充
  - 自由拖拽
  - 响应式网格

扩展方式:
  - DashboardGrid接收layoutStrategy参数
  - layoutCards函数根据策略选择算法
  - 默认"strict-order"，可选"compact"
```

### 3.4.3 Prompt扩展

**目标**: 业务层灵活控制Canvas组件使用

**扩展接口**:
```typescript
dslLibrary.prompt({
  additionalRules: [
    // 业务层自定义规则
    "仅当用户明确请求仪表盘时使用DashboardCard",
    "PreviewCard用于文档类预览",
  ],
  examples: [
    // 业务层自定义示例
    `cpuDashboard = DashboardCard([cpuChart], "CPU Monitor", {tab: "System"})`,
  ],
});
```

### 3.4.4 Tab功能扩展

**目标**: 未来支持Tab操作(关闭、重命名)时，改动最小化

**预留接口**:
```typescript
interface CanvasTabsProps {
  // 未来扩展预留
  onTabClose?: (tabKey: string) => void;
  onTabRename?: (tabKey: string, newName: string) => void;
  closableTabs?: boolean;
  editableTabs?: boolean;
}
```

## 3.5 安全性设计

### 3.5.1 iframe安全

**目标**: HtmlTab iframe嵌入不引入安全风险

**设计要点**:

| 安全项 | 实现方式 |
|----------|----------|
| sandbox属性 | iframe添加sandbox="allow-scripts allow-same-origin" |
| CSP策略 | 业务层配置Content-Security-Policy |
| URL校验 | Schema校验url为有效URL格式 |
| 跨域限制 | 业务层确保URL允许iframe嵌入 |

### 3.5.2 DSL注入防护

**目标**: Canvas组件不执行恶意DSL代码

**设计要点**:

| 安全项 | 实现方式 |
|----------|----------|
| Schema严格校验 | z.strict()拒绝未定义属性 |
| 类型限制 | children类型限制为DSL组件，不执行脚本 |
| 无eval | renderElementNode不使用eval或Function |

### 3.5.3 数据隔离

**目标**: Canvas组件不泄露敏感数据

**设计要点**:

| 安全项 | 实现方式 |
|----------|----------|
| dataModel控制 | 业务层控制dataModel内容，不传入敏感数据 |
| 无持久化 | Canvas组件不持久化到localStorage |
| 无网络请求 | Canvas组件本身不发网络请求(Query除外) |

## 3.6 兼容性设计

### 3.6.1 向后兼容

**目标**: 现有DSL和使用方式不受影响

**设计要点**:

| 兼容项 | 实现方式 |
|----------|----------|
| LUI默认渲染 | 无Canvas组件的DSL正常渲染到LUI |
| onCanvasItems可选 | 未提供时Renderer不报错，Canvas不渲染 |
| ParseResult扩展 | 新增canvasItems字段不影响现有字段 |
| Library扩展 | 新组件注册不影响现有组件 |

### 3.6.2 版本兼容

**目标**: 支持渐进式升级

**迁移路径**:
```
v1.0: LUI渲染，无Canvas
v2.0: 引入Canvas组件
  - 现有DSL无需改动
  - 新DSL可使用Canvas组件
  - 业务层可选启用Canvas
```

### 3.6.3 平台兼容

**目标**: 支持主流浏览器和React版本

**兼容矩阵**:

| 平台 | 版本要求 |
|------|----------|
| Chrome | ≥80 |
| Firefox | ≥75 |
| Safari | ≥13 |
| Edge | ≥80 |
| React | ≥18 |
| TypeScript | ≥5 |

---

# 第四章 附录

## 4.1 术语表

| 术语 | 定义 | 来源 |
|------|------|------|
| LUI | Language UI，对话区LLM响应渲染位置 | CONTEXT.md |
| Canvas | Intelligent Canvas，独立可视化工作区 | CONTEXT.md |
| DashboardTab | Canvas中的网格布局Tab | CONTEXT.md |
| PreviewTab | Canvas中的整页预览Tab | CONTEXT.md |
| HtmlTab | Canvas中的iframe嵌入Tab | CONTEXT.md |
| DashboardCard | 渲染到DashboardTab的DSL组件 | CONTEXT.md |
| PreviewCard | 渲染到PreviewTab的DSL组件 | CONTEXT.md |
| HtmlTab | 渲染到HtmlTab的DSL组件(同名) | CONTEXT.md |
| CanvasItem | DashboardCardNode | PreviewCardNode | HtmlTabNode联合类型 | CONTEXT.md |
| Grid Unit | DashboardCard尺寸单位，列跨度(1-12) | CONTEXT.md |
| ElementNode | DSL组件解析后的节点表示 | lang-core/types.ts |

## 4.2 决策记录索引

| ADR编号 | 决策主题 | 文档路径 |
|---------|----------|----------|
| ADR-0001 | DSL组件设计(新组件vs扩展Card) | docs/adr/0001-canvas-component-design.md |
| ADR-0002 | 网格布局策略(静态vs动态) | docs/adr/0002-canvas-grid-layout.md |
| ADR-0003 | Tab实现位置(前端vs DSL层) | docs/adr/0003-canvas-tab-implementation.md |

## 4.3 实现清单

### 4.3.1 Phase 1: DSL和解析层 (Week 1)

| 任务 | 文件 | 优先级 |
|------|------|--------|
| DashboardCard schema和组件 | genui-lib/DashboardCard/ | P0 |
| PreviewCard schema和组件 | genui-lib/PreviewCard/ | P0 |
| HtmlTab schema和组件 | genui-lib/HtmlTab/ | P0 |
| CanvasItem类型定义 | lang-core/parser/types.ts | P0 |
| 类型守卫函数 | lang-core/parser/types.ts | P0 |
| extractCanvasItems逻辑 | lang-core/parser/parser.ts | P0 |
| dslLibrary注册Canvas组件 | genui-lib/dslLibrary.tsx | P0 |

### 4.3.2 Phase 2: 渲染层 (Week 2)

| 任务 | 文件 | 优先级 |
|------|------|--------|
| RendererProps onCanvasItems | react-lang/Renderer.tsx | P0 |
| Renderer回调触发 | react-lang/Renderer.tsx | P0 |
| renderElementNode导出 | react-lang/renderElementNode.ts | P0 |
| react-lang导出更新 | react-lang/index.ts | P0 |
| Parser单元测试 | lang-core/__tests__/parser.test.ts | P1 |

### 4.3.3 Phase 3: 前端组件 (Week 2-3)

| 任务 | 文件 | 优先级 |
|------|------|--------|
| CanvasTabs容器 | components/CanvasTabs/CanvasTabs.tsx | P0 |
| DashboardGrid网格 | components/CanvasTabs/DashboardGrid.tsx | P0 |
| PreviewContent整页 | components/CanvasTabs/PreviewContent.tsx | P0 |
| HtmlEmbed iframe | components/CanvasTabs/HtmlEmbed.tsx | P0 |
| 清空按钮功能 | components/CanvasTabs/CanvasTabs.tsx | P1 |
| 组件导出 | react-ui-dsl/index.ts | P0 |
| 组件测试 | react-ui-dsl/__tests__/CanvasTabs.test.tsx | P1 |

### 4.3.4 Phase 4: 测试和文档 (Week 3-4)

| 任务 | 文件 | 优先级 |
|------|------|--------|
| E2E fixtures | react-ui-dsl/__tests__/e2e/fixtures.ts | P1 |
| E2E snapshots | react-ui-dsl/__tests__/e2e/snapshots/ | P1 |
| 使用文档更新 | docs/README.md | P2 |
| 示例应用 | examples/canvas-demo/ | P2 |

## 4.4 验收标准汇总

### 4.4.1 DSL层验收

| 验收项 | 验收方法 | 通过标准 |
|--------|----------|----------|
| DashboardCard解析正确 | Parser单元测试 | typeName="DashboardCard", props正确 |
| PreviewCard解析正确 | Parser单元测试 | typeName="PreviewCard", title必填校验 |
| HtmlTab解析正确 | Parser单元测试 | typeName="HtmlTab", url/title必填校验 |
| Canvas组件剔除LUI树 | Parser单元测试 | LUI root不含Canvas节点 |
| canvasItems提取完整 | Parser单元测试 | 数组长度=DSL中Canvas组件数量 |

### 4.4.2 渲染层验收

| 验收项 | 验收方法 | 通过标准 |
|--------|----------|----------|
| onCanvasItems触发 | Renderer集成测试 | ParseResult变化时回调触发 |
| CanvasItem数组正确 | Renderer集成测试 | 数组内容与Parser一致 |
| dataModel共享 | Renderer集成测试 | Canvas组件可访问data.* |
| renderElementNode导出 | TypeScript编译 | 导出成功，类型正确 |

### 4.4.3 前端组件验收

| 验收项 | 验收方法 | 通过标准 |
|--------|----------|----------|
| CanvasTabs渲染 | 组件测试 | Tab正确显示，内容渲染 |
| DashboardGrid布局 | 组件测试 | 网格排列符合算法预期 |
| PreviewContent整页 | 组件测试 | 内容占满Tab区域 |
| HtmlEmbed iframe | 组件测试 | iframe正确加载URL |
| Tab分组正确 | 组件测试 | DashboardCard按tab分组 |
| 清空按钮功能 | 组件测试 | 点击触发onClear |
| 垂直滚动 | 手动测试 | 内容超出时可滚动 |

### 4.4.4 Prompt层验收

| 验收项 | 验收方法 | 通过标准 |
|--------|----------|----------|
| 组件签名包含 | prompt输出检查 | 包含DashboardCard/PreviewCard/HtmlTab签名 |
| additionalRules生效 | prompt输出检查 | 业务层规则包含 |
| examples生效 | prompt输出检查 | 业务层示例包含 |

## 4.5 风险和缓解措施

| 风险ID | 风险描述 | 概率 | 影响 | 缓解措施 |
|--------|----------|------|------|----------|
| R01 | LLM生成过多DashboardCard导致画布拥挤 | 中 | 中 | Prompt建议上限，前端清空按钮 |
| R02 | iframe跨域导致HtmlTab加载失败 | 中 | 高 | 业务层URL校验，错误提示 |
| R03 | 流式解析时canvasItems频繁更新 | 低 | 低 | useEffect依赖优化 |
| R04 | Canvas组件嵌套(理论上不应出现) | 低 | 高 | Parser检测嵌套报错 |
| R05 | size.w值异常(如负数) | 低 | 中 | Schema min/max校验 |
| R06 | renderElementNode递归过深 | 低 | 中 | 限制嵌套层级，性能监控 |

## 4.6 参考资料

| 资料 | 来源 |
|------|------|
| OpenUI DSL语法 | packages/lang-core/src/parser/README.md |
| ElementNode类型 | packages/lang-core/src/parser/types.ts |
| Renderer组件 | packages/react-lang/src/Renderer.tsx |
| dslLibrary实现 | packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx |
| Ant Design Tabs | https://ant.design/components/tabs |
| React Grid Layout | https://github.com/react-grid-layout/react-grid-layout |

---

**文档结束**