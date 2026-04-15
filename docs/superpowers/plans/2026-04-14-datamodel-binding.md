# DataModel Binding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a host-provided `dataModel` root that OpenUI Lang programs can read as `data.*`, with parser support, prompt generation support, and `react-lang` runtime wiring.

**Architecture:** Extend the parser with a generic `externalRefs` option so unresolved names such as `data` can lower to `RuntimeRef` instead of `Ph`. Reuse the existing evaluator `resolveRef()` path to supply the runtime object from `react-lang`, and keep prompt support separate via `PromptSpec.dataModel` metadata.

**Tech Stack:** TypeScript, Vitest, React 19, `@openuidev/lang-core`, `@openuidev/react-lang`

---

## File Structure

### Existing files to modify

- `packages/lang-core/src/parser/ast.ts`
  Responsibility: AST node unions and `RuntimeRef` typing.
- `packages/lang-core/src/parser/materialize.ts`
  Responsibility: ref lowering from parse-time symbols into runtime AST nodes.
- `packages/lang-core/src/parser/parser.ts`
  Responsibility: parser public API, parser option threading, stream parser creation.
- `packages/lang-core/src/parser/prompt.ts`
  Responsibility: `PromptSpec` types and prompt text generation.
- `packages/lang-core/src/parser/index.ts`
  Responsibility: parser-level exports.
- `packages/lang-core/src/index.ts`
  Responsibility: package public exports.
- `packages/lang-core/src/parser/__tests__/parser.test.ts`
  Responsibility: parser/materializer behavior coverage.
- `packages/react-lang/src/hooks/useOpenUIState.ts`
  Responsibility: parser creation and runtime `resolveRef()` wiring.
- `packages/react-lang/src/Renderer.tsx`
  Responsibility: public renderer props and prop pass-through.
- `packages/react-lang/src/index.ts`
  Responsibility: package exports for React consumers.
- `packages/react-lang/package.json`
  Responsibility: package-local test/dev dependencies.

### New files to create

- `packages/lang-core/src/parser/__tests__/prompt.test.ts`
  Responsibility: prompt generation coverage for the `## Data Model` section.
- `packages/react-lang/src/__tests__/Renderer.dataModel.test.tsx`
  Responsibility: integration coverage that `Renderer` can resolve `data.*` from host-supplied `dataModel`.

---

### Task 1: Extend Parser External Ref Support

**Files:**
- Modify: `packages/lang-core/src/parser/ast.ts`
- Modify: `packages/lang-core/src/parser/materialize.ts`
- Modify: `packages/lang-core/src/parser/parser.ts`
- Modify: `packages/lang-core/src/parser/__tests__/parser.test.ts`

- [ ] **Step 1: Write the failing parser tests for `externalRefs`**

Add these tests to `packages/lang-core/src/parser/__tests__/parser.test.ts`:

```ts
it("treats configured external refs as RuntimeRef nodes", () => {
  const result = parse("root = Title(data.user.name)", schema, undefined, {
    externalRefs: ["data"],
  });

  expect(result.meta.unresolved).toHaveLength(0);

  const text = result.root?.props.text as any;
  expect(text).toMatchObject({
    k: "Member",
    field: "name",
    obj: {
      k: "Member",
      field: "user",
      obj: {
        k: "RuntimeRef",
        n: "data",
        refType: "data",
      },
    },
  });
});

it("keeps data unresolved when externalRefs is not enabled", () => {
  const result = parse("root = Title(data.user.name)", schema);
  expect(result.meta.unresolved).toContain("data");
});

it("streaming parser preserves configured external refs", () => {
  const parser = createStreamParser(schema, undefined, {
    externalRefs: ["data"],
  });

  const result = parser.push("root = Title(data.user.name)\n");
  expect(result.meta.unresolved).toHaveLength(0);

  const text = result.root?.props.text as any;
  expect(text.obj.obj).toMatchObject({
    k: "RuntimeRef",
    n: "data",
    refType: "data",
  });
});
```

- [ ] **Step 2: Run the parser tests to verify they fail**

Run:

```bash
pnpm --filter @openuidev/lang-core test -- src/parser/__tests__/parser.test.ts
```

Expected: FAIL with TypeScript or runtime errors because `parse()` and `createStreamParser()` do not accept an `externalRefs` option and `RuntimeRef.refType` does not include `"data"`.

- [ ] **Step 3: Add `ParserOptions` and thread `externalRefs` into materialization**

Update `packages/lang-core/src/parser/ast.ts`:

```ts
| { k: "RuntimeRef"; n: string; refType: "query" | "mutation" | "data" }
```

Update `packages/lang-core/src/parser/materialize.ts`:

```ts
export interface MaterializeCtx {
  syms: Map<string, ASTNode>;
  cat: ParamMap | undefined;
  errors: ValidationError[];
  unres: string[];
  visited: Set<string>;
  partial: boolean;
  externalRefs?: Set<string>;
  currentStatementId?: string;
  unreached?: Set<string>;
}

function resolveRef(name: string, ctx: MaterializeCtx, mode: "value" | "expr"): unknown | ASTNode {
  if (ctx.visited.has(name)) {
    ctx.unres.push(name);
    return mode === "expr" ? { k: "Ph", n: name } : null;
  }
  if (!ctx.syms.has(name)) {
    if (ctx.externalRefs?.has(name)) {
      return { k: "RuntimeRef", n: name, refType: "data" };
    }
    ctx.unres.push(name);
    return mode === "expr" ? { k: "Ph", n: name } : null;
  }
  // existing symbol resolution continues unchanged
}
```

Update `packages/lang-core/src/parser/parser.ts`:

```ts
export interface ParserOptions {
  externalRefs?: string[];
}

function buildResult(
  stmtMap: Map<string, Statement>,
  typedStmts: Statement[],
  firstId: string,
  wasIncomplete: boolean,
  stmtCount: number,
  cat: ParamMap | undefined,
  rootName?: string,
  options?: ParserOptions,
): ParseResult {
  // ...
  const ctx: MaterializeCtx = {
    syms,
    cat,
    errors,
    unres,
    visited: new Set(),
    partial: wasIncomplete,
    externalRefs: options?.externalRefs?.length ? new Set(options.externalRefs) : undefined,
    currentStatementId: entryId,
    unreached,
  };
  // ...
}

export function parse(
  input: string,
  cat: ParamMap,
  rootName?: string,
  options?: ParserOptions,
): ParseResult {
  // ...
  return buildResult(stmtMap, typedStmts, firstId, wasIncomplete, stmtMap.size, cat, rootName, options);
}

export function createStreamParser(
  cat: ParamMap,
  rootName?: string,
  options?: ParserOptions,
): StreamParser {
  // every buildResult(...) call in this factory must pass options through
}

export function createParser(
  schema: LibraryJSONSchema,
  rootName?: string,
  options?: ParserOptions,
): Parser {
  const paramMap = compileSchema(schema);
  return {
    parse(input: string): ParseResult {
      return parse(input, paramMap, rootName, options);
    },
  };
}

export function createStreamingParser(
  schema: LibraryJSONSchema,
  rootName?: string,
  options?: ParserOptions,
): StreamParser {
  return createStreamParser(compileSchema(schema), rootName, options);
}
```

- [ ] **Step 4: Run the parser tests to verify they pass**

Run:

```bash
pnpm --filter @openuidev/lang-core test -- src/parser/__tests__/parser.test.ts
```

Expected: PASS with the new `externalRefs` coverage green and existing parser behavior unchanged.

- [ ] **Step 5: Commit**

```bash
git add packages/lang-core/src/parser/ast.ts packages/lang-core/src/parser/materialize.ts packages/lang-core/src/parser/parser.ts packages/lang-core/src/parser/__tests__/parser.test.ts
git commit -m "feat(lang-core): support external runtime refs"
```

### Task 2: Add PromptSpec Data Model Support

**Files:**
- Modify: `packages/lang-core/src/parser/prompt.ts`
- Modify: `packages/lang-core/src/parser/index.ts`
- Modify: `packages/lang-core/src/index.ts`
- Create: `packages/lang-core/src/parser/__tests__/prompt.test.ts`

- [ ] **Step 1: Write the failing prompt tests**

Create `packages/lang-core/src/parser/__tests__/prompt.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { generatePrompt, type PromptSpec } from "../prompt";

describe("generatePrompt dataModel support", () => {
  const baseSpec: PromptSpec = {
    root: "Root",
    components: {
      Root: {
        signature: "Root(children: Component[])",
        description: "Root container",
      },
      Label: {
        signature: 'Label(text: string)',
        description: "Simple text label",
      },
    },
  };

  it("omits the Data Model section when dataModel is absent", () => {
    const prompt = generatePrompt(baseSpec);
    expect(prompt).not.toContain("## Data Model");
  });

  it("renders a Data Model section when dataModel metadata is present", () => {
    const prompt = generatePrompt({
      ...baseSpec,
      dataModel: {
        description: "Host business data",
        fields: {
          sales: { type: "array", description: "Quarterly sales rows" },
          user: { type: "object", description: "Current user" },
          totalRevenue: { type: "scalar", description: "Total revenue value" },
        },
      },
    });

    expect(prompt).toContain("## Data Model");
    expect(prompt).toContain("`data.sales` (array): Quarterly sales rows");
    expect(prompt).toContain("`data.user` (object): Current user");
    expect(prompt).toContain("`data.totalRevenue` (scalar): Total revenue value");
    expect(prompt).toContain("Array pluck works on arrays: `data.sales.revenue`");
  });
});
```

- [ ] **Step 2: Run the new prompt test to verify it fails**

Run:

```bash
pnpm --filter @openuidev/lang-core test -- src/parser/__tests__/prompt.test.ts
```

Expected: FAIL because `PromptSpec` does not yet have a `dataModel` field and `generatePrompt()` does not render the new section.

- [ ] **Step 3: Implement `DataModelSpec` and prompt rendering**

Update `packages/lang-core/src/parser/prompt.ts`:

```ts
export interface DataModelFieldSpec {
  type: "array" | "object" | "scalar";
  description?: string;
}

export interface DataModelSpec {
  description?: string;
  fields: Record<string, DataModelFieldSpec>;
}

export interface PromptSpec {
  root?: string;
  components: Record<string, ComponentPromptSpec>;
  componentGroups?: ComponentGroup[];
  tools?: (string | ToolSpec)[];
  editMode?: boolean;
  inlineMode?: boolean;
  toolCalls?: boolean;
  bindings?: boolean;
  preamble?: string;
  examples?: string[];
  toolExamples?: string[];
  additionalRules?: string[];
  dataModel?: DataModelSpec;
}

function dataModelSection(dataModel: DataModelSpec): string {
  const lines = ["## Data Model", ""];

  if (dataModel.description) {
    lines.push(dataModel.description, "");
  }

  lines.push("The following host data is available via `data.<field>`:", "");

  for (const [fieldName, fieldSpec] of Object.entries(dataModel.fields)) {
    const suffix = fieldSpec.description ? `: ${fieldSpec.description}` : "";
    lines.push(`- \`data.${fieldName}\` (${fieldSpec.type})${suffix}`);
  }

  lines.push(
    "",
    "Use `data.<field>` to read host data.",
    "Use `Each(...)` to iterate arrays.",
    "Array pluck works on arrays: `data.sales.revenue`.",
  );

  return lines.join("\n");
}
```

Then append the section in `generatePrompt()` immediately after component signatures:

```ts
parts.push(generateComponentSignatures(spec, { toolCalls, bindings, usesActionExpression }));

if (spec.dataModel) {
  parts.push("");
  parts.push(dataModelSection(spec.dataModel));
}
```

Update exports:

```ts
// packages/lang-core/src/parser/index.ts
export type {
  ComponentGroup,
  ComponentPromptSpec,
  DataModelFieldSpec,
  DataModelSpec,
  PromptSpec,
  ToolSpec,
} from "./prompt";

// packages/lang-core/src/index.ts
export type {
  ComponentPromptSpec,
  DataModelFieldSpec,
  DataModelSpec,
  PromptSpec,
  ToolSpec,
} from "./parser/prompt";
```

- [ ] **Step 4: Run parser package tests**

Run:

```bash
pnpm --filter @openuidev/lang-core test -- src/parser/__tests__/parser.test.ts src/parser/__tests__/prompt.test.ts
pnpm --filter @openuidev/lang-core typecheck
```

Expected: PASS for both test files and a clean TypeScript build for the package.

- [ ] **Step 5: Commit**

```bash
git add packages/lang-core/src/parser/prompt.ts packages/lang-core/src/parser/index.ts packages/lang-core/src/index.ts packages/lang-core/src/parser/__tests__/prompt.test.ts
git commit -m "feat(lang-core): document host data model in prompts"
```

### Task 3: Wire `dataModel` into `react-lang`

**Files:**
- Modify: `packages/react-lang/src/hooks/useOpenUIState.ts`
- Modify: `packages/react-lang/src/Renderer.tsx`
- Modify: `packages/react-lang/src/index.ts`
- Modify: `packages/react-lang/package.json`
- Create: `packages/react-lang/src/__tests__/Renderer.dataModel.test.tsx`

- [ ] **Step 1: Add the React integration test**

Create `packages/react-lang/src/__tests__/Renderer.dataModel.test.tsx`:

```tsx
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { z } from "zod";
import { Renderer } from "../Renderer";
import { createLibrary, defineComponent } from "../library";

const Root = defineComponent({
  name: "Root",
  description: "Root container",
  props: z.object({
    children: z.array(z.any()),
  }),
  component: ({ props, renderNode }) => <div>{props.children.map((child) => renderNode(child))}</div>,
});

const Label = defineComponent({
  name: "Label",
  description: "Simple label",
  props: z.object({
    text: z.any(),
  }),
  component: ({ props }) => <span>{String(props.text)}</span>,
});

const library = createLibrary({
  root: "Root",
  components: { Root, Label },
});

describe("Renderer dataModel integration", () => {
  it("renders host data through the data root", () => {
    const html = renderToStaticMarkup(
      <Renderer
        response={'root = Root([Label(data.user.name)])'}
        library={library}
        dataModel={{ user: { name: "Alice" } }}
      />,
    );

    expect(html).toContain("Alice");
  });
});
```

- [ ] **Step 2: Add the missing test dependency and verify the test fails**

Update `packages/react-lang/package.json` dev dependencies:

```json
"devDependencies": {
  "@modelcontextprotocol/sdk": "^1.27.1",
  "@types/react": "^19.0.0",
  "@types/react-dom": "^19.0.0",
  "react-dom": "^19.0.0",
  "vitest": "^4.0.18"
}
```

Run:

```bash
pnpm --filter @openuidev/react-lang test -- src/__tests__/Renderer.dataModel.test.tsx
```

Expected: FAIL because `RendererProps` and `useOpenUIState()` do not yet accept `dataModel`, and parser creation does not enable the `data` external ref.

- [ ] **Step 3: Implement `dataModel` support in `react-lang`**

Update `packages/react-lang/src/Renderer.tsx`:

```ts
export interface RendererProps {
  response: string | null;
  library: Library;
  isStreaming?: boolean;
  onAction?: (event: ActionEvent) => void;
  onStateUpdate?: (state: Record<string, unknown>) => void;
  initialState?: Record<string, any>;
  onParseResult?: (result: ParseResult | null) => void;
  dataModel?: Record<string, unknown>;
  toolProvider?: Record<string, (args: Record<string, unknown>) => Promise<unknown>> | McpClientLike | null;
  queryLoader?: React.ReactNode;
  onError?: (errors: OpenUIError[]) => void;
}
```

Pass it through:

```ts
export function Renderer({
  response,
  library,
  isStreaming = false,
  onAction,
  onStateUpdate,
  initialState,
  onParseResult,
  dataModel,
  toolProvider,
  queryLoader,
  onError,
}: RendererProps) {
  const { result, parseResult, contextValue, isQueryLoading } = useOpenUIState(
    {
      response,
      library,
      isStreaming,
      onAction,
      onStateUpdate,
      initialState,
      dataModel,
      toolProvider: resolvedToolProvider,
      onError,
    },
    renderDeep,
  );
}
```

Update `packages/react-lang/src/hooks/useOpenUIState.ts`:

```ts
export interface UseOpenUIStateOptions {
  response: string | null;
  library: Library;
  isStreaming: boolean;
  onAction?: (event: ActionEvent) => void;
  onStateUpdate?: (state: Record<string, unknown>) => void;
  initialState?: Record<string, any>;
  dataModel?: Record<string, unknown>;
  toolProvider?: ToolProvider | null;
  onError?: (errors: OpenUIError[]) => void;
}
```

Change parser creation:

```ts
const sp = useMemo(
  () =>
    createStreamingParser(library.toJSONSchema(), library.root, {
      externalRefs: dataModel ? ["data"] : undefined,
    }),
  [library, dataModel != null],
);
```

Change runtime resolution:

```ts
const evaluationContext = useMemo<EvaluationContext>(
  () => ({
    getState: (name: string) => unwrapFieldValue(store.get(name)),
    resolveRef: (name: string) => {
      if (name === "data" && dataModel) return dataModel;
      const mutResult = queryManager.getMutationResult(name);
      if (mutResult) return mutResult;
      return queryManager.getResult(name);
    },
  }),
  [store, queryManager, dataModel],
);
```

Update `packages/react-lang/src/index.ts`:

```ts
export type {
  ComponentPromptSpec,
  DataModelFieldSpec,
  DataModelSpec,
  PromptSpec,
  ToolSpec,
} from "@openuidev/lang-core";
```

- [ ] **Step 4: Run the React package tests and typecheck**

Run:

```bash
pnpm --filter @openuidev/react-lang test -- src/__tests__/Renderer.dataModel.test.tsx
pnpm --filter @openuidev/react-lang typecheck
```

Expected: PASS with the SSR test rendering `Alice` and TypeScript accepting the new prop and exports.

- [ ] **Step 5: Commit**

```bash
git add packages/react-lang/package.json packages/react-lang/src/Renderer.tsx packages/react-lang/src/hooks/useOpenUIState.ts packages/react-lang/src/index.ts packages/react-lang/src/__tests__/Renderer.dataModel.test.tsx
git commit -m "feat(react-lang): add host dataModel support"
```

### Task 4: Final Cross-Package Verification

**Files:**
- Modify: none
- Test: `packages/lang-core/src/parser/__tests__/parser.test.ts`
- Test: `packages/lang-core/src/parser/__tests__/prompt.test.ts`
- Test: `packages/react-lang/src/__tests__/Renderer.dataModel.test.tsx`

- [ ] **Step 1: Run focused package tests**

Run:

```bash
pnpm --filter @openuidev/lang-core test -- src/parser/__tests__/parser.test.ts src/parser/__tests__/prompt.test.ts
pnpm --filter @openuidev/react-lang test -- src/__tests__/Renderer.dataModel.test.tsx
```

Expected: PASS across all focused tests with no unresolved `data` regressions.

- [ ] **Step 2: Run package typechecks**

Run:

```bash
pnpm --filter @openuidev/lang-core typecheck
pnpm --filter @openuidev/react-lang typecheck
```

Expected: PASS with exported `ParserOptions`, `DataModelSpec`, and React prop types all aligned.

- [ ] **Step 3: Run package builds**

Run:

```bash
pnpm --filter @openuidev/lang-core build
pnpm --filter @openuidev/react-lang build
```

Expected: PASS and emit updated package bundles/types with no export errors.

- [ ] **Step 4: Commit the verification checkpoint**

```bash
git add -A
git commit -m "chore: verify datamodel binding implementation"
```

---

## Spec Coverage Check

- Parser support for external refs is covered by Task 1.
- Prompt `dataModel` metadata and the `## Data Model` prompt section are covered by Task 2.
- `Renderer`/`useOpenUIState` runtime wiring and package exports are covered by Task 3.
- Package-level verification and build confidence are covered by Task 4.

## Placeholder Scan

- No `TBD`, `TODO`, or deferred “add tests later” steps remain.
- Every code-changing task includes exact file paths, code snippets, commands, and commit messages.

## Type Consistency Check

- The plan consistently uses `ParserOptions.externalRefs`.
- The runtime root name is consistently `data`.
- Prompt types are consistently named `DataModelFieldSpec` and `DataModelSpec`.

---

Plan complete and saved to `docs/superpowers/plans/2026-04-14-datamodel-binding.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
