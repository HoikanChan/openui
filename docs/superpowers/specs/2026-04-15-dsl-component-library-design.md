---
title: DSL Component Library Replacement Design
date: 2026-04-15
status: approved
---

# DSL Component Library Replacement Design

## Background

The project currently has a UI component library in `packages/react-ui` with two layers:

- `src/components/` — raw React UI components (Button, Card, Table, etc.) using Radix UI + SCSS
- `src/genui-lib/` — DSL adapter layer that wraps components with `defineComponent` + Zod schemas for the `react-lang` runtime

The goal is to replace both layers with the company's own enterprise DSL component library, while keeping the `react-lang` runtime (`defineComponent`, `createLibrary`, `useTriggerAction`, etc.) intact.

## Decision

**Approach: New isolated package** (`packages/react-ui-dsl`)

Build a new package in the monorepo containing the company's components and a fresh DSL adapter layer. This allows development in complete isolation from the existing `react-ui` package, with zero risk to existing functionality. Once complete, consumers swap their import from `@openuidev/react-ui` to `@yourcompany/react-ui-dsl`.

Rejected alternatives:
- **Big Bang replacement**: Too risky — everything breaks at once, hard to verify incrementally.
- **Incremental in-place replacement**: Cleaner than Big Bang but still introduces a long transition period with mixed old/new state in the same package.

## Component DSL Format

The company's DSL components follow a consistent interface shape:

```ts
interface ComponentDSL {
  type: string;           // discriminator (e.g. 'button', 'text', 'gaugeChart')
  properties?: { ... };  // component-specific config
  style?: CSSProperties; // optional inline styles
  actions?: Action[];    // event handlers (opaque, handled internally by React component)
  data?: { ... };        // data input for charts and data-driven components
}
```

Children are expressed as `DSL[]` arrays inside `data.content.children` or `properties` depending on the component.

## Package Structure

```
packages/react-ui-dsl/
├── src/
│   ├── components/              # Company React components (copied in as source)
│   │   ├── Button/
│   │   ├── Text/
│   │   ├── Select/
│   │   ├── GaugeChart/
│   │   ├── TimeLine/
│   │   └── ...
│   ├── genui-lib/               # DSL adapter layer
│   │   ├── Button/
│   │   │   ├── index.tsx        # defineComponent wrapper
│   │   │   └── schema.ts        # Zod schema
│   │   ├── Text/
│   │   ├── Select/
│   │   ├── GaugeChart/
│   │   ├── TimeLine/
│   │   └── dslLibrary.tsx       # createLibrary registration entry point
│   └── styles/                  # Company styles (copied in as needed)
├── package.json
└── tsconfig.json
```

## Schema Pattern

Each component's Zod schema mirrors the TypeScript DSL interface directly. The nested `properties` structure is preserved rather than flattened.

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

`actions` is typed as `z.array(z.any())` — the company React component handles action execution internally. No integration with `react-lang`'s `useTriggerAction` at this stage (deferred for future iteration).

`style` is typed as `z.record(z.string(), z.any())` to accept arbitrary CSSProperties.

## Component Wrapper Pattern

Each `genui-lib` wrapper uses `defineComponent` from `@openuidev/react-lang` and passes props directly to the underlying React component with minimal transformation:

```tsx
// genui-lib/Button/index.tsx
"use client"

import { defineComponent } from "@openuidev/react-lang"
import { YourButton } from "../../components/Button"
import { ButtonSchema } from "./schema"

export const Button = defineComponent({
  name: "Button",
  props: ButtonSchema,
  description: "Clickable button",
  component: ({ props }) => (
    <YourButton
      properties={props.properties}
      style={props.style}
      actions={props.actions}
    />
  ),
})
```

For components with recursive children (e.g. TimeLine), `useRenderNode` from `react-lang` is used to render the `DSL[]` children array.

## Library Registration

```tsx
// genui-lib/dslLibrary.tsx
"use client"

import { createLibrary } from "@openuidev/react-lang"
import { Button } from "./Button"
import { Text } from "./Text"
import { GaugeChart } from "./GaugeChart"
import { TimeLine } from "./TimeLine"
// ... other components

export const dslLibrary = createLibrary({
  root: "Stack",   // top-level layout component
  components: [
    Button,
    Text,
    GaugeChart,
    TimeLine,
    // ...
  ],
})
```

## Styles

The company's components bring their own styles. The existing `react-ui` SCSS is not reused. Component-specific SCSS files are copied alongside their React component source. A top-level `styles/index.scss` imports all component stylesheets.

## package.json

```json
{
  "name": "@yourcompany/react-ui-dsl",
  "type": "module",
  "peerDependencies": {
    "@openuidev/react-lang": "workspace:^",
    "react": ">=19.0.0",
    "react-dom": ">=19.0.0"
  }
}
```

## Out of Scope (Deferred)

- **`actions` integration with `react-lang`**: The company's `Action[]` type will eventually be mapped to `react-lang`'s `ActionPlan` (ToAssistant, Run, Set, OpenUrl). Deferred to a follow-up iteration.
- **`openuiLibrary` / `componentGroups` / prompt notes**: AI prompt guidance (component groups, usage notes, examples) is not part of this spec. Add once the component set is stable.
- **Storybook / tests**: Out of scope for initial implementation.
