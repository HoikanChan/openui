# DSL Component Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `packages/react-ui-dsl` — a 15-component DSL adapter library using Ant Design v5 and ECharts, compatible with the `@openuidev/react-lang` runtime.

**Architecture:** Non-chart `genui-lib` wrappers import Ant Design v5 directly. ECharts chart components are self-encapsulated in `src/components/chart/`, applying the existing theme from `src/components/chart/theme/`. All 15 components register in `dslLibrary.tsx` via `createLibrary`. No intermediate component layer for non-chart components.

**Tech Stack:** React 19, Ant Design v5 (default theme), ECharts 5, Zod 4, `@openuidev/react-lang`, TypeScript, react-markdown

---

## File Map

```
packages/react-ui-dsl/
├── package.json                               Task 1
├── tsconfig.json                              Task 1
├── src/
│   ├── components/
│   │   └── chart/
│   │       ├── BaseChart.tsx                  Task 2 — shared ECharts hook
│   │       ├── PieChart.tsx                   Task 2
│   │       ├── LineChart.tsx                  Task 2
│   │       ├── BarChart.tsx                   Task 2
│   │       ├── GaugeChart.tsx                 Task 2
│   │       └── index.ts                       Task 2
│   │       └── theme/                         EXISTS — do not modify
│   ├── genui-lib/
│   │   ├── VLayout/schema.ts                  Task 3
│   │   ├── VLayout/index.tsx                  Task 3
│   │   ├── HLayout/schema.ts                  Task 3
│   │   ├── HLayout/index.tsx                  Task 3
│   │   ├── Text/schema.ts                     Task 4
│   │   ├── Text/index.tsx                     Task 4
│   │   ├── Image/schema.ts                    Task 4
│   │   ├── Image/index.tsx                    Task 4
│   │   ├── Link/schema.ts                     Task 4
│   │   ├── Link/index.tsx                     Task 4
│   │   ├── Button/schema.ts                   Task 5
│   │   ├── Button/index.tsx                   Task 5
│   │   ├── Select/schema.ts                   Task 5
│   │   ├── Select/index.tsx                   Task 5
│   │   ├── Card/schema.ts                     Task 6
│   │   ├── Card/index.tsx                     Task 6
│   │   ├── List/schema.ts                     Task 6
│   │   ├── List/index.tsx                     Task 6
│   │   ├── Form/schema.ts                     Task 7
│   │   ├── Form/index.tsx                     Task 7
│   │   ├── Table/schema.ts                    Task 8
│   │   ├── Table/index.tsx                    Task 8
│   │   ├── TimeLine/schema.ts                 Task 9
│   │   ├── TimeLine/index.tsx                 Task 9
│   │   ├── Charts/PieChart/schema.ts          Task 10
│   │   ├── Charts/PieChart/index.tsx          Task 10
│   │   ├── Charts/LineChart/schema.ts         Task 10
│   │   ├── Charts/LineChart/index.tsx         Task 10
│   │   ├── Charts/BarChart/schema.ts          Task 10
│   │   ├── Charts/BarChart/index.tsx          Task 10
│   │   ├── Charts/GaugeChart/schema.ts        Task 10
│   │   ├── Charts/GaugeChart/index.tsx        Task 10
│   │   ├── Charts/index.ts                    Task 10
│   │   └── dslLibrary.tsx                     Task 11
│   └── index.ts                               Task 11
```

---

### Task 1: Package Scaffold

**Files:**
- Create: `packages/react-ui-dsl/package.json`
- Create: `packages/react-ui-dsl/tsconfig.json`

- [ ] **Step 1: Create `packages/react-ui-dsl/package.json`**

```json
{
  "name": "@openuidev/react-ui-dsl",
  "version": "0.1.0",
  "type": "module",
  "description": "DSL component library built on Ant Design v5 and ECharts",
  "main": "dist/index.cjs",
  "module": "dist/index.mjs",
  "types": "dist/index.d.cts",
  "exports": {
    ".": {
      "import": {
        "types": "./dist/index.d.mts",
        "default": "./dist/index.mjs"
      },
      "require": {
        "types": "./dist/index.d.cts",
        "default": "./dist/index.cjs"
      }
    }
  },
  "files": ["dist"],
  "scripts": {
    "build": "tsc -p .",
    "typecheck": "tsc --noEmit"
  },
  "peerDependencies": {
    "@openuidev/react-lang": "workspace:^",
    "antd": "^5.0.0",
    "echarts": "^5.0.0",
    "react": ">=19.0.0",
    "react-dom": ">=19.0.0",
    "zod": "^4.0.0"
  },
  "dependencies": {
    "react-markdown": "^10.1.0"
  },
  "devDependencies": {
    "@types/react": ">=19.0.0",
    "@types/react-dom": ">=19.0.0"
  }
}
```

- [ ] **Step 2: Create `packages/react-ui-dsl/tsconfig.json`**

```json
{
  "$schema": "https://json.schemastore.org/tsconfig",
  "extends": "../../tsconfig.json",
  "include": ["src/**/*"],
  "compilerOptions": {
    "moduleResolution": "bundler",
    "module": "ESNext",
    "outDir": "./dist",
    "rootDir": "./src"
  }
}
```

- [ ] **Step 3: Install dependencies**

Run from the repo root:
```bash
pnpm install
```

Expected: `packages/react-ui-dsl` is resolved in the workspace, antd/echarts installed as peer deps in consuming apps.

- [ ] **Step 4: Commit**

```bash
git add packages/react-ui-dsl/package.json packages/react-ui-dsl/tsconfig.json
git commit -m "feat(react-ui-dsl): scaffold package with antd + echarts peer deps"
```

---

### Task 2: ECharts Chart Components

**Files:**
- Create: `packages/react-ui-dsl/src/components/chart/BaseChart.tsx`
- Create: `packages/react-ui-dsl/src/components/chart/PieChart.tsx`
- Create: `packages/react-ui-dsl/src/components/chart/LineChart.tsx`
- Create: `packages/react-ui-dsl/src/components/chart/BarChart.tsx`
- Create: `packages/react-ui-dsl/src/components/chart/GaugeChart.tsx`
- Create: `packages/react-ui-dsl/src/components/chart/index.ts`

**Context:** The theme is already at `src/components/chart/theme/index.ts` and exports `lightTheme` and `darkTheme`. These are plain JS objects passed to `echarts.registerTheme`. Chart components must: init on mount, apply theme, call `setOption` when props change, handle resize with `ResizeObserver`, dispose on unmount.

- [ ] **Step 1: Create `BaseChart.tsx`**

This is the shared ECharts React wrapper. Each specific chart component passes its merged `EChartsOption` to this.

```tsx
// packages/react-ui-dsl/src/components/chart/BaseChart.tsx
import * as echarts from "echarts";
import React from "react";
import { lightTheme } from "./theme";

const THEME_NAME = "openui-dsl";
let themeRegistered = false;

function ensureTheme() {
  if (!themeRegistered) {
    echarts.registerTheme(THEME_NAME, lightTheme);
    themeRegistered = true;
  }
}

interface BaseChartProps {
  option: echarts.EChartsOption;
  style?: React.CSSProperties;
}

export const BaseChart: React.FC<BaseChartProps> = ({ option, style }) => {
  const containerRef = React.useRef<HTMLDivElement>(null);
  const chartRef = React.useRef<echarts.ECharts | null>(null);

  // Init
  React.useEffect(() => {
    ensureTheme();
    if (!containerRef.current) return;
    chartRef.current = echarts.init(containerRef.current, THEME_NAME);
    return () => {
      chartRef.current?.dispose();
      chartRef.current = null;
    };
  }, []);

  // Update option
  React.useEffect(() => {
    chartRef.current?.setOption(option, true);
  }, [option]);

  // Resize
  React.useEffect(() => {
    const observer = new ResizeObserver(() => {
      chartRef.current?.resize();
    });
    if (containerRef.current) observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  return (
    <div
      ref={containerRef}
      style={{ width: "100%", height: 300, ...style }}
    />
  );
};
```

- [ ] **Step 2: Create `PieChart.tsx`**

DSL `properties` is `Omit<EChartsOption, 'title'> & { title?: string }`. The component merges `title` shorthand and optional `data.source` dataset.

```tsx
// packages/react-ui-dsl/src/components/chart/PieChart.tsx
import type * as echarts from "echarts";
import React from "react";
import { BaseChart } from "./BaseChart";

interface PieChartProps {
  properties?: Omit<echarts.EChartsOption, "title"> & { title?: string };
  data?: { source: number[][] };
  style?: React.CSSProperties;
}

export const PieChart: React.FC<PieChartProps> = ({ properties, data, style }) => {
  const { title, ...rest } = properties ?? {};
  const option: echarts.EChartsOption = {
    ...rest,
    ...(title ? { title: { text: title } } : {}),
    ...(data ? { dataset: { source: data.source } } : {}),
  };
  return <BaseChart option={option} style={style} />;
};
```

- [ ] **Step 3: Create `LineChart.tsx`**

```tsx
// packages/react-ui-dsl/src/components/chart/LineChart.tsx
import type * as echarts from "echarts";
import React from "react";
import { BaseChart } from "./BaseChart";

interface LineChartProps {
  properties?: Omit<echarts.EChartsOption, "title"> & { title?: string };
  data?: { source: number[][] };
  style?: React.CSSProperties;
}

export const LineChart: React.FC<LineChartProps> = ({ properties, data, style }) => {
  const { title, ...rest } = properties ?? {};
  const option: echarts.EChartsOption = {
    ...rest,
    ...(title ? { title: { text: title } } : {}),
    ...(data ? { dataset: { source: data.source } } : {}),
  };
  return <BaseChart option={option} style={style} />;
};
```

- [ ] **Step 4: Create `BarChart.tsx`**

```tsx
// packages/react-ui-dsl/src/components/chart/BarChart.tsx
import type * as echarts from "echarts";
import React from "react";
import { BaseChart } from "./BaseChart";

interface BarChartProps {
  properties?: Omit<echarts.EChartsOption, "title"> & { title?: string };
  style?: React.CSSProperties;
}

export const BarChart: React.FC<BarChartProps> = ({ properties, style }) => {
  const { title, ...rest } = properties ?? {};
  const option: echarts.EChartsOption = {
    ...rest,
    ...(title ? { title: { text: title } } : {}),
  };
  return <BaseChart option={option} style={style} />;
};
```

- [ ] **Step 5: Create `GaugeChart.tsx`**

```tsx
// packages/react-ui-dsl/src/components/chart/GaugeChart.tsx
import type * as echarts from "echarts";
import React from "react";
import { BaseChart } from "./BaseChart";

interface GaugeChartProps {
  properties?: Omit<echarts.EChartsOption, "title"> & { title?: string };
  data?: { source: number[][] };
  style?: React.CSSProperties;
}

export const GaugeChart: React.FC<GaugeChartProps> = ({ properties, data, style }) => {
  const { title, ...rest } = properties ?? {};
  const option: echarts.EChartsOption = {
    ...rest,
    ...(title ? { title: { text: title } } : {}),
    ...(data ? { dataset: { source: data.source } } : {}),
  };
  return <BaseChart option={option} style={style} />;
};
```

- [ ] **Step 6: Create `index.ts`**

```ts
// packages/react-ui-dsl/src/components/chart/index.ts
export { PieChart } from "./PieChart";
export { LineChart } from "./LineChart";
export { BarChart } from "./BarChart";
export { GaugeChart } from "./GaugeChart";
```

- [ ] **Step 7: Commit**

```bash
git add packages/react-ui-dsl/src/components/chart/
git commit -m "feat(react-ui-dsl): add ECharts chart components with theme"
```

---

### Task 3: Layout Components (VLayout, HLayout)

**Files:**
- Create: `packages/react-ui-dsl/src/genui-lib/VLayout/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/VLayout/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/HLayout/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/HLayout/index.tsx`

**Context:** `VLayout` is the default root component. Both use antd `Flex`. Children are rendered with `renderNode` (from `defineComponent`'s render props). `actions` on VLayout is opaque — ignored for now.

- [ ] **Step 1: Create `VLayout/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/VLayout/schema.ts
import { z } from "zod";

export const VLayoutSchema = z.object({
  properties: z
    .object({
      gap: z.number().optional(),
    })
    .optional(),
  children: z.array(z.any()).optional(),
  style: z.record(z.string(), z.any()).optional(),
  actions: z.array(z.any()).optional(),
});
```

- [ ] **Step 2: Create `VLayout/index.tsx`**

```tsx
// packages/react-ui-dsl/src/genui-lib/VLayout/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Flex } from "antd";
import { VLayoutSchema } from "./schema";

export const VLayout = defineComponent({
  name: "VLayout",
  props: VLayoutSchema,
  description: "Vertical flex layout — default root container",
  component: ({ props, renderNode }) => (
    <Flex
      vertical
      gap={props.properties?.gap}
      style={props.style as React.CSSProperties}
    >
      {renderNode(props.children)}
    </Flex>
  ),
});
```

- [ ] **Step 3: Create `HLayout/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/HLayout/schema.ts
import { z } from "zod";

export const HLayoutSchema = z.object({
  properties: z.object({
    gap: z.number().optional(),
    wrap: z.boolean().optional(),
  }),
  children: z.array(z.any()),
  style: z.record(z.string(), z.any()).optional(),
});
```

- [ ] **Step 4: Create `HLayout/index.tsx`**

```tsx
// packages/react-ui-dsl/src/genui-lib/HLayout/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Flex } from "antd";
import { HLayoutSchema } from "./schema";

export const HLayout = defineComponent({
  name: "HLayout",
  props: HLayoutSchema,
  description: "Horizontal flex layout",
  component: ({ props, renderNode }) => (
    <Flex
      gap={props.properties?.gap}
      wrap={props.properties?.wrap ? "wrap" : "nowrap"}
      style={props.style as React.CSSProperties}
    >
      {renderNode(props.children)}
    </Flex>
  ),
});
```

- [ ] **Step 5: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/VLayout packages/react-ui-dsl/src/genui-lib/HLayout
git commit -m "feat(react-ui-dsl): add VLayout and HLayout genui-lib components"
```

---

### Task 4: Display Components (Text, Image, Link)

**Files:**
- Create: `packages/react-ui-dsl/src/genui-lib/Text/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Text/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Image/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Image/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Link/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Link/index.tsx`

- [ ] **Step 1: Create `Text/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/Text/schema.ts
import { z } from "zod";

export const TextSchema = z.object({
  properties: z.object({
    type: z.enum(["default", "markdown", "html"]).optional(),
    content: z.string(),
  }),
  style: z.record(z.string(), z.any()).optional(),
});
```

- [ ] **Step 2: Create `Text/index.tsx`**

`default` renders as a plain `<span>`. `html` uses `dangerouslySetInnerHTML`. `markdown` uses `react-markdown`.

```tsx
// packages/react-ui-dsl/src/genui-lib/Text/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import ReactMarkdown from "react-markdown";
import { TextSchema } from "./schema";

export const Text = defineComponent({
  name: "Text",
  props: TextSchema,
  description: "Text content — supports plain, markdown, and HTML",
  component: ({ props }) => {
    const { type = "default", content } = props.properties;
    const style = props.style as React.CSSProperties | undefined;

    if (type === "html") {
      return <div style={style} dangerouslySetInnerHTML={{ __html: content }} />;
    }
    if (type === "markdown") {
      return (
        <div style={style}>
          <ReactMarkdown>{content}</ReactMarkdown>
        </div>
      );
    }
    return <span style={style}>{content}</span>;
  },
});
```

- [ ] **Step 3: Create `Image/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/Image/schema.ts
import { z } from "zod";

export const ImageSchema = z.object({
  properties: z.object({
    type: z.enum(["url", "base64", "svg"]),
    content: z.string(),
  }),
  style: z.record(z.string(), z.any()).optional(),
});
```

- [ ] **Step 4: Create `Image/index.tsx`**

```tsx
// packages/react-ui-dsl/src/genui-lib/Image/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import { ImageSchema } from "./schema";

export const Image = defineComponent({
  name: "Image",
  props: ImageSchema,
  description: "Image — url, base64, or inline SVG",
  component: ({ props }) => {
    const { type, content } = props.properties;
    const style = props.style as React.CSSProperties | undefined;

    if (type === "svg") {
      return <div style={style} dangerouslySetInnerHTML={{ __html: content }} />;
    }
    const src = type === "base64" ? `data:image/png;base64,${content}` : content;
    return <img src={src} style={{ maxWidth: "100%", ...style }} alt="" />;
  },
});
```

- [ ] **Step 5: Create `Link/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/Link/schema.ts
import { z } from "zod";

export const LinkSchema = z.object({
  properties: z.object({
    href: z.string(),
    text: z.string().optional(),
    target: z.enum(["_self", "_blank"]).optional(),
    disabled: z.boolean().optional(),
    download: z.string().optional(),
  }),
  style: z.record(z.string(), z.any()).optional(),
});
```

- [ ] **Step 6: Create `Link/index.tsx`**

```tsx
// packages/react-ui-dsl/src/genui-lib/Link/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Typography } from "antd";
import { LinkSchema } from "./schema";

export const Link = defineComponent({
  name: "Link",
  props: LinkSchema,
  description: "Anchor link",
  component: ({ props }) => {
    const { href, text, target, disabled, download } = props.properties;
    return (
      <Typography.Link
        href={disabled ? undefined : href}
        target={target}
        download={download}
        disabled={disabled}
        style={props.style as React.CSSProperties}
      >
        {text ?? href}
      </Typography.Link>
    );
  },
});
```

- [ ] **Step 7: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/Text packages/react-ui-dsl/src/genui-lib/Image packages/react-ui-dsl/src/genui-lib/Link
git commit -m "feat(react-ui-dsl): add Text, Image, Link genui-lib components"
```

---

### Task 5: Interactive Components (Button, Select)

**Files:**
- Create: `packages/react-ui-dsl/src/genui-lib/Button/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Button/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Select/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Select/index.tsx`

- [ ] **Step 1: Create `Button/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/Button/schema.ts
import { z } from "zod";

export const ButtonSchema = z.object({
  properties: z
    .object({
      status: z.enum(["default", "primary", "risk"]).optional(),
      disabled: z.boolean().optional(),
      text: z.string().optional(),
      type: z.enum(["default", "text"]).optional(),
    })
    .optional(),
  style: z.record(z.string(), z.any()).optional(),
  actions: z.array(z.any()).optional(),
});
```

- [ ] **Step 2: Create `Button/index.tsx`**

`status` mapping: `"primary"` → antd `type="primary"`, `"risk"` → antd `danger`, `"default"` → antd `type="default"`. DSL `type: "text"` → antd `type="text"`.

```tsx
// packages/react-ui-dsl/src/genui-lib/Button/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Button as AntButton } from "antd";
import { ButtonSchema } from "./schema";

export const Button = defineComponent({
  name: "Button",
  props: ButtonSchema,
  description: "Clickable button",
  component: ({ props }) => {
    const { status, text, disabled, type } = props.properties ?? {};
    const antType =
      type === "text" ? "text" : status === "primary" ? "primary" : "default";
    return (
      <AntButton
        type={antType}
        danger={status === "risk"}
        disabled={disabled}
        style={props.style as React.CSSProperties}
      >
        {text}
      </AntButton>
    );
  },
});
```

- [ ] **Step 3: Create `Select/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/Select/schema.ts
import { z } from "zod";

export const SelectSchema = z.object({
  properties: z.object({
    options: z.array(
      z.object({
        label: z.string(),
        value: z.union([z.number(), z.string()]),
      }),
    ),
    allowClear: z.boolean().optional(),
    defaultValue: z.union([z.number(), z.string()]).optional(),
  }),
  style: z.record(z.string(), z.any()).optional(),
});
```

- [ ] **Step 4: Create `Select/index.tsx`**

```tsx
// packages/react-ui-dsl/src/genui-lib/Select/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Select as AntSelect } from "antd";
import { SelectSchema } from "./schema";

export const Select = defineComponent({
  name: "Select",
  props: SelectSchema,
  description: "Dropdown select",
  component: ({ props }) => {
    const { options, allowClear, defaultValue } = props.properties;
    return (
      <AntSelect
        options={options}
        allowClear={allowClear}
        defaultValue={defaultValue}
        style={{ width: "100%", ...(props.style as React.CSSProperties) }}
      />
    );
  },
});
```

- [ ] **Step 5: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/Button packages/react-ui-dsl/src/genui-lib/Select
git commit -m "feat(react-ui-dsl): add Button and Select genui-lib components"
```

---

### Task 6: Container Components (Card, List)

**Files:**
- Create: `packages/react-ui-dsl/src/genui-lib/Card/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Card/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/List/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/List/index.tsx`

**Context:** Both Card and List have `children: DSL[]` — use `renderNode` to render them.

- [ ] **Step 1: Create `Card/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/Card/schema.ts
import { z } from "zod";

export const CardSchema = z.object({
  properties: z.object({
    tag: z.string().optional(),
    header: z.string().optional(),
    headerAlign: z.enum(["left", "center", "right"]).optional(),
  }),
  children: z.array(z.any()),
  style: z.record(z.string(), z.any()).optional(),
});
```

- [ ] **Step 2: Create `Card/index.tsx`**

```tsx
// packages/react-ui-dsl/src/genui-lib/Card/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Card as AntCard } from "antd";
import { CardSchema } from "./schema";

export const Card = defineComponent({
  name: "Card",
  props: CardSchema,
  description: "Card container with optional header",
  component: ({ props, renderNode }) => {
    const { header, headerAlign = "left", tag } = props.properties;
    const title = header ? (
      <span style={{ textAlign: headerAlign }}>{tag ? `[${tag}] ${header}` : header}</span>
    ) : undefined;
    return (
      <AntCard title={title} style={props.style as React.CSSProperties}>
        {renderNode(props.children)}
      </AntCard>
    );
  },
});
```

- [ ] **Step 3: Create `List/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/List/schema.ts
import { z } from "zod";

export const ListSchema = z.object({
  properties: z.object({
    header: z.string().optional(),
    isOrder: z.boolean().optional(),
  }),
  children: z.array(z.any()),
  style: z.record(z.string(), z.any()).optional(),
});
```

- [ ] **Step 4: Create `List/index.tsx`**

```tsx
// packages/react-ui-dsl/src/genui-lib/List/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import { ListSchema } from "./schema";

export const List = defineComponent({
  name: "List",
  props: ListSchema,
  description: "Ordered or unordered list",
  component: ({ props, renderNode }) => {
    const { header, isOrder } = props.properties;
    const Tag = isOrder ? "ol" : "ul";
    return (
      <div style={props.style as React.CSSProperties}>
        {header && <div style={{ fontWeight: 600, marginBottom: 8 }}>{header}</div>}
        <Tag style={{ paddingLeft: 24, margin: 0 }}>
          {(props.children as unknown[]).map((child, i) => (
            <li key={i}>{renderNode(child as any)}</li>
          ))}
        </Tag>
      </div>
    );
  },
});
```

- [ ] **Step 5: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/Card packages/react-ui-dsl/src/genui-lib/List
git commit -m "feat(react-ui-dsl): add Card and List genui-lib components"
```

---

### Task 7: Form Component

**Files:**
- Create: `packages/react-ui-dsl/src/genui-lib/Form/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Form/index.tsx`

**Context:** The DSL Form has inline field definitions with `label`, `name`, `rules`, and `component: DSL`. There is no separate `buttons` field — actions are handled by button components inside the DSL (not form-level). `useRenderNode` renders each field's `component` DSL node. Validation rules are basic (`required: boolean`).

- [ ] **Step 1: Create `Form/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/Form/schema.ts
import { z } from "zod";

export const FormSchema = z.object({
  properties: z.object({
    layout: z.enum(["vertical", "inline", "horizontal"]).optional(),
    labelAlign: z.enum(["left", "right"]).optional(),
    initialValues: z.record(z.string(), z.any()).optional(),
    fields: z.array(
      z.object({
        label: z.string(),
        name: z.string(),
        rules: z.array(z.object({ required: z.boolean() })).optional(),
        component: z.any(),
      }),
    ),
  }),
});
```

- [ ] **Step 2: Create `Form/index.tsx`**

```tsx
// packages/react-ui-dsl/src/genui-lib/Form/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Form as AntForm } from "antd";
import { FormSchema } from "./schema";

export const Form = defineComponent({
  name: "Form",
  props: FormSchema,
  description: "Form with inline field definitions",
  component: ({ props, renderNode }) => {
    const { layout = "vertical", labelAlign, initialValues, fields } = props.properties;
    return (
      <AntForm
        layout={layout}
        labelAlign={labelAlign}
        initialValues={initialValues}
      >
        {fields.map((field, i) => (
          <AntForm.Item
            key={i}
            label={field.label}
            name={field.name}
            rules={field.rules?.map((r) => ({ required: r.required }))}
          >
            {renderNode(field.component)}
          </AntForm.Item>
        ))}
      </AntForm>
    );
  },
});
```

- [ ] **Step 3: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/Form
git commit -m "feat(react-ui-dsl): add Form genui-lib component"
```

---

### Task 8: Table Component

**Files:**
- Create: `packages/react-ui-dsl/src/genui-lib/Table/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Table/index.tsx`

**Context:** The DSL Table defines columns with `field` (row data key), `title`, `sortable`, `filterable`, `filterOptions`, `customized` (DSL for cell rendering), `format`, `tooltip`. Table data (`dataSource`) is not part of the DSL — it is expected to be injected via the `react-lang` data model context. For now, the component renders the column structure; `dataSource` defaults to an empty array.

- [ ] **Step 1: Create `Table/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/Table/schema.ts
import { z } from "zod";

export const TableSchema = z.object({
  properties: z.object({
    columns: z.array(
      z.object({
        title: z.string(),
        field: z.string(),
        sortable: z.boolean().optional(),
        filterable: z.boolean().optional(),
        filterOptions: z.array(z.string()).optional(),
        customized: z.any().optional(),
        format: z.enum(["data", "dateTime", "time"]).optional(),
        tooltip: z.boolean().optional(),
      }),
    ),
  }),
  style: z.record(z.string(), z.any()).optional(),
});
```

- [ ] **Step 2: Create `Table/index.tsx`**

```tsx
// packages/react-ui-dsl/src/genui-lib/Table/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Table as AntTable, Tooltip } from "antd";
import type { ColumnType } from "antd/es/table";
import { TableSchema } from "./schema";

function formatCell(value: unknown, format?: "data" | "dateTime" | "time"): string {
  if (value == null) return "";
  if (format === "dateTime" || format === "time") {
    const d = new Date(value as string);
    if (isNaN(d.getTime())) return String(value);
    return format === "time" ? d.toLocaleTimeString() : d.toLocaleString();
  }
  return String(value);
}

export const Table = defineComponent({
  name: "Table",
  props: TableSchema,
  description: "Data table with column definitions",
  component: ({ props, renderNode }) => {
    const { columns } = props.properties;

    const antColumns: ColumnType<Record<string, unknown>>[] = columns.map((col) => ({
      title: col.title,
      dataIndex: col.field,
      key: col.field,
      sorter: col.sortable ? (a, b) => {
        const av = a[col.field];
        const bv = b[col.field];
        return String(av ?? "").localeCompare(String(bv ?? ""));
      } : undefined,
      filters: col.filterable && col.filterOptions
        ? col.filterOptions.map((o) => ({ text: o, value: o }))
        : undefined,
      onFilter: col.filterable
        ? (value, record) => String(record[col.field]) === String(value)
        : undefined,
      render: (value: unknown, record: Record<string, unknown>) => {
        if (col.customized) {
          return renderNode(col.customized);
        }
        const text = formatCell(value, col.format);
        if (col.tooltip) {
          return <Tooltip title={text}><span>{text}</span></Tooltip>;
        }
        return text;
      },
    }));

    return (
      <AntTable
        columns={antColumns}
        dataSource={[]}
        rowKey={(_, i) => String(i)}
        style={props.style as React.CSSProperties}
        pagination={false}
        size="middle"
      />
    );
  },
});
```

- [ ] **Step 3: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/Table
git commit -m "feat(react-ui-dsl): add Table genui-lib component"
```

---

### Task 9: TimeLine Component

**Files:**
- Create: `packages/react-ui-dsl/src/genui-lib/TimeLine/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/TimeLine/index.tsx`

**Context:** DSL `data[]` has `iconType` (`success`/`error`/`default`) and `content.children: DSL[]`. antd `Timeline` uses `items[]` with `color` and `children`. Map `iconType` to color: `success` → `"green"`, `error` → `"red"`, `default` → `"gray"`.

- [ ] **Step 1: Create `TimeLine/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/TimeLine/schema.ts
import { z } from "zod";

export const TimeLineSchema = z.object({
  properties: z
    .object({
      title: z.string().optional(),
      id: z.string().optional(),
    })
    .optional(),
  data: z.array(
    z.object({
      content: z.object({
        title: z.string(),
        children: z.array(z.any()),
      }),
      iconType: z.enum(["success", "error", "default"]),
    }),
  ),
  style: z.record(z.string(), z.any()).optional(),
});
```

- [ ] **Step 2: Create `TimeLine/index.tsx`**

```tsx
// packages/react-ui-dsl/src/genui-lib/TimeLine/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Timeline } from "antd";
import { TimeLineSchema } from "./schema";

const iconColorMap = {
  success: "green",
  error: "red",
  default: "gray",
} as const;

export const TimeLine = defineComponent({
  name: "TimeLine",
  props: TimeLineSchema,
  description: "Timeline with typed items, each containing a DSL children tree",
  component: ({ props, renderNode }) => {
    const title = props.properties?.title;
    const items = props.data.map((item) => ({
      color: iconColorMap[item.iconType],
      children: (
        <>
          <div style={{ fontWeight: 600, marginBottom: 4 }}>{item.content.title}</div>
          {renderNode(item.content.children)}
        </>
      ),
    }));

    return (
      <div style={props.style as React.CSSProperties}>
        {title && <div style={{ fontWeight: 700, marginBottom: 12 }}>{title}</div>}
        <Timeline items={items} />
      </div>
    );
  },
});
```

- [ ] **Step 3: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/TimeLine
git commit -m "feat(react-ui-dsl): add TimeLine genui-lib component"
```

---

### Task 10: Chart DSL Wrappers (genui-lib)

**Files:**
- Create: `packages/react-ui-dsl/src/genui-lib/Charts/PieChart/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Charts/PieChart/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Charts/LineChart/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Charts/LineChart/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Charts/BarChart/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Charts/BarChart/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Charts/GaugeChart/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Charts/GaugeChart/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Charts/index.ts`

**Context:** All chart schemas follow the same pattern: `properties` extends `Omit<EChartsOption, 'title'>` with `title?: string`. Pie/Line/Gauge have optional `data: { source: number[][] }`. Bar has no `data`. Wrappers import from `src/components/chart/`.

- [ ] **Step 1: Create `Charts/PieChart/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/Charts/PieChart/schema.ts
import { z } from "zod";

export const PieChartSchema = z.object({
  properties: z.record(z.string(), z.any()).optional(),
  data: z.object({ source: z.array(z.array(z.number())) }).optional(),
  style: z.record(z.string(), z.any()).optional(),
});
```

- [ ] **Step 2: Create `Charts/PieChart/index.tsx`**

```tsx
// packages/react-ui-dsl/src/genui-lib/Charts/PieChart/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import { PieChart as PieChartComponent } from "../../../components/chart";
import { PieChartSchema } from "./schema";

export const PieChart = defineComponent({
  name: "PieChart",
  props: PieChartSchema,
  description: "ECharts pie chart",
  component: ({ props }) => (
    <PieChartComponent
      properties={props.properties as any}
      data={props.data}
      style={props.style as React.CSSProperties}
    />
  ),
});
```

- [ ] **Step 3: Create `Charts/LineChart/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/Charts/LineChart/schema.ts
import { z } from "zod";

export const LineChartSchema = z.object({
  properties: z.record(z.string(), z.any()).optional(),
  data: z.object({ source: z.array(z.array(z.number())) }).optional(),
  style: z.record(z.string(), z.any()).optional(),
});
```

- [ ] **Step 4: Create `Charts/LineChart/index.tsx`**

```tsx
// packages/react-ui-dsl/src/genui-lib/Charts/LineChart/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import { LineChart as LineChartComponent } from "../../../components/chart";
import { LineChartSchema } from "./schema";

export const LineChart = defineComponent({
  name: "LineChart",
  props: LineChartSchema,
  description: "ECharts line chart",
  component: ({ props }) => (
    <LineChartComponent
      properties={props.properties as any}
      data={props.data}
      style={props.style as React.CSSProperties}
    />
  ),
});
```

- [ ] **Step 5: Create `Charts/BarChart/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/Charts/BarChart/schema.ts
import { z } from "zod";

export const BarChartSchema = z.object({
  properties: z.record(z.string(), z.any()).optional(),
  style: z.record(z.string(), z.any()).optional(),
});
```

- [ ] **Step 6: Create `Charts/BarChart/index.tsx`**

```tsx
// packages/react-ui-dsl/src/genui-lib/Charts/BarChart/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import { BarChart as BarChartComponent } from "../../../components/chart";
import { BarChartSchema } from "./schema";

export const BarChart = defineComponent({
  name: "BarChart",
  props: BarChartSchema,
  description: "ECharts bar chart",
  component: ({ props }) => (
    <BarChartComponent
      properties={props.properties as any}
      style={props.style as React.CSSProperties}
    />
  ),
});
```

- [ ] **Step 7: Create `Charts/GaugeChart/schema.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/Charts/GaugeChart/schema.ts
import { z } from "zod";

export const GaugeChartSchema = z.object({
  properties: z.record(z.string(), z.any()).optional(),
  data: z.object({ source: z.array(z.array(z.number())) }).optional(),
  style: z.record(z.string(), z.any()).optional(),
});
```

- [ ] **Step 8: Create `Charts/GaugeChart/index.tsx`**

```tsx
// packages/react-ui-dsl/src/genui-lib/Charts/GaugeChart/index.tsx
"use client";

import { defineComponent } from "@openuidev/react-lang";
import { GaugeChart as GaugeChartComponent } from "../../../components/chart";
import { GaugeChartSchema } from "./schema";

export const GaugeChart = defineComponent({
  name: "GaugeChart",
  props: GaugeChartSchema,
  description: "ECharts gauge chart",
  component: ({ props }) => (
    <GaugeChartComponent
      properties={props.properties as any}
      data={props.data}
      style={props.style as React.CSSProperties}
    />
  ),
});
```

- [ ] **Step 9: Create `Charts/index.ts`**

```ts
// packages/react-ui-dsl/src/genui-lib/Charts/index.ts
export { PieChart } from "./PieChart";
export { LineChart } from "./LineChart";
export { BarChart } from "./BarChart";
export { GaugeChart } from "./GaugeChart";
```

- [ ] **Step 10: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/Charts
git commit -m "feat(react-ui-dsl): add chart DSL wrappers (PieChart, LineChart, BarChart, GaugeChart)"
```

---

### Task 11: Library Registration

**Files:**
- Create: `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`
- Create: `packages/react-ui-dsl/src/index.ts`

- [ ] **Step 1: Create `genui-lib/dslLibrary.tsx`**

```tsx
// packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx
"use client";

import { createLibrary } from "@openuidev/react-lang";
import { BarChart, GaugeChart, LineChart, PieChart } from "./Charts";
import { Button } from "./Button";
import { Card } from "./Card";
import { Form } from "./Form";
import { HLayout } from "./HLayout";
import { Image } from "./Image";
import { Link } from "./Link";
import { List } from "./List";
import { Select } from "./Select";
import { Table } from "./Table";
import { Text } from "./Text";
import { TimeLine } from "./TimeLine";
import { VLayout } from "./VLayout";

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
    PieChart,
    LineChart,
    BarChart,
    GaugeChart,
    TimeLine,
  ],
});
```

- [ ] **Step 2: Create `src/index.ts`**

```ts
// packages/react-ui-dsl/src/index.ts
export { dslLibrary } from "./genui-lib/dslLibrary";
```

- [ ] **Step 3: Run typecheck**

From `packages/react-ui-dsl/`:
```bash
cd packages/react-ui-dsl && pnpm typecheck
```

Expected: No errors. If there are import errors for `antd` or `echarts`, run `pnpm install` from the repo root first.

- [ ] **Step 4: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx packages/react-ui-dsl/src/index.ts
git commit -m "feat(react-ui-dsl): register all 15 components in dslLibrary"
```
