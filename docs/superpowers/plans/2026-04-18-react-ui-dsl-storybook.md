# React UI DSL Storybook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a standalone Storybook setup under `packages/react-ui-dsl` with one overview story that proves the DSL library can render independently.

**Architecture:** Copy the existing `packages/react-ui/.storybook` setup into `packages/react-ui-dsl/.storybook`, then trim it to the dependencies and styling that `react-ui-dsl` actually needs. Add one story file that renders a representative DSL schema through `@openuidev/react-lang` using the local `dslLibrary`.

**Tech Stack:** Storybook 8, Vite, React 19, TypeScript, Ant Design, ECharts, `@openuidev/react-lang`

---

### Task 1: Scaffold standalone Storybook config for `react-ui-dsl`

**Files:**
- Create: `packages/react-ui-dsl/.storybook/main.ts`
- Create: `packages/react-ui-dsl/.storybook/preview.tsx`
- Modify: `packages/react-ui-dsl/package.json`

- [ ] **Step 1: Add Storybook scripts and dev dependencies to `packages/react-ui-dsl/package.json`**

```json
{
  "scripts": {
    "storybook": "storybook dev -p 6007",
    "build:storybook": "storybook build"
  },
  "devDependencies": {
    "@storybook/addon-essentials": "^8.5.3",
    "@storybook/addon-interactions": "^8.5.3",
    "@storybook/blocks": "^8.5.3",
    "@storybook/react": "^8.5.3",
    "@storybook/react-vite": "^8.5.3",
    "@storybook/test": "^8.5.3",
    "storybook": "^8.5.3",
    "vite": "^5.0.0"
  }
}
```

- [ ] **Step 2: Create `packages/react-ui-dsl/.storybook/main.ts` by adapting the `react-ui` config**

```ts
import type { StorybookConfig } from "@storybook/react-vite";
import path from "path";
import { mergeConfig } from "vite";

const config: StorybookConfig = {
  stories: ["../src/**/*.stories.@(js|jsx|mjs|ts|tsx)"],
  addons: ["@storybook/addon-essentials", "@storybook/addon-interactions", "@storybook/blocks"],
  framework: "@storybook/react-vite",
  viteFinal: async (config) =>
    mergeConfig(config, {
      resolve: {
        alias: {
          "@openuidev/react-lang": path.resolve(__dirname, "../../react-lang/src/index.ts"),
        },
      },
      optimizeDeps: {
        exclude: ["@openuidev/react-lang"],
        include: ["react", "react-dom", "antd", "echarts"],
      },
    }),
};

export default config;
```

- [ ] **Step 3: Create `packages/react-ui-dsl/.storybook/preview.tsx` with minimal global styling**

```tsx
import type { Preview } from "@storybook/react";
import "antd/dist/reset.css";
import React from "react";

const preview: Preview = {
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
    layout: "fullscreen",
  },
};

export default preview;
```

- [ ] **Step 4: Run targeted typecheck for the package**

Run: `pnpm --filter @openuidev/react-ui-dsl typecheck`
Expected: PASS with no TypeScript errors from the new Storybook config files.

### Task 2: Add one overview story that renders the DSL library

**Files:**
- Create: `packages/react-ui-dsl/src/stories/DslLibrary.stories.tsx`
- Inspect: `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`

- [ ] **Step 1: Write the overview story metadata and representative DSL payload**

```tsx
import type { Meta, StoryObj } from "@storybook/react";

const meta = {
  title: "DSL/Overview",
  parameters: {
    layout: "fullscreen",
  },
} satisfies Meta;

export default meta;
export type Story = StoryObj<typeof meta>;
```

- [ ] **Step 2: Render a representative schema using the local `dslLibrary`**

```tsx
import { createRenderer } from "@openuidev/react-lang";
import { Card, ConfigProvider } from "antd";
import { dslLibrary } from "../genui-lib/dslLibrary";

const Renderer = createRenderer(dslLibrary);

const exampleNode = {
  type: "VLayout",
  children: [
    {
      type: "Card",
      title: "Quarterly performance",
      children: [
        { type: "Text", text: "This story verifies standalone DSL rendering." },
        { type: "Button", text: "Open report", variant: "primary" }
      ]
    }
  ]
};

export const Overview: Story = {
  render: () => (
    <ConfigProvider>
      <Card style={{ margin: 24 }}>
        <Renderer node={exampleNode} />
      </Card>
    </ConfigProvider>
  ),
};
```

- [ ] **Step 3: Expand the example payload to include one table or chart component if the renderer contract is straightforward**

```tsx
{
  type: "Table",
  columns: [
    { key: "region", title: "Region", dataIndex: "region" },
    { key: "revenue", title: "Revenue", dataIndex: "revenue" }
  ],
  dataSource: [
    { key: "na", region: "North America", revenue: "$1.2M" },
    { key: "eu", region: "Europe", revenue: "$860K" }
  ]
}
```

- [ ] **Step 4: Run Storybook build for the package**

Run: `pnpm --filter @openuidev/react-ui-dsl build:storybook`
Expected: PASS with a generated static Storybook output and the `DSL/Overview` story in the index.

### Task 3: Verify developer workflow in the isolated package

**Files:**
- Inspect: `packages/react-ui-dsl/package.json`
- Inspect: `packages/react-ui-dsl/.storybook/main.ts`
- Inspect: `packages/react-ui-dsl/src/stories/DslLibrary.stories.tsx`

- [ ] **Step 1: Start Storybook locally to verify the package can boot independently**

Run: `pnpm --filter @openuidev/react-ui-dsl storybook -- --ci`
Expected: Storybook starts on port `6007` without requiring the `react-ui` package Storybook.

- [ ] **Step 2: Check that the story renders the intended standalone surface**

Expected:
- Story sidebar contains `DSL/Overview`
- The page renders the overview card and sample DSL content
- No missing-module or missing-style runtime errors appear in startup logs

- [ ] **Step 3: Commit the isolated Storybook setup**

```bash
git add packages/react-ui-dsl/package.json packages/react-ui-dsl/.storybook packages/react-ui-dsl/src/stories/DslLibrary.stories.tsx docs/superpowers/plans/2026-04-18-react-ui-dsl-storybook.md
git commit -m "feat(react-ui-dsl): add standalone storybook overview"
```
