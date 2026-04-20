## 1. Install dependencies

- [x] 1.1 Add `@testing-library/react` as a dev dependency to `packages/react-ui-dsl/package.json`
- [x] 1.2 Add `openai` as a dev dependency to `packages/react-ui-dsl/package.json`
- [x] 1.3 Run `pnpm install` in the monorepo root to update the lockfile

## 2. Vitest config updates

- [x] 2.1 Create `src/__tests__/e2e/setup.ts` with a no-op `ResizeObserver` polyfill for jsdom
- [x] 2.2 Add `setupFiles` entry for `setup.ts` and set `testTimeout: 30000` in `vitest.config.ts`

## 3. Fixture definitions

- [x] 3.1 Create `src/__tests__/e2e/fixtures.ts` with the `Fixture` interface and initial scenario set:
  - Table: `table-basic`, `table-sortable-date`
  - PieChart: `pie-sales-by-region`
  - LineChart: `line-monthly-revenue`
  - BarChart: `bar-product-comparison`
  - GaugeChart: `gauge-kpi`

## 4. LLM helper

- [x] 4.1 Create `src/__tests__/e2e/llm.ts` with `loadOrGenerate()` (VCR logic: read snapshot if exists, else call LLM and save)
- [x] 4.2 Implement `callLLM()` using `openai` SDK with `temperature: 0`; system prompt includes `dslLibrary.toSpec()` and `dataModel` shape
- [x] 4.3 Add clear error message when snapshot is missing and `OPENAI_API_KEY` is unset

## 5. Test file

- [x] 5.1 Create `src/__tests__/e2e/dsl-e2e.test.ts` with `// @vitest-environment jsdom` header
- [x] 5.2 Add `vi.mock('echarts')` stub for `init`, `registerTheme`, `dispose`, `resize`
- [x] 5.3 Implement `describe.each(Object.entries(fixtures))` / `it.each(scenarios)` test structure
- [x] 5.4 Add parse layer assertion: `expect(parsed.meta.errors).toHaveLength(0)` with DSL in failure message
- [x] 5.5 Add render layer assertion: `render(<Renderer .../>)` and `expect(container.innerHTML).toContain(...)`

## 6. Package scripts

- [x] 6.1 Add `"test:e2e": "vitest run src/__tests__/e2e"` to `packages/react-ui-dsl/package.json`
- [x] 6.2 Add `"test:e2e:regen": "REGEN_SNAPSHOTS=1 vitest run src/__tests__/e2e"` to `packages/react-ui-dsl/package.json`

## 7. Generate and commit snapshots

- [x] 7.1 Run `pnpm test:e2e:regen` with a valid `OPENAI_API_KEY` to generate all `.dsl` snapshot files
- [x] 7.2 Review generated `.dsl` files to confirm they are valid openui-lang DSL
- [x] 7.3 Commit the `snapshots/` directory
