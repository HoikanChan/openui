# Technology Stack

## Project Type

TypeScript library (npm package). Dual ESM + CJS build. No runtime framework dependencies.

## Core Technologies

### Primary Language
- **TypeScript** — ESNext module syntax, `bundler` moduleResolution
- **Node.js** — runtime target (no browser-specific APIs in core)
- **pnpm** — package manager (monorepo workspace: `packages/lang-core`)

### Key Dependencies

#### Peer Dependencies (consumer must install)
- **zod ^4.0.0** — component prop schema definitions via `z.ZodObject`. Key to positional-to-named argument mapping and prompt signature generation.
- **@modelcontextprotocol/sdk >=1.0.0** (optional) — MCP tool call execution. Used in `src/runtime/mcp.ts` and `src/runtime/toolProvider.ts`.

#### Dev Dependencies
- **vitest ^4.0.18** — test runner
- **tsdown** — build tool producing dual CJS (`dist/index.cjs`) + ESM (`dist/index.mjs`) outputs with `.d.cts` / `.d.mts` type declarations

### Architecture

Pipeline-based library with three major subsystems:

1. **Parser subsystem** (`src/parser/`) — lexer → tokenizer → parser → AST → materializer → prompt generator. Stateless functions; streaming state held in `StreamParser` instances.
2. **Runtime subsystem** (`src/runtime/`) — evaluator, reactive store, query/mutation manager, MCP adapter. Stateful; consumers create instances via factory functions (`createStore`, `createQueryManager`).
3. **Library API** (`src/library.ts`) — Zod-based component registration. Bridges consumer-defined schemas to the parser's `LibraryJSONSchema` format.

### External Integrations
- **MCP (Model Context Protocol)** — tool execution via `McpClientLike` interface (structural typing, not hard dependency)
- No HTTP clients, no database drivers, no file system access in public API

## Development Environment

### Build & Scripts
| Script | Purpose |
|--------|---------|
| `pnpm build` | `tsdown` — produces `dist/` |
| `pnpm watch` | `tsdown --watch` for development |
| `pnpm test` | `vitest run` |
| `pnpm typecheck` | `tsc --noEmit` |
| `pnpm lint:check` | ESLint on `./src` |
| `pnpm format:check` | Prettier check on `./src` |
| `pnpm check:publint` | Validates package.json exports |
| `pnpm check:attw` | Validates type declaration correctness |
| `pnpm ci` | lint + format check (used in CI) |
| `pnpm prepublishOnly` | publint + attw (gates npm publish) |

### Code Quality
- **ESLint** — static analysis
- **Prettier** — formatting
- **publint** — validates package exports correctness
- **@arethetypeswrong/cli (attw)** — validates TypeScript declaration file correctness for dual CJS/ESM builds

### tsconfig
- `noEmit: true` (build handled by tsdown, not tsc)
- `moduleResolution: bundler`
- `module: ESNext`
- Relaxed: `noPropertyAccessFromIndexSignature: false`, `noUncheckedIndexedAccess: false`, `noImplicitReturns: false`, `noImplicitOverride: false`

## Technical Constraints

- **No framework imports** — `lang-core` must never import React, Vue, Svelte, or DOM APIs
- **Zod v4** — peer dep is `^4.0.0`; breaking from Zod v3 API surface
- **Dual build requirement** — both CJS and ESM must be produced and verified with attw before publish
- **Tree-shakeable** — `"sideEffects": false` in package.json; avoid module-level side effects
