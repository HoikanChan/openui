# `@openuidev/react-ui-dsl`

DSL component library for OpenUI built on React, Ant Design v5, and ECharts.

## Development

Run commands from `packages/react-ui-dsl`:

```bash
pnpm build
pnpm test
pnpm typecheck
pnpm ci
```

## E2E Workflow

The package keeps committed `.dsl` snapshots under `src/__tests__/e2e/snapshots` and validates them against fixture-based e2e tests in `src/__tests__/e2e`.

Common commands:

```bash
pnpm test:e2e
pnpm test:e2e:report
pnpm test:e2e:regen
pnpm test:e2e:regen:fixture -- -t table-basic
pnpm test:e2e:regen:fixture -- -t "table-basic|button-primary"
pnpm test:fuzz
pnpm test:fuzz:report
pnpm test:fuzz:regen
```

What each command does:

- `pnpm test:e2e` runs the e2e suite without generating a report.
- `pnpm test:e2e:report` runs the e2e suite and writes a timestamped HTML report to `src/__tests__/e2e/reports/<timestamp>/index.html`.
- `pnpm test:e2e:regen` regenerates all committed `.dsl` snapshots to match the current fixture set.
- `pnpm test:e2e:regen:fixture -- -t <fixture-id>` regenerates snapshots only for fixtures whose test names match the Vitest pattern.
- `pnpm test:e2e:regen:fixture -- -t "fixture-a|fixture-b"` regenerates multiple fixtures in one run by using a regex pattern.
- `pnpm test:e2e:report -- --update-snapshot <fixture-id>` is the report-oriented flow: it regenerates one fixture, runs that fixture, and opens the HTML report.
- `pnpm test:fuzz` runs only the fuzz suite and keeps fuzz out of the default `pnpm test:e2e` run.
- `pnpm test:fuzz:report` runs only the fuzz suite and writes a timestamped HTML report to `src/__tests__/e2e/reports/<timestamp>/index.html`.
- `pnpm test:fuzz:regen` regenerates the committed fuzz snapshots under `src/__tests__/e2e/fuzz-snapshots`.

## Snapshot Regeneration

Snapshot regeneration uses the configured LLM and expects the credentials to be configured in `packages/react-ui-dsl/.env`.

Start from `.env.example` and create `.env`:

```bash
cp .env.example .env
```

Example `.env`:

```bash
LLM_API_KEY=sk-...
LLM_BASE_URL=https://api.deepseek.com/v1
# LLM_MODEL=deepseek-chat
```

After `.env` is configured, run:

```bash
pnpm test:e2e:regen
pnpm test:e2e:regen:fixture -- -t table-basic
```

If `src/__tests__/e2e/fixtures.test.ts` fails with `DSL snapshot coverage is out of date`, that is a fixture/snapshot sync problem rather than a renderer regression. Regenerate the missing snapshots and commit the updated `.dsl` files.

Do not edit files in `src/__tests__/e2e/snapshots` manually. They are generated test inputs and should only be updated through the regen commands.
