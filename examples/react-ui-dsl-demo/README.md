# react-ui-dsl-demo

A Vite + React demo that generates UI from a text prompt. Rendering uses `@openuidev/react-ui-dsl`; prompt assembly and LLM generation are served by **GenUI Service** (`examples/genui-service`, Java) — the REST reference implementation of the Java Generation SDK.

## Prerequisites

- **Node.js + pnpm** — for the Vite client.
- **JDK ≥ 21 + Maven** — for GenUI Service (`examples/genui-service`). Java 21 is a hard requirement of `packages/genui-java-sdk` (it uses records).

Build the workspace packages before starting the demo if lang-core or react-lang have changed:

```bash
# From the monorepo root (only needed after source changes to these packages)
pnpm --filter @openuidev/lang-core build
pnpm --filter @openuidev/react-lang build
```

## Setup

```bash
cp .env.example .env
# Fill in LLM_API_KEY / LLM_BASE_URL / LLM_MODEL in .env
```

Install dependencies (from the monorepo root or this directory):

```bash
pnpm install
```

## Dev

**Startup order matters: start GenUI Service first, then the Vite client.**

```bash
# 1. From the monorepo root — start GenUI Service on port 3001
#    (reads LLM_* env vars; on Unix-like shells:)
set -a; source examples/react-ui-dsl-demo/.env; set +a
mvn -pl examples/genui-service spring-boot:run

# 2. From this directory — start the Vite app on port 5173
pnpm dev
```

The first `mvn` run downloads dependencies and generates the API interfaces from `examples/genui-service/src/main/resources/swagger/genui-service.yaml` (Swagger 2.0, codegen at build time).

## How it works

- **Client** (`src/App.tsx`): imports `dslLibrary` from `@openuidev/react-ui-dsl` and passes it to the `<Renderer>` from `@openuidev/react-lang`. Vite resolves the package to TypeScript source via the alias in `vite.config.ts`.
- **GenUI Service** (`examples/genui-service`): wraps the Java Generation SDK. The prompt tab shows the prompt assembled by `POST /v1/prompts/assemble` (byte-aligned with the TypeScript `dslLibrary.prompt()`); `POST /v1/generate` assembles the prompt, calls the LLM, and streams `openui-lang` back as plain text.
- **Context selector**: the sidebar dropdown lists Generation Contexts from `GET /v1/contexts`. The service seeds three presets at startup (`noe-alarm-tools` tools, `noe-ops-rules` rules, `noe-biz-components` custom components); selecting one adds its contracts to the assembled prompt. Registrations are in-memory — restart restores only the seeds.
- **Tool execution**: `Query`/`Mutation` nodes in generated DSL are executed through `POST /v1/tools/{toolName}/execute` (the client wires `Renderer`'s `toolProvider` to it). The reference service ships mock executors for the seed tools; replace `SeedToolExecutors` with your real tool channel when adapting it in-house.
- **Prompt Override (debug)**: editing the prompt tab marks it dirty; generation then sends your edited text as `promptOverride`, bypassing server-side assembly entirely. Production callers should not use this field.

## Registering your own extension

See `examples/genui-service/register-extension.http` for a copy-paste REST walkthrough (register → list → assemble).

Component extensions need both sides: the backend contract (model-visible, goes into the prompt) and a front-end implementation registered via `dslLibrary.extend()` (renderable). `src/extensions.tsx` is the worked example paired with the `noe-biz-components` seed — it defines the `AlarmBadge` React component with matching name/props and swaps the extended library into the Renderer/parser when that context is selected.

## Peer dependencies

`@openuidev/react-ui-dsl` requires the following peers (already listed in `package.json`):

- `antd ^5` — component styling via CSS-in-JS, no stylesheet import needed
- `echarts ^5` — chart rendering
- `react-markdown ^10` — markdown text rendering (optional)
- `zod ^4`
