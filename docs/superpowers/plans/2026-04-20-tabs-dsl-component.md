# Tabs DSL Component Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `Tabs` DSL component to `packages/react-ui-dsl/src/genui-lib/Tabs/` using antd, with streaming-aware skeleton screens and auto tab-switching.

**Architecture:** View layer (`view/index.tsx`) is a stateless antd `<Tabs>` renderer receiving controlled props. All streaming logic (auto tab-switching, skeleton detection) lives in `index.tsx` via `defineComponent`. Schema is in `schema.ts`.

**Tech Stack:** React, antd `<Tabs>` + `<Skeleton>`, zod, `@openuidev/react-lang` `defineComponent`, Storybook, Vitest

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `packages/react-ui-dsl/src/genui-lib/Tabs/schema.ts` | Create | `TabItemSchema` + `TabsSchema` (zod) |
| `packages/react-ui-dsl/src/genui-lib/Tabs/view/index.tsx` | Create | Stateless antd `<Tabs>` render, skeleton when `loading` |
| `packages/react-ui-dsl/src/genui-lib/Tabs/index.tsx` | Create | `defineComponent` with streaming state logic |
| `packages/react-ui-dsl/src/genui-lib/Tabs/stories/index.stories.tsx` | Create | Storybook stories for `TabView` |
| `packages/react-ui-dsl/src/genui-lib/Tabs/stories/index.stories.test.tsx` | Create | Vitest unit test for story structure |
| `packages/react-ui-dsl/src/genui-lib/story-structure.test.ts` | Modify | Add `"Tabs"` to `componentDirs` array |
| `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx` | Modify | Import and register `Tabs` component |

---

## Task 1: Schema

**Files:**
- Create: `packages/react-ui-dsl/src/genui-lib/Tabs/schema.ts`

- [ ] **Step 1: Create schema.ts**

```ts
import { z } from "zod";

export const TabItemSchema = z.object({
  value: z.string(),
  label: z.string(),
  content: z.array(z.any()),
});

export const TabsSchema = z.object({
  items: z.array(TabItemSchema),
  style: z.record(z.string(), z.any()).optional(),
});
```

- [ ] **Step 2: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/Tabs/schema.ts
git commit -m "feat(react-ui-dsl): add Tabs schema"
```

---

## Task 2: View Layer

**Files:**
- Create: `packages/react-ui-dsl/src/genui-lib/Tabs/view/index.tsx`

- [ ] **Step 1: Create view/index.tsx**

```tsx
"use client";

import { Skeleton, Tabs } from "antd";
import type { CSSProperties, ReactNode } from "react";

export type TabViewItem = {
  value: string;
  label: string;
  children: ReactNode;
  loading: boolean;
};

export type TabViewProps = {
  items: TabViewItem[];
  activeTab: string;
  onTabChange: (value: string) => void;
  style?: CSSProperties;
};

export function TabView({ items, activeTab, onTabChange, style }: TabViewProps) {
  return (
    <div style={style}>
      <Tabs
        activeKey={activeTab}
        onChange={onTabChange}
        items={items.map((item) => ({
          key: item.value,
          label: item.label,
          children: item.loading ? (
            <Skeleton active paragraph={{ rows: 3 }} />
          ) : (
            item.children
          ),
        }))}
      />
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/Tabs/view/index.tsx
git commit -m "feat(react-ui-dsl): add TabView with antd Tabs and skeleton"
```

---

## Task 3: defineComponent with Streaming Logic

**Files:**
- Create: `packages/react-ui-dsl/src/genui-lib/Tabs/index.tsx`

- [ ] **Step 1: Create index.tsx**

```tsx
"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import React from "react";
import { z } from "zod";
import { TabsSchema } from "./schema";
import { TabView } from "./view";

export const Tabs = defineComponent({
  name: "Tabs",
  props: TabsSchema,
  description: "Tabbed container with streaming-aware skeleton and auto tab-switching",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof TabsSchema>>) => {
    const items = props.items ?? [];
    const [activeTab, setActiveTab] = React.useState("");
    const userHasInteracted = React.useRef(false);
    const prevContentSizes = React.useRef<Record<string, number>>({});

    React.useEffect(() => {
      const first = items[0];
      if (items.length && !activeTab && first) {
        setActiveTab(first.value);
      }
    }, [items.length, activeTab]);

    React.useEffect(() => {
      if (userHasInteracted.current) return;

      let candidate: string | null = null;
      const nextSizes: Record<string, number> = {};

      for (const item of items) {
        const size = JSON.stringify(item.content).length;
        const prevSize = prevContentSizes.current[item.value] ?? 0;
        nextSizes[item.value] = size;
        if (size > prevSize) {
          candidate = item.value;
        }
      }

      prevContentSizes.current = nextSizes;

      if (candidate && candidate !== activeTab) {
        setActiveTab(candidate);
      }
    });

    const handleTabChange = (value: string) => {
      userHasInteracted.current = true;
      setActiveTab(value);
    };

    if (!items.length) return null;

    return (
      <TabView
        activeTab={activeTab}
        onTabChange={handleTabChange}
        style={props.style as React.CSSProperties}
        items={items.map((item) => ({
          value: item.value,
          label: item.label,
          children: renderNode(item.content),
          loading: item.content.length === 0,
        }))}
      />
    );
  },
});
```

- [ ] **Step 2: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/Tabs/index.tsx
git commit -m "feat(react-ui-dsl): add Tabs defineComponent with streaming logic"
```

---

## Task 4: Stories

**Files:**
- Create: `packages/react-ui-dsl/src/genui-lib/Tabs/stories/index.stories.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Tabs/stories/index.stories.test.tsx`

- [ ] **Step 1: Create index.stories.tsx**

```tsx
import type { Meta, StoryObj } from "@storybook/react";
import { TabView } from "../view";

const meta = {
  title: "DSL Components/Tabs",
  component: TabView,
  args: {
    activeTab: "overview",
    items: [
      { value: "overview", label: "Overview", children: "Overview content", loading: false },
      { value: "settings", label: "Settings", children: "Settings content", loading: false },
    ],
  },
  argTypes: {
    style: { control: "object" },
  },
  render: (args) => <TabView {...args} onTabChange={() => {}} />,
} satisfies Meta<typeof TabView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Loading: Story = {
  args: {
    activeTab: "overview",
    items: [
      { value: "overview", label: "Overview", children: "Overview content", loading: false },
      { value: "settings", label: "Settings", children: null, loading: true },
    ],
  },
};
```

- [ ] **Step 2: Create index.stories.test.tsx**

```tsx
import React from "react";
import { describe, expect, test } from "vitest";
import meta, { Default, Loading } from "./index.stories";

describe("Tabs story", () => {
  test("Default story renders two loaded tabs", () => {
    if (!meta.render || !meta.args) {
      throw new Error("Tabs story meta must define render and args");
    }
    const rendered = meta.render(meta.args, {} as never);
    expect(rendered.props.items).toHaveLength(2);
    expect(rendered.props.items[0].loading).toBe(false);
    expect(rendered.props.items[1].loading).toBe(false);
  });

  test("Loading story has one loading tab", () => {
    const args = { ...meta.args, ...Loading.args };
    const rendered = meta.render!(args as typeof meta.args, {} as never);
    expect(rendered.props.items[1].loading).toBe(true);
  });
});
```

- [ ] **Step 3: Run tests**

```bash
cd packages/react-ui-dsl && pnpm test --reporter=verbose 2>&1 | grep -E "Tabs story|PASS|FAIL"
```

Expected: both `Tabs story` tests pass.

- [ ] **Step 4: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/Tabs/stories/
git commit -m "feat(react-ui-dsl): add Tabs stories and story tests"
```

---

## Task 5: Register in story-structure.test.ts

**Files:**
- Modify: `packages/react-ui-dsl/src/genui-lib/story-structure.test.ts`

- [ ] **Step 1: Add "Tabs" to componentDirs**

In `story-structure.test.ts`, add `"Tabs"` to the `componentDirs` array (line 6–22):

```ts
const componentDirs = [
  "Button",
  "Card",
  "Form",
  "HLayout",
  "Image",
  "Link",
  "List",
  "Select",
  "Table",
  "Tabs",
  "Text",
  "TimeLine",
  "VLayout",
  "Charts/BarChart",
  "Charts/GaugeChart",
  "Charts/LineChart",
  "Charts/PieChart",
];
```

- [ ] **Step 2: Run story-structure test**

```bash
cd packages/react-ui-dsl && pnpm test story-structure --reporter=verbose
```

Expected: `Tabs exposes view and story files` passes.

- [ ] **Step 3: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/story-structure.test.ts
git commit -m "test(react-ui-dsl): register Tabs in story-structure test"
```

---

## Task 6: Register in dslLibrary

**Files:**
- Modify: `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`

- [ ] **Step 1: Add import and register Tabs**

In `dslLibrary.tsx`, add after the `TimeLine` import:

```tsx
import { Tabs } from "./Tabs";
```

And add `Tabs` to the `components` array:

```tsx
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
    Col,
    Table,
    PieChart,
    LineChart,
    BarChart,
    GaugeChart,
    TimeLine,
    Tabs,
  ],
});
```

- [ ] **Step 2: Run full test suite**

```bash
cd packages/react-ui-dsl && pnpm test --reporter=verbose
```

Expected: all tests pass.

- [ ] **Step 3: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx
git commit -m "feat(react-ui-dsl): register Tabs in dslLibrary"
```
