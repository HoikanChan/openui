# Separator DSL Component Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `Separator` component to `packages/react-ui-dsl` that matches `packages/react-ui` schema and styling.

**Architecture:** `react-ui-dsl` will define a thin `defineComponent` wrapper and a matching zod schema, both aligned with `react-ui`. Rendering delegates to the shared `@openuidev/react-ui` `Separator`, while `dslLibrary` and package exports expose the new component to prompt generation and runtime parsing.

**Tech Stack:** React, `@openuidev/react-lang`, `@openuidev/react-ui`, zod, Vitest, Testing Library

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `packages/react-ui-dsl/src/genui-lib/Separator/schema.ts` | Create | DSL zod schema matching `react-ui` separator props |
| `packages/react-ui-dsl/src/genui-lib/Separator/index.tsx` | Create | `defineComponent` wrapper around `@openuidev/react-ui` `Separator` |
| `packages/react-ui-dsl/src/genui-lib/Separator/index.test.tsx` | Create | Red-green render coverage for default and vertical orientation |
| `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx` | Modify | Register `Separator` in the DSL library |
| `packages/react-ui-dsl/src/genui-lib/dslLibrary.test.ts` | Modify | Assert spec/schema surfaces now include `Separator` |
| `packages/react-ui-dsl/src/index.ts` | Modify | Re-export `Separator` from the package root |

---

### Task 1: Add failing library-surface test

**Files:**
- Modify: `packages/react-ui-dsl/src/genui-lib/dslLibrary.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
it("includes Separator in spec and json schema output", () => {
  const spec = dslLibrary.toSpec();
  const schema = dslLibrary.toJSONSchema();
  const defs = ("$defs" in schema ? schema.$defs : {}) as Record<string, { properties?: Record<string, unknown> }>;

  expect(spec.components.Separator.signature).toContain("Separator(");
  expect(spec.components.Separator.signature).toContain("orientation?:");
  expect(spec.components.Separator.signature).toContain("decorative?:");
  expect(defs.Separator?.properties).toMatchObject({
    orientation: expect.anything(),
    decorative: expect.anything(),
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm --filter @openuidev/react-ui-dsl test -- src/genui-lib/dslLibrary.test.ts`
Expected: FAIL because `Separator` is not registered yet.

- [ ] **Step 3: Write minimal implementation**

Register and export `Separator` after the render test exists.

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm --filter @openuidev/react-ui-dsl test -- src/genui-lib/dslLibrary.test.ts`
Expected: PASS

---

### Task 2: Add failing render test

**Files:**
- Create: `packages/react-ui-dsl/src/genui-lib/Separator/index.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render } from "@testing-library/react";
import { Renderer } from "@openuidev/react-lang";
import { describe, expect, it } from "vitest";
import { dslLibrary } from "../dslLibrary";

describe("Separator DSL component", () => {
  it("renders the shared separator class by default", () => {
    const { container } = render(<Renderer library={dslLibrary} response={`root = VLayout([rule])\nrule = Separator()`} dataModel={{}} />);
    expect(container.querySelector(".openui-separator")).not.toBeNull();
  });

  it("passes vertical orientation to the rendered separator", () => {
    const { container } = render(
      <Renderer
        library={dslLibrary}
        response={`root = VLayout([rule])\nrule = Separator({ orientation: "vertical" })`}
        dataModel={{}}
      />,
    );
    expect(container.querySelector('.openui-separator[data-orientation="vertical"]')).not.toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm --filter @openuidev/react-ui-dsl test -- src/genui-lib/Separator/index.test.tsx`
Expected: FAIL because the component files do not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `schema.ts` and `index.tsx` as a thin wrapper over `@openuidev/react-ui`.

- [ ] **Step 4: Run test to verify it passes**

Run: `pnpm --filter @openuidev/react-ui-dsl test -- src/genui-lib/Separator/index.test.tsx`
Expected: PASS

---

### Task 3: Wire package exports and library registration

**Files:**
- Modify: `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`
- Modify: `packages/react-ui-dsl/src/index.ts`

- [ ] **Step 1: Add the component to the library and root exports**

```ts
import { Separator } from "./Separator";
```

Add `Separator` to the `components` array in `dslLibrary.tsx`, and add:

```ts
export { Separator } from "./genui-lib/Separator";
```

to `src/index.ts`.

- [ ] **Step 2: Run focused tests**

Run: `pnpm --filter @openuidev/react-ui-dsl test -- src/genui-lib/dslLibrary.test.ts src/genui-lib/Separator/index.test.tsx`
Expected: PASS

---

### Task 4: Final verification

**Files:**
- Test: `packages/react-ui-dsl/src/genui-lib/dslLibrary.test.ts`
- Test: `packages/react-ui-dsl/src/genui-lib/Separator/index.test.tsx`

- [ ] **Step 1: Run package-level verification**

Run: `pnpm --filter @openuidev/react-ui-dsl test -- src/genui-lib/dslLibrary.test.ts src/genui-lib/Separator/index.test.tsx`
Expected: PASS

- [ ] **Step 2: Run CI-shaped verification for the package if time permits**

Run: `pnpm --filter @openuidev/react-ui-dsl run test`
Expected: PASS without regressions in existing DSL components.
