# react-ui-dsl-demo

A Vite + React demo that generates UI from a text prompt using `@openuidev/react-ui-dsl` for both rendering and system prompt generation.

## Prerequisites

Build the workspace packages before starting the demo. The server and Vite client both resolve `@openuidev/react-ui-dsl` and its transitive workspace deps from TypeScript source via tsconfig paths / Vite aliases, so no separate publish step is needed. However, if lang-core or react-lang have changed, rebuild them first:

```bash
# From the monorepo root (only needed after source changes to these packages)
pnpm --filter @openuidev/lang-core build
pnpm --filter @openuidev/react-lang build
```

## Setup

```bash
cp .env.example .env
# Fill in OPENAI_API_KEY in .env
```

Install dependencies (from the monorepo root or this directory):

```bash
pnpm install
```

## Dev

```bash
# From this directory
pnpm dev
```

Starts the Express server on port 3001 and the Vite app on port 5173.

## How it works

- **Client** (`src/App.tsx`): imports `dslLibrary` from `@openuidev/react-ui-dsl` and passes it to the `<Renderer>` from `@openuidev/react-lang`. Vite resolves the package to TypeScript source via the alias in `vite.config.ts`.
- **Server** (`server/systemPrompt.ts`): imports the same `dslLibrary` and calls `dslLibrary.prompt()` to generate the system prompt at startup. tsx resolves the package to TypeScript source via the `paths` mapping in `tsconfig.json`.

## Peer dependencies

`@openuidev/react-ui-dsl` requires the following peers (already listed in `package.json`):

- `antd ^5` — component styling via CSS-in-JS, no stylesheet import needed
- `echarts ^5` — chart rendering
- `react-markdown ^10` — markdown text rendering (optional)
- `zod ^4`
