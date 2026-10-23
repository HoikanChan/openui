# @cloudsop/openui-lang-core

Framework-agnostic core for [OpenUI Lang](https://openui.com): parser, prompt generation, runtime evaluator, and type definitions.

[![npm](https://img.shields.io/npm/v/@cloudsop/openui-lang-core)](https://www.npmjs.com/package/@cloudsop/openui-lang-core)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://github.com/thesysdev/openui/blob/main/LICENSE)

## Install

```bash
npm install @cloudsop/openui-lang-core
# or
pnpm add @cloudsop/openui-lang-core
```

## What this package does

`@cloudsop/openui-lang-core` is the framework-agnostic foundation that powers OpenUI Lang. It has no React or other framework dependencies. Use it when you need to:

- **Parse** OpenUI Lang text into a typed element tree (one-shot or streaming)
- **Generate system prompts** from a component spec + tool definitions
- **Evaluate** reactive expressions, `$variables`, and query results at runtime
- **Merge** incremental edits into existing programs

If you're building a framework-specific app, use `@cloudsop/openui-react-lang`, `@openuidev/vue-lang` or `@openuidev/svelte-lang` instead. It re-exports everything from this package plus framework-specific components and hooks.

## Quick Start

### Parse OpenUI Lang

```ts
import { createParser } from "@cloudsop/openui-lang-core";

const parser = createParser(libraryJsonSchema);
const result = parser.parse(`
root = Stack([header, content])
header = CardHeader("Hello")
content = TextContent("World")
`);

console.log(result.root);       // ElementNode tree
console.log(result.meta);       // { incomplete, unresolved, statementCount, validationErrors }
```

### Streaming parser

```ts
import { createStreamingParser } from "@cloudsop/openui-lang-core";

const sp = createStreamingParser(libraryJsonSchema);

// Feed chunks as they arrive from the LLM
const result1 = sp.set("root = Stack([header])\n");
const result2 = sp.set("root = Stack([header])\nheader = CardHeader(\"Hello\")\n");
// result2.root now resolves the forward reference
```

### Generate a system prompt

```ts
import { generatePrompt, type PromptSpec } from "@cloudsop/openui-lang-core";
import componentSpec from "./generated/component-spec.json";

const prompt = generatePrompt({
  ...componentSpec,
  tools: myToolSpecs,
  toolCalls: true,
  bindings: true,
  editMode: true,
  preamble: "You build dashboards.",
});
```

### Merge incremental edits

```ts
import { mergeStatements } from "@cloudsop/openui-lang-core";

const original = `root = Stack([header, tbl])\nheader = CardHeader("Tickets")\ntbl = Table([...])`;
const patch = `root = Stack([header, chart, tbl])\nchart = PieChart(...)`;
const merged = mergeStatements(original, patch);
// header and tbl kept from original, root replaced, chart added
```

## API

### Parser

| Export | Description |
| :--- | :--- |
| `createParser(schema)` | One-shot parser for complete text |
| `createStreamingParser(schema)` | Incremental parser for streaming input |
| `parse(input, schema)` | Convenience one-shot parse |

### Prompt Generation

| Export | Description |
| :--- | :--- |
| `generatePrompt(spec)` | Generate a system prompt from a `PromptSpec` |

**`PromptSpec`** includes component signatures, tool definitions (`ToolSpec[]`), feature flags (`toolCalls`, `bindings`, `editMode`, `inlineMode`), examples, and custom rules.

**`ToolSpec`** describes a tool for prompt generation (name, description, inputSchema, outputSchema). Shape inspired by MCP's tool schema.

### Runtime

| Export | Description |
| :--- | :--- |
| `createQueryManager(toolProvider)` | Manages Query/Mutation execution and caching |
| `createStore()` | Reactive store for `$variables` and form state |
| `evaluate(ast, context)` | Evaluate an AST node to a concrete value |
| `evaluateElementProps(root, context)` | Recursively evaluate all props in an element tree |
| `extractToolResult(result)` | Extract data from an MCP `callTool` response |
| `mergeStatements(original, patch)` | Merge incremental edits by statement name |

### Types

```ts
import type {
  PromptSpec,
  ToolSpec,
  ParseResult,
  ElementNode,
  ToolProvider,
  McpClientLike,
  QueryManager,
  Store,
  OpenUIError,
} from "@cloudsop/openui-lang-core";
```

## Documentation

Full documentation, guides, and the language specification are available at **[openui.com](https://openui.com)**.

## License

[MIT](https://github.com/thesysdev/openui/blob/main/LICENSE)

### Capability gaps and contract export

`GenerationContract.capabilityGaps` describes known unsupported capabilities.
Each entry has `id`, `kind` (`"data" | "component"`), `name`, `aliases`,
`phrases`, and `fallback`. `CapabilityGapSchema` is the public runtime schema.
`createLibrary({ components, capabilityGaps }).toSpec()` exports these entries;
`extend({ capabilityGaps })` appends entries. Omission uses the built-in
`group-by`, `top-n`, and `join` gaps; an explicit `[]` disables defaults.
Duplicate IDs and aliases matching component or builtin names fail export.

To export a complete JSON contract, call `exportGenerationContract(source)`.
This preserves every other source field, including structured builtin type
rules, and validates gaps against the component and builtin names actually in
that source. Builtin entries must include their authoritative `name`; this
exporter does not derive executable rules or names from display signatures.

```bash
pnpm --dir packages/lang-core run generate:base-contract
pnpm --dir packages/lang-core run generate:base-contract --input source.json --output contract.json
```

The default command validates and regenerates the shared canonical artifact at
`packages/genui-java-sdk/src/main/resources/openui/base-contract.json`.
Validation completes before the output is replaced. Source/output paths supplied
on the command line are relative to the command's working directory.

This branch removed the former `react-ui-dsl` package and its component export
script. The surviving TypeScript exporter owns capability-gap schema/defaults
and preserves the current canonical contract; it does not reconstruct the removed
frontend component library or replace structured builtin rules with the older
runtime documentation manifest. No second contract snapshot is maintained.
Broader frontend structured-rule export work is tracked by issue #91.
