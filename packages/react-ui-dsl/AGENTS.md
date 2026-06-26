# react-ui-dsl — Agent Guide

## Build / Test / Lint

```bash
# From monorepo root:
pnpm --filter @cloudsop/openui-react-ui-dsl run build
pnpm --filter @cloudsop/openui-react-ui-dsl run test
pnpm --filter @cloudsop/openui-react-ui-dsl run ci       # lint:check + format:check

# Or from this directory:
pnpm build && pnpm test
```

## Do NOT Change

### E2E Snapshots — `src/__tests__/e2e/snapshots/`

**Never manually edit any `.dsl` file in this directory.**

These files are LLM-generated outputs captured during a regen run. They are the test inputs for `dsl-e2e.test.tsx` — editing them directly to make a test pass is not a fix; it hides the real problem.

If an e2e test fails:
- Fix the DSL component, renderer, or fixture definition — not the snapshot.
- If the snapshot is legitimately stale (e.g., after a DSL schema change), regenerate it:

```bash
# From this directory:
pnpm test:e2e:regen
```

> **Note:** API keys and LLM configuration (`LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`) are already set in `packages/react-ui-dsl/.env`. Do **not** ask the user to provide a key — just run the command directly.

The regen script calls the LLM with the fixture prompt and overwrites the snapshot. That is the only correct way to update these files.
