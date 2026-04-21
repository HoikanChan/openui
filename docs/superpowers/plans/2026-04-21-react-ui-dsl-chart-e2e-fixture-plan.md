# React UI DSL Chart E2E Fixture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor chart e2e fixtures so they validate chart-specific ECharts option contracts, while moving generic chart smoke coverage into a dedicated `BaseChart` test.

**Architecture:** Extend the e2e harness to capture the last mocked `echarts.setOption()` payload, expose that payload through a chart-focused fixture assertion API, and update chart fixtures to assert only stable option fields. Keep the existing fixture/snapshot coverage tests for top-level UI components, but exclude helper chart input nodes that do not own renderer-level chart outcomes. Add a separate `BaseChart` smoke test for init, resize, dispose, and default sizing behavior.

**Tech Stack:** Vitest, Testing Library, React 19, ECharts mock, TypeScript, jsdom

---

## File Structure

### Modify

- `packages/react-ui-dsl/src/__tests__/e2e/dsl-e2e.test.tsx`
  Extend the mocked ECharts instance so the test can read `setOption` calls and pass the last option into chart fixtures.
- `packages/react-ui-dsl/src/__tests__/e2e/fixtures.ts`
  Replace chart fixture smoke assertions with chart option assertions; narrow or remove helper chart fixtures that do not defend a renderer outcome.
- `packages/react-ui-dsl/src/__tests__/e2e/fixtures.test.ts`
  Keep fixture coverage aligned with top-level DSL components while exempting helper chart input nodes from the “every component needs a fixture group” rule.
- `packages/react-ui-dsl/src/__tests__/e2e/snapshots/bar-product-comparison.dsl`
  Simplify the bar chart fixture so the snapshot focuses on chart behavior instead of mixed chart-plus-table coverage.
- `packages/react-ui-dsl/src/__tests__/e2e/snapshots/series-interface-traffic.dsl`
  Delete because `Series` will no longer be treated as a renderer-facing e2e fixture target.
- `packages/react-ui-dsl/src/__tests__/e2e/snapshots/scatter-series-routers.dsl`
  Delete because `ScatterSeries` will no longer be treated as a renderer-facing e2e fixture target.
- `packages/react-ui-dsl/src/__tests__/e2e/snapshots/point-scatter-correlation.dsl`
  Delete because `Point` will no longer be treated as a renderer-facing e2e fixture target.

### Create

- `packages/react-ui-dsl/src/__tests__/e2e/chartOption.ts`
  Small helper for extracting the last `setOption` payload from the mocked chart instance and returning a stable chart assertion context.
- `packages/react-ui-dsl/src/components/chart/BaseChart.test.tsx`
  Dedicated smoke test for `BaseChart` init, `setOption`, resize, dispose, and default height behavior.

## Task 1: Add a chart-option assertion harness to e2e tests

**Files:**
- Create: `packages/react-ui-dsl/src/__tests__/e2e/chartOption.ts`
- Modify: `packages/react-ui-dsl/src/__tests__/e2e/dsl-e2e.test.tsx`
- Modify: `packages/react-ui-dsl/src/__tests__/e2e/fixtures.ts`
- Test: `packages/react-ui-dsl/src/__tests__/e2e/dsl-e2e.test.tsx`

- [ ] **Step 1: Write the failing harness test shape in `fixtures.ts`**

```ts
type ChartSetOptionCall = {
  option: unknown;
  notMerge: boolean | undefined;
};

type FixtureVerifyContext = {
  chart?: {
    option: unknown;
    setOptionCalls: ChartSetOptionCall[];
  };
};

export interface Fixture {
  id: string;
  prompt: string;
  expectedDescription?: string;
  dataModel: Record<string, unknown>;
  assert: {
    contains: string[];
    notContains?: string[];
    verify?: (container: HTMLElement, context: FixtureVerifyContext) => void;
  };
}
```

Run: `pnpm --dir packages/react-ui-dsl test:e2e -- --runInBand src/__tests__/e2e/dsl-e2e.test.tsx`
Expected: FAIL because `dsl-e2e.test.tsx` does not yet populate `context.chart`.

- [ ] **Step 2: Add a dedicated chart option extractor**

```ts
// packages/react-ui-dsl/src/__tests__/e2e/chartOption.ts
import { expect, type Mock } from "vitest";

export type ChartSetOptionCall = {
  option: unknown;
  notMerge: boolean | undefined;
};

export function readChartOption(setOption: Mock): {
  option: unknown;
  setOptionCalls: ChartSetOptionCall[];
} {
  const setOptionCalls = setOption.mock.calls.map(([option, notMerge]) => ({
    option,
    notMerge: typeof notMerge === "boolean" ? notMerge : undefined,
  }));

  expect(setOptionCalls.length, "expected chart.setOption to be called at least once").toBeGreaterThan(0);

  return {
    option: setOptionCalls.at(-1)!.option,
    setOptionCalls,
  };
}
```

- [ ] **Step 3: Wire the ECharts mock in `dsl-e2e.test.tsx` to expose `setOption`**

```ts
const echartsInstances: Array<{
  setOption: ReturnType<typeof vi.fn>;
  dispose: ReturnType<typeof vi.fn>;
  resize: ReturnType<typeof vi.fn>;
}> = [];

vi.mock("echarts", () => ({
  init: vi.fn(() => {
    const instance = {
      setOption: vi.fn(),
      dispose: vi.fn(),
      resize: vi.fn(),
    };
    echartsInstances.push(instance);
    return instance;
  }),
  registerTheme: vi.fn(),
}));

afterEach(() => {
  cleanup();
  vi.mocked(echarts.init).mockClear();
  echartsInstances.length = 0;
});
```

- [ ] **Step 4: Pass the chart option context into fixture assertions**

```ts
import { readChartOption } from "./chartOption";

const chartInstance = echartsInstances.at(-1);
const chart = chartInstance ? readChartOption(chartInstance.setOption) : undefined;

assert.verify?.(container, { chart });
```

- [ ] **Step 5: Re-run the focused e2e file**

Run: `pnpm --dir packages/react-ui-dsl test:e2e -- src/__tests__/e2e/dsl-e2e.test.tsx`
Expected: PASS on non-chart fixtures and existing chart fixtures after they are updated to use `context.chart`.

- [ ] **Step 6: Commit harness changes**

```bash
git add packages/react-ui-dsl/src/__tests__/e2e/chartOption.ts packages/react-ui-dsl/src/__tests__/e2e/dsl-e2e.test.tsx packages/react-ui-dsl/src/__tests__/e2e/fixtures.ts
git commit -m "test: capture chart option context in e2e fixtures"
```

## Task 2: Replace chart smoke assertions with chart option contract assertions

**Files:**
- Modify: `packages/react-ui-dsl/src/__tests__/e2e/fixtures.ts`
- Modify: `packages/react-ui-dsl/src/__tests__/e2e/snapshots/bar-product-comparison.dsl`
- Test: `packages/react-ui-dsl/src/__tests__/e2e/dsl-e2e.test.tsx`

- [ ] **Step 1: Write failing chart assertions for one representative chart from each family**

```ts
PieChart: [
  {
    id: "pie-sales-by-region",
    prompt: "Show a pie chart of sales distribution by region using data.labels and data.values",
    dataModel: {
      labels: ["North America", "Europe", "APAC"],
      values: [1200000, 860000, 1050000],
    },
    assert: {
      contains: [],
      verify: (_container, { chart }) => {
        expect(chart?.option).toMatchObject({
          legend: { orient: "vertical" },
          series: [
            {
              type: "pie",
              data: [
                { name: "North America", value: 1200000 },
                { name: "Europe", value: 860000 },
                { name: "APAC", value: 1050000 },
              ],
            },
          ],
        });
      },
    },
  },
],
```

Run: `pnpm --dir packages/react-ui-dsl test:e2e -- src/__tests__/e2e/dsl-e2e.test.tsx`
Expected: FAIL on the first chart fixture that still expects `echarts.init` or a `300px` DOM node.

- [ ] **Step 2: Update all retained chart fixtures to assert only key option fields**

```ts
LineChart: [
  {
    id: "line-monthly-revenue",
    prompt: "Show monthly revenue trend as a line chart using data.labels and data.series",
    dataModel: {
      labels: ["Jan", "Feb", "Mar"],
      series: [{ category: "Revenue", values: [420000, 530000, 610000] }],
    },
    assert: {
      contains: [],
      verify: (_container, { chart }) => {
        expect(chart?.option).toMatchObject({
          xAxis: { type: "category", data: ["Jan", "Feb", "Mar"], name: "Month" },
          yAxis: { type: "value", name: "Revenue ($)" },
          series: [{ type: "line", name: "Revenue", data: [420000, 530000, 610000], smooth: true }],
        });
      },
    },
  },
],
```

```ts
BarChart: [
  {
    id: "bar-product-comparison",
    prompt: "Compare quarterly revenue for two product lines as a bar chart using data.labels and data.series",
    dataModel: {
      labels: ["Q1", "Q2"],
      series: [
        { category: "Product A", values: [800000, 920000] },
        { category: "Product B", values: [350000, 410000] },
      ],
    },
    assert: {
      contains: [],
      verify: (_container, { chart }) => {
        expect(chart?.option).toMatchObject({
          xAxis: { type: "category", data: ["Q1", "Q2"], name: "Quarter" },
          yAxis: { type: "value", name: "Revenue (USD)" },
          series: [
            { type: "bar", name: "Product A", data: [800000, 920000] },
            { type: "bar", name: "Product B", data: [350000, 410000] },
          ],
        });
      },
    },
  },
],
```

Apply the same pattern for:

- `GaugeChart`: assert gauge series and reading mapping
- `HorizontalBarChart`: assert axis orientation differs from normal bar chart
- `AreaChart`: assert area-specific config, not just `type: "line"`
- `RadarChart`: assert indicator and series value mapping
- `HeatmapChart`: assert `xAxis.data`, `yAxis.data`, and transformed series tuples
- `TreeMapChart`: assert treemap grouping shape
- `ScatterChart`: assert scatter series, point tuples, and x/y labels

- [ ] **Step 3: Simplify the bar chart snapshot so it focuses on chart behavior**

```dsl
root = VLayout([chartTitle, barChart])
chartTitle = Text("Revenue by Product Line (USD)", "default")
barChart = BarChart(data.labels, data.series, "grouped", "Quarter", "Revenue (USD)")
```

This removes the unrelated table and card content from `bar-product-comparison.dsl`.

- [ ] **Step 4: Re-run the chart e2e suite**

Run: `pnpm --dir packages/react-ui-dsl test:e2e -- src/__tests__/e2e/dsl-e2e.test.tsx`
Expected: PASS with chart failures now pointing to specific option fields when regressions are introduced.

- [ ] **Step 5: Commit chart contract fixture updates**

```bash
git add packages/react-ui-dsl/src/__tests__/e2e/fixtures.ts packages/react-ui-dsl/src/__tests__/e2e/snapshots/bar-product-comparison.dsl packages/react-ui-dsl/src/__tests__/e2e/snapshots/area-bandwidth-utilization.dsl
git commit -m "test: assert chart option contracts in e2e fixtures"
```

## Task 3: Exempt helper chart input nodes from top-level e2e fixture coverage

**Files:**
- Modify: `packages/react-ui-dsl/src/__tests__/e2e/fixtures.ts`
- Modify: `packages/react-ui-dsl/src/__tests__/e2e/fixtures.test.ts`
- Delete: `packages/react-ui-dsl/src/__tests__/e2e/snapshots/series-interface-traffic.dsl`
- Delete: `packages/react-ui-dsl/src/__tests__/e2e/snapshots/scatter-series-routers.dsl`
- Delete: `packages/react-ui-dsl/src/__tests__/e2e/snapshots/point-scatter-correlation.dsl`
- Test: `packages/react-ui-dsl/src/__tests__/e2e/fixtures.test.ts`

- [ ] **Step 1: Write the failing coverage expectation for helper node exclusions**

```ts
const e2eFixtureCoverageExclusions = ["Col", "Series", "ScatterSeries", "Point"];

it("keeps a committed fixture group for every renderer-facing DSL component", () => {
  const componentNames = Object.keys(dslLibrary.toSpec().components).filter(
    (name) => !e2eFixtureCoverageExclusions.includes(name),
  );

  expect(Object.keys(fixtures).sort()).toEqual(componentNames.sort());
});
```

Run: `pnpm --dir packages/react-ui-dsl test:e2e -- src/__tests__/e2e/fixtures.test.ts`
Expected: FAIL until the helper fixture groups and snapshot ids are removed or aligned.

- [ ] **Step 2: Remove helper chart fixture groups from `fixtures.ts`**

```ts
export const fixtures: Record<string, Fixture[]> = {
  Table,
  PieChart,
  LineChart,
  BarChart,
  GaugeChart,
  HorizontalBarChart,
  AreaChart,
  RadarChart,
  HeatmapChart,
  TreeMapChart,
  ScatterChart,
  VLayout,
  HLayout,
  Text,
  Button,
  Select,
  Image,
  Link,
  Card,
  List,
  Form,
  TimeLine,
  Tabs,
};
```

Concretely, delete the `Series`, `ScatterSeries`, and `Point` keys from `fixtures.ts` and leave every other group intact.

- [ ] **Step 3: Delete orphaned helper snapshots and confirm snapshot coverage still passes**

Run: `pnpm --dir packages/react-ui-dsl test:e2e -- src/__tests__/e2e/fixtures.test.ts src/__tests__/e2e/fixtureCoverage.test.ts`
Expected: PASS with no missing or orphaned snapshot ids.

- [ ] **Step 4: Commit the fixture coverage narrowing**

```bash
git add packages/react-ui-dsl/src/__tests__/e2e/fixtures.ts packages/react-ui-dsl/src/__tests__/e2e/fixtures.test.ts packages/react-ui-dsl/src/__tests__/e2e/fixtureCoverage.test.ts
git rm packages/react-ui-dsl/src/__tests__/e2e/snapshots/series-interface-traffic.dsl packages/react-ui-dsl/src/__tests__/e2e/snapshots/scatter-series-routers.dsl packages/react-ui-dsl/src/__tests__/e2e/snapshots/point-scatter-correlation.dsl
git commit -m "test: narrow e2e fixture coverage to renderer-facing chart nodes"
```

## Task 4: Add a dedicated `BaseChart` smoke test

**Files:**
- Create: `packages/react-ui-dsl/src/components/chart/BaseChart.test.tsx`
- Test: `packages/react-ui-dsl/src/components/chart/BaseChart.test.tsx`

- [ ] **Step 1: Write the failing `BaseChart` smoke test**

```tsx
// @vitest-environment jsdom
import { cleanup, render } from "@testing-library/react";
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as echarts from "echarts";
import { BaseChart } from "./BaseChart";

const instances: Array<{
  setOption: ReturnType<typeof vi.fn>;
  dispose: ReturnType<typeof vi.fn>;
  resize: ReturnType<typeof vi.fn>;
}> = [];

vi.mock("echarts", () => ({
  init: vi.fn(() => {
    const instance = { setOption: vi.fn(), dispose: vi.fn(), resize: vi.fn() };
    instances.push(instance);
    return instance;
  }),
  registerTheme: vi.fn(),
}));

afterEach(() => {
  cleanup();
  instances.length = 0;
});

describe("BaseChart", () => {
  it("initializes echarts, sets the option, keeps the default height, and disposes on unmount", () => {
    const option = { series: [{ type: "line", data: [1, 2, 3] }] };
    const { container, unmount } = render(<BaseChart option={option} />);

    expect(vi.mocked(echarts.init)).toHaveBeenCalledTimes(1);
    expect(instances[0]?.setOption).toHaveBeenCalledWith(option, true);
    expect(container.querySelector('div[style*="height: 300px"]')).not.toBeNull();

    unmount();

    expect(instances[0]?.dispose).toHaveBeenCalledTimes(1);
  });
});
```

Run: `pnpm --dir packages/react-ui-dsl test -- src/components/chart/BaseChart.test.tsx`
Expected: FAIL until the test file is created and the jsdom environment is configured in the test header.

- [ ] **Step 2: Add a resize smoke assertion**

```tsx
it("resizes the chart when ResizeObserver fires", () => {
  let resizeObserverCallback: ResizeObserverCallback | undefined;

  class ResizeObserverStub {
    constructor(callback: ResizeObserverCallback) {
      resizeObserverCallback = callback;
    }
    observe() {}
    disconnect() {}
  }

  vi.stubGlobal("ResizeObserver", ResizeObserverStub);

  render(<BaseChart option={{ series: [{ type: "bar", data: [1] }] }} />);

  resizeObserverCallback?.([] as ResizeObserverEntry[], {} as ResizeObserver);

  expect(instances[0]?.resize).toHaveBeenCalledTimes(1);
});
```

- [ ] **Step 3: Run the chart smoke and e2e suites together**

Run: `pnpm --dir packages/react-ui-dsl test -- src/components/chart/BaseChart.test.tsx src/__tests__/e2e/dsl-e2e.test.tsx src/__tests__/e2e/fixtures.test.ts src/__tests__/e2e/fixtureCoverage.test.ts`
Expected: PASS

- [ ] **Step 4: Commit the smoke test**

```bash
git add packages/react-ui-dsl/src/components/chart/BaseChart.test.tsx
git commit -m "test: add base chart smoke coverage"
```

## Task 5: Run full package verification

**Files:**
- Modify: none
- Test: `packages/react-ui-dsl/package.json`

- [ ] **Step 1: Run the full package test suite**

Run: `pnpm --dir packages/react-ui-dsl test`
Expected: PASS

- [ ] **Step 2: Run typecheck**

Run: `pnpm --dir packages/react-ui-dsl typecheck`
Expected: PASS

- [ ] **Step 3: Run lint check**

Run: `pnpm --dir packages/react-ui-dsl lint:check`
Expected: PASS

- [ ] **Step 4: Create a verification commit if any final fixes were needed**

```bash
git status --short
git add packages/react-ui-dsl/src/__tests__/e2e packages/react-ui-dsl/src/components/chart/BaseChart.test.tsx
git commit -m "test: finish chart e2e fixture refactor"
```

## Spec Coverage Check

- Split generic chart smoke coverage from per-chart semantics: Task 4 and Task 2
- Assert final `setOption` payload instead of DOM smoke markers: Task 1 and Task 2
- Keep only key chart contracts, not full option snapshots: Task 2
- Narrow mixed chart fixture scope and simplify bar chart fixture: Task 2
- Reevaluate helper chart component fixtures: Task 3

## Placeholder Scan

This plan intentionally avoids placeholder language. Every change references an exact file path, concrete command, and concrete assertion shape.

## Type Consistency Check

- `FixtureVerifyContext.chart` is introduced in Task 1 and used consistently in Task 2
- `readChartOption()` is defined in Task 1 and reused only through `context.chart`
- helper fixture exclusions are named consistently as `Series`, `ScatterSeries`, and `Point`
