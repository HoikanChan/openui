# OpenUI Lang Template Scope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make extracted `@Each` templates and nested deferred `@Render` expressions obey one lexical-scope rule, including independent instances, shadowing, and hard errors for unbound template references.

**Architecture:** Add a focused `template-scope.ts` module that immutably instantiates materialized AST templates from a binding environment while respecting nested binders. `evaluator.ts` uses this module for every ordinary `@Each` iteration and removes the direct-`@Render` callable special case. Materialization continues to expand named statement dependencies, while parser validation reports free references that remain unbound at an extracted template use site.

**Tech Stack:** TypeScript, Vitest, pnpm, OpenUI Lang AST/materializer/evaluator, generated JSON Component Contract.

---

## File map

- Create `packages/lang-core/src/runtime/template-scope.ts`: lexical template instantiation and binder handling.
- Create `packages/lang-core/src/__tests__/template-scope.test.ts`: runtime scope, capture, shadowing, and deferred-template regression tests.
- Modify `packages/lang-core/src/runtime/evaluator.ts`: route `@Each` through lexical instantiation and delete callable-`@Render` behavior.
- Modify `packages/lang-core/src/parser/materialize.ts`: identify unbound free references reached through named templates.
- Modify `packages/lang-core/src/parser/parser.ts`: identify the entry statement in materialization context.
- Modify `packages/lang-core/src/parser/types.ts`: add the structured validation error code.
- Modify `packages/lang-core/src/parser/__tests__/parser.test.ts`: parser error and streaming-forward-reference coverage.
- Modify `packages/lang-core/src/parser/builtins.ts`: canonical named-template contract text.
- Modify `packages/lang-core/src/parser/prompt.ts`: remove inline-only guidance.
- Modify `packages/lang-core/src/parser/__tests__/prompt.test.ts`: prompt contract regression assertions.
- Modify `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`: align generation guidance with extracted templates.
- Regenerate `packages/react-ui-dsl/generated/base-contract.json` and `packages/genui-java-sdk/src/main/resources/openui/base-contract.json`.

### Task 1: Scope-aware template instantiation

**Files:**
- Create: `packages/lang-core/src/__tests__/template-scope.test.ts`
- Create: `packages/lang-core/src/runtime/template-scope.ts`
- Modify: `packages/lang-core/src/runtime/evaluator.ts:290-530`

- [ ] **Step 1: Write failing runtime tests**

Create `packages/lang-core/src/__tests__/template-scope.test.ts`:

```ts
import { describe, expect, it } from "vitest";

import type { ASTNode } from "../parser/ast";
import type { ElementNode } from "../parser/types";
import { evaluate, type EvaluationContext } from "../runtime/evaluator";

const context: EvaluationContext = {
  getState: () => null,
  resolveRef: () => null,
};

const ref = (name: string): ASTNode => ({ k: "Ref", n: name });
const str = (value: string): ASTNode => ({ k: "Str", v: value });
const member = (name: string, field: string): ASTNode => ({
  k: "Member",
  obj: ref(name),
  field,
});

function renderSlot(binders: string[], body: ASTNode): ASTNode {
  return {
    k: "Comp",
    name: "Render",
    args: [...binders.map(str), body],
  };
}

function label(text: ASTNode): ASTNode {
  return {
    k: "Comp",
    name: "Label",
    args: [text],
    mappedProps: { text },
  };
}

function each(array: ASTNode, binder: string, template: ASTNode): ASTNode {
  return {
    k: "Comp",
    name: "Each",
    args: [array, str(binder), template],
  };
}

describe("lexical template scope", () => {
  it("lets a nested Each binder shadow an outer binder with the same name", () => {
    const expression = each(
      {
        k: "Arr",
        els: [
          {
            k: "Obj",
            entries: [
              ["name", str("outer")],
              [
                "devices",
                {
                  k: "Arr",
                  els: [{ k: "Obj", entries: [["name", str("inner")]] }],
                },
              ],
            ],
          },
        ],
      },
      "group",
      each(member("group", "devices"), "group", member("group", "name")),
    );

    expect(evaluate(expression, context)).toEqual([["inner"]]);
  });

  it("captures an outer Each binding inside a nested deferred Render", () => {
    const expression = each(
      { k: "Arr", els: [{ k: "Obj", entries: [["count", { k: "Num", v: 2 }]] }] },
      "group",
      {
        k: "Comp",
        name: "Col",
        args: [],
        mappedProps: {
          cell: renderSlot(["v", "row"], label(member("group", "count"))),
        },
      },
    );

    const [column] = evaluate(expression, context) as ElementNode[];
    const slot = column.props.cell as ASTNode & { k: "Comp" };
    const body = slot.args.at(-1) as ASTNode & { k: "Comp" };

    expect(body.mappedProps?.text).toEqual({ k: "Num", v: 2 });
  });

  it("lets a Render binder shadow a captured Each binding", () => {
    const expression = each(
      { k: "Arr", els: [{ k: "Obj", entries: [["name", str("outer")]] }] },
      "group",
      {
        k: "Comp",
        name: "Col",
        args: [],
        mappedProps: {
          cell: renderSlot(["group"], label(member("group", "name"))),
        },
      },
    );

    const [column] = evaluate(expression, context) as ElementNode[];
    const slot = column.props.cell as ASTNode & { k: "Comp" };
    const body = slot.args.at(-1)!;
    const rendered = evaluate(body, {
      ...context,
      resolveRef: (name) => (name === "group" ? { name: "inner" } : null),
    }) as ElementNode;

    expect(rendered.props.text).toBe("inner");
  });

  it("does not treat Render as a callable Each template", () => {
    const expression = each(
      { k: "Arr", els: [{ k: "Num", v: 7 }] },
      "item",
      renderSlot(["v"], label(ref("v"))),
    );

    const [result] = evaluate(expression, context) as ASTNode[];

    expect(result).toMatchObject({ k: "Comp", name: "Render" });
  });

  it("keeps state references live in captured Render bodies", () => {
    const expression = each(
      { k: "Arr", els: [{ k: "Obj", entries: [["name", str("outer")]] }] },
      "group",
      {
        k: "Comp",
        name: "Col",
        args: [],
        mappedProps: {
          cell: renderSlot(["v"], label({ k: "StateRef", n: "status" })),
        },
      },
    );

    const [column] = evaluate(expression, context) as ElementNode[];
    const slot = column.props.cell as ASTNode & { k: "Comp" };
    const body = slot.args.at(-1) as ASTNode & { k: "Comp" };

    expect(body.mappedProps?.text).toEqual({ k: "StateRef", n: "status" });
  });

  it("creates independent captures when the same template AST is reused", () => {
    const template: ASTNode = {
      k: "Comp",
      name: "Col",
      args: [],
      mappedProps: {
        cell: renderSlot(["v"], label(member("group", "name"))),
      },
    };
    const evaluateName = (name: string) => {
      const expression = each(
        { k: "Arr", els: [{ k: "Obj", entries: [["name", str(name)]] }] },
        "group",
        template,
      );
      const [column] = evaluate(expression, context) as ElementNode[];
      const slot = column.props.cell as ASTNode & { k: "Comp" };
      const body = slot.args.at(-1) as ASTNode & { k: "Comp" };
      return body.mappedProps?.text;
    };

    expect(evaluateName("first")).toEqual({ k: "Str", v: "first" });
    expect(evaluateName("second")).toEqual({ k: "Str", v: "second" });
  });
});
```

- [ ] **Step 2: Run the tests and verify the current implementation fails for lexical shadowing**

Run:

```powershell
pnpm --filter @cloudsop/openui-lang-core exec vitest run src/__tests__/template-scope.test.ts
```

Expected: the nested-`@Each` test reports `[["outer"]]` instead of `[["inner"]]`; the render-shadowing test reports `"outer"`; and the direct-`@Render` test receives an evaluated element rather than a preserved Render template.

- [ ] **Step 3: Implement the lexical instantiation module**

Create `packages/lang-core/src/runtime/template-scope.ts`:

```ts
import type { ASTNode } from "../parser/ast";

export type TemplateBindings = ReadonlyMap<string, unknown>;

function getBinderName(node: ASTNode): string | null {
  if (node.k === "Str") return node.v;
  if (node.k === "Ref") return node.n;
  return null;
}

function toLiteralAST(value: unknown): ASTNode {
  if (value === null || value === undefined) return { k: "Null" };
  if (typeof value === "string") return { k: "Str", v: value };
  if (typeof value === "number") return { k: "Num", v: value };
  if (typeof value === "boolean") return { k: "Bool", v: value };
  if (Array.isArray(value)) return { k: "Arr", els: value.map(toLiteralAST) };
  if (typeof value === "object") {
    return {
      k: "Obj",
      entries: Object.entries(value).map(
        ([key, item]) => [key, toLiteralAST(item)] as [string, ASTNode],
      ),
    };
  }
  return { k: "Null" };
}

function addShadowed(
  shadowed: ReadonlySet<string>,
  binders: readonly ASTNode[],
): ReadonlySet<string> {
  const next = new Set(shadowed);
  for (const binder of binders) {
    const name = getBinderName(binder);
    if (name) next.add(name);
  }
  return next;
}

function instantiateMappedProps(
  mappedProps: Record<string, ASTNode> | undefined,
  bindings: TemplateBindings,
  shadowed: ReadonlySet<string>,
): Record<string, ASTNode> | undefined {
  if (!mappedProps) return undefined;
  return Object.fromEntries(
    Object.entries(mappedProps).map(([key, value]) => [
      key,
      instantiateTemplate(value, bindings, shadowed),
    ]),
  );
}

export function instantiateTemplate(
  node: ASTNode,
  bindings: TemplateBindings,
  shadowed: ReadonlySet<string> = new Set(),
): ASTNode {
  switch (node.k) {
    case "Ref":
      return !shadowed.has(node.n) && bindings.has(node.n)
        ? toLiteralAST(bindings.get(node.n))
        : node;
    case "Member": {
      const obj = instantiateTemplate(node.obj, bindings, shadowed);
      if (obj.k === "Obj") {
        const entry = obj.entries.find(([key]) => key === node.field);
        if (entry) return entry[1];
      }
      return { ...node, obj };
    }
    case "Index":
      return {
        ...node,
        obj: instantiateTemplate(node.obj, bindings, shadowed),
        index: instantiateTemplate(node.index, bindings, shadowed),
      };
    case "BinOp":
      return {
        ...node,
        left: instantiateTemplate(node.left, bindings, shadowed),
        right: instantiateTemplate(node.right, bindings, shadowed),
      };
    case "UnaryOp":
      return { ...node, operand: instantiateTemplate(node.operand, bindings, shadowed) };
    case "Ternary":
      return {
        ...node,
        cond: instantiateTemplate(node.cond, bindings, shadowed),
        then: instantiateTemplate(node.then, bindings, shadowed),
        else: instantiateTemplate(node.else, bindings, shadowed),
      };
    case "Arr":
      return {
        ...node,
        els: node.els.map((item) => instantiateTemplate(item, bindings, shadowed)),
      };
    case "Obj":
      return {
        ...node,
        entries: node.entries.map(([key, value]) => [
          key,
          instantiateTemplate(value, bindings, shadowed),
        ]),
      };
    case "Comp": {
      if (node.name === "Each" && node.args.length >= 3) {
        const [array, binder, template, ...rest] = node.args;
        const templateScope = addShadowed(shadowed, [binder]);
        return {
          ...node,
          args: [
            instantiateTemplate(array, bindings, shadowed),
            binder,
            instantiateTemplate(template, bindings, templateScope),
            ...rest.map((arg) => instantiateTemplate(arg, bindings, shadowed)),
          ],
          mappedProps: instantiateMappedProps(node.mappedProps, bindings, shadowed),
        };
      }
      if (node.name === "Render" && node.args.length >= 2) {
        const binders = node.args.slice(0, -1);
        const body = node.args.at(-1)!;
        return {
          ...node,
          args: [
            ...binders,
            instantiateTemplate(body, bindings, addShadowed(shadowed, binders)),
          ],
          mappedProps: instantiateMappedProps(node.mappedProps, bindings, shadowed),
        };
      }
      return {
        ...node,
        args: node.args.map((arg) => instantiateTemplate(arg, bindings, shadowed)),
        mappedProps: instantiateMappedProps(node.mappedProps, bindings, shadowed),
      };
    }
    case "Assign":
      return { ...node, value: instantiateTemplate(node.value, bindings, shadowed) };
    default:
      return node;
  }
}
```

- [ ] **Step 4: Route `@Each` through the module and delete the special branch**

In `packages/lang-core/src/runtime/evaluator.ts`:

```ts
import { instantiateTemplate } from "./template-scope";
```

Delete the local `toLiteralAST`, `substituteRef`, and the entire `if (template.k === "Comp" && template.name === "Render" ...)` branch. Replace ordinary iteration setup with:

```ts
return arr.map((item) => {
  const instantiated = instantiateTemplate(template, new Map([[varName, item]]));
  const childCtx: EvaluationContext = {
    ...context,
    resolveRef: (refName: string) => {
      if (refName === varName) return item;
      return context.resolveRef(refName);
    },
  };
  const result = evaluate(instantiated, childCtx, schemaCtx);
  if (schemaCtx && isElementNode(result)) {
    return evaluateElementInline(result as ElementNode, childCtx, schemaCtx);
  }
  return result;
});
```

- [ ] **Step 5: Run focused and full lang-core tests**

Run:

```powershell
pnpm --filter @cloudsop/openui-lang-core exec vitest run src/__tests__/template-scope.test.ts src/__tests__/render-builtin.test.ts
pnpm --filter @cloudsop/openui-lang-core test
pnpm --filter @cloudsop/openui-lang-core typecheck
```

Expected: focused tests pass; full suite reports zero failures; TypeScript exits 0.

- [ ] **Step 6: Commit the runtime slice**

```powershell
git add -- packages/lang-core/src/runtime/template-scope.ts packages/lang-core/src/runtime/evaluator.ts packages/lang-core/src/__tests__/template-scope.test.ts
git commit -m "fix(lang-core): capture lexical template scope"
```

### Task 2: Hard error for unbound extracted-template references

**Files:**
- Modify: `packages/lang-core/src/parser/types.ts:72-82`
- Modify: `packages/lang-core/src/parser/materialize.ts:25-30,111-153`
- Modify: `packages/lang-core/src/parser/parser.ts:183-225`
- Modify: `packages/lang-core/src/parser/__tests__/parser.test.ts:112-160`

- [ ] **Step 1: Write failing parser tests**

Add to `packages/lang-core/src/parser/__tests__/parser.test.ts`:

```ts
describe("unbound extracted-template references", () => {
  it("reports a named template used without its required iterator binding", () => {
    const result = parse(
      "root = Stack([itemTpl])\nitemTpl = Title(item.name)",
      schema,
      "Stack",
    );

    expect(result.meta.errors).toContainEqual(
      expect.objectContaining({
        code: "unbound-template-reference",
        component: "itemTpl",
        statementId: "itemTpl",
        message: expect.stringContaining('requires binding "item"'),
      }),
    );
  });

  it("does not report when Each supplies the named template binding", () => {
    const result = parse(
      'root = Stack([@Each(data.items, "item", itemTpl)])\nitemTpl = Title(item.name)',
      schema,
      "Stack",
      { externalRefs: ["data"] },
    );

    expect(result.meta.errors).not.toContainEqual(
      expect.objectContaining({ code: "unbound-template-reference" }),
    );
  });

  it("keeps an unresolved entry-statement reference non-fatal while streaming", () => {
    const parser = createStreamParser(schema, "Stack");
    const result = parser.push("root = Stack([future])\n");

    expect(result.meta.unresolved).toContain("future");
    expect(result.meta.errors).not.toContainEqual(
      expect.objectContaining({ code: "unbound-template-reference" }),
    );
  });
});
```

- [ ] **Step 2: Run the parser tests and verify the hard-error test fails**

Run:

```powershell
pnpm --filter @cloudsop/openui-lang-core exec vitest run src/parser/__tests__/parser.test.ts -t "unbound extracted-template references"
```

Expected: the first test fails because no `unbound-template-reference` error exists; the bound and streaming cases remain non-fatal.

- [ ] **Step 3: Add the structured error code and entry context**

Replace the end of `ValidationErrorCode` in `packages/lang-core/src/parser/types.ts` with:

```ts
  | "invalid-prop"
  | "unbound-template-reference";
```

Add this optional field to `MaterializeCtx` in `packages/lang-core/src/parser/materialize.ts`:

```ts
  /** Root statement for distinguishing transient entry refs from template free refs. */
  entryStatementId?: string;
```

Set it in `buildResult` in `packages/lang-core/src/parser/parser.ts`:

```ts
const ctx: MaterializeCtx = {
  syms,
  cat,
  errors,
  unres,
  visited: new Set(),
  partial: wasIncomplete,
  externalRefs: options?.externalRefs?.length ? new Set(options.externalRefs) : undefined,
  currentStatementId: entryId,
  entryStatementId: entryId,
  unreached,
};
```

- [ ] **Step 4: Emit the error only from an extracted statement**

In the missing-symbol branch of `resolveRef` in `packages/lang-core/src/parser/materialize.ts`, after the `externalRefs` check and before returning the placeholder, add:

```ts
const templateName = ctx.currentStatementId;
if (
  mode === "expr" &&
  templateName &&
  ctx.entryStatementId &&
  templateName !== ctx.entryStatementId
) {
  const available = [...scopedRefs].sort();
  ctx.errors.push({
    code: "unbound-template-reference",
    component: templateName,
    path: "",
    message:
      `Template "${templateName}" requires binding "${name}", but its use site does not provide it. ` +
      `Available bindings: ${available.length > 0 ? available.join(", ") : "none"}.`,
    statementId: templateName,
  });
}
```

Keep `ctx.unres.push(name)` and placeholder behavior for backward-compatible streaming metadata.

- [ ] **Step 5: Run parser and full package tests**

Run:

```powershell
pnpm --filter @cloudsop/openui-lang-core exec vitest run src/parser/__tests__/parser.test.ts
pnpm --filter @cloudsop/openui-lang-core test
pnpm --filter @cloudsop/openui-lang-core typecheck
```

Expected: all parser and package tests pass with no TypeScript errors.

- [ ] **Step 6: Commit the validation slice**

```powershell
git add -- packages/lang-core/src/parser/types.ts packages/lang-core/src/parser/materialize.ts packages/lang-core/src/parser/parser.ts packages/lang-core/src/parser/__tests__/parser.test.ts
git commit -m "feat(lang-core): report unbound template references"
```

### Task 3: Align model-visible template guidance and generated contracts

**Files:**
- Modify: `packages/lang-core/src/parser/builtins.ts:503-513`
- Modify: `packages/lang-core/src/parser/prompt.ts:189-195`
- Modify: `packages/lang-core/src/parser/__tests__/prompt.test.ts`
- Modify: `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx:156-175`
- Regenerate: `packages/react-ui-dsl/generated/base-contract.json`
- Regenerate: `packages/genui-java-sdk/src/main/resources/openui/base-contract.json`

- [ ] **Step 1: Write failing prompt contract assertions**

Add to the existing prompt tests in `packages/lang-core/src/parser/__tests__/prompt.test.ts`:

```ts
it("allows Each templates to be extracted as named statements", () => {
  const prompt = generatePrompt(baseSpec);

  expect(prompt).toContain("The template may be inline or a named statement");
  expect(prompt).toContain("transitive statement dependencies may reference item");
  expect(prompt).not.toContain("Always inline the template");
  expect(prompt).not.toContain("do NOT extract it to a separate statement");
});
```

- [ ] **Step 2: Run the prompt test and verify it fails on inline-only guidance**

Run:

```powershell
pnpm --filter @cloudsop/openui-lang-core exec vitest run src/parser/__tests__/prompt.test.ts -t "allows Each templates"
```

Expected: required named-template text is absent and inline-only text remains.

- [ ] **Step 3: Replace the builtin and prompt wording**

Set `LAZY_BUILTIN_DEFS.Each.description` in `packages/lang-core/src/parser/builtins.ts` to:

```ts
description:
  "Evaluate a template once per array element. varName is the lexical iterator binding. The template may be inline or a named statement; named templates and their transitive statement dependencies may reference the iterator binding. Every iteration has an independent scope.",
```

Replace the inline-only `@Each` block in `packages/lang-core/src/parser/prompt.ts` with:

```ts
IMPORTANT @Each rule: The loop variable (e.g. "item") is available throughout the @Each template and its transitive named-statement dependencies.
The template may be inline or a named statement. Each iteration creates an independent lexical scope.
INLINE: `Col("Actions", @Each(rows, "t", Button("Edit", Action([@Set($id, t.id)]))))`
EXTRACTED: `items = @Each(rows, "t", itemTpl)` then `itemTpl = TextContent(t.name)`.
```

Replace the contradictory pseudo-template rule in `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx` with:

```ts
  'An @Each template may be inline or a named statement. Named template statements and their transitive dependencies may reference the declared iterator binding; do not use those statements outside a scope that provides the binding.',
```

- [ ] **Step 4: Run prompt tests and regenerate the base contracts**

Run:

```powershell
pnpm --filter @cloudsop/openui-lang-core exec vitest run src/parser/__tests__/prompt.test.ts src/__tests__/builtins-manifest.test.ts
pnpm --filter @cloudsop/openui-react-ui-dsl generate:base-contract
```

Expected: tests pass. Both generated JSON files contain the new `Each` description and do not contain `Do NOT create a separate statement for the template.`

- [ ] **Step 5: Verify generated contract parity**

Run:

```powershell
$dslContract = Get-FileHash 'packages/react-ui-dsl/generated/base-contract.json'
$javaContract = Get-FileHash 'packages/genui-java-sdk/src/main/resources/openui/base-contract.json'
if ($dslContract.Hash -ne $javaContract.Hash) { throw 'base contract outputs differ' }
rg -n 'The template may be inline or a named statement' packages/react-ui-dsl/generated/base-contract.json packages/genui-java-sdk/src/main/resources/openui/base-contract.json
```

Expected: hashes match and both paths contain the canonical rule.

- [ ] **Step 6: Commit contract alignment**

```powershell
git add -- packages/lang-core/src/parser/builtins.ts packages/lang-core/src/parser/prompt.ts packages/lang-core/src/parser/__tests__/prompt.test.ts packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx packages/react-ui-dsl/generated/base-contract.json packages/genui-java-sdk/src/main/resources/openui/base-contract.json
git commit -m "docs(lang-core): allow extracted Each templates"
```

### Task 4: End-to-end syntax regression and package verification

**Files:**
- Modify: `packages/lang-core/src/__tests__/template-scope.test.ts`
- Inspect only: `packages/lang-core/dist/*`

- [ ] **Step 1: Add the full extracted-template syntax regression**

Add these imports to `packages/lang-core/src/__tests__/template-scope.test.ts`:

```ts
import { parse } from "../parser/parser";
import type { ParamMap } from "../parser/types";
import { evaluateElementProps } from "../runtime/evaluate-tree";
```

Then add this test:

```ts
it("captures Each bindings through an extracted template dependency graph", () => {
  const schema: ParamMap = new Map([
    ["Stack", { params: [{ name: "children", required: true }] }],
    ["Tabs", { params: [{ name: "items", required: true }] }],
    [
      "TabItem",
      {
        params: [
          { name: "value", required: true },
          { name: "label", required: true },
          { name: "content", required: true },
        ],
      },
    ],
    [
      "Table",
      {
        params: [
          { name: "columns", required: true },
          { name: "rows", required: true },
        ],
      },
    ],
    [
      "Col",
      {
        params: [
          { name: "title", required: true },
          { name: "field", required: true },
          { name: "options", required: false },
        ],
      },
    ],
    ["TextContent", { params: [{ name: "text", required: true }] }],
  ]);
  const dsl = `root = Stack([modelTabs])
modelTabs = Tabs(@Each(data.grouped_by_model, "group", modelTabTpl))
modelTabTpl = TabItem(group.model, group.model + " (" + group.count + " devices)", [modelTable])
modelTable = Table([modelCol, countCol], group.devices)
modelCol = Col("Model", "model", {cell: @Render("v", "row", TextContent(group.model))})
countCol = Col("Count", "count", {cell: @Render("v", "row", TextContent("" + group.count))})`;
  const data = {
    grouped_by_model: [
      { model: "Model-X", count: 2, devices: [{ model: "row-model", count: 999 }] },
    ],
  };
  const parsed = parse(dsl, schema, "Stack", { externalRefs: ["data"] });
  const library = {
    components: Object.fromEntries(
      [...schema.keys()].map((name) => [name, { props: { shape: {} } }]),
    ),
  };
  const evaluated = evaluateElementProps(parsed.root!, {
    ctx: {
      getState: () => null,
      resolveRef: (name) => (name === "data" ? data : null),
    },
    library,
  });
  const tabs = (evaluated.props.children as ElementNode[])[0];
  const tab = (tabs.props.items as ElementNode[])[0];
  const table = (tab.props.content as ElementNode[])[0];
  const [modelColumn, countColumn] = table.props.columns as ElementNode[];
  const modelSlot = (modelColumn.props.options as { cell: ASTNode }).cell as ASTNode & {
    k: "Comp";
  };
  const countSlot = (countColumn.props.options as { cell: ASTNode }).cell as ASTNode & {
    k: "Comp";
  };
  const modelBody = modelSlot.args.at(-1) as ASTNode & { k: "Comp" };
  const countBody = countSlot.args.at(-1) as ASTNode & { k: "Comp" };

  expect(parsed.meta.errors).toEqual([]);
  expect(table.props.rows).toEqual([{ model: "row-model", count: 999 }]);
  expect(modelBody.mappedProps?.text).toEqual({ k: "Str", v: "Model-X" });
  expect(countBody.mappedProps?.text).toEqual({
    k: "BinOp",
    op: "+",
    left: { k: "Str", v: "" },
    right: { k: "Num", v: 2 },
  });
});
```

This is a regression guard; the shadowing tests in Task 1 provide the mandatory RED signal for the implementation.

- [ ] **Step 2: Run all directly affected package checks**

Run:

```powershell
pnpm --filter @cloudsop/openui-lang-core test
pnpm --filter @cloudsop/openui-lang-core typecheck
pnpm --filter @cloudsop/openui-lang-core lint:check
pnpm --filter @cloudsop/openui-lang-core build
```

Expected: all commands exit 0. If repository-wide pre-existing formatting failures remain, run Prettier only against changed TypeScript files and report unrelated baseline failures separately.

- [ ] **Step 3: Verify the built artifact contains the new path**

Run:

```powershell
rg -n 'instantiateTemplate|callable-template semantics' packages/lang-core/dist packages/lang-core/src/runtime
```

Expected: source contains `instantiateTemplate`; built runtime imports or bundles its behavior; neither source nor built output contains `callable-template semantics`.

- [ ] **Step 4: Check the final diff and working tree isolation**

Run:

```powershell
git diff --check 1e3ffc93..HEAD
git status --short
```

Expected: no whitespace errors in the implementation commits. Existing user changes to `.gitignore`, `skills-lock.json`, and `test.md` remain untouched and uncommitted.

- [ ] **Step 5: Commit the end-to-end regression if it was not included earlier**

```powershell
git add -- packages/lang-core/src/__tests__/template-scope.test.ts
git commit -m "test(lang-core): cover extracted Render scope"
```

## Plan self-review

- Scope covers all accepted decisions: named templates, transitive dependencies, per-instance capture, nested shadowing, render shadowing, live state, unbound errors, removal of callable-Render behavior, prompt alignment, and packaged-output verification.
- No concrete UI component implementation is required; `TabItem` appears only as syntax/schema shape in the end-to-end regression.
- Publishing is intentionally excluded because it changes external registry state. The plan builds and verifies the artifact; release/version selection remains a separate explicit action.
