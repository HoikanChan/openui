# OpenUI 智能画布技术设计文档

**版本**: v1.0  
**状态**: 实现完成  
**日期**: 2026-05-16

---

## 一、实现思路概述

### 1.1 设计理念

OpenUI智能画布的设计遵循**分离渲染目标**原则，将LLM生成的DSL组件按类型分发到不同的渲染区域：

- **LUI（Language UI）**：对话区域的默认渲染目标，展示对话式交互内容
- **Canvas（智能画布）**：独立可视化工作区，支持仪表盘卡片、全页预览、外部HTML嵌入

### 1.2 核心思路

```
┌───────────────────────────────────────────────────────────────┐
│                        DSL解析阶段                              │
│                                                               │
│  DSL文本 → Parser → ElementNode树 + CanvasItem[]              │
│                        │                                       │
│                        ├─ root → LUI渲染                        │
│                        └─ canvasItems → Canvas渲染              │
│                                                               │
│  关键决策：                                                    │
│  1. Parser遍历root树 + 扫描独立Canvas语句                       │
│  2. Canvas组件从LUI树剔除，不渲染任何占位符                      │
│  3. LUI和Canvas共享同一dataModel                               │
└───────────────────────────────────────────────────────────────┘
```

### 1.3 三类Canvas组件定位

| 组件类型 | 用途 | 渲染位置 | 特点 |
|----------|------|----------|------|
| **DashboardCard** | 网格仪表盘卡片 | DashboardTab | 12列网格布局，多卡片同Tab |
| **PreviewCard** | 全页预览 | PreviewTab | 整页渲染，独占Tab |
| **HtmlTab** | 外部应用嵌入 | HtmlTab | iframe嵌入，独占Tab |

---

## 二、逻辑视图

### 2.1 整体架构逻辑视图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              应用层                                      │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      ChatPage / Demo App                          │   │
│  │                                                                   │   │
│  │  ┌──────────────────────┐      ┌─────────────────────────────┐   │   │
│  │  │      LUI Area         │      │      Canvas Area            │   │   │
│  │  │                       │      │                             │   │   │
│  │  │  state:               │      │  state:                     │   │   │
│  │  │    - dslInput         │      │    - canvasItems[]          │   │   │
│  │  │                       │      │                             │   │   │
│  │  │  <Renderer            │      │  <CanvasTabs                │   │   │
│  │  │    onCanvasItems={    │──────│    items={canvasItems}      │   │   │
│  │  │      setCanvasItems   │      │  />                         │   │   │
│  │  │    }                  │      │                             │   │   │
│  │  │  />                   │      │                             │   │   │
│  │  └──────────────────────┘      └─────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ onCanvasItems回调
                                    │ dataModel共享
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                            组件层                                        │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │              @openuidev/react-ui-dsl                              │   │
│  │                                                                   │   │
│  │  ┌─────────────────┐  ┌─────────────────────────────────────┐   │   │
│  │  │  Canvas组件定义  │  │         Canvas渲染组件              │   │   │
│  │  │                 │  │                                     │   │   │
│  │  │  DashboardCard  │  │  CanvasTabs                         │   │   │
│  │  │  PreviewCard    │  │   ├─ DashboardGrid (12列网格)       │   │   │
│  │  │  HtmlTab        │  │   ├─ PreviewContent (整页)          │   │   │
│  │  │                 │  │   └─ HtmlEmbed (iframe)             │   │   │
│  │  └─────────────────┘  └─────────────────────────────────────┘   │   │
│  │                                                                   │   │
│  │  ┌────────────────────────────────────────────────────────────┐ │   │
│  │  │                    dslLibrary                                │ │   │
│  │  │                                                              │ │   │
│  │  │  - 注册Canvas组件到library                                   │ │   │
│  │  │  - toJSONSchema()供Parser使用                                │ │   │
│  │  │  - prompt()包含Canvas组件签名                                │ │   │
│  │  └────────────────────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ renderElementNode()
                                    │ library.toJSONSchema()
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                            渲染层                                        │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │              @openuidev/react-lang                                │   │
│  │                                                                   │   │
│  │  ┌────────────────────────────────────────────────────────────┐ │   │
│  │  │                      Renderer                                │ │   │
│  │  │                                                              │ │   │
│  │  │  Props:                                                     │ │   │
│  │  │    - response: DSL文本                                       │ │   │
│  │  │    - library: dslLibrary                                     │ │   │
│  │  │    - onCanvasItems: (items[]) => void                        │ │   │
│  │  │    - dataModel: Record<string, unknown>                      │ │   │
│  │  │                                                              │ │   │
│  │  │  内部流程:                                                   │ │   │
│  │  │    useOpenUIState()                                          │ │   │
│  │  │      ├─ createStreamingParser(library.toJSONSchema())        │ │   │
│  │  │      ├─ parser.set(response) → ParseResult                   │ │   │
│  │  │      ├─ ParseResult.canvasItems[]                           │ │   │
│  │  │      └─ useEffect → onCanvasItems(parseResult.canvasItems)  │ │   │
│  │  └────────────────────────────────────────────────────────────┘ │   │
│  │                                                                   │   │
│  │  ┌────────────────────────────────────────────────────────────┐ │   │
│  │  │              renderElementNode()                             │ │   │
│  │  │                                                              │ │   │
│  │  │  功能：渲染ElementNode树为React节点                           │ │   │
│  │  │  输入：ElementNode | ElementNode[]                           │ │   │
│  │  │  输出：React.ReactNode                                       │ │   │
│  │  │  用途：CanvasTabs渲染DashboardCard.children                  │ │   │
│  │  └────────────────────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Parser.extractCanvasItems()
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                            解析层                                        │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │              @openuidev/lang-core                                 │   │
│  │                                                                   │   │
│  │  ┌────────────────────────────────────────────────────────────┐ │   │
│  │  │                      Parser                                  │ │   │
│  │  │                                                              │ │   │
│  │  │  流程：                                                      │ │   │
│  │  │                                                              │ │   │
│  │  │  DSL文本                                                     │ │   │
│  │  │    │                                                         │ │   │
│  │  │    ├─ tokenize() → Tokens                                    │ │   │
│  │  │    ├─ split() → RawStmt[]                                    │ │   │
│  │  │    ├─ parseExpression() → ASTNode                            │ │   │
│  │  │    ├─ classifyStatement() → Statement                        │ │   │
│  │  │    │                                                         │ │   │
│  │  │    │  Statement类型：                                         │ │   │
│  │  │    │    - value: 普通组件调用                                 │ │   │
│  │  │    │    - state: $变量声明                                    │ │   │
│  │  │    │    - query: Query()调用                                  │ │   │
│  │  │    │    - mutation: Mutation()调用                           │ │   │
│  │  │    │                                                         │ │   │
│  │  │    ├─ materializeValue() → ElementNode                       │ │   │
│  │  │    │                                                         │ │   │
│  │  │    ├─ extractCanvasItems()                                   │ │   │
│  │  │    │    ├─ 遍历root树提取Canvas组件                           │ │   │
│  │  │    │    └─ 扫描独立Canvas语句（未被root引用）                  │ │   │
│  │  │    │                                                         │ │   │
│  │  │    └─ ParseResult                                            │ │   │
│  │  │         ├─ root: ElementNode (LUI树)                         │ │   │
│  │  │         ├─ canvasItems: CanvasItem[]                         │ │   │
│  │  │         ├─ meta: { orphaned, errors... }                     │ │   │
│  │  │         └─ stateDeclarations, queryStatements...             │ │   │
│  │  └────────────────────────────────────────────────────────────┘ │   │
│  │                                                                   │   │
│  │  ┌────────────────────────────────────────────────────────────┐ │   │
│  │  │                CanvasItem类型定义                            │ │   │
│  │  │                                                              │ │   │
│  │  │  CanvasItem = DashboardCardNode                              │ │   │
│  │  │             | PreviewCardNode                                │ │   │
│  │  │             | HtmlTabNode                                    │ │   │
│  │  │                                                              │ │   │
│  │  │  类型守卫：                                                   │ │   │
│  │  │    isDashboardCardNode(node)                                 │ │   │
│  │  │    isPreviewCardNode(node)                                   │ │   │
│  │  │    isHtmlTabNode(node)                                       │ │   │
│  │  │    isCanvasItem(node)                                        │ │   │
│  │  └────────────────────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Canvas组件提取逻辑视图

```
┌───────────────────────────────────────────────────────────────────────┐
│                    Parser.extractCanvasItems()                        │
│                                                                       │
│  输入：Statement Map + MaterializeCtx                                 │
│  输出：{ luiRoot, canvasItems[] }                                      │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                    Step 1: 遍历root树                            │ │
│  │                                                                 │ │
│  │  traverse(node):                                                │ │
│  │    if isElementNode(node):                                      │ │
│  │      if isCanvasItem(node):                                     │ │
│  │        └─ canvasItems.push(node)                                │ │
│  │        └ return null (剔除)                                     │ │
│  │      else:                                                      │ │
│  │        └ traverse each prop                                    │ │
│  │        └ return modified node                                  │ │
│  │                                                                 │ │
│  │  结果：root中的Canvas组件被剔除，收集到canvasItems               │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │            Step 2: 扫描独立Canvas语句                            │ │
│  │                                                                 │ │
│  │  for stmt in typedStmts:                                        │ │
│  │    if isCanvasStatement(stmt):                                  │ │
│  │      if unreached.has(stmt.id):                                 │ │
│  │        └ materializeValue(stmt.expr, canvasCtx)                 │ │
│  │        └ if isElementNode(result):                              │ │
│  │          └ canvasItems.push(result)                            │ │
│  │                                                                 │ │
│  │  关键判断：                                                      │ │
│  │    isCanvasStatement(stmt):                                     │ │
│  │      stmt.kind === "value"                                      │ │
│  │      stmt.expr.k === "Comp"                                     │ │
│  │      stmt.expr.name ∈ ["DashboardCard", "PreviewCard", "HtmlTab"]│ │
│  │                                                                 │ │
│  │  结果：未被root引用的Canvas语句也被提取                           │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                    Step 3: 合并结果                              │ │
│  │                                                                 │ │
│  │  canvasItems = rootCanvasItems + standaloneCanvasItems          │ │
│  │  luiRoot = root树（Canvas组件已剔除）                            │ │
│  │                                                                 │ │
│  │  ParseResult:                                                   │ │
│  │    root: luiRoot                                                │ │
│  │    canvasItems: canvasItems                                     │ │
│  │    meta.orphaned: 剩余未到达的非Canvas语句                       │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                       │
└───────────────────────────────────────────────────────────────────────┘
```

### 2.3 CanvasTabs分组渲染逻辑视图

```
┌───────────────────────────────────────────────────────────────────────┐
│                    CanvasTabs组件逻辑                                  │
│                                                                       │
│  输入：CanvasItem[] + Library + dataModel                              │
│  输出：<Tabs>组件                                                      │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                    Step 1: 类型分离                              │ │
│  │                                                                 │ │
│  │  useMemo(() => {                                                │ │
│  │    dashboardCards = items.filter(isDashboardCardNode)           │ │
│  │    previewCards = items.filter(isPreviewCardNode)               │ │
│  │    htmlTabs = items.filter(isHtmlTabNode)                       │ │
│  │  })                                                             │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                    Step 2: DashboardCard分组                     │ │
│  │                                                                 │ │
│  │  useMemo(() => {                                                │ │
│  │    groups = {}                                                  │ │
│  │    for card in dashboardCards:                                  │ │
│  │      tabName = card.props.tab ?? "Dashboard"                    │ │
│  │      groups[tabName].push(card)                                 │ │
│  │  })                                                             │ │
│  │                                                                 │ │
│  │  结果：                                                          │ │
│  │    {                                                            │ │
│  │      "Dashboard": [card1, card2, ...],                          │ │
│  │      "Monitor": [card3, ...],                                   │ │
│  │    }                                                            │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                    Step 3: 构建Tab列表                           │ │
│  │                                                                 │ │
│  │  tabItems = []                                                  │ │
│  │                                                                 │ │
│  │  // DashboardTab                                                │ │
│  │  for [tabName, cards] in groups:                                │ │
│  │    tabItems.push({                                              │ │
│  │      key: "dashboard-{tabName}",                                │ │
│  │      label: tabName,                                            │ │
│  │      children: <DashboardGrid cards={cards} />                  │ │
│  │    })                                                           │ │
│  │                                                                 │ │
│  │  // PreviewTab                                                  │ │
│  │  for card in previewCards:                                      │ │
│  │    tabItems.push({                                              │ │
│  │      key: "preview-{card.statementId}",                         │ │
│  │      label: card.props.title,                                   │ │
│  │      children: <PreviewContent content={card.props.children} /> │ │
│  │    })                                                           │ │
│  │                                                                 │ │
│  │  // HtmlTab                                                     │ │
│  │  for tab in htmlTabs:                                           │ │
│  │    tabItems.push({                                              │ │
│  │      key: "html-{tab.statementId}",                             │ │
│  │      label: tab.props.title,                                    │ │
│  │      children: <HtmlEmbed url={tab.props.url} />                │ │
│  │    })                                                           │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                    Step 4: 渲染Tabs                              │ │
│  │                                                                 │ │
│  │  <Tabs                                                          │ │
│  │    activeKey={activeKey}                                        │ │
│  │    onChange={setActiveKey}                                      │ │
│  │    items={tabItems}                                             │ │
│  │  />                                                             │ │
│  │                                                                 │ │
│  │  + 清空按钮：<button onClick={onClear}>清空画布</button>          │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                       │
└───────────────────────────────────────────────────────────────────────┘
```

---

## 三、关键接口定义

### 3.1 DSL组件接口

#### DashboardCard接口

```typescript
interface DashboardCardProps {
  children?: unknown[];              // 子组件数组，可选
  title?: string;                    // 卡片标题，可选
  tab?: string;                      // DashboardTab归属，默认"Dashboard"
  size?: {                           // 网格尺寸，可选
    w?: number;                      // 列跨度(1-12)，默认6
    h?: number;                      // 行高度(可选，自适应)
  };
  cardId?: string;                   // 卡片唯一标识，可选
}

// Zod Schema定义
const DashboardCardSchema = z.object({
  children: z.array(z.any()).optional(),
  title: z.string().optional(),
  tab: z.string().optional().default("Dashboard"),
  size: z.object({
    w: z.number().int().min(1).max(12).optional().default(6),
    h: z.number().int().positive().optional(),
  }).optional().default({ w: 6 }),
  cardId: z.string().optional(),
}).strict();
```

**关键点**：
- `tab`属性决定DashboardTab分组
- `size.w`为网格列跨度，默认6（占半行）
- `size.h`可选，未指定时高度自适应

#### PreviewCard接口

```typescript
interface PreviewCardProps {
  children?: unknown[];              // 子组件数组，可选
  title: string;                     // Tab名称，必填
  cardId?: string;                   // 卡片唯一标识，可选
}

// Zod Schema定义
const PreviewCardSchema = z.object({
  children: z.array(z.any()).optional(),
  title: z.string(),                 // 必填
  cardId: z.string().optional(),
}).strict();
```

**关键点**：
- `title`必填，直接作为Tab名称
- 每个PreviewCard独占一个Tab

#### HtmlTab接口

```typescript
interface HtmlTabProps {
  url: string;                       // iframe URL，必填
  title: string;                     // Tab名称，必填
  cardId?: string;                   // 卡片唯一标识，可选
}

// Zod Schema定义
const HtmlTabSchema = z.object({
  url: z.string(),                   // 必填
  title: z.string(),                 // 必填
  cardId: z.string().optional(),
}).strict();
```

**关键点**：
- `url`和`title`必填
- 每个HtmlTab独占一个Tab，iframe嵌入

### 3.2 Parser接口

#### ParseResult扩展

```typescript
interface ParseResult {
  root: ElementNode | null;          // LUI树（Canvas组件已剔除）
  meta: {
    incomplete: boolean;
    unresolved: string[];
    orphaned: string[];              // 未到达的非Canvas语句
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

#### 类型守卫函数

```typescript
// 判断是否为DashboardCard节点
function isDashboardCardNode(node: ElementNode): node is DashboardCardNode {
  return node.typeName === "DashboardCard";
}

// 判断是否为PreviewCard节点
function isPreviewCardNode(node: ElementNode): node is PreviewCardNode {
  return node.typeName === "PreviewCard";
}

// 判断是否为HtmlTab节点
function isHtmlTabNode(node: ElementNode): node is HtmlTabNode {
  return node.typeName === "HtmlTab";
}

// 判断是否为任一Canvas节点
function isCanvasItem(node: ElementNode): node is CanvasItem {
  return isDashboardCardNode(node) 
      || isPreviewCardNode(node) 
      || isHtmlTabNode(node);
}
```

### 3.3 Renderer接口

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

**使用方式**：
```typescript
<Renderer
  response={dsl}
  library={dslLibrary}
  onCanvasItems={(items) => setCanvasItems(items)}
  dataModel={data}
/>
```

#### renderElementNode导出函数

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

**实现要点**：
- 使用`React.createElement`而非JSX语法（避免扩展名问题）
- 递归遍历ElementNode树
- 通过`library.components[typeName]`获取组件渲染器
- 处理数组、null、非ElementNode值

### 3.4 CanvasTabs组件接口

```typescript
interface CanvasTabsProps {
  items: CanvasItem[];               // Canvas组件数组
  library: Library;                  // 组件库（渲染children）
  dataModel?: Record<string, unknown>; // 数据模型
  showClearButton?: boolean;         // 显示清空按钮，默认true
  onClear?: () => void;              // 清空回调
}
```

**使用方式**：
```typescript
<CanvasTabs
  items={canvasItems}
  library={dslLibrary}
  dataModel={data}
  onClear={() => setCanvasItems([])}
/>
```

---

## 四、核心数据结构

### 4.1 ElementNode结构

```typescript
interface ElementNode {
  type: "element";                   // 固定标识
  statementId?: string;              // DSL语句ID（如"cpuCard"）
  typeName: string;                  // 组件类型名
  props: Record<string, unknown>;    // 已解析的命名属性
  partial: boolean;                  // 流式解析状态
  hasDynamicProps?: boolean;         // 是否含动态属性
}
```

**示例**：
```typescript
{
  type: "element",
  statementId: "cpuCard",
  typeName: "DashboardCard",
  props: {
    children: [{ type: "element", typeName: "LineChart", ... }],
    title: "CPU Usage",
    tab: "Dashboard",
    size: { w: 6 }
  },
  partial: false
}
```

### 4.2 Statement结构

```typescript
type Statement =
  | { kind: "value"; id: string; expr: ASTNode }
  | { kind: "state"; id: string; init: ASTNode }
  | { kind: "query"; id: string; call: CallNode; expr: ASTNode; deps?: string[] }
  | { kind: "mutation"; id: string; call: CallNode; expr: ASTNode };
```

**关键判断**：
```typescript
function isCanvasStatement(stmt: Statement): boolean {
  return stmt.kind === "value" 
      && stmt.expr.k === "Comp"
      && stmt.expr.name ∈ CANVAS_COMPONENT_NAMES;
}

const CANVAS_COMPONENT_NAMES = ["DashboardCard", "PreviewCard", "HtmlTab"];
```

### 4.3 DashboardGrid布局结构

```typescript
type LayoutRow = Array<{
  card: DashboardCardNode;
  startCol: number;    // 起始列(0-11)
  width: number;       // 列跨度(1-12)
}>;
```

**布局算法输出示例**：
```typescript
[
  // Row 1
  [
    { card: cardA, startCol: 0, width: 6 },
    // 空6列，cardB(w=8)无法放入
  ],
  // Row 2
  [
    { card: cardB, startCol: 0, width: 8 },
  ],
  // Row 3
  [
    { card: cardC, startCol: 0, width: 4 },
    { card: cardD, startCol: 4, width: 6 },
  ]
]
```

---

## 五、实现规格说明

### 5.1 Parser提取规格

#### 规格要点

| 规格项 | 说明 |
|--------|------|
| **提取范围** | root树遍历 + 独立Canvas语句扫描 |
| **剔除行为** | Canvas组件从LUI树完全剔除，无占位符 |
| **children处理** | Canvas组件的children引用会触发materialize |
| **unreached更新** | 独立Canvas语句materialize时更新共享unreached集合 |
| **顺序保证** | canvasItems数组顺序与DSL定义顺序一致 |

#### 实现伪代码

```typescript
function buildResult(stmtMap, typedStmts, ...): ParseResult {
  // Step 1: Materialize root树
  const materialized = materializeValue(syms.get(entryId), ctx);
  const rawRoot = isElementNode(materialized) ? materialized : null;
  
  // Step 2: 从root树提取Canvas
  const { luiRoot, canvasItems: rootCanvasItems } = extractCanvasItems(rawRoot);
  
  // Step 3: 扫描独立Canvas语句
  const standaloneCanvasItems = [];
  for (stmt of typedStmts) {
    if (isCanvasStatement(stmt) && unreached.has(stmt.id)) {
      const canvasCtx = { ...ctx, unreached: ctx.unreached };  // 共享集合
      const canvasMaterialized = materializeValue(stmt.expr, canvasCtx);
      if (isElementNode(canvasMaterialized)) {
        canvasMaterialized.statementId = stmt.id;
        standaloneCanvasItems.push(canvasMaterialized);
      }
    }
  }
  
  // Step 4: 合并
  const canvasItems = rootCanvasItems.concat(standaloneCanvasItems);
  
  return {
    root: isElementNode(luiRoot) ? luiRoot : null,
    canvasItems,
    meta: { orphaned: [...unreached], ... },
    ...
  };
}
```

### 5.2 DashboardGrid布局规格

#### 网格参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 总列数 | 12 | 固定12列网格 |
| 默认宽度 | 6 | 未指定size.w时默认6列 |
| 行高 | 自适应 | 未指定size.h时内容自适应 |
| 滚动 | 垂直 | 内容超出时垂直滚动 |

#### 排列算法规格

```
算法：严格顺序填充

输入：DashboardCardNode[] cards
输出：LayoutRow[] rows

过程：
  rows = []
  currentRow = []
  currentCol = 0
  
  for card in cards:
    width = card.props.size?.w ?? 6
    
    if currentCol + width <= 12:
      // 当前行剩余空间足够
      currentRow.push({ card, startCol: currentCol, width })
      currentCol += width
    else:
      // 空间不足，换行
      if currentRow.length > 0:
        rows.push(currentRow)
      currentRow = [{ card, startCol: 0, width }]
      currentCol = width
  
  // 添加最后一行
  if currentRow.length > 0:
    rows.push(currentRow)
  
  return rows
```

#### 排列示例

DSL：
```openui-lang
cardA = DashboardCard([], "A", {size: {w: 6}})
cardB = DashboardCard([], "B", {size: {w: 8}})
cardC = DashboardCard([], "C", {size: {w: 4}})
cardD = DashboardCard([], "D")  // 默认w=6
```

排列结果：
```
Row 1: [cardA(0-5)] + [空(6-11)]  ← cardB(8)无法放入
Row 2: [cardB(0-7)] + [空(8-11)]
Row 3: [cardC(0-3)] + [cardD(4-9)] + [空(10-11)]
```

### 5.3 CanvasTabs分组规格

#### 分组规则

| 组件类型 | 分组方式 | Tab命名 |
|----------|----------|---------|
| DashboardCard | 按props.tab分组 | props.tab值，默认"Dashboard" |
| PreviewCard | 不分组，每卡片独占Tab | props.title |
| HtmlTab | 不分组，每组件独占Tab | props.title |

#### Tab Key生成规则

```
DashboardTab: "dashboard-{tabName}"
PreviewTab:   "preview-{statementId | title}"
HtmlTab:      "html-{statementId | title}"
```

### 5.4 Renderer回调规格

#### 回调触发时机

```typescript
useEffect(() => {
  if (parseResult?.canvasItems && onCanvasItemsRef.current) {
    onCanvasItemsRef.current(parseResult.canvasItems);
  }
}, [parseResult?.canvasItems]);
```

**触发条件**：
- `parseResult`不为null
- `parseResult.canvasItems`存在
- `onCanvasItems`回调已提供

#### 回调频率

- 流式解析：每次Parser更新时触发
- 静态解析：解析完成后一次性触发
- 内容相同时不触发（React useEffect依赖比较）

### 5.5 dataModel共享规格

#### 共享机制

```typescript
// Renderer内部
const dataModelRef = useRef(dataModel);

// useOpenUIState中
const sp = createStreamingParser(library.toJSONSchema(), library.root, {
  externalRefs: dataModel ? ["data"] : undefined,
});

// CanvasTabs使用
renderElementNode(card.props.children, library, dataModel);
```

#### 访问方式

DSL中统一使用`data.*`路径：
```openui-lang
cpuCard = DashboardCard([chart], "CPU")
chart = LineChart([], [], "smooth", "Time", data.unit)
```

---

## 六、模块交互流程

### 6.1 DSL生成与解析流程

```
┌────────────┐
│   用户请求 │
└────────────┘
      │
      ▼
┌────────────┐     ┌────────────────┐
│  Skill层   │────→│ Prompt配置     │
│  意图解析   │     │ additionalRules│
└────────────┘     │ examples       │
      │            └────────────────┘
      ▼                    │
┌────────────┐             │
│ Tool调用   │             │
│ 数据获取   │             ▼
└────────────┘     ┌────────────────┐
      │            │ dslLibrary     │
      ▼            │ .prompt()      │
┌────────────┐     └────────────────┐
│   LLM推理  │────→ 生成System Prompt
│  DSL生成   │            │
└────────────┘            ▼
      │            ┌────────────────┐
      │            │ LLM接收Prompt  │
      ▼            │ + Tool结果     │
┌────────────┐     └────────────────┐
│ DSL文本    │           │
│            │           ▼
└────────────┘     ┌────────────────┐
      │            │ LLM生成DSL     │
      │            │                │
      ▼            └────────────────┘
```

### 6.2 解析与渲染流程

```
┌────────────────────────────────────────────────────────────────┐
│                        DSL文本                                  │
│                                                                │
│  root = VLayout([text])                                        │
│  text = Text("Hello")                                          │
│  cpuCard = DashboardCard([], "CPU")                            │
│                                                                │
└────────────────────────────────────────────────────────────────┘
                         │
                         │ Renderer.response = DSL文本
                         ▼
┌────────────────────────────────────────────────────────────────┐
│              useOpenUIState (Renderer内部)                      │
│                                                                │
│  createStreamingParser(library.toJSONSchema())                 │
│                         │                                      │
│                         ▼                                      │
│  parser.set(response)                                          │
│                         │                                      │
│                         ▼                                      │
│  ParseResult                                                   │
│    ├─ root: ElementNode (VLayout)                              │
│    ├─ canvasItems: [DashboardCardNode]                         │
│    └─ meta: { orphaned: ["cpuCard"], ... }                     │
│                                                                │
└────────────────────────────────────────────────────────────────┘
            │                              │
            │ result.root                  │ parseResult.canvasItems
            ▼                              ▼
┌──────────────────────┐        ┌──────────────────────────────┐
│    LUI渲染           │        │    useEffect触发             │
│                      │        │                              │
│  <RenderNode         │        │  onCanvasItems(canvasItems)  │
│    node={root}       │        │    → setCanvasItems(items)   │
│  />                  │        │                              │
│                      │        └──────────────────────────────┘
│  输出：               │                     │
│    <VLayout>         │                     │ 状态更新
│      <Text>          │                     ▼
│        Hello         │        ┌──────────────────────────────┐
│      </Text>         │        │    CanvasItems状态           │
│    </VLayout>        │        │    canvasItems = [...]       │
│                      │        └──────────────────────────────┘
└──────────────────────┘                     │
                                             │ CanvasTabs重新渲染
                                             ▼
                               ┌──────────────────────────────┐
                               │    <CanvasTabs               │
                               │      items={canvasItems}     │
                               │      library={dslLibrary}    │
                               │      dataModel={data}        │
                               │    />                        │
                               │                              │
                               │    内部：                     │
                               │      分离DashboardCard       │
                               │      分组 → "Dashboard" Tab   │
                               │      <DashboardGrid          │
                               │        cards={[cpuCard]}     │
                               │      />                      │
                               │                              │
                               │    输出：                     │
                               │      <Tabs>                  │
                               │        <Tab "Dashboard">     │
                               │          <Card "CPU Usage"> │
                               │          </Card>             │
                               │        </Tab>                │
                               │      </Tabs>                 │
                               └──────────────────────────────┘
```

---

## 七、文件组织结构

### 7.1 新增文件清单

```
packages/
├── lang-core/
│   └── src/
│       ├── parser/
│       │   └── types.ts          # CanvasItem类型定义 + 类型守卫
│       └── __tests__/
│           ├── canvas-types.test.ts
│           ├── canvas-extraction.test.ts
│           └── standalone-canvas.test.ts
│
├── react-lang/
│   └── src/
│       ├── Renderer.tsx          # onCanvasItems回调
│       ├── renderElementNode.tsx  # ElementNode渲染函数
│       └── index.ts              # 导出CanvasItem类型
│
├── react-ui-dsl/
│   └── src/
│       ├── genui-lib/
│       │   ├── DashboardCard/
│       │   │   ├── schema.ts
│       │   │   ├── index.tsx
│       │   └── PreviewCard/
│       │   │   ├── schema.ts
│       │   │   ├── index.tsx
│       │   └── HtmlTab/
│       │   │   ├── schema.ts
│       │   │   ├── index.tsx
│       │   ├── dslLibrary.tsx    # 注册Canvas组件
│       │
│       ├── components/
│       │   └── CanvasTabs/
│       │       ├── CanvasTabs.tsx
│       │       ├── DashboardGrid.tsx
│       │       ├── PreviewContent.tsx
│       │       ├── HtmlEmbed.tsx
│       │       └── index.ts
│       │
│       ├── index.ts             # 导出所有Canvas相关
│       └── __tests__/
│           └── DashboardGrid.test.tsx
│
└── examples/
    └── canvas-demo/
        ├── package.json
        ├── vite.config.ts
        ├── tsconfig.json
        ├── src/
        │   ├── App.tsx
        │   ├── main.tsx
        │   └── styles.css
        └── README.md
```

### 7.2 修改文件清单

```
packages/
├── lang-core/
│   └── src/
│       ├── parser/
│       │   └── parser.ts         # extractCanvasItems逻辑
│       └── index.ts              # 导出CanvasItem类型
│
├── react-lang/
│   └── src/
│       └── index.ts              # 导出renderElementNode
│
├── react-ui-dsl/
│   └── src/
│       └── genui-lib/
│           └── dslLibrary.tsx    # 注册Canvas组件
│       └── index.ts              # 导出Canvas组件和类型
```

---

## 八、使用示例

### 8.1 DSL示例

```openui-lang
root = VLayout([header, table])
header = Text("Employee List", "large")
table = Table([nameCol, salaryCol], data.employees)
nameCol = Col("Name", "name")
salaryCol = Col("Salary", "salary")

cpuCard = DashboardCard([cpuChart], "CPU Usage", {tab: "Dashboard", size: {w: 6}})
cpuChart = LineChart(timestamps, [cpuSeries], "smooth", "Time", "CPU %")
cpuSeries = Series("CPU", data.cpuValues)

memoryCard = DashboardCard([memChart], "Memory", {tab: "Dashboard", size: {w: 6}})
memChart = LineChart(timestamps, [memSeries], "smooth", "Time", "Memory")
memSeries = Series("Memory", data.memValues)

reportPreview = PreviewCard([reportHeader, reportTable], "Monthly Report")
reportHeader = Text("Summary", "large")
reportTable = Table([col1, col2], data.reportData)

externalApp = HtmlTab("https://grafana.example.com", "Grafana")
```

### 8.2 前端集成示例

```typescript
import { useState } from "react";
import {
  Renderer,
  CanvasItem,
  ParseResult,
} from "@openuidev/react-lang";
import {
  CanvasTabs,
  dslLibrary,
} from "@openuidev/react-ui-dsl";

function ChatPage() {
  const [dslResponse, setDslResponse] = useState<string>("");
  const [canvasItems, setCanvasItems] = useState<CanvasItem[]>([]);
  const [dataModel, setDataModel] = useState({ employees: [], ... });

  return (
    <div className="app-layout">
      {/* LUI对话区 */}
      <div className="lui-panel">
        <Renderer
          response={dslResponse}
          library={dslLibrary}
          dataModel={dataModel}
          onCanvasItems={setCanvasItems}
        />
      </div>

      {/* Canvas画布区 */}
      <div className="canvas-panel">
        <CanvasTabs
          items={canvasItems}
          library={dslLibrary}
          dataModel={dataModel}
          onClear={() => setCanvasItems([])}
        />
      </div>
    </div>
  );
}
```

---

## 九、性能与可靠性规格

### 9.1 性能目标

| 指标 | 目标值 | 测量方式 |
|------|--------|----------|
| DSL解析增量 | ≤现有+5% | Parser响应时间对比 |
| Canvas首次渲染 | ≤200ms | CanvasTabs mount时间 |
| Tab切换 | ≤100ms | activeKey变更到渲染完成 |
| DashboardGrid滚动 | ≥30FPS | 滚动性能测量 |

### 9.2 可靠性目标

| 指标 | 目标值 |
|------|--------|
| Canvas组件解析成功率 | ≥99.9% |
| iframe加载失败处理 | 显示错误提示，不崩溃 |
| 流式解析内存稳定 | 无内存泄漏 |

---

## 十、扩展与维护规格

### 10.1 新增Canvas组件扩展步骤

```
新增组件(如VideoTab):

1. lang-core/parser/types.ts:
   - 新增VideoTabNode接口
   - 新增isVideoTabNode类型守卫
   - CanvasItem类型添加VideoTabNode
   - isCanvasItem添加"VideoTab"判断
   - CANVAS_COMPONENT_NAMES.push("VideoTab")

2. react-ui-dsl/genui-lib/:
   - 新增VideoTab/schema.ts, index.tsx

3. react-ui-dsl/genui-lib/dslLibrary.tsx:
   - 注册VideoTab组件

4. react-ui-dsl/components/CanvasTabs:
   - useMemo分离添加videoTabs
   - tabItems构建添加VideoTab渲染
   - 新增VideoEmbed组件

5. Prompt:
   - dslLibrary.prompt()自动包含签名

预估改动：5文件，20行代码
```

### 10.2 维护检查清单

| 检查项 | 频率 |
|--------|------|
| 单元测试运行 | 每次修改 |
| 类型检查`tsc --noEmit` | 每次修改 |
| CONTEXT.md术语更新 | 新增术语时 |
| ADR更新 | 设计决策变更时 |

---

**文档结束**