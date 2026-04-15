# react-ui-dsl-demo

A Vite + React demo that generates UI from a text prompt using OpenUI's DSL runtime.

## Prerequisites

Build the workspace packages first (required for module resolution and CSS):

```bash
# From the monorepo root
pnpm --filter @openuidev/react-lang build
pnpm --filter @openuidev/react-ui build
```

## Setup

```bash
cp .env.example .env
# Fill in OPENAI_API_KEY in .env
```

## Dev

```bash
# From this directory
pnpm dev
```

Starts the Express server on port 3001 and the Vite app on port 5173.

## Swapping in the real library

When `@openuidev/react-ui-dsl` is ready:
1. Add `"@openuidev/react-ui-dsl": "workspace:*"` to `package.json` dependencies
2. Replace `src/lib/placeholderLibrary.ts` contents with `export { dslLibrary } from "@openuidev/react-ui-dsl"`
3. Replace `server/systemPrompt.ts` import with `import { dslLibrary } from "@openuidev/react-ui-dsl"`
