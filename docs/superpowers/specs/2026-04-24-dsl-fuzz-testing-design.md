# DSL Fuzz Testing Design

**Date:** 2026-04-24  
**Scope:** `packages/react-ui-dsl`

## Goal

Validate that the DSL generation pipeline (LLM prompt → DSL → parse → render) does not crash or produce parse errors when given real-world API response data as `dataModel`. No semantic assertions — the only success criteria are:

1. Parser reports zero errors
2. React renderer does not throw

## Why a Separate Fuzz Suite

The existing e2e fixtures (`fixtures.ts`) are curated scenarios with explicit content assertions. Fuzz data comes from real API integrations, can number in the hundreds, and has no pre-known expected output. Mixing them into the default `test:e2e` run would make it too slow and noisy. Fuzz tests run on-demand via a dedicated script.

## Directory Structure

```
packages/react-ui-dsl/src/__tests__/e2e/
  fuzz-data/           # user drops JSON files here
  fuzz-snapshots/      # generated .dsl snapshots (same pattern as snapshots/)
  dsl-fuzz.test.tsx    # new test file
```

## Input Convention

Each file in `fuzz-data/` is a raw JSON API response used directly as `dataModel`.

**Filename format:** `{ComponentHint}-{description}.json`

Examples:
- `table-employee-list.json` → `"Show a Table for the given data"`
- `linechart-bandwidth.json` → `"Show a LineChart for the given data"`
- `card-device-status.json` → `"Show a Card for the given data"`

The first segment before `-` is the component hint (case-insensitive). If it doesn't match a known DSL component, the prompt falls back to `"Show an appropriate component for the given data"`.

The `id` used for snapshot naming is the filename without extension (e.g., `table-employee-list`).

## Snapshot Behavior

Identical to the existing `loadOrGenerate` mechanism in `llm.ts`:

- **Normal run (`REGEN_SNAPSHOTS=0`):** loads from `fuzz-snapshots/{id}.dsl`; fails if snapshot missing
- **Regen run (`REGEN_SNAPSHOTS=1`):** calls LLM, writes snapshot
- Snapshots are committed to git so CI can run without LLM credentials

## Test Logic (`dsl-fuzz.test.tsx`)

```
for each .json file in fuzz-data/:
  id       = filename without extension
  prompt   = "Show a {ComponentHint} for the given data"  (or fallback)
  dataModel = parsed JSON content

  dsl = loadOrGenerate(id, prompt, dataModel)   // from existing llm.ts

  assert: parser.parse(dsl).meta.errors.length === 0
  assert: render(<Renderer library={dslLibrary} response={dsl} dataModel={dataModel} />) does not throw
```

No content assertions. Echarts is mocked (same as `dsl-e2e.test.tsx`).

## npm Scripts

```json
"test:fuzz":       "cross-env REGEN_SNAPSHOTS=0 vitest run src/__tests__/e2e/dsl-fuzz.test.tsx",
"test:fuzz:regen": "cross-env REGEN_SNAPSHOTS=1 vitest run src/__tests__/e2e/dsl-fuzz.test.tsx"
```

`test`, `test:e2e`, and `test:e2e:regen` are unchanged.

## What Is Not In Scope

- Semantic validation of generated DSL content
- Automatic prompt inference beyond component hint (no shape analysis)
- Integration with the existing e2e HTML report (can be added later)
- Running fuzz tests in CI by default
