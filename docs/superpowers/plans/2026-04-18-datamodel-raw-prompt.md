# DataModel Raw Prompt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the unused typed `DataModelSpec.fields` API with a `raw` JSON mode, wire `generatePrompt` to render a `## Data Model` fenced JSON block, and thread `dataModel` through the demo server so the LLM sees the host data shape in its system prompt.

**Architecture:** All prompt-generation logic lives in `lang-core/src/parser/prompt.ts`. The `raw` field on `DataModelSpec` is the only new public surface. The demo server calls `dslLibrary.prompt({ dataModel: { raw } })` per request — no new abstraction layers.

**Tech Stack:** TypeScript, Vitest (tests), Express (demo server), `@openuidev/lang-core`, `@openuidev/react-ui-dsl`

---

### Task 1: Update prompt tests to cover the new `raw` API

**Files:**
- Modify: `packages/lang-core/src/parser/__tests__/prompt.test.ts`

- [ ] **Step 1: Replace the existing dataModel test block**

Open `packages/lang-core/src/parser/__tests__/prompt.test.ts` and replace the entire `describe("generatePrompt dataModel support", ...)` block with:

```ts
describe("generatePrompt dataModel support", () => {
  const baseSpec: PromptSpec = {
    root: "Root",
    components: {
      Root: { signature: "Root(children: Component[])", description: "Root container" },
      Label: { signature: "Label(text: string)", description: "Simple text label" },
    },
  };

  it("omits the Data Model section when dataModel is absent", () => {
    const prompt = generatePrompt(baseSpec);
    expect(prompt).not.toContain("## Data Model");
  });

  it("omits the Data Model section when dataModel has no raw field", () => {
    const prompt = generatePrompt({ ...baseSpec, dataModel: {} });
    expect(prompt).not.toContain("## Data Model");
  });

  it("omits the Data Model section when dataModel.raw is empty", () => {
    const prompt = generatePrompt({ ...baseSpec, dataModel: { raw: {} } });
    expect(prompt).not.toContain("## Data Model");
  });

  it("renders a Data Model section with raw JSON", () => {
    const raw = {
      sales: [{ quarter: "Q1", revenue: 100 }],
      user: { name: "Alice" },
      totalRevenue: 220,
    };
    const prompt = generatePrompt({ ...baseSpec, dataModel: { raw } });
    expect(prompt).toContain("## Data Model");
    expect(prompt).toContain(JSON.stringify(raw, null, 2));
    expect(prompt).toContain("Use `data.<field>` to read host data.");
    expect(prompt).toContain("Array pluck works on arrays: `data.sales.revenue`");
  });

  it("includes optional description alongside raw JSON", () => {
    const prompt = generatePrompt({
      ...baseSpec,
      dataModel: { description: "Business data.", raw: { total: 42 } },
    });
    expect(prompt).toContain("Business data.");
    expect(prompt).toContain("## Data Model");
  });
});
```

- [ ] **Step 2: Run the tests — verify the new cases fail**

```bash
cd packages/lang-core && npm test -- --reporter=verbose 2>&1 | grep -A3 "dataModel"
```

Expected: the three new cases fail (`omits when no raw field`, `omits when raw is empty`, `renders raw JSON`). The existing `omits when absent` test may still pass.

---

### Task 2: Update `DataModelSpec` and rewrite `dataModelSection()` in `prompt.ts`

**Files:**
- Modify: `packages/lang-core/src/parser/prompt.ts`

- [ ] **Step 1: Delete `DataModelFieldSpec` and update `DataModelSpec`**

In `packages/lang-core/src/parser/prompt.ts`, replace lines 28–36:

```ts
// DELETE this interface entirely:
export interface DataModelFieldSpec {
  type: "array" | "object" | "scalar";
  description?: string;
}

// CHANGE DataModelSpec to:
export interface DataModelSpec {
  description?: string;
  raw?: Record<string, unknown>;
}
```

- [ ] **Step 2: Rewrite `dataModelSection()`**

Replace the existing `dataModelSection` function (around line 598) with:

```ts
function dataModelSection(dataModel: DataModelSpec): string {
  const lines = ["## Data Model", ""];

  if (dataModel.description) {
    lines.push(dataModel.description, "");
  }

  lines.push("The following host data is available via `data.<field>`:", "");
  lines.push("```json");
  lines.push(JSON.stringify(dataModel.raw, null, 2));
  lines.push("```");
  lines.push(
    "",
    "Use `data.<field>` to read host data.",
    "Use `Each(...)` to iterate arrays.",
    "Array pluck works on arrays: `data.sales.revenue`.",
  );

  return lines.join("\n");
}
```

- [ ] **Step 3: Update the guard in `generatePrompt()`**

Find the block in `generatePrompt()` that calls `dataModelSection`:

```ts
if (spec.dataModel) {
  parts.push("");
  parts.push(dataModelSection(spec.dataModel));
}
```

Replace it with:

```ts
if (spec.dataModel?.raw && Object.keys(spec.dataModel.raw).length > 0) {
  parts.push("");
  parts.push(dataModelSection(spec.dataModel));
}
```

- [ ] **Step 4: Run the tests — all dataModel cases should pass**

```bash
cd packages/lang-core && npm test -- --reporter=verbose 2>&1 | grep -A3 "dataModel"
```

Expected: all 5 cases PASS.

- [ ] **Step 5: Commit**

```bash
cd packages/lang-core
git add src/parser/prompt.ts src/parser/__tests__/prompt.test.ts
git commit -m "feat(lang-core): replace DataModelSpec.fields with raw JSON mode"
```

---

### Task 3: Add `dataModel` to `PromptOptions` in `lang-core`

This allows callers to pass `dataModel` through `library.prompt({ dataModel: ... })`.

**Files:**
- Modify: `packages/lang-core/src/library.ts`

- [ ] **Step 1: Add `DataModelSpec` to the import and `dataModel` to `PromptOptions`**

Change the first import line in `packages/lang-core/src/library.ts`:

```ts
// Before:
import type { ComponentPromptSpec, PromptSpec, ToolSpec } from "./parser/prompt";

// After:
import type { ComponentPromptSpec, DataModelSpec, PromptSpec, ToolSpec } from "./parser/prompt";
```

Then in `PromptOptions` (around line 81), add one field at the end:

```ts
export interface PromptOptions {
  preamble?: string;
  additionalRules?: string[];
  examples?: string[];
  toolExamples?: string[];
  tools?: ToolDescriptor[];
  editMode?: boolean;
  inlineMode?: boolean;
  toolCalls?: boolean;
  bindings?: boolean;
  dataModel?: DataModelSpec;
}
```

- [ ] **Step 2: Run typecheck**

```bash
cd packages/lang-core && npm run typecheck
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add packages/lang-core/src/library.ts
git commit -m "feat(lang-core): expose dataModel in PromptOptions"
```

---

### Task 4: Remove `DataModelFieldSpec` re-exports

**Files:**
- Modify: `packages/lang-core/src/parser/index.ts`
- Modify: `packages/lang-core/src/index.ts`
- Modify: `packages/react-lang/src/index.ts`

- [ ] **Step 1: Remove from `packages/lang-core/src/parser/index.ts`**

Find the export block for prompt types and remove `DataModelFieldSpec`:

```ts
// Before:
export type {
  ComponentGroup,
  ComponentPromptSpec,
  DataModelFieldSpec,
  DataModelSpec,
  PromptSpec,
  ToolSpec,
} from "./prompt";

// After:
export type {
  ComponentGroup,
  ComponentPromptSpec,
  DataModelSpec,
  PromptSpec,
  ToolSpec,
} from "./prompt";
```

- [ ] **Step 2: Remove from `packages/lang-core/src/index.ts`**

Find the re-export for prompt types and remove `DataModelFieldSpec`:

```ts
// Before:
export type {
  ComponentGroup,
  ComponentPromptSpec,
  DataModelFieldSpec,
  DataModelSpec,
  PromptSpec,
  ToolSpec,
} from "./parser/prompt";

// After:
export type {
  ComponentGroup,
  ComponentPromptSpec,
  DataModelSpec,
  PromptSpec,
  ToolSpec,
} from "./parser/prompt";
```

- [ ] **Step 3: Remove from `packages/react-lang/src/index.ts`**

Find the re-export block (around line 36) and remove `DataModelFieldSpec`:

```ts
// Before:
export type {
  ComponentPromptSpec,
  DataModelFieldSpec,
  DataModelSpec,
  PromptSpec,
  ToolSpec,
} from "@openuidev/lang-core";

// After:
export type {
  ComponentPromptSpec,
  DataModelSpec,
  PromptSpec,
  ToolSpec,
} from "@openuidev/lang-core";
```

- [ ] **Step 4: Run typecheck across both packages**

```bash
cd packages/lang-core && npm run typecheck
cd ../react-lang && npm run typecheck
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
cd C:/workspace/openui
git add packages/lang-core/src/parser/index.ts packages/lang-core/src/index.ts packages/react-lang/src/index.ts
git commit -m "chore: remove DataModelFieldSpec re-exports"
```

---

### Task 5: Wire `dataModel` through the demo server

**Files:**
- Modify: `examples/react-ui-dsl-demo/server/systemPrompt.ts`
- Modify: `examples/react-ui-dsl-demo/server/index.ts`

- [ ] **Step 1: Replace the static export in `systemPrompt.ts` with a function**

Replace the entire contents of `examples/react-ui-dsl-demo/server/systemPrompt.ts` with:

```ts
import { dslLibrary } from "@openuidev/react-ui-dsl";

export function buildSystemPrompt(dataModel?: Record<string, unknown>): string {
  if (!dataModel || Object.keys(dataModel).length === 0) {
    return dslLibrary.prompt();
  }
  return dslLibrary.prompt({ dataModel: { raw: dataModel } });
}
```

- [ ] **Step 2: Update `index.ts` to use `buildSystemPrompt` per request**

In `examples/react-ui-dsl-demo/server/index.ts`:

Change the import at line 5:
```ts
// Before:
import { systemPrompt } from "./systemPrompt.js";

// After:
import { buildSystemPrompt } from "./systemPrompt.js";
```

Change the request body destructuring inside `app.post("/api/generate", ...)`:
```ts
// Before:
const { prompt } = req.body as { prompt: string };

// After:
const { prompt, dataModel } = req.body as { prompt: string; dataModel?: Record<string, unknown> };
```

Change the messages array in the OpenAI call:
```ts
// Before:
messages: [
  { role: "system", content: systemPrompt },
  { role: "user", content: prompt },
],

// After:
messages: [
  { role: "system", content: buildSystemPrompt(dataModel) },
  { role: "user", content: prompt },
],
```

- [ ] **Step 3: Commit**

```bash
git add examples/react-ui-dsl-demo/server/systemPrompt.ts examples/react-ui-dsl-demo/server/index.ts
git commit -m "feat(demo): wire dataModel into per-request system prompt"
```

---

### Task 6: Wire `dataModel` through the demo frontend

**Files:**
- Modify: `examples/react-ui-dsl-demo/src/useGenerate.ts`
- Modify: `examples/react-ui-dsl-demo/src/App.tsx`

- [ ] **Step 1: Update `useGenerate.ts` to accept and forward `dataModel`**

Replace the `UseGenerateResult` interface and `generate` signature:

```ts
export interface UseGenerateResult {
  response: string;
  isStreaming: boolean;
  error: string | null;
  generate: (prompt: string, dataModel?: Record<string, unknown>) => Promise<void>;
  reset: () => void;
}
```

Inside the `generate` callback, update the fetch body:
```ts
// Before:
body: JSON.stringify({ prompt }),

// After:
body: JSON.stringify({ prompt, dataModel }),
```

The full updated `generate` callback signature:
```ts
const generate = useCallback(async (prompt: string, dataModel?: Record<string, unknown>) => {
```

- [ ] **Step 2: Update `App.tsx` to pass `dataModel` to `generate`**

In `App.tsx`, find `handleGenerate` and update the `generate` call:

```ts
// Before:
void generate(prompt);

// After:
void generate(prompt, dataModel);
```

- [ ] **Step 3: Commit**

```bash
git add examples/react-ui-dsl-demo/src/useGenerate.ts examples/react-ui-dsl-demo/src/App.tsx
git commit -m "feat(demo): pass dataModel from UI through to server"
```
