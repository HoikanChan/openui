# react-ui-dsl Package Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a new `packages/react-ui-dsl` monorepo package that wraps the company's enterprise React components in the `react-lang` DSL runtime using Zod schemas, so consumers can swap from `@openuidev/react-ui` to the new package.

**Architecture:** New isolated package with two layers — `src/components/` (company React component placeholders, to be replaced with actual source) and `src/genui-lib/` (DSL adapter layer using `defineComponent` + Zod). The `react-lang` runtime (`defineComponent`, `createLibrary`, `useRenderNode`) is kept as-is. `actions` on components are passed through transparently — no integration with `useTriggerAction` in this iteration.

**Tech Stack:** React 19, TypeScript, Zod v4, `@openuidev/react-lang` (workspace), ECharts (for chart components), tsdown (build), pnpm workspaces.

---

## File Map

**Created:**
- `packages/react-ui-dsl/package.json`
- `packages/react-ui-dsl/tsconfig.json`
- `packages/react-ui-dsl/tsdown.config.ts`
- `packages/react-ui-dsl/src/index.ts`
- `packages/react-ui-dsl/src/shared/style.ts` — shared Zod `StyleSchema`
- `packages/react-ui-dsl/src/components/HLayout/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/VLayout/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/GridLayout/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/Text/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/Image/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/Link/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/Button/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/Card/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/List/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/Select/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/Form/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/Table/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/Charts/PieChart/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/Charts/LineChart/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/Charts/BarChart/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/Charts/GaugeChart/index.tsx` — placeholder
- `packages/react-ui-dsl/src/components/TimeLine/index.tsx` — placeholder
- `packages/react-ui-dsl/src/genui-lib/HLayout/schema.ts`
- `packages/react-ui-dsl/src/genui-lib/HLayout/index.tsx`
- `packages/react-ui-dsl/src/genui-lib/VLayout/schema.ts`
- `packages/react-ui-dsl/src/genui-lib/VLayout/index.tsx`
- `packages/react-ui-dsl/src/genui-lib/GridLayout/schema.ts`
- `packages/react-ui-dsl/src/genui-lib/GridLayout/index.tsx`
- `packages/react-ui-dsl/src/genui-lib/Text/schema.ts`
- `packages/react-ui-dsl/src/genui-lib/Text/index.tsx`
- `packages/react-ui-dsl/src/genui-lib/Image/schema.ts`
- `packages/react-ui-dsl/src/genui-lib/Image/index.tsx`
- `packages/react-ui-dsl/src/genui-lib/Link/schema.ts`
- `packages/react-ui-dsl/src/genui-lib/Link/index.tsx`
- `packages/react-ui-dsl/src/genui-lib/Button/schema.ts`
- `packages/react-ui-dsl/src/genui-lib/Button/index.tsx`
- `packages/react-ui-dsl/src/genui-lib/Card/schema.ts`
- `packages/react-ui-dsl/src/genui-lib/Card/index.tsx`
- `packages/react-ui-dsl/src/genui-lib/List/schema.ts`
- `packages/react-ui-dsl/src/genui-lib/List/index.tsx`
- `packages/react-ui-dsl/src/genui-lib/Select/schema.ts`
- `packages/react-ui-dsl/src/genui-lib/Select/index.tsx`
- `packages/react-ui-dsl/src/genui-lib/Form/schema.ts`
- `packages/react-ui-dsl/src/genui-lib/Form/index.tsx`
- `packages/react-ui-dsl/src/genui-lib/Table/schema.ts`
- `packages/react-ui-dsl/src/genui-lib/Table/index.tsx`
- `packages/react-ui-dsl/src/genui-lib/Charts/schema.ts`
- `packages/react-ui-dsl/src/genui-lib/Charts/index.tsx`
- `packages/react-ui-dsl/src/genui-lib/TimeLine/schema.ts`
- `packages/react-ui-dsl/src/genui-lib/TimeLine/index.tsx`
- `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`
- `packages/react-ui-dsl/src/genui-lib/index.ts`

---

## Task 1: Package Scaffolding

**Files:**
- Create: `packages/react-ui-dsl/package.json`
- Create: `packages/react-ui-dsl/tsconfig.json`
- Create: `packages/react-ui-dsl/tsdown.config.ts`
- Create: `packages/react-ui-dsl/src/index.ts`

- [ ] **Step 1: Create `packages/react-ui-dsl/package.json`**

```json
{
  "type": "module",
  "name": "@company/react-ui-dsl",
  "version": "0.1.0",
  "description": "Company DSL component library for react-lang runtime",
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
    },
    "./genui-lib": {
      "import": {
        "types": "./dist/genui-lib/index.d.mts",
        "default": "./dist/genui-lib/index.mjs"
      },
      "require": {
        "types": "./dist/genui-lib/index.d.cts",
        "default": "./dist/genui-lib/index.cjs"
      }
    }
  },
  "sideEffects": ["*.css", "*.scss"],
  "files": ["dist"],
  "scripts": {
    "build": "rm -rf dist && pnpm build:tsc && pnpm build:cjs",
    "build:tsc": "tsc -p . || true",
    "build:cjs": "tsdown",
    "typecheck": "tsc --noEmit"
  },
  "peerDependencies": {
    "@openuidev/react-lang": "workspace:^",
    "echarts": "^5.0.0",
    "react": ">=19.0.0",
    "react-dom": ">=19.0.0"
  },
  "dependencies": {
    "zod": "^4.3.6"
  },
  "devDependencies": {
    "@types/react": ">=19.0.0",
    "@types/react-dom": ">=19.0.0",
    "tsdown": "^0.12.0",
    "typescript": "^5.0.0"
  }
}
```

> **Note:** Change `@company/react-ui-dsl` to your actual package name.

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

- [ ] **Step 3: Create `packages/react-ui-dsl/tsdown.config.ts`**

```ts
import { defineConfig } from "tsdown";

const shared = {
  dts: false,
  sourcemap: true,
  target: "es2022",
  outDir: "dist",
  clean: false,
  deps: {
    neverBundle: [/^[^./]/, /\.scss$/, /\.css$/],
  },
} satisfies Parameters<typeof defineConfig>[0];

export default defineConfig([
  // Main index — CJS
  { ...shared, format: ["cjs"], dts: true, entry: { index: "src/index.ts" } },
  // Main index — ESM
  { ...shared, format: ["esm"], dts: true, entry: { index: "src/index.ts" } },
  // genui-lib — CJS
  {
    ...shared,
    format: ["cjs"],
    dts: true,
    outDir: "dist/genui-lib",
    entry: { index: "src/genui-lib/index.ts" },
  },
  // genui-lib — ESM
  {
    ...shared,
    format: ["esm"],
    dts: true,
    outDir: "dist/genui-lib",
    entry: { index: "src/genui-lib/index.ts" },
  },
]);
```

- [ ] **Step 4: Create `packages/react-ui-dsl/src/index.ts`**

```ts
// Re-export genui-lib as the primary public API
export * from "./genui-lib/index";
```

- [ ] **Step 5: Install dependencies**

Run from repo root:
```bash
pnpm install
```

- [ ] **Step 6: Commit**

```bash
git add packages/react-ui-dsl/
git commit -m "feat(react-ui-dsl): scaffold package structure"
```

---

## Task 2: Shared Style Schema

**Files:**
- Create: `packages/react-ui-dsl/src/shared/style.ts`

- [ ] **Step 1: Create `src/shared/style.ts`**

```ts
import { z } from "zod";

/**
 * Accepts any CSS property bag (CSSProperties).
 * Using record(string, any) keeps Zod lean — runtime validation
 * of individual CSS values is not required.
 */
export const StyleSchema = z.record(z.string(), z.any()).optional();
```

- [ ] **Step 2: Commit**

```bash
git add packages/react-ui-dsl/src/shared/
git commit -m "feat(react-ui-dsl): add shared StyleSchema"
```

---

## Task 3: Layout Components (HLayout, VLayout, GridLayout)

**Files:**
- Create: `packages/react-ui-dsl/src/components/HLayout/index.tsx`
- Create: `packages/react-ui-dsl/src/components/VLayout/index.tsx`
- Create: `packages/react-ui-dsl/src/components/GridLayout/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/HLayout/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/HLayout/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/VLayout/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/VLayout/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/GridLayout/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/GridLayout/index.tsx`

- [ ] **Step 1: Create placeholder `src/components/HLayout/index.tsx`**

```tsx
import type { CSSProperties, ReactNode } from "react";

export interface HLayoutProps {
  properties?: {
    gap?: number;
    wrap?: boolean;
  };
  style?: CSSProperties;
  children?: ReactNode;
  actions?: unknown[];
}

/**
 * PLACEHOLDER — replace this implementation with your company's HLayout component.
 * The interface above must be preserved (or adjust the genui-lib wrapper to match
 * your actual component's prop API).
 */
export function HLayout({ properties, style, children }: HLayoutProps) {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "row",
        gap: properties?.gap != null ? `${properties.gap}px` : undefined,
        flexWrap: properties?.wrap ? "wrap" : undefined,
        ...style,
      }}
    >
      {children}
    </div>
  );
}
```

- [ ] **Step 2: Create placeholder `src/components/VLayout/index.tsx`**

```tsx
import type { CSSProperties, ReactNode } from "react";

export interface VLayoutProps {
  properties?: {
    gap?: number;
  };
  style?: CSSProperties;
  children?: ReactNode;
  actions?: unknown[];
}

/**
 * PLACEHOLDER — replace with your company's VLayout component.
 */
export function VLayout({ properties, style, children }: VLayoutProps) {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: properties?.gap != null ? `${properties.gap}px` : undefined,
        ...style,
      }}
    >
      {children}
    </div>
  );
}
```

- [ ] **Step 3: Create placeholder `src/components/GridLayout/index.tsx`**

```tsx
import type { CSSProperties, ReactNode } from "react";

export interface GridLayoutProps {
  properties?: {
    columns?: number;
    gap?: number;
    rowGap?: number;
    columnGap?: number;
  };
  style?: CSSProperties;
  children?: ReactNode;
}

/**
 * PLACEHOLDER — replace with your company's GridLayout component.
 */
export function GridLayout({ properties, style, children }: GridLayoutProps) {
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: properties?.columns
          ? `repeat(${properties.columns}, 1fr)`
          : undefined,
        gap: properties?.gap != null ? `${properties.gap}px` : undefined,
        rowGap: properties?.rowGap != null ? `${properties.rowGap}px` : undefined,
        columnGap: properties?.columnGap != null ? `${properties.columnGap}px` : undefined,
        ...style,
      }}
    >
      {children}
    </div>
  );
}
```

- [ ] **Step 4: Create `src/genui-lib/HLayout/schema.ts`**

```ts
import { z } from "zod";
import { StyleSchema } from "../../shared/style";

export const HLayoutSchema = z.object({
  properties: z
    .object({
      gap: z.number().optional(),
      wrap: z.boolean().optional(),
    })
    .optional(),
  children: z.array(z.any()).optional(),
  style: StyleSchema,
  actions: z.array(z.any()).optional(),
});
```

- [ ] **Step 5: Create `src/genui-lib/HLayout/index.tsx`**

```tsx
"use client";

import React from "react";
import { defineComponent } from "@openuidev/react-lang";
import { HLayout as HLayoutComponent } from "../../components/HLayout";
import { HLayoutSchema } from "./schema";

export { HLayoutSchema } from "./schema";

export const HLayout = defineComponent({
  name: "HLayout",
  props: HLayoutSchema,
  description:
    "Horizontal flex container. gap: pixel spacing between items. wrap: allow items to wrap to next line. children: DSL nodes arranged side by side.",
  component: ({ props, renderNode }) => (
    <HLayoutComponent
      properties={props.properties}
      style={props.style as React.CSSProperties}
      actions={props.actions}
    >
      {renderNode(props.children)}
    </HLayoutComponent>
  ),
});
```

- [ ] **Step 6: Create `src/genui-lib/VLayout/schema.ts`**

```ts
import { z } from "zod";
import { StyleSchema } from "../../shared/style";

export const VLayoutSchema = z.object({
  properties: z
    .object({
      gap: z.number().optional(),
    })
    .optional(),
  children: z.array(z.any()).optional(),
  style: StyleSchema,
  actions: z.array(z.any()).optional(),
});
```

- [ ] **Step 7: Create `src/genui-lib/VLayout/index.tsx`**

```tsx
"use client";

import React from "react";
import { defineComponent } from "@openuidev/react-lang";
import { VLayout as VLayoutComponent } from "../../components/VLayout";
import { VLayoutSchema } from "./schema";

export { VLayoutSchema } from "./schema";

export const VLayout = defineComponent({
  name: "VLayout",
  props: VLayoutSchema,
  description:
    "Vertical flex container (default root layout). gap: pixel spacing between items. children: DSL nodes stacked top-to-bottom.",
  component: ({ props, renderNode }) => (
    <VLayoutComponent
      properties={props.properties}
      style={props.style as React.CSSProperties}
      actions={props.actions}
    >
      {renderNode(props.children)}
    </VLayoutComponent>
  ),
});
```

- [ ] **Step 8: Create `src/genui-lib/GridLayout/schema.ts`**

```ts
import { z } from "zod";
import { StyleSchema } from "../../shared/style";

export const GridLayoutSchema = z.object({
  properties: z
    .object({
      columns: z.number().optional(),
      gap: z.number().optional(),
      rowGap: z.number().optional(),
      columnGap: z.number().optional(),
    })
    .optional(),
  children: z.array(z.any()).optional(),
  style: StyleSchema,
});
```

- [ ] **Step 9: Create `src/genui-lib/GridLayout/index.tsx`**

```tsx
"use client";

import React from "react";
import { defineComponent } from "@openuidev/react-lang";
import { GridLayout as GridLayoutComponent } from "../../components/GridLayout";
import { GridLayoutSchema } from "./schema";

export { GridLayoutSchema } from "./schema";

export const GridLayout = defineComponent({
  name: "GridLayout",
  props: GridLayoutSchema,
  description:
    "CSS grid container. columns: number of equal-width columns. gap/rowGap/columnGap: pixel spacing. children: DSL nodes placed in grid cells.",
  component: ({ props, renderNode }) => (
    <GridLayoutComponent
      properties={props.properties}
      style={props.style as React.CSSProperties}
    >
      {renderNode(props.children)}
    </GridLayoutComponent>
  ),
});
```

- [ ] **Step 10: Commit**

```bash
git add packages/react-ui-dsl/src/components/HLayout \
        packages/react-ui-dsl/src/components/VLayout \
        packages/react-ui-dsl/src/components/GridLayout \
        packages/react-ui-dsl/src/genui-lib/HLayout \
        packages/react-ui-dsl/src/genui-lib/VLayout \
        packages/react-ui-dsl/src/genui-lib/GridLayout
git commit -m "feat(react-ui-dsl): add layout components (HLayout, VLayout, GridLayout)"
```

---

## Task 4: Display Components (Text, Image, Link)

**Files:**
- Create: `packages/react-ui-dsl/src/components/Text/index.tsx`
- Create: `packages/react-ui-dsl/src/components/Image/index.tsx`
- Create: `packages/react-ui-dsl/src/components/Link/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Text/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Text/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Image/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Image/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Link/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Link/index.tsx`

- [ ] **Step 1: Create placeholder `src/components/Text/index.tsx`**

```tsx
import type { CSSProperties } from "react";

export interface TextProps {
  properties: {
    type?: "default" | "markdown" | "html";
    content: string;
  };
  style?: CSSProperties;
}

/** PLACEHOLDER — replace with your company's Text component. */
export function Text({ properties, style }: TextProps) {
  if (properties.type === "html") {
    return (
      <div
        style={style}
        dangerouslySetInnerHTML={{ __html: properties.content }}
      />
    );
  }
  return <span style={style}>{properties.content}</span>;
}
```

- [ ] **Step 2: Create placeholder `src/components/Image/index.tsx`**

```tsx
import type { CSSProperties } from "react";

export interface ImageProps {
  properties: {
    type: "url" | "base64" | "svg";
    content: string;
  };
  style?: CSSProperties;
}

/** PLACEHOLDER — replace with your company's Image component. */
export function Image({ properties, style }: ImageProps) {
  if (properties.type === "svg") {
    return (
      <div
        style={style}
        dangerouslySetInnerHTML={{ __html: properties.content }}
      />
    );
  }
  const src =
    properties.type === "base64"
      ? `data:image/png;base64,${properties.content}`
      : properties.content;
  return <img src={src} style={style} alt="" />;
}
```

- [ ] **Step 3: Create placeholder `src/components/Link/index.tsx`**

```tsx
import type { CSSProperties } from "react";

export interface LinkProps {
  properties: {
    href: string;
    text?: string;
    target?: "_self" | "_blank";
    disabled?: boolean;
    download?: string;
  };
  style?: CSSProperties;
}

/** PLACEHOLDER — replace with your company's Link component. */
export function Link({ properties, style }: LinkProps) {
  return (
    <a
      href={properties.disabled ? undefined : properties.href}
      target={properties.target ?? "_self"}
      download={properties.download}
      style={{
        pointerEvents: properties.disabled ? "none" : undefined,
        opacity: properties.disabled ? 0.5 : undefined,
        ...style,
      }}
    >
      {properties.text ?? properties.href}
    </a>
  );
}
```

- [ ] **Step 4: Create `src/genui-lib/Text/schema.ts`**

```ts
import { z } from "zod";
import { StyleSchema } from "../../shared/style";

export const TextSchema = z.object({
  properties: z.object({
    type: z.enum(["default", "markdown", "html"]).optional(),
    content: z.string(),
  }),
  style: StyleSchema,
});
```

- [ ] **Step 5: Create `src/genui-lib/Text/index.tsx`**

```tsx
"use client";

import React from "react";
import { defineComponent } from "@openuidev/react-lang";
import { Text as TextComponent } from "../../components/Text";
import { TextSchema } from "./schema";

export { TextSchema } from "./schema";

export const Text = defineComponent({
  name: "Text",
  props: TextSchema,
  description:
    'Display text content. type: "default" (plain text) | "markdown" (rendered MD) | "html" (raw HTML). content: the text string.',
  component: ({ props }) => (
    <TextComponent
      properties={props.properties as { type?: "default" | "markdown" | "html"; content: string }}
      style={props.style as React.CSSProperties}
    />
  ),
});
```

- [ ] **Step 6: Create `src/genui-lib/Image/schema.ts`**

```ts
import { z } from "zod";
import { StyleSchema } from "../../shared/style";

export const ImageSchema = z.object({
  properties: z.object({
    type: z.enum(["url", "base64", "svg"]),
    content: z.string(),
  }),
  style: StyleSchema,
});
```

- [ ] **Step 7: Create `src/genui-lib/Image/index.tsx`**

```tsx
"use client";

import React from "react";
import { defineComponent } from "@openuidev/react-lang";
import { Image as ImageComponent } from "../../components/Image";
import { ImageSchema } from "./schema";

export { ImageSchema } from "./schema";

export const Image = defineComponent({
  name: "Image",
  props: ImageSchema,
  description:
    'Display an image. type: "url" (HTTP URL) | "base64" (base64-encoded string) | "svg" (raw SVG markup). content: the image data.',
  component: ({ props }) => (
    <ImageComponent
      properties={props.properties as { type: "url" | "base64" | "svg"; content: string }}
      style={props.style as React.CSSProperties}
    />
  ),
});
```

- [ ] **Step 8: Create `src/genui-lib/Link/schema.ts`**

```ts
import { z } from "zod";
import { StyleSchema } from "../../shared/style";

export const LinkSchema = z.object({
  properties: z.object({
    href: z.string(),
    text: z.string().optional(),
    target: z.enum(["_self", "_blank"]).optional(),
    disabled: z.boolean().optional(),
    download: z.string().optional(),
  }),
  style: StyleSchema,
});
```

- [ ] **Step 9: Create `src/genui-lib/Link/index.tsx`**

```tsx
"use client";

import React from "react";
import { defineComponent } from "@openuidev/react-lang";
import { Link as LinkComponent } from "../../components/Link";
import { LinkSchema } from "./schema";

export { LinkSchema } from "./schema";

export const Link = defineComponent({
  name: "Link",
  props: LinkSchema,
  description:
    'Anchor link. href: URL. text: display label (defaults to href). target: "_self" | "_blank". disabled: greys out and disables click. download: triggers file download with given filename.',
  component: ({ props }) => (
    <LinkComponent
      properties={props.properties as any}
      style={props.style as React.CSSProperties}
    />
  ),
});
```

- [ ] **Step 10: Commit**

```bash
git add packages/react-ui-dsl/src/components/Text \
        packages/react-ui-dsl/src/components/Image \
        packages/react-ui-dsl/src/components/Link \
        packages/react-ui-dsl/src/genui-lib/Text \
        packages/react-ui-dsl/src/genui-lib/Image \
        packages/react-ui-dsl/src/genui-lib/Link
git commit -m "feat(react-ui-dsl): add display components (Text, Image, Link)"
```

---

## Task 5: Button Component

**Files:**
- Create: `packages/react-ui-dsl/src/components/Button/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Button/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Button/index.tsx`

- [ ] **Step 1: Create placeholder `src/components/Button/index.tsx`**

```tsx
import type { CSSProperties } from "react";

export interface ButtonProps {
  properties?: {
    status?: "default" | "primary" | "risk";
    disabled?: boolean;
    text?: string;
    type?: "default" | "text";
  };
  style?: CSSProperties;
  actions?: unknown[];
}

/** PLACEHOLDER — replace with your company's Button component. */
export function Button({ properties, style }: ButtonProps) {
  return (
    <button
      disabled={properties?.disabled}
      style={style}
    >
      {properties?.text}
    </button>
  );
}
```

- [ ] **Step 2: Create `src/genui-lib/Button/schema.ts`**

```ts
import { z } from "zod";
import { StyleSchema } from "../../shared/style";

export const ButtonSchema = z.object({
  properties: z
    .object({
      status: z.enum(["default", "primary", "risk"]).optional(),
      disabled: z.boolean().optional(),
      text: z.string().optional(),
      type: z.enum(["default", "text"]).optional(),
    })
    .optional(),
  style: StyleSchema,
  actions: z.array(z.any()).optional(),
});
```

- [ ] **Step 3: Create `src/genui-lib/Button/index.tsx`**

```tsx
"use client";

import React from "react";
import { defineComponent } from "@openuidev/react-lang";
import { Button as ButtonComponent } from "../../components/Button";
import { ButtonSchema } from "./schema";

export { ButtonSchema } from "./schema";

export const Button = defineComponent({
  name: "Button",
  props: ButtonSchema,
  description:
    'Clickable button. properties.status: "default" | "primary" | "risk" (visual style). properties.text: button label. properties.disabled: disables interaction. properties.type: "default" (filled) | "text" (text-only). actions: event handlers (handled by component internally).',
  component: ({ props }) => (
    <ButtonComponent
      properties={props.properties}
      style={props.style as React.CSSProperties}
      actions={props.actions}
    />
  ),
});
```

- [ ] **Step 4: Commit**

```bash
git add packages/react-ui-dsl/src/components/Button \
        packages/react-ui-dsl/src/genui-lib/Button
git commit -m "feat(react-ui-dsl): add Button component"
```

---

## Task 6: Container Components (Card, List)

**Files:**
- Create: `packages/react-ui-dsl/src/components/Card/index.tsx`
- Create: `packages/react-ui-dsl/src/components/List/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Card/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Card/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/List/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/List/index.tsx`

- [ ] **Step 1: Create placeholder `src/components/Card/index.tsx`**

```tsx
import type { CSSProperties, ReactNode } from "react";

export interface CardProps {
  properties?: {
    tag?: string;
    header?: string;
    headerAlign?: "left" | "center" | "right";
  };
  style?: CSSProperties;
  children?: ReactNode;
}

/** PLACEHOLDER — replace with your company's Card component. */
export function Card({ properties, style, children }: CardProps) {
  return (
    <div style={{ border: "1px solid #e5e7eb", borderRadius: 8, padding: 16, ...style }}>
      {properties?.header && (
        <div style={{ textAlign: properties.headerAlign ?? "left", fontWeight: 600, marginBottom: 12 }}>
          {properties.header}
        </div>
      )}
      {children}
    </div>
  );
}
```

- [ ] **Step 2: Create placeholder `src/components/List/index.tsx`**

```tsx
import type { CSSProperties, ReactNode } from "react";

export interface ListProps {
  properties?: {
    header?: string;
    isOrder?: boolean;
  };
  style?: CSSProperties;
  children?: ReactNode;
}

/** PLACEHOLDER — replace with your company's List component. */
export function List({ properties, style, children }: ListProps) {
  const Tag = properties?.isOrder ? "ol" : "ul";
  return (
    <div style={style}>
      {properties?.header && <div style={{ fontWeight: 600, marginBottom: 8 }}>{properties.header}</div>}
      <Tag>{children}</Tag>
    </div>
  );
}
```

- [ ] **Step 3: Create `src/genui-lib/Card/schema.ts`**

```ts
import { z } from "zod";
import { StyleSchema } from "../../shared/style";

export const CardSchema = z.object({
  properties: z
    .object({
      tag: z.string().optional(),
      header: z.string().optional(),
      headerAlign: z.enum(["left", "center", "right"]).optional(),
    })
    .optional(),
  children: z.array(z.any()).optional(),
  style: StyleSchema,
});
```

- [ ] **Step 4: Create `src/genui-lib/Card/index.tsx`**

```tsx
"use client";

import React from "react";
import { defineComponent } from "@openuidev/react-lang";
import { Card as CardComponent } from "../../components/Card";
import { CardSchema } from "./schema";

export { CardSchema } from "./schema";

export const Card = defineComponent({
  name: "Card",
  props: CardSchema,
  description:
    'Styled card container. properties.header: optional title text. properties.headerAlign: "left" | "center" | "right". properties.tag: optional label/badge text. children: DSL content inside the card.',
  component: ({ props, renderNode }) => (
    <CardComponent
      properties={props.properties}
      style={props.style as React.CSSProperties}
    >
      {renderNode(props.children)}
    </CardComponent>
  ),
});
```

- [ ] **Step 5: Create `src/genui-lib/List/schema.ts`**

```ts
import { z } from "zod";
import { StyleSchema } from "../../shared/style";

export const ListSchema = z.object({
  properties: z
    .object({
      header: z.string().optional(),
      isOrder: z.boolean().optional(),
    })
    .optional(),
  children: z.array(z.any()).optional(),
  style: StyleSchema,
});
```

- [ ] **Step 6: Create `src/genui-lib/List/index.tsx`**

```tsx
"use client";

import React from "react";
import { defineComponent } from "@openuidev/react-lang";
import { List as ListComponent } from "../../components/List";
import { ListSchema } from "./schema";

export { ListSchema } from "./schema";

export const List = defineComponent({
  name: "List",
  props: ListSchema,
  description:
    "List container. properties.isOrder: true for ordered (numbered) list, false for unordered (bullets). properties.header: optional section title. children: DSL list item nodes.",
  component: ({ props, renderNode }) => (
    <ListComponent
      properties={props.properties}
      style={props.style as React.CSSProperties}
    >
      {renderNode(props.children)}
    </ListComponent>
  ),
});
```

- [ ] **Step 7: Commit**

```bash
git add packages/react-ui-dsl/src/components/Card \
        packages/react-ui-dsl/src/components/List \
        packages/react-ui-dsl/src/genui-lib/Card \
        packages/react-ui-dsl/src/genui-lib/List
git commit -m "feat(react-ui-dsl): add container components (Card, List)"
```

---

## Task 7: Select Component

**Files:**
- Create: `packages/react-ui-dsl/src/components/Select/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Select/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Select/index.tsx`

- [ ] **Step 1: Create placeholder `src/components/Select/index.tsx`**

```tsx
import type { CSSProperties } from "react";

export interface SelectProps {
  properties: {
    allowClear?: boolean;
    options: { label: string; value: number | string }[];
    defaultValue?: number | string;
  };
  style?: CSSProperties;
}

/** PLACEHOLDER — replace with your company's Select component. */
export function Select({ properties, style }: SelectProps) {
  return (
    <select defaultValue={properties.defaultValue} style={style}>
      {properties.allowClear && <option value="">--</option>}
      {properties.options.map((opt) => (
        <option key={opt.value} value={opt.value}>
          {opt.label}
        </option>
      ))}
    </select>
  );
}
```

- [ ] **Step 2: Create `src/genui-lib/Select/schema.ts`**

```ts
import { z } from "zod";
import { StyleSchema } from "../../shared/style";

export const SelectSchema = z.object({
  properties: z.object({
    allowClear: z.boolean().optional(),
    options: z.array(
      z.object({
        label: z.string(),
        value: z.union([z.number(), z.string()]),
      }),
    ),
    defaultValue: z.union([z.number(), z.string()]).optional(),
  }),
  style: StyleSchema,
});
```

- [ ] **Step 3: Create `src/genui-lib/Select/index.tsx`**

```tsx
"use client";

import React from "react";
import { defineComponent } from "@openuidev/react-lang";
import { Select as SelectComponent } from "../../components/Select";
import { SelectSchema } from "./schema";

export { SelectSchema } from "./schema";

export const Select = defineComponent({
  name: "Select",
  props: SelectSchema,
  description:
    "Dropdown select input. properties.options: array of {label, value} pairs. properties.defaultValue: pre-selected value. properties.allowClear: show a clear/empty option.",
  component: ({ props }) => (
    <SelectComponent
      properties={props.properties as any}
      style={props.style as React.CSSProperties}
    />
  ),
});
```

- [ ] **Step 4: Commit**

```bash
git add packages/react-ui-dsl/src/components/Select \
        packages/react-ui-dsl/src/genui-lib/Select
git commit -m "feat(react-ui-dsl): add Select component"
```

---

## Task 8: Form Component

**Files:**
- Create: `packages/react-ui-dsl/src/components/Form/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Form/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Form/index.tsx`

The Form DSL has an unusual structure: each field definition contains an inline `component: DSL` (a nested DSL node). The genui-lib wrapper must use `renderNode` to render each field's component.

- [ ] **Step 1: Create placeholder `src/components/Form/index.tsx`**

```tsx
import type { CSSProperties, ReactNode } from "react";

export interface FormFieldDef {
  label: string;
  name: string;
  rules?: { required: boolean }[];
  renderedComponent: ReactNode; // pre-rendered by genui-lib wrapper
}

export interface FormProps {
  properties: {
    layout?: "vertical" | "inline" | "horizontal";
    labelAlign?: "left" | "right";
    initialValues?: Record<string, unknown>;
    fields: FormFieldDef[];
  };
  style?: CSSProperties;
}

/** PLACEHOLDER — replace with your company's Form component. */
export function Form({ properties, style }: FormProps) {
  return (
    <form style={style}>
      {properties.fields.map((field) => (
        <div key={field.name} style={{ marginBottom: 16 }}>
          <label>
            {field.rules?.some((r) => r.required) && <span style={{ color: "red" }}>* </span>}
            {field.label}
          </label>
          <div>{field.renderedComponent}</div>
        </div>
      ))}
    </form>
  );
}
```

- [ ] **Step 2: Create `src/genui-lib/Form/schema.ts`**

```ts
import { z } from "zod";
import { StyleSchema } from "../../shared/style";

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
        component: z.any(), // DSL node — rendered via renderNode in the wrapper
      }),
    ),
  }),
  style: StyleSchema,
});
```

- [ ] **Step 3: Create `src/genui-lib/Form/index.tsx`**

```tsx
"use client";

import React from "react";
import { defineComponent } from "@openuidev/react-lang";
import { Form as FormComponent } from "../../components/Form";
import { FormSchema } from "./schema";

export { FormSchema } from "./schema";

export const Form = defineComponent({
  name: "Form",
  props: FormSchema,
  description:
    'Form container. properties.layout: "vertical" | "inline" | "horizontal". properties.labelAlign: "left" | "right". properties.initialValues: default field values keyed by field name. properties.fields: array of {label, name, rules, component} where component is a DSL input node (e.g. Select, Text).',
  component: ({ props, renderNode }) => {
    const rawFields = (props.properties?.fields ?? []) as Array<{
      label: string;
      name: string;
      rules?: { required: boolean }[];
      component: unknown;
    }>;

    const fields = rawFields.map((f) => ({
      label: f.label,
      name: f.name,
      rules: f.rules,
      renderedComponent: renderNode(f.component),
    }));

    return (
      <FormComponent
        properties={{
          layout: props.properties?.layout as "vertical" | "inline" | "horizontal" | undefined,
          labelAlign: props.properties?.labelAlign as "left" | "right" | undefined,
          initialValues: props.properties?.initialValues,
          fields,
        }}
        style={props.style as React.CSSProperties}
      />
    );
  },
});
```

- [ ] **Step 4: Commit**

```bash
git add packages/react-ui-dsl/src/components/Form \
        packages/react-ui-dsl/src/genui-lib/Form
git commit -m "feat(react-ui-dsl): add Form component with inline DSL field rendering"
```

---

## Task 9: Table Component

**Files:**
- Create: `packages/react-ui-dsl/src/components/Table/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Table/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Table/index.tsx`

The `columns[].customized` field is a DSL node for custom cell rendering. The genui-lib wrapper pre-renders it with `renderNode`.

- [ ] **Step 1: Create placeholder `src/components/Table/index.tsx`**

```tsx
import type { CSSProperties, ReactNode } from "react";

export interface TableColumnDef {
  title: string;
  field: string;
  sortable?: boolean;
  filterable?: boolean;
  filterOptions?: string[];
  renderedCustomized?: ReactNode;
  format?: "data" | "dateTime" | "time";
  tooltip?: boolean;
}

export interface TableProps {
  properties: {
    columns: TableColumnDef[];
  };
  style?: CSSProperties;
}

/** PLACEHOLDER — replace with your company's Table component. */
export function Table({ properties, style }: TableProps) {
  return (
    <table style={{ width: "100%", borderCollapse: "collapse", ...style }}>
      <thead>
        <tr>
          {properties.columns.map((col) => (
            <th key={col.field} style={{ borderBottom: "1px solid #e5e7eb", padding: "8px 12px", textAlign: "left" }}>
              {col.title}
            </th>
          ))}
        </tr>
      </thead>
    </table>
  );
}
```

- [ ] **Step 2: Create `src/genui-lib/Table/schema.ts`**

```ts
import { z } from "zod";
import { StyleSchema } from "../../shared/style";

export const TableSchema = z.object({
  properties: z.object({
    columns: z.array(
      z.object({
        title: z.string(),
        field: z.string(),
        sortable: z.boolean().optional(),
        filterable: z.boolean().optional(),
        filterOptions: z.array(z.string()).optional(),
        customized: z.any().optional(), // DSL node for custom cell rendering
        format: z.enum(["data", "dateTime", "time"]).optional(),
        tooltip: z.boolean().optional(),
      }),
    ),
  }),
  style: StyleSchema,
});
```

- [ ] **Step 3: Create `src/genui-lib/Table/index.tsx`**

```tsx
"use client";

import React from "react";
import { defineComponent } from "@openuidev/react-lang";
import { Table as TableComponent } from "../../components/Table";
import { TableSchema } from "./schema";

export { TableSchema } from "./schema";

export const Table = defineComponent({
  name: "Table",
  props: TableSchema,
  description:
    "Data table. properties.columns: array of column definitions. Each column: title (header label), field (data key), sortable, filterable, filterOptions, format (\"data\"|\"dateTime\"|\"time\"), tooltip, customized (DSL node for custom cell rendering).",
  component: ({ props, renderNode }) => {
    const rawColumns = (props.properties?.columns ?? []) as Array<{
      title: string;
      field: string;
      sortable?: boolean;
      filterable?: boolean;
      filterOptions?: string[];
      customized?: unknown;
      format?: "data" | "dateTime" | "time";
      tooltip?: boolean;
    }>;

    const columns = rawColumns.map((col) => ({
      ...col,
      renderedCustomized: col.customized ? renderNode(col.customized) : undefined,
    }));

    return (
      <TableComponent
        properties={{ columns }}
        style={props.style as React.CSSProperties}
      />
    );
  },
});
```

- [ ] **Step 4: Commit**

```bash
git add packages/react-ui-dsl/src/components/Table \
        packages/react-ui-dsl/src/genui-lib/Table
git commit -m "feat(react-ui-dsl): add Table component"
```

---

## Task 10: Chart Components (PieChart, LineChart, BarChart, GaugeChart)

All four charts are ECharts-based and follow the same pattern. Properties extend EChartsOption (minus `title`) with a shorthand `title` string.

**Files:**
- Create: `packages/react-ui-dsl/src/components/Charts/PieChart/index.tsx`
- Create: `packages/react-ui-dsl/src/components/Charts/LineChart/index.tsx`
- Create: `packages/react-ui-dsl/src/components/Charts/BarChart/index.tsx`
- Create: `packages/react-ui-dsl/src/components/Charts/GaugeChart/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/Charts/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/Charts/index.tsx`

- [ ] **Step 1: Create placeholder `src/components/Charts/PieChart/index.tsx`**

```tsx
import type { CSSProperties } from "react";

export interface PieChartProps {
  properties: {
    title?: string;
    series?: unknown[];
    [key: string]: unknown; // rest of EChartsOption
  };
  data?: { source: number[][] };
  style?: CSSProperties;
}

/** PLACEHOLDER — replace with your company's PieChart component (ECharts-based). */
export function PieChart({ properties, style }: PieChartProps) {
  return (
    <div style={{ height: 300, background: "#f9fafb", display: "flex", alignItems: "center", justifyContent: "center", ...style }}>
      [PieChart: {properties.title ?? "untitled"}]
    </div>
  );
}
```

- [ ] **Step 2: Create placeholder `src/components/Charts/LineChart/index.tsx`**

```tsx
import type { CSSProperties } from "react";

export interface LineChartProps {
  properties: {
    title?: string;
    series?: unknown[];
    [key: string]: unknown;
  };
  data?: { source: number[][] };
  style?: CSSProperties;
}

/** PLACEHOLDER — replace with your company's LineChart component (ECharts-based). */
export function LineChart({ properties, style }: LineChartProps) {
  return (
    <div style={{ height: 300, background: "#f9fafb", display: "flex", alignItems: "center", justifyContent: "center", ...style }}>
      [LineChart: {properties.title ?? "untitled"}]
    </div>
  );
}
```

- [ ] **Step 3: Create placeholder `src/components/Charts/BarChart/index.tsx`**

```tsx
import type { CSSProperties } from "react";

export interface BarChartProps {
  properties: {
    title?: string;
    series?: unknown[];
    [key: string]: unknown;
  };
  style?: CSSProperties;
}

/** PLACEHOLDER — replace with your company's BarChart component (ECharts-based). */
export function BarChart({ properties, style }: BarChartProps) {
  return (
    <div style={{ height: 300, background: "#f9fafb", display: "flex", alignItems: "center", justifyContent: "center", ...style }}>
      [BarChart: {properties.title ?? "untitled"}]
    </div>
  );
}
```

- [ ] **Step 4: Create placeholder `src/components/Charts/GaugeChart/index.tsx`**

```tsx
import type { CSSProperties } from "react";

export interface GaugeChartProps {
  properties: {
    title?: string;
    series?: unknown[];
    [key: string]: unknown;
  };
  data?: { source: number[][] };
  style?: CSSProperties;
}

/** PLACEHOLDER — replace with your company's GaugeChart component (ECharts-based). */
export function GaugeChart({ properties, style }: GaugeChartProps) {
  return (
    <div style={{ height: 300, background: "#f9fafb", display: "flex", alignItems: "center", justifyContent: "center", ...style }}>
      [GaugeChart: {properties.title ?? "untitled"}]
    </div>
  );
}
```

- [ ] **Step 5: Create `src/genui-lib/Charts/schema.ts`**

```ts
import { z } from "zod";
import { StyleSchema } from "../../shared/style";

// EChartsOption is complex — accept any key-value pairs.
// title is extracted as a top-level shorthand string.
const EChartsPropertiesSchema = z
  .object({
    title: z.string().optional(),
    series: z.array(z.any()).optional(),
  })
  .and(z.record(z.string(), z.any()));

const DataSchema = z
  .object({
    source: z.array(z.array(z.number())),
  })
  .optional();

export const PieChartSchema = z.object({
  properties: EChartsPropertiesSchema,
  data: DataSchema,
  style: StyleSchema,
});

export const LineChartSchema = z.object({
  properties: EChartsPropertiesSchema,
  data: DataSchema,
  style: StyleSchema,
});

export const BarChartSchema = z.object({
  properties: EChartsPropertiesSchema,
  style: StyleSchema,
});

export const GaugeChartSchema = z.object({
  properties: EChartsPropertiesSchema,
  data: DataSchema,
  style: StyleSchema,
});
```

- [ ] **Step 6: Create `src/genui-lib/Charts/index.tsx`**

```tsx
"use client";

import React from "react";
import { defineComponent } from "@openuidev/react-lang";
import { PieChart as PieChartComponent } from "../../components/Charts/PieChart";
import { LineChart as LineChartComponent } from "../../components/Charts/LineChart";
import { BarChart as BarChartComponent } from "../../components/Charts/BarChart";
import { GaugeChart as GaugeChartComponent } from "../../components/Charts/GaugeChart";
import { PieChartSchema, LineChartSchema, BarChartSchema, GaugeChartSchema } from "./schema";

export { PieChartSchema, LineChartSchema, BarChartSchema, GaugeChartSchema } from "./schema";

export const PieChart = defineComponent({
  name: "PieChart",
  props: PieChartSchema,
  description:
    "ECharts pie chart. properties.title: chart heading. properties.series: ECharts PieSeriesOption array. data.source: 2D number array for dataset-driven rendering.",
  component: ({ props }) => (
    <PieChartComponent
      properties={props.properties as any}
      data={props.data as any}
      style={props.style as React.CSSProperties}
    />
  ),
});

export const LineChart = defineComponent({
  name: "LineChart",
  props: LineChartSchema,
  description:
    "ECharts line chart. properties.title: chart heading. properties.series: ECharts LineSeriesOption array. data.source: 2D number array for dataset-driven rendering.",
  component: ({ props }) => (
    <LineChartComponent
      properties={props.properties as any}
      data={props.data as any}
      style={props.style as React.CSSProperties}
    />
  ),
});

export const BarChart = defineComponent({
  name: "BarChart",
  props: BarChartSchema,
  description:
    "ECharts bar chart. properties.title: chart heading. properties.series: ECharts BarSeriesOption array.",
  component: ({ props }) => (
    <BarChartComponent
      properties={props.properties as any}
      style={props.style as React.CSSProperties}
    />
  ),
});

export const GaugeChart = defineComponent({
  name: "GaugeChart",
  props: GaugeChartSchema,
  description:
    "ECharts gauge chart. properties.title: chart heading. properties.series: ECharts GaugeSeriesOption array. data.source: 2D number array.",
  component: ({ props }) => (
    <GaugeChartComponent
      properties={props.properties as any}
      data={props.data as any}
      style={props.style as React.CSSProperties}
    />
  ),
});
```

- [ ] **Step 7: Commit**

```bash
git add packages/react-ui-dsl/src/components/Charts \
        packages/react-ui-dsl/src/genui-lib/Charts
git commit -m "feat(react-ui-dsl): add chart components (PieChart, LineChart, BarChart, GaugeChart)"
```

---

## Task 11: TimeLine Component

**Files:**
- Create: `packages/react-ui-dsl/src/components/TimeLine/index.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/TimeLine/schema.ts`
- Create: `packages/react-ui-dsl/src/genui-lib/TimeLine/index.tsx`

TimeLine items each contain a `content.children: DSL[]` array that must be rendered with `renderNode`.

- [ ] **Step 1: Create placeholder `src/components/TimeLine/index.tsx`**

```tsx
import type { CSSProperties, ReactNode } from "react";

export interface TimeLineItem {
  renderedChildren: ReactNode;
  contentTitle: string;
  iconType: "success" | "error" | "default";
}

export interface TimeLineProps {
  properties?: {
    title?: string;
    id?: string;
  };
  items: TimeLineItem[];
  style?: CSSProperties;
}

const iconColors: Record<string, string> = {
  success: "#22c55e",
  error: "#ef4444",
  default: "#6b7280",
};

/** PLACEHOLDER — replace with your company's TimeLine component. */
export function TimeLine({ properties, items, style }: TimeLineProps) {
  return (
    <div style={style}>
      {properties?.title && <div style={{ fontWeight: 600, marginBottom: 12 }}>{properties.title}</div>}
      {items.map((item, i) => (
        <div key={i} style={{ display: "flex", gap: 12, marginBottom: 16 }}>
          <div
            style={{
              width: 12,
              height: 12,
              borderRadius: "50%",
              background: iconColors[item.iconType],
              marginTop: 4,
              flexShrink: 0,
            }}
          />
          <div>
            <div style={{ fontWeight: 500 }}>{item.contentTitle}</div>
            <div>{item.renderedChildren}</div>
          </div>
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 2: Create `src/genui-lib/TimeLine/schema.ts`**

```ts
import { z } from "zod";
import { StyleSchema } from "../../shared/style";

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
  style: StyleSchema,
});
```

- [ ] **Step 3: Create `src/genui-lib/TimeLine/index.tsx`**

```tsx
"use client";

import React from "react";
import { defineComponent } from "@openuidev/react-lang";
import { TimeLine as TimeLineComponent } from "../../components/TimeLine";
import { TimeLineSchema } from "./schema";

export { TimeLineSchema } from "./schema";

export const TimeLine = defineComponent({
  name: "TimeLine",
  props: TimeLineSchema,
  description:
    'Timeline. properties.title: optional section heading. data: array of timeline items — each has content.title (item heading), content.children (DSL nodes shown below the heading), and iconType ("success" | "error" | "default" — controls the indicator color).',
  component: ({ props, renderNode }) => {
    const rawData = (props.data ?? []) as Array<{
      content: { title: string; children: unknown[] };
      iconType: "success" | "error" | "default";
    }>;

    const items = rawData.map((item) => ({
      contentTitle: item.content.title,
      renderedChildren: renderNode(item.content.children),
      iconType: item.iconType,
    }));

    return (
      <TimeLineComponent
        properties={props.properties}
        items={items}
        style={props.style as React.CSSProperties}
      />
    );
  },
});
```

- [ ] **Step 4: Commit**

```bash
git add packages/react-ui-dsl/src/components/TimeLine \
        packages/react-ui-dsl/src/genui-lib/TimeLine
git commit -m "feat(react-ui-dsl): add TimeLine component"
```

---

## Task 12: Library Registration & Exports

**Files:**
- Create: `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`
- Create: `packages/react-ui-dsl/src/genui-lib/index.ts`

- [ ] **Step 1: Create `src/genui-lib/dslLibrary.tsx`**

```tsx
"use client";

import { createLibrary } from "@openuidev/react-lang";

import { HLayout } from "./HLayout";
import { VLayout } from "./VLayout";
import { GridLayout } from "./GridLayout";
import { Text } from "./Text";
import { Image } from "./Image";
import { Link } from "./Link";
import { Button } from "./Button";
import { Card } from "./Card";
import { List } from "./List";
import { Select } from "./Select";
import { Form } from "./Form";
import { Table } from "./Table";
import { PieChart, LineChart, BarChart, GaugeChart } from "./Charts";
import { TimeLine } from "./TimeLine";

export const dslLibrary = createLibrary({
  root: "VLayout",
  components: [
    VLayout,
    HLayout,
    GridLayout,
    Text,
    Image,
    Link,
    Button,
    Card,
    List,
    Select,
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

- [ ] **Step 2: Create `src/genui-lib/index.ts`**

```ts
export { dslLibrary } from "./dslLibrary";

// Layout
export { HLayout, HLayoutSchema } from "./HLayout";
export { VLayout, VLayoutSchema } from "./VLayout";
export { GridLayout, GridLayoutSchema } from "./GridLayout";

// Display
export { Text, TextSchema } from "./Text";
export { Image, ImageSchema } from "./Image";
export { Link, LinkSchema } from "./Link";
export { Button, ButtonSchema } from "./Button";

// Containers
export { Card, CardSchema } from "./Card";
export { List, ListSchema } from "./List";

// Forms
export { Select, SelectSchema } from "./Select";
export { Form, FormSchema } from "./Form";

// Data
export { Table, TableSchema } from "./Table";

// Charts
export {
  PieChart,
  PieChartSchema,
  LineChart,
  LineChartSchema,
  BarChart,
  BarChartSchema,
  GaugeChart,
  GaugeChartSchema,
} from "./Charts";

// Timeline
export { TimeLine, TimeLineSchema } from "./TimeLine";
```

- [ ] **Step 3: Commit**

```bash
git add packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx \
        packages/react-ui-dsl/src/genui-lib/index.ts \
        packages/react-ui-dsl/src/index.ts
git commit -m "feat(react-ui-dsl): register all components in dslLibrary and export index"
```

---

## Task 13: Build Verification

- [ ] **Step 1: Install dependencies from repo root**

```bash
pnpm install
```

Expected: no errors, `@company/react-ui-dsl` resolved in workspace.

- [ ] **Step 2: Type-check the package**

```bash
cd packages/react-ui-dsl
pnpm typecheck
```

Expected: exits 0 (or only warnings from placeholder `as any` casts — these are intentional until real components are swapped in).

- [ ] **Step 3: Build the package**

```bash
pnpm build
```

Expected: `dist/index.mjs`, `dist/index.cjs`, `dist/genui-lib/index.mjs`, `dist/genui-lib/index.cjs` created. No errors.

- [ ] **Step 4: Verify `dslLibrary` exports resolve**

Create a temporary test file `packages/react-ui-dsl/src/smoke.ts` (delete after):

```ts
import { dslLibrary } from "./genui-lib/dslLibrary";

const names = Object.keys(dslLibrary.components);
console.log("Registered components:", names.join(", "));
// Expected output includes: VLayout, HLayout, GridLayout, Text, Image, Link,
//   Button, Card, List, Select, Form, Table, PieChart, LineChart, BarChart,
//   GaugeChart, TimeLine
```

Run:
```bash
npx tsx src/smoke.ts
```

Expected: prints all 17 component names. Delete `smoke.ts` afterwards.

- [ ] **Step 5: Final commit**

```bash
git add packages/react-ui-dsl/
git commit -m "feat(react-ui-dsl): build verified — all 17 components registered"
```

---

## Post-Implementation Notes

**Swapping in your real components:**
For each `src/components/<Name>/index.tsx`, replace the placeholder implementation with your company's actual React component. The genui-lib wrapper (`src/genui-lib/<Name>/index.tsx`) calls the component with `properties`, `style`, `actions`, and (for containers) `children` — if your real component's prop API differs from this, update the wrapper's `component: ({ props }) => (...)` body accordingly.

**Consuming the library:**
```tsx
import { dslLibrary } from "@company/react-ui-dsl/genui-lib";
import { Renderer } from "@openuidev/react-lang";

<Renderer library={dslLibrary} value={dslNode} />
```

**actions integration (deferred):**
When ready to wire actions to `react-lang`'s `useTriggerAction`, update `src/genui-lib/Button/index.tsx` (and VLayout, which also has `actions`). The pattern from `packages/react-ui/src/genui-lib/Button/index.tsx` in the existing package shows how to call `useTriggerAction` on click.
