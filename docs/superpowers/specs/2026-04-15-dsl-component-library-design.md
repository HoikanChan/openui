---
title: DSL Component Library Design
date: 2026-04-15
status: approved
---

# DSL Component Library Design

## Background

The project currently has a UI component library in `packages/react-ui` with two layers:

- `src/components/` — raw React UI components (Button, Card, Table, etc.) using Radix UI + SCSS
- `src/genui-lib/` — DSL adapter layer that wraps components with `defineComponent` + Zod schemas for the `react-lang` runtime

The goal is to replace both layers with a new DSL component library built on **Ant Design v5** (for general UI components) and **ECharts** (for charts), while keeping the `react-lang` runtime (`defineComponent`, `createLibrary`, `useTriggerAction`, etc.) intact.

## Decision

**Approach: New isolated package** (`packages/react-ui-dsl`)

Build a new package in the monorepo with two layers:

1. `src/components/chart/` — ECharts-based React chart components with custom theme (partially exists)
2. `src/genui-lib/` — DSL adapter layer using `defineComponent` + Zod schemas

Non-chart components (`Button`, `Table`, `Form`, etc.) have no intermediate component layer — `genui-lib` wrappers import Ant Design directly. Chart components are heavier and have an existing theme, so they live in `src/components/chart/` and `genui-lib` imports from there.

Rejected alternatives:
- **Big Bang replacement**: Too risky — everything breaks at once, hard to verify incrementally.
- **Incremental in-place replacement**: Long transition period with mixed old/new state in the same package.
- **Thin wrapper layer for all components**: Adds indirection with no benefit when antd is a stable public API.

## Component DSL Format

DSL components follow a consistent interface shape (defined in `dsl.py`):

```ts
interface ComponentDSL {
  id?: string;
  type: string;           // discriminator (e.g. 'button', 'text', 'gaugeChart')
  properties?: { ... };  // component-specific config
  style?: CSSProperties; // optional inline styles
  actions?: Action[];    // event handlers (opaque)
  data?: { ... };        // data input for data-driven components
}
```

Children are expressed as `DSL[]` arrays inside `children` (card, list, hLayout, vLayout) or `data[].content.children` (timeLine).

## Complete Component Set

| DSL type | Ant Design / lib | Notes |
|---|---|---|
| `vLayout` | `Flex` (column) | Default root. `gap` prop |
| `hLayout` | `Flex` (row) | `gap`, `wrap` props |
| `text` | Native HTML | Supports `default` \| `markdown` \| `html` via `properties.type` |
| `button` | `Button` | `status` → antd `type`/`danger`; `type: 'text'` → antd `type: 'text'` |
| `select` | `Select` | `options`, `defaultValue`, `allowClear` direct pass-through |
| `image` | Native `<img>` / inline SVG | `type: 'url' \| 'base64' \| 'svg'` |
| `link` | `Typography.Link` | `href`, `target`, `download`, `disabled` |
| `card` | `Card` | `header` → antd `title`; optional `tag` |
| `list` | `List` | `header`, `isOrder` (ordered/unordered) |
| `form` | `Form` | Dynamic `fields[]` with label/name/rules/component DSL |
| `table` | `Table` | `columns[]` with sortable, filterable, format, tooltip, customized DSL |
| `pieChart` | ECharts | `src/components/chart/PieChart` + existing theme |
| `lineChart` | ECharts | `src/components/chart/LineChart` + existing theme |
| `barChart` | ECharts | `src/components/chart/BarChart` + existing theme |
| `gaugeChart` | ECharts | `src/components/chart/GaugeChart` + existing theme |
| `timeLine` | `Timeline` | `iconType: 'success' \| 'error' \| 'default'` → dot color; each item has a DSL children tree |

**Note on charts**: All charts are ECharts-based (`echarts` npm package). Properties extend `Omit<echarts.EChartsOption, 'title'>` with a top-level `title` string shorthand. The existing theme at `src/components/chart/theme/` (colors, tokens, light/dark variants) is applied inside each chart component.

**Note on Ant Design**: Using v5 with default theme. No `ConfigProvider` theme customization at this stage. No manual CSS imports required (antd v5 uses CSS-in-JS).

## Package Structure

```
packages/react-ui-dsl/
├── src/
│   ├── components/
│   │   └── chart/                  # ECharts React components (self-encapsulated)
│   │       ├── PieChart.tsx
│   │       ├── LineChart.tsx
│   │       ├── BarChart.tsx
│   │       ├── GaugeChart.tsx
│   │       ├── index.ts
│   │       └── theme/              # Already exists
│   │           ├── colors.ts
│   │           ├── tokens.ts
│   │           └── index.ts
│   ├── genui-lib/                  # DSL adapter layer
│   │   ├── Button/
│   │   │   ├── index.tsx           # defineComponent wrapper
│   │   │   └── schema.ts           # Zod schema
│   │   ├── Text/
│   │   ├── Select/
│   │   ├── Image/
│   │   ├── Link/
│   │   ├── Card/
│   │   ├── List/
│   │   ├── Form/
│   │   ├── Table/
│   │   ├── HLayout/
│   │   ├── VLayout/
│   │   ├── Charts/                 # PieChart, LineChart, BarChart, GaugeChart
│   │   ├── TimeLine/
│   │   └── dslLibrary.tsx          # createLibrary registration
│   └── index.ts                    # Public exports
├── package.json
└── tsconfig.json
```

## Schema Pattern

Each component's Zod schema mirrors the TypeScript DSL interface from `dsl.py` directly. The nested `properties` structure is preserved rather than flattened.

Example — Button:

```ts
// genui-lib/Button/schema.ts
import { z } from "zod"

export const ButtonSchema = z.object({
  properties: z.object({
    status: z.enum(["default", "primary", "risk"]).optional(),
    disabled: z.boolean().optional(),
    text: z.string().optional(),
    type: z.enum(["default", "text"]).optional(),
  }).optional(),
  style: z.record(z.string(), z.any()).optional(),
  actions: z.array(z.any()).optional(),
})
```

`style` is `z.record(z.string(), z.any())` to accept arbitrary CSSProperties.
`actions` is `z.array(z.any())` — handled opaquely by the component.

## Component Wrapper Pattern

Each `genui-lib` wrapper uses `defineComponent` from `@openuidev/react-lang` and imports antd (or chart components) directly:

```tsx
// genui-lib/Button/index.tsx
"use client"

import { Button as AntButton } from "antd"
import { defineComponent } from "@openuidev/react-lang"
import { ButtonSchema } from "./schema"

export const Button = defineComponent({
  name: "Button",
  props: ButtonSchema,
  description: "Clickable button",
  component: ({ props }) => {
    const { status, text, disabled, type } = props.properties ?? {}
    return (
      <AntButton
        type={type === "text" ? "text" : status === "primary" ? "primary" : "default"}
        danger={status === "risk"}
        disabled={disabled}
        style={props.style}
      >
        {text}
      </AntButton>
    )
  },
})
```

For components with recursive children (Card, List, HLayout, VLayout, TimeLine), `useRenderNode` from `react-lang` is used to render the `DSL[]` children array.

### Chart Wrapper Pattern

Chart genui-lib wrappers import from `src/components/chart/`:

```tsx
// genui-lib/Charts/PieChart/index.tsx
"use client"

import { defineComponent } from "@openuidev/react-lang"
import { PieChart } from "../../components/chart"
import { PieChartSchema } from "./schema"

export const PieChartDSL = defineComponent({
  name: "PieChart",
  props: PieChartSchema,
  description: "ECharts pie chart",
  component: ({ props }) => (
    <PieChart option={props.properties} data={props.data} style={props.style} />
  ),
})
```

The chart component in `src/components/chart/PieChart.tsx` handles ECharts initialization, resize observation, and theme application (`lightTheme` / `darkTheme` from `theme/index.ts`).

## Library Registration

```tsx
// genui-lib/dslLibrary.tsx
"use client"

import { createLibrary } from "@openuidev/react-lang"
import { VLayout } from "./VLayout"
import { HLayout } from "./HLayout"
import { Text } from "./Text"
import { Button } from "./Button"
import { Select } from "./Select"
import { Image } from "./Image"
import { Link } from "./Link"
import { Card } from "./Card"
import { List } from "./List"
import { Form } from "./Form"
import { Table } from "./Table"
import { PieChartDSL, LineChartDSL, BarChartDSL, GaugeChartDSL } from "./Charts"
import { TimeLine } from "./TimeLine"

export const dslLibrary = createLibrary({
  root: "VLayout",
  components: [
    VLayout,
    HLayout,
    Text,
    Button,
    Select,
    Image,
    Link,
    Card,
    List,
    Form,
    Table,
    PieChartDSL,
    LineChartDSL,
    BarChartDSL,
    GaugeChartDSL,
    TimeLine,
  ],
})
```

## package.json

```json
{
  "name": "@openuidev/react-ui-dsl",
  "type": "module",
  "peerDependencies": {
    "@openuidev/react-lang": "workspace:^",
    "react": ">=19.0.0",
    "react-dom": ">=19.0.0",
    "antd": "^5.0.0",
    "echarts": "^5.0.0"
  }
}
```

Both `antd` and `echarts` are peer dependencies — the consuming app installs them. No separate CSS entry point is needed (antd v5 CSS-in-JS, chart theme is a JS object).

## Out of Scope (Deferred)

- **`actions` integration with `react-lang`**: The `Action[]` type will eventually be mapped to `react-lang`'s `ActionPlan` (ToAssistant, Run, Set, OpenUrl). Deferred to a follow-up iteration.
- **`openuiLibrary` / `componentGroups` / prompt notes**: AI prompt guidance is not part of this spec. Add once the component set is stable.
- **antd ConfigProvider theme customization**: Using antd defaults for now. Custom design tokens deferred.
- **TreeTableDSL, PIUDSL, IframeDSL**: Deferred — definitions TBD.
- **Storybook / tests**: Out of scope for initial implementation.
