# 智能画布实现方案设计

## 概述

本文档详细描述OpenUI智能画布功能的完整实现方案，涵盖DSL层、解析层、渲染层和前端组件层的具体设计与实现细节。

---

## 一、整体架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                         LLM生成DSL                                   │
│  root = VLayout([table])                                             │
│  cpuCard = DashboardCard([chart], "CPU", {tab: "Dashboard"})        │
│  preview1 = PreviewCard([content], "Report Preview")                 │
│  html1 = HtmlTab("https://app.example.com", "External App")          │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       DSL Parser (lang-core)                         │
│  - 解析DSL文本为ElementNode树                                         │
│  - 提取DashboardCard/PreviewCard/HtmlTab节点                         │
│  - 从LUI树中剔除Canvas组件                                            │
│  - 返回ParseResult + CanvasItem[]                                    │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    ▼                               ▼
┌─────────────────────────────┐     ┌─────────────────────────────┐
│      LUI Render Tree         │     │      Canvas Items           │
│  root = VLayout([table])     │     │  DashboardCardNode[]        │
│  (不含Canvas组件)            │     │  PreviewCardNode[]          │
│                              │     │  HtmlTabNode[]              │
└─────────────────────────────┘     └─────────────────────────────┘
                    │                               │
                    ▼                               ▼
┌─────────────────────────────┐     ┌─────────────────────────────┐
│      Renderer (LUI)          │     │      CanvasTabs             │
│  <Renderer response={dsl}    │     │  <CanvasTabs                │
│    onCanvasItems={callback}  │     │    items={canvasItems}      │
│  />                          │     │    library={dslLibrary}     │
│                              │     │    dataModel={data}         │
│  → 渲染到LUI对话区域          │     │  />                          │
│                              │     │                              │
│                              │     │  → 渲染到画布Tab区域          │
│                              │     │    - DashboardTab (网格)     │
│                              │     │    - PreviewTab (整页)       │
│                              │     │    - HtmlTab (iframe)       │
└─────────────────────────────┘     └─────────────────────────────┘
```

---

## 二、DSL层实现

### 2.1 新增组件定义

#### 2.1.1 DashboardCard

**路径**: `packages/react-ui-dsl/src/genui-lib/DashboardCard/`

**schema.ts**:
```typescript
import { z } from "zod";

export const DashboardCardSchema = z.object({
  children: z.array(z.any()).optional(),
  title: z.string().optional(),
  tab: z.string().optional().default("Dashboard"),
  size: z.object({
    w: z.number().min(1).max(12).optional().default(6),
    h: z.number().optional(),  // 可选，高度自适应
  }).optional().default({ w: 6 }),
  cardId: z.string().optional(),
}).strict();
```

**index.tsx**:
```typescript
"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { DashboardCardSchema } from "./schema";

export { DashboardCardSchema } from "./schema";

export const DashboardCard = defineComponent({
  name: "DashboardCard",
  props: DashboardCardSchema,
  description: `
Canvas dashboard card with grid layout.
- title: Card title (optional)
- tab: DashboardTab name, default "Dashboard"
- size: Grid dimensions {w: columns, h: rows?}. w is 1-12, default 6.
- cardId: Unique identifier (optional, auto-generated if omitted)
- children: Any DSL components (charts, tables, text, etc.)

Renders to DashboardTab in Canvas. Multiple cards with same tab value
group into one DashboardTab with 12-column grid layout.
  `,
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof DashboardCardSchema>>) => {
    // Note: DashboardCard itself doesn't render in LUI
    // It's extracted by parser and rendered in CanvasTabs
    return null;
  },
});
```

#### 2.1.2 PreviewCard

**路径**: `packages/react-ui-dsl/src/genui-lib/PreviewCard/`

**schema.ts**:
```typescript
import { z } from "zod";

export const PreviewCardSchema = z.object({
  children: z.array(z.any()).optional(),
  title: z.string(),  // Required, becomes Tab name
  cardId: z.string().optional(),
}).strict();
```

**index.tsx**:
```typescript
"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { PreviewCardSchema } from "./schema";

export { PreviewCardSchema } from "./schema";

export const PreviewCard = defineComponent({
  name: "PreviewCard",
  props: PreviewCardSchema,
  description: `
Canvas preview card with full-page layout.
- title: Tab name (required, each PreviewCard occupies its own tab)
- cardId: Unique identifier (optional, auto-generated if omitted)
- children: Any DSL components

Renders to dedicated PreviewTab in Canvas. Each PreviewCard creates
a separate tab. Content fills entire tab area without grid constraints.
  `,
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof PreviewCardSchema>>) => {
    return null;  // Extracted by parser, rendered in CanvasTabs
  },
});
```

#### 2.1.3 HtmlTab

**路径**: `packages/react-ui-dsl/src/genui-lib/HtmlTab/`

**schema.ts**:
```typescript
import { z } from "zod";

export const HtmlTabSchema = z.object({
  url: z.string(),  // Required, iframe source
  title: z.string(),  // Required, becomes Tab name
  cardId: z.string().optional(),
}).strict();
```

**index.tsx**:
```typescript
"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { HtmlTabSchema } from "./schema";

export { HtmlTabSchema } from "./schema";

export const HtmlTab = defineComponent({
  name: "HtmlTab",
  props: HtmlTabSchema,
  description: `
Canvas tab with iframe-embedded external HTML.
- url: iframe source URL (required)
- title: Tab name (required, each HtmlTab occupies its own tab)
- cardId: Unique identifier (optional, auto-generated if omitted)

Renders to dedicated HtmlTab in Canvas. iframe embeds the URL content.
  `,
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof HtmlTabSchema>>) => {
    return null;  // Extracted by parser, rendered in CanvasTabs
  },
});
```

### 2.2 组件注册

**路径**: `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`

修改：
```typescript
import { DashboardCard } from "./DashboardCard";
import { PreviewCard } from "./PreviewCard";
import { HtmlTab } from "./HtmlTab";

const baseDslLibrary = createLibrary({
  root: "VLayout",
  components: [
    // ... existing components
    DashboardCard,
    PreviewCard,
    HtmlTab,
  ],
});
```

---

## 三、lang-core层实现

### 3.1 类型定义

**路径**: `packages/lang-core/src/parser/types.ts`

新增类型：
```typescript
/**
 * DashboardCard node extracted from DSL.
 */
export interface DashboardCardNode {
  type: "element";
  typeName: "DashboardCard";
  statementId?: string;
  props: {
    children?: unknown[];
    title?: string;
    tab?: string;  // Default "Dashboard"
    size?: { w?: number; h?: number };
    cardId?: string;
  };
  partial: boolean;
}

/**
 * PreviewCard node extracted from DSL.
 */
export interface PreviewCardNode {
  type: "element";
  typeName: "PreviewCard";
  statementId?: string;
  props: {
    children?: unknown[];
    title: string;  // Tab name
    cardId?: string;
  };
  partial: boolean;
}

/**
 * HtmlTab node extracted from DSL.
 */
export interface HtmlTabNode {
  type: "element";
  typeName: "HtmlTab";
  statementId?: string;
  props: {
    url: string;
    title: string;  // Tab name
    cardId?: string;
  };
  partial: boolean;
}

/**
 * Union type for all canvas-renderable items.
 */
export type CanvasItem = DashboardCardNode | PreviewCardNode | HtmlTabNode;

/**
 * Type guard for DashboardCardNode.
 */
export function isDashboardCardNode(node: ElementNode): node is DashboardCardNode {
  return node.typeName === "DashboardCard";
}

/**
 * Type guard for PreviewCardNode.
 */
export function isPreviewCardNode(node: ElementNode): node is PreviewCardNode {
  return node.typeName === "PreviewCard";
}

/**
 * Type guard for HtmlTabNode.
 */
export function isHtmlTabNode(node: ElementNode): node is HtmlTabNode {
  return node.typeName === "HtmlTab";
}

/**
 * Type guard for any CanvasItem.
 */
export function isCanvasItem(node: ElementNode): node is CanvasItem {
  return isDashboardCardNode(node) || isPreviewCardNode(node) || isHtmlTabNode(node);
}
```

### 3.2 解析器扩展

**路径**: `packages/lang-core/src/parser/parser.ts`

新增提取逻辑：
```typescript
import { isCanvasItem, type CanvasItem } from "./types";

export interface ParseResult {
  root: ElementNode | null;
  meta: {
    incomplete: boolean;
    unresolved: string[];
    orphaned: string[];
    statementCount: number;
    errors: ValidationError[];
  };
  stateDeclarations: Record<string, unknown>;
  queryStatements: QueryStatementInfo[];
  mutationStatements: MutationStatementInfo[];
  // NEW: Canvas items extracted from DSL
  canvasItems: CanvasItem[];
}

/**
 * Extract canvas items from parsed tree and remove them from LUI tree.
 */
function extractCanvasItems(root: ElementNode | null): {
  luiRoot: ElementNode | null;
  canvasItems: CanvasItem[];
} {
  if (!root) return { luiRoot: null, canvasItems: [] };

  const canvasItems: CanvasItem[] = [];

  function traverse(node: unknown): unknown {
    if (!node || typeof node !== "object") return node;

    if (isElementNode(node)) {
      if (isCanvasItem(node)) {
        canvasItems.push(node);
        return null;  // Remove from LUI tree
      }

      // Recursively process props
      const newProps: Record<string, unknown> = {};
      for (const [key, value] of Object.entries(node.props)) {
        newProps[key] = traverseValue(value);
      }

      return {
        ...node,
        props: newProps,
      };
    }

    if (Array.isArray(node)) {
      const result = node.map(traverse).filter(v => v !== null);
      return result.length > 0 ? result : null;
    }

    return node;
  }

  function traverseValue(value: unknown): unknown {
    if (Array.isArray(value)) {
      const result = value.map(traverse).filter(v => v !== null);
      return result.length > 0 ? result : undefined;
    }
    if (typeof value === "object" && value !== null) {
      return traverse(value);
    }
    return value;
  }

  const luiRoot = traverse(root) as ElementNode | null;

  return { luiRoot, canvasItems };
}

// In parse function:
export function parse(text: string, schema: LibraryJSONSchema): ParseResult {
  // ... existing parsing logic

  const { luiRoot, canvasItems } = extractCanvasItems(result.root);

  return {
    root: luiRoot,
    meta: result.meta,
    stateDeclarations: result.stateDeclarations,
    queryStatements: result.queryStatements,
    mutationStatements: result.mutationStatements,
    canvasItems,  // NEW field
  };
}
```

### 3.3 流式解析器扩展

**路径**: `packages/lang-core/src/parser/parser.ts`

```typescript
export interface StreamingParser {
  push(chunk: string): ParseResult;
  reset(): void;
}

export function createStreamingParser(schema: LibraryJSONSchema): StreamingParser {
  // ... existing implementation

  return {
    push(chunk: string): ParseResult {
      // ... existing parsing
      const result = parse(accumulatedText, schema);
      return result;  // Already includes canvasItems extraction
    },
    reset(): void {
      // ... existing reset
    },
  };
}
```

---

## 四、react-lang层实现

### 4.1 Renderer Props扩展

**路径**: `packages/react-lang/src/Renderer.tsx`

```typescript
import type { CanvasItem } from "@openuidev/lang-core";

export interface RendererProps {
  // ... existing props

  /**
   * Called when canvas items are extracted from DSL.
   * Receives array of DashboardCardNode, PreviewCardNode, HtmlTabNode.
   */
  onCanvasItems?: (items: CanvasItem[]) => void;
}
```

### 4.2 Renderer实现更新

**路径**: `packages/react-lang/src/Renderer.tsx`

```typescript
export function Renderer({
  response,
  library,
  // ... other props
  onCanvasItems,
}: RendererProps) {
  // ... existing state logic

  const onCanvasItemsRef = useRef(onCanvasItems);
  onCanvasItemsRef.current = onCanvasItems;

  const { result, parseResult, contextValue, isQueryLoading } = useOpenUIState(
    {
      response,
      library,
      // ... other params
    },
    renderDeep,
  );

  // NEW: Fire onCanvasItems callback when canvas items change
  useEffect(() => {
    if (parseResult?.canvasItems && onCanvasItemsRef.current) {
      onCanvasItemsRef.current(parseResult.canvasItems);
    }
  }, [parseResult?.canvasItems]);

  // ... rest of render logic
}
```

### 4.3 导出renderElementNode

**路径**: `packages/react-lang/src/renderElementNode.ts`

新增文件：
```typescript
import type { ElementNode, Library } from "@openuidev/lang-core";
import type { ComponentRenderProps } from "./library";
import React, { Fragment } from "react";
import { useOpenUI, useRenderNode } from "./context";

/**
 * Recursively renders ElementNode tree to React nodes.
 * Used by CanvasTabs to render DashboardCard/PreviewCard children.
 */
export function renderElementNode(
  node: ElementNode | ElementNode[] | unknown,
  library: Library,
  dataModel?: Record<string, unknown>,
): React.ReactNode {
  if (node == null) return null;

  if (Array.isArray(node)) {
    return node.map((n, i) => (
      <Fragment key={i}>{renderElementNode(n, library, dataModel)}</Fragment>
    ));
  }

  if (!isElementNode(node)) {
    if (typeof node === "string") return node;
    if (typeof node === "number") return String(node);
    return null;
  }

  const Comp = library.components[node.typeName]?.component;
  if (!Comp) return null;

  const renderNode = (child: unknown) => renderElementNode(child, library, dataModel);

  const componentProps: ComponentRenderProps<typeof node.props> = {
    props: node.props,
    renderNode,
  };

  return React.createElement(Comp, componentProps);
}

function isElementNode(value: unknown): value is ElementNode {
  return (
    value != null &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    (value as any).type === "element" &&
    typeof (value as any).typeName === "string"
  );
}
```

**路径**: `packages/react-lang/src/index.ts`

新增导出：
```typescript
export { renderElementNode } from "./renderElementNode";
export type { CanvasItem, DashboardCardNode, PreviewCardNode, HtmlTabNode } from "@openuidev/lang-core";
```

---

## 五、react-ui-dsl层实现

### 5.1 CanvasTabs组件

**路径**: `packages/react-ui-dsl/src/components/CanvasTabs/`

**CanvasTabs.tsx**:
```typescript
"use client";

import React, { useMemo, useState } from "react";
import type { CanvasItem, Library, DashboardCardNode, PreviewCardNode, HtmlTabNode } from "@openuidev/react-lang";
import { isDashboardCardNode, isPreviewCardNode, isHtmlTabNode, renderElementNode } from "@openuidev/react-lang";
import { Tabs } from "antd";

export interface CanvasTabsProps {
  /** Canvas items from Renderer.onCanvasItems callback */
  items: CanvasItem[];
  /** DSL component library for rendering children */
  library: Library;
  /** Shared data model for LUI and Canvas */
  dataModel?: Record<string, unknown>;
  /** Show clear canvas button */
  showClearButton?: boolean;
  /** Callback when user clicks clear button */
  onClear?: () => void;
}

export function CanvasTabs({
  items,
  library,
  dataModel,
  showClearButton = true,
  onClear,
}: CanvasTabsProps) {
  const [activeKey, setActiveKey] = useState("Dashboard");

  // Separate items by type
  const { dashboardCards, previewCards, htmlTabs } = useMemo(() => {
    const dashboardCards: DashboardCardNode[] = [];
    const previewCards: PreviewCardNode[] = [];
    const htmlTabs: HtmlTabNode[] = [];

    for (const item of items) {
      if (isDashboardCardNode(item)) {
        dashboardCards.push(item);
      } else if (isPreviewCardNode(item)) {
        previewCards.push(item);
      } else if (isHtmlTabNode(item)) {
        htmlTabs.push(item);
      }
    }

    return { dashboardCards, previewCards, htmlTabs };
  }, [items]);

  // Group dashboard cards by tab name
  const dashboardGroups = useMemo(() => {
    const groups: Record<string, DashboardCardNode[]> = {};
    for (const card of dashboardCards) {
      const tabName = card.props.tab ?? "Dashboard";
      if (!groups[tabName]) {
        groups[tabName] = [];
      }
      groups[tabName].push(card);
    }
    return groups;
  }, [dashboardCards]);

  // Build tab items
  const tabItems = useMemo(() => {
    const tabs: Array<{ key: string; label: string; children: React.ReactNode }> = [];

    // Dashboard tabs
    for (const [tabName, cards] of Object.entries(dashboardGroups)) {
      tabs.push({
        key: `dashboard-${tabName}`,
        label: tabName,
        children: <DashboardGrid cards={cards} library={library} dataModel={dataModel} />,
      });
    }

    // Preview tabs
    for (const card of previewCards) {
      tabs.push({
        key: `preview-${card.statementId ?? card.props.title}`,
        label: card.props.title,
        children: (
          <PreviewContent
            content={card.props.children}
            library={library}
            dataModel={dataModel}
          />
        ),
      });
    }

    // Html tabs
    for (const tab of htmlTabs) {
      tabs.push({
        key: `html-${tab.statementId ?? tab.props.title}`,
        label: tab.props.title,
        children: <HtmlEmbed url={tab.props.url} />,
      });
    }

    return tabs;
  }, [dashboardGroups, previewCards, htmlTabs, library, dataModel]);

  if (tabItems.length === 0) {
    return null;
  }

  return (
    <div className="canvas-tabs-container">
      {showClearButton && onClear && (
        <button className="canvas-clear-button" onClick={onClear}>
          清空画布
        </button>
      )}
      <Tabs
        activeKey={activeKey}
        onChange={setActiveKey}
        items={tabItems}
      />
    </div>
  );
}
```

### 5.2 DashboardGrid组件

**路径**: `packages/react-ui-dsl/src/components/CanvasTabs/DashboardGrid.tsx`

```typescript
import React from "react";
import type { DashboardCardNode, Library } from "@openuidev/react-lang";
import { renderElementNode } from "@openuidev/react-lang";
import { Card } from "antd";

export interface DashboardGridProps {
  cards: DashboardCardNode[];
  library: Library;
  dataModel?: Record<string, unknown>;
}

export function DashboardGrid({ cards, library, dataModel }: DashboardGridProps) {
  // Layout algorithm: strict order, left-to-right, wrap when insufficient space
  const rows = useMemo(() => {
    const result: Array<Array<{ card: DashboardCardNode; startCol: number; width: number }>> = [];
    let currentRow: Array<{ card: DashboardCardNode; startCol: number; width: number }> = [];
    let currentCol = 0;

    for (const card of cards) {
      const width = card.props.size?.w ?? 6;

      // Check if fits in current row
      if (currentCol + width <= 12) {
        currentRow.push({ card, startCol: currentCol, width });
        currentCol += width;
      } else {
        // Wrap to next row
        if (currentRow.length > 0) {
          result.push(currentRow);
        }
        currentRow = [{ card, startCol: 0, width }];
        currentCol = width;
      }
    }

    // Add last row
    if (currentRow.length > 0) {
      result.push(currentRow);
    }

    return result;
  }, [cards]);

  return (
    <div className="dashboard-grid" style={{ overflowY: "auto", maxHeight: "100vh" }}>
      {rows.map((row, rowIndex) => (
        <div key={rowIndex} className="dashboard-row" style={{ display: "flex" }}>
          {row.map(({ card, startCol, width }) => {
            const leftPercent = (startCol / 12) * 100;
            const widthPercent = (width / 12) * 100;

            return (
              <div
                key={card.statementId ?? card.props.cardId ?? startCol}
                className="dashboard-card-wrapper"
                style={{
                  width: `${widthPercent}%`,
                  marginLeft: startCol === 0 ? 0 : undefined,
                }}
              >
                <Card title={card.props.title}>
                  {renderElementNode(card.props.children, library, dataModel)}
                </Card>
              </div>
            );
          })}
        </div>
      ))}
    </div>
  );
}
```

### 5.3 PreviewContent组件

**路径**: `packages/react-ui-dsl/src/components/CanvasTabs/PreviewContent.tsx`

```typescript
import React from "react";
import type { Library } from "@openuidev/react-lang";
import { renderElementNode } from "@openuidev/react-lang";

export interface PreviewContentProps {
  content: unknown[];
  library: Library;
  dataModel?: Record<string, unknown>;
}

export function PreviewContent({ content, library, dataModel }: PreviewContentProps) {
  return (
    <div className="preview-content" style={{ width: "100%", minHeight: "100%" }}>
      {renderElementNode(content, library, dataModel)}
    </div>
  );
}
```

### 5.4 HtmlEmbed组件

**路径**: `packages/react-ui-dsl/src/components/CanvasTabs/HtmlEmbed.tsx`

```typescript
import React from "react";

export interface HtmlEmbedProps {
  url: string;
}

export function HtmlEmbed({ url }: HtmlEmbedProps) {
  return (
    <iframe
      src={url}
      className="html-embed"
      style={{
        width: "100%",
        height: "100%",
        minHeight: "500px",
        border: "none",
      }}
      title="Embedded Content"
    />
  );
}
```

### 5.5 组件导出

**路径**: `packages/react-ui-dsl/src/index.ts`

新增导出：
```typescript
export { CanvasTabs } from "./components/CanvasTabs";
export type { CanvasTabsProps } from "./components/CanvasTabs";
export { DashboardCard } from "./genui-lib/DashboardCard";
export { PreviewCard } from "./genui-lib/PreviewCard";
export { HtmlTab } from "./genui-lib/HtmlTab";
```

---

## 六、Prompt层实现

### 6.1 组件签名自动生成

DashboardCard、PreviewCard、HtmlTab已注册到dslLibrary，其签名会自动包含在`dslLibrary.prompt()`生成的系统提示中。

LLM可看到的签名示例：
```
DashboardCard(children?, title?, tab?, size?, cardId?) — Canvas dashboard card with 12-column grid
PreviewCard(children?, title!, cardId?) — Canvas preview with full-page layout, title becomes tab name
HtmlTab(url!, title!, cardId?) — Canvas tab with iframe-embedded external HTML
```

### 6.2 业务层扩展接口

业务层通过`additionalRules`和`examples`自行添加使用规则：

**示例**：
```typescript
const prompt = dslLibrary.prompt({
  additionalRules: [
    "Use DashboardCard for charts and visualizations that need persistent canvas display.",
    "DashboardCard size.w is column span (1-12), default 6. Cards arrange left-to-right in strict DSL order.",
    "Use PreviewCard for full-page previews like reports, documents, or complex visualizations.",
    "Use HtmlTab to embed external web applications or dashboards via iframe.",
    "Default: non-Canvas components render to LUI dialog. Canvas components are extracted and removed from LUI tree.",
    "Suggested limit: 10 DashboardCards per DashboardTab to maintain visual clarity.",
  ],
  examples: [
    `root = VLayout([summary])
summary = Descriptions([DescField("Status", data.status)])
cpuCard = DashboardCard([cpuChart], "CPU Usage", {tab: "Dashboard", size: {w: 6}})
cpuChart = LineChart(timestamps, [cpuSeries], "smooth", "Time", "CPU %")`,
    `root = VLayout([header])
header = Text("Report Generated", "large")
reportPreview = PreviewCard([reportContent], "Monthly Report")
reportContent = VLayout([summary, charts])
charts = LineChart(...)`,
    `root = VLayout([info])
info = Text("External dashboard available in Canvas tab")
externalDashboard = HtmlTab("https://grafana.example.com/d/abc123", "Grafana Dashboard")`,
  ],
});
```

---

## 七、前端使用示例

### 7.1 基本集成

```typescript
import { useState } from "react";
import { Renderer, CanvasTabs, dslLibrary, CanvasItem } from "@openuidev/react-ui-dsl";

function ChatPage() {
  const [dslResponse, setDslResponse] = useState<string>("");
  const [canvasItems, setCanvasItems] = useState<CanvasItem[]>([]);
  const [dataModel, setDataModel] = useState<Record<string, unknown>>({});

  return (
    <div className="chat-container">
      {/* LUI Dialog Area */}
      <div className="lui-area">
        <Renderer
          response={dslResponse}
          library={dslLibrary}
          dataModel={dataModel}
          onCanvasItems={setCanvasItems}
          isStreaming={false}
        />
      </div>

      {/* Canvas Area */}
      <div className="canvas-area">
        <CanvasTabs
          items={canvasItems}
          library={dslLibrary}
          dataModel={dataModel}
          showClearButton={true}
          onClear={() => setCanvasItems([])}
        />
      </div>
    </div>
  );
}
```

### 7.2 DSL生成示例

LLM生成的DSL：
```openui-lang
root = VLayout([summary, actions])
summary = Descriptions([DescField("Total Users", data.totalUsers), DescField("Active", data.activeUsers)], "User Summary")
actions = Button("Refresh", Action([@Run(userQuery)]))

userChartCard = DashboardCard([userTrendChart], "User Trend", {tab: "Dashboard", size: {w: 8}})
userTrendChart = LineChart(timestamps, [userSeries], "smooth", "Time", "Users")

reportPreview = PreviewCard([reportLayout], "Monthly Report")
reportLayout = VLayout([reportHeader, reportTable])
reportHeader = Text("Monthly Statistics", "large")
reportTable = Table([nameCol, valueCol], data.statistics)
nameCol = Col("Metric", "name")
valueCol = Col("Value", "value")

externalApp = HtmlTab("https://analytics.example.com/dashboard", "Analytics Dashboard")
```

渲染结果：
- **LUI**: VLayout with summary descriptions and refresh button
- **Canvas Tabs**:
  - Tab "Dashboard": DashboardGrid with User Trend card (8 columns wide)
  - Tab "Monthly Report": Full-page preview with header and table
  - Tab "Analytics Dashboard": iframe嵌入analytics.example.com

---

## 八、测试计划

### 8.1 DSL解析测试

**路径**: `packages/lang-core/src/__tests__/canvas-extraction.test.ts`

测试内容：
- DashboardCard节点识别和提取
- PreviewCard节点识别和提取
- HtmlTab节点识别和提取
- LUI树剔除Canvas组件后的结构
- 多DashboardTab分组逻辑
- 混合Canvas组件场景

### 8.2 Renderer测试

**路径**: `packages/react-lang/src/__tests__/Renderer.test.tsx`

测试内容：
- onCanvasItems回调触发时机
- CanvasItem数组内容正确性
- 流式解析时CanvasItems增量更新
- dataModel共享机制

### 8.3 CanvasTabs测试

**路径**: `packages/react-ui-dsl/src/__tests__/CanvasTabs.test.tsx`

测试内容：
- DashboardGrid排列算法（严格顺序、换行逻辑）
- 多DashboardTab分组渲染
- PreviewContent整页渲染
- HtmlEmbed iframe加载
- 清空按钮功能
- 空items处理

### 8.4 E2E测试

**路径**: `packages/react-ui-dsl/src/__tests__/e2e/fixtures.ts`

新增fixtures：
- 纯LUI渲染场景
- 单DashboardCard场景
- 多DashboardCard + 混合LUI场景
- PreviewCard场景
- HtmlTab场景
- 三种Canvas组件混合场景

---

## 九、实现优先级

### Phase 1: 核心DSL和解析层 (Week 1)
1. DashboardCard、PreviewCard、HtmlTab schema定义
2. lang-core类型扩展（CanvasItem类型）
3. 解析器提取逻辑（extractCanvasItems）
4. ParseResult新增canvasItems字段

### Phase 2: Renderer层集成 (Week 2)
1. RendererProps扩展onCanvasItems
2. Renderer回调触发逻辑
3. renderElementNode导出实现
4. react-lang导出更新

### Phase 3: 前端组件实现 (Week 2-3)
1. CanvasTabs容器组件
2. DashboardGrid网格布局
3. PreviewContent整页渲染
4. HtmlEmbed iframe组件
5. 清空按钮功能

### Phase 4: 测试和文档 (Week 3-4)
1. DSL解析单元测试
2. Renderer集成测试
3. CanvasTabs组件测试
4. E2E fixtures和快照
5. 使用文档更新

---

## 十、风险和缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| LLM生成过多DashboardCard | 画布拥挤，用户体验差 | Prompt警告建议上限，前端清空按钮 |
| Canvas组件属性验证失败 | 渲染错误 | schema strict验证，错误回调报告 |
| iframe跨域限制 | HtmlTab加载失败 | 业务层需确保URL可嵌入，文档说明 |
| 流式解析时CanvasItems增量 | 回调频繁触发 | useEffect依赖数组，仅在内容变化时触发 |
| renderElementNode递归深度 | 性能问题 | 限制children嵌套层级，监控性能 |

---

## 十一、附录：完整类型定义

```typescript
// packages/lang-core/src/parser/types.ts

export interface DashboardCardNode {
  type: "element";
  typeName: "DashboardCard";
  statementId?: string;
  props: {
    children?: unknown[];
    title?: string;
    tab?: string;
    size?: { w?: number; h?: number };
    cardId?: string;
  };
  partial: boolean;
}

export interface PreviewCardNode {
  type: "element";
  typeName: "PreviewCard";
  statementId?: string;
  props: {
    children?: unknown[];
    title: string;
    cardId?: string;
  };
  partial: boolean;
}

export interface HtmlTabNode {
  type: "element";
  typeName: "HtmlTab";
  statementId?: string;
  props: {
    url: string;
    title: string;
    cardId?: string;
  };
  partial: boolean;
}

export type CanvasItem = DashboardCardNode | PreviewCardNode | HtmlTabNode;

export function isDashboardCardNode(node: ElementNode): node is DashboardCardNode;
export function isPreviewCardNode(node: ElementNode): node is PreviewCardNode;
export function isHtmlTabNode(node: ElementNode): node is HtmlTabNode;
export function isCanvasItem(node: ElementNode): node is CanvasItem;
```

---

**文档结束**