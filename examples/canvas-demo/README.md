# OpenUI Canvas Demo

This example demonstrates the Intelligent Canvas feature in OpenUI.

## Features Demonstrated

### 1. LUI + Canvas Split Rendering
- **LUI Panel**: Shows traditional dialog-style UI (Text, Descriptions, Table)
- **Canvas Panel**: Shows DashboardCard grid, PreviewCard full-page, and HtmlTab iframe

### 2. Canvas Components

#### DashboardCard
```openui-lang
cpuCard = DashboardCard([cpuChart], "CPU Usage", "Dashboard", {w: 6})
```
- Renders to DashboardTab with 12-column grid
- `title`: Card title
- `tab`: DashboardTab name (default: "Dashboard")
- `size`: Grid dimensions `{w: columns, h: rows?}` (default: w=6)

#### PreviewCard
```openui-lang
reportPreview = PreviewCard([reportHeader, reportTable], "Monthly Report")
```
- Renders to dedicated PreviewTab (full-page)
- `title`: Tab name (required)

#### HtmlTab
```openui-lang
grafanaTab = HtmlTab("https://grafana.example.com", "Grafana Dashboard")
```
- Renders to dedicated HtmlTab (iframe embedded)
- `url`: iframe source (required)
- `title`: Tab name (required)

### 3. Grid Layout Algorithm

DashboardCards are arranged in strict DSL order:
- 12-column fixed grid
- Default width: 6 columns
- Wrap to next row when insufficient space

Example layout:
```
DSL: card1(w=6), card2(w=8), card3(w=4), card4(w=6)

Row 1: [card1(6)] [empty(6)]         <- card2(8) can't fit, wrap
Row 2: [card2(8)] [empty(4)]
Row 3: [card3(4)] [card4(6)] [empty(2)]
```

### 4. Tab Organization

- **DashboardCard**: Grouped by `tab` property
- **PreviewCard**: Each creates its own tab
- **HtmlTab**: Each creates its own tab

## Running the Demo

```bash
# Install dependencies
pnpm install

# Start dev server
pnpm dev
```

## Usage in Your Application

```typescript
import { useState } from "react";
import {
  Renderer,
  CanvasTabs,
  dslLibrary,
  CanvasItem,
} from "@openuidev/react-ui-dsl";

function MyApp() {
  const [canvasItems, setCanvasItems] = useState<CanvasItem[]>([]);

  return (
    <>
      {/* LUI Rendering */}
      <Renderer
        response={dsl}
        library={dslLibrary}
        dataModel={data}
        onCanvasItems={setCanvasItems}
      />

      {/* Canvas Rendering */}
      <CanvasTabs
        items={canvasItems}
        library={dslLibrary}
        dataModel={data}
        onClear={() => setCanvasItems([])}
      />
    </>
  );
}
```

## Customizing Canvas Behavior

### Business Layer Prompt Control

```typescript
const prompt = dslLibrary.prompt({
  additionalRules: [
    "Use DashboardCard for monitoring charts and KPIs",
    "Use PreviewCard for document previews",
    "Suggest no more than 10 DashboardCards per tab",
  ],
  examples: [
    `monitorCard = DashboardCard([chart], "CPU", {tab: "System", size: {w: 8}})`,
  ],
});
```

### Canvas Component Properties

| Component | Props | Default |
|-----------|-------|---------|
| DashboardCard | children?, title?, tab?, size?, cardId? | tab="Dashboard", size.w=6 |
| PreviewCard | children?, title!, cardId? | title required |
| HtmlTab | url!, title!, cardId? | url & title required |

## Architecture

```
DSL Input
    │
    ▼ Parser.parse()
ParseResult
    ├─ root (LUI tree)
    └─ canvasItems[]
    │
    ├─► Renderer (LUI)
    │   onCanvasItems callback
    │
    └─► CanvasTabs
        ├─ DashboardGrid (12-column)
        ├─ PreviewContent (full-page)
        └─ HtmlEmbed (iframe)
```