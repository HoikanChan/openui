# Project Structure

## Directory Layout

```
packages/lang-core/
├── src/
│   ├── index.ts                  # Single public entry point — re-exports everything
│   ├── library.ts                # defineComponent / createLibrary API
│   ├── reactive.ts               # markReactive / isReactiveSchema helpers
│   ├── parser/
│   │   ├── index.ts              # Parser public API (createParser, createStreamingParser, parse)
│   │   ├── lexer.ts              # Tokenizes raw OpenUI Lang text
│   │   ├── tokens.ts             # Token type definitions
│   │   ├── parser.ts             # Recursive descent parser → raw AST
│   │   ├── ast.ts                # AST node types and type guards
│   │   ├── statements.ts         # Statement-level parsing logic
│   │   ├── expressions.ts        # Expression-level parsing logic
│   │   ├── materialize.ts        # AST → ElementNode tree (resolves refs, maps positional args)
│   │   ├── merge.ts              # mergeStatements() — incremental patch logic
│   │   ├── prompt.ts             # generatePrompt() and PromptSpec / ToolSpec types
│   │   ├── builtins.ts           # Built-in function/action definitions (Query, Mutation, Action, etc.)
│   │   ├── enrich-errors.ts      # Adds hints to ValidationErrors for LLM correction
│   │   ├── types.ts              # Shared parser output types (ElementNode, ParseResult, OpenUIError, etc.)
│   │   └── __tests__/
│   │       └── parser.test.ts    # Parser unit tests
│   ├── runtime/
│   │   ├── index.ts              # Runtime public re-exports
│   │   ├── evaluator.ts          # evaluate() — AST node → concrete value
│   │   ├── evaluate-prop.ts      # Single-prop evaluation logic
│   │   ├── evaluate-tree.ts      # evaluateElementProps() — recursive tree evaluation
│   │   ├── store.ts              # createStore() — reactive $variable store
│   │   ├── state-field.ts        # StateField type and resolveStateField()
│   │   ├── queryManager.ts       # createQueryManager() — Query/Mutation execution and caching
│   │   ├── mcp.ts                # MCP tool call adapter and extractToolResult()
│   │   └── toolProvider.ts       # ToolProvider interface and ToolNotFoundError
│   └── utils/
│       └── validation.ts         # builtInValidators, parseRules, validate()
├── dist/                         # Build output (gitignored)
│   ├── index.cjs                 # CommonJS bundle
│   ├── index.mjs                 # ESM bundle
│   ├── index.d.cts               # CJS type declarations
│   └── index.d.mts               # ESM type declarations
├── package.json
├── tsconfig.json
├── tsconfig.test.json
└── README.md
```

## Naming Conventions

### Files
- **All source files**: `kebab-case.ts` (e.g., `query-manager.ts`, `evaluate-tree.ts`)
- **Test files**: colocated in `__tests__/` subdirectory, named `[module].test.ts`

### Code
- **Types/Interfaces**: `PascalCase` (e.g., `ElementNode`, `ParseResult`, `ToolSpec`)
- **Enums**: `PascalCase` with `PascalCase` members (e.g., `BuiltinActionType.ContinueConversation`)
- **Functions**: `camelCase` (e.g., `createParser`, `generatePrompt`, `mergeStatements`)
- **Constants**: `UPPER_SNAKE_CASE` for module-level (e.g., `BUILTINS`, `ACTION_NAMES`)
- **Variables/parameters**: `camelCase`
- **Type parameters**: single uppercase letter or `PascalCase` (e.g., `T`, `C`, `RenderNode`)

## Import Patterns

- Relative imports only (no path aliases configured)
- Type-only imports use `import type { ... }` syntax
- `src/index.ts` is the only file that imports across subsystem boundaries for re-export
- Internal subsystem files import from sibling files directly (no barrel files within subsystems except `parser/index.ts` and `runtime/index.ts`)

## Module Boundaries

| Layer | Can import from | Cannot import from |
|-------|----------------|-------------------|
| `src/index.ts` | everything | — |
| `src/library.ts` | `parser/prompt`, `parser/types`, `reactive` | `runtime/` |
| `src/parser/**` | `parser/**` siblings, `reactive` | `runtime/`, `library` |
| `src/runtime/**` | `runtime/**` siblings, `parser/types`, `parser/ast` | `library`, `parser/prompt` |
| `src/utils/**` | nothing internal | everything |

**Critical constraint**: Never import React, Vue, Svelte, DOM, or Node.js-specific APIs into any file in this package.

## Public API Design

- `src/index.ts` is the single export surface — all public types and functions must be re-exported here
- Internal implementation details that are not re-exported from `src/index.ts` are private
- Factory functions (`createParser`, `createStore`, `createQueryManager`) are preferred over classes for stateful subsystems
- Type-only exports use `export type` to keep them tree-shakeable

## Testing

- Test runner: **vitest**
- Test files: colocated in `src/parser/__tests__/` (runtime tests to be added similarly)
- `tsconfig.test.json` includes test files (excluded from main `tsconfig.json`)
- Run with `pnpm test` (vitest run — no watch mode in CI)

## Code Style Principles

- Prefer explicit `export type` for type-only exports
- JSDoc comments on all exported types and functions
- Avoid module-level side effects (package is `"sideEffects": false`)
- Error types are structured for machine consumption (LLM correction loops) — include `code`, `statementId`, `hint`
