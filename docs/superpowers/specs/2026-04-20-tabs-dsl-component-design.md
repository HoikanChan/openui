# Tabs DSL Component Design

**Date:** 2026-04-20
**Package:** `packages/react-ui-dsl`
**Status:** Approved

## Overview

Add a `Tabs` DSL component to `packages/react-ui-dsl/src/genui-lib/`, based on antd's `<Tabs>`, following the existing Card/TimeLine pattern (view layer separated from defineComponent logic). Includes streaming-aware behavior: skeleton screens and auto tab-switching during AI content generation.

## Schema

```ts
// schema.ts
TabItemSchema = z.object({
  value: z.string(),       // unique identifier for the tab
  label: z.string(),       // display text shown on the tab button
  content: z.array(z.any()),
})

TabsSchema = z.object({
  items: z.array(TabItemSchema),
  style: z.record(z.string(), z.any()).optional(),
})
```

`label` replaces the `trigger` naming used in `react-ui/genui-lib/Tabs` — it is clearer and maps directly to antd's `items[].label`.

## File Structure

```
packages/react-ui-dsl/src/genui-lib/Tabs/
  index.tsx                  — defineComponent: streaming state logic + schema → TabView mapping
  schema.ts                  — TabItemSchema + TabsSchema (zod)
  view/
    index.tsx                — TabView: pure render using antd <Tabs>, skeleton when loading
  stories/
    index.stories.tsx
    index.stories.test.tsx
```

## Architecture

### index.tsx — Streaming Logic

`defineComponent` hosts all streaming-aware state:

- `activeTab: string` — controlled active tab value
- `userHasInteracted: React.useRef<boolean>` — set to `true` on manual tab click; disables auto-switching
- `prevContentSizes: React.useRef<Record<string, number>>` — tracks `JSON.stringify(content).length` per tab each render; auto-switches to the tab whose content grew most recently

Two `useEffect` hooks mirror the pattern in `react-ui/genui-lib/Tabs/index.tsx`:
1. Initialize `activeTab` to first item's `value` when items arrive
2. On every render (no dep array), scan `prevContentSizes` for growth and switch if `!userHasInteracted.current`

Maps each item to `TabView` props, adding `loading: item.content.length === 0`.

### view/index.tsx — Pure Render

`TabView` is a stateless component:

```ts
type TabViewItem = {
  value: string
  label: string
  children: ReactNode
  loading: boolean
}

type TabViewProps = {
  items: TabViewItem[]
  activeTab: string
  onTabChange: (value: string) => void
  style?: CSSProperties
}
```

Uses antd `<Tabs>` with the `items` prop API:
- When `loading === true`: renders `<Skeleton active paragraph={{ rows: 3 }} />`
- When `loading === false`: renders the real `children`

No local state. Receives `activeTab` and `onTabChange` from parent.

### stories/index.stories.tsx

Renders `TabView` directly (same pattern as Card stories) with static `items` — no DSL runtime needed. Includes at least:
- Default: two tabs, both with content
- Loading: one tab with content, one with `loading: true`

## Registration

Add `Tabs` to `genui-lib/dslLibrary.tsx`:

```ts
import { Tabs } from "./Tabs";
// ...
components: [...existingComponents, Tabs]
```

## Constraints

- antd `<Tabs>` is already a dependency — no new packages needed
- Schema field is `label` (not `trigger`) to align with antd naming and improve readability
- `TabItem` is not exposed as a separate `defineComponent` (unlike `react-ui`) — the DSL schema embeds items inline in `TabsSchema`
- No CSS module needed — antd handles all tab styling
