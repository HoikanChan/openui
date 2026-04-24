# DSL Fuzz Testing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in fuzz test suite that feeds real API JSON data through the DSL generation pipeline and asserts only that the output parses cleanly and renders without crashing.

**Architecture:** Four new files alongside the existing e2e infrastructure — a pure prompt-inference helper (`fuzz-loader.ts`), its unit tests, the fuzz test runner (`dsl-fuzz.test.tsx`), and two new directories (`fuzz-data/` for input JSON, `fuzz-snapshots/` for generated DSL). The existing `loadOrGenerate` in `llm.ts` gains an optional `snapshotDir` parameter so the fuzz suite can redirect snapshots without duplicating logic.

**Tech Stack:** Vitest 4, React Testing Library, `@openuidev/lang-core` parser, `@openuidev/react-lang` Renderer, same OpenAI-compatible client already used by `llm.ts`.

---

### Task 1: Create fuzz directories and add npm scripts

**Files:**
- Create: `packages/react-ui-dsl/src/__tests__/e2e/fuzz-data/.gitkeep`
- Create: `packages/react-ui-dsl/src/__tests__/e2e/fuzz-snapshots/.gitkeep`
- Modify: `packages/react-ui-dsl/package.json`

- [ ] **Step 1: Create the two directories**

```bash
mkdir -p packages/react-ui-dsl/src/__tests__/e2e/fuzz-data
mkdir -p packages/react-ui-dsl/src/__tests__/e2e/fuzz-snapshots
touch packages/react-ui-dsl/src/__tests__/e2e/fuzz-data/.gitkeep
touch packages/react-ui-dsl/src/__tests__/e2e/fuzz-snapshots/.gitkeep
```

- [ ] **Step 2: Add scripts to `package.json`**

In `packages/react-ui-dsl/package.json`, add two lines after `"test:e2e:regen:fixture"`:

```json
"test:fuzz":       "cross-env REGEN_SNAPSHOTS=0 vitest run src/__tests__/e2e/dsl-fuzz.test.tsx",
"test:fuzz:regen": "cross-env REGEN_SNAPSHOTS=1 vitest run src/__tests__/e2e/dsl-fuzz.test.tsx",
```

- [ ] **Step 3: Commit**

```bash
git add packages/react-ui-dsl/src/__tests__/e2e/fuzz-data/.gitkeep \
        packages/react-ui-dsl/src/__tests__/e2e/fuzz-snapshots/.gitkeep \
        packages/react-ui-dsl/package.json
git commit -m "chore(react-ui-dsl): add fuzz directories and test scripts"
```

---

### Task 2: Add optional `snapshotDir` parameter to `loadOrGenerate`

The existing `loadOrGenerate` in `llm.ts` hard-codes `SNAPSHOT_DIR`. Adding an optional parameter lets the fuzz suite redirect snapshots to `fuzz-snapshots/` without duplicating any logic. The default value keeps all existing callers working unchanged.

**Files:**
- Modify: `packages/react-ui-dsl/src/__tests__/e2e/llm.ts:15-38`

- [ ] **Step 1: Update `loadOrGenerate` signature and body**

Replace the current `loadOrGenerate` function in `llm.ts` with:

```ts
export async function loadOrGenerate(
  id: string,
  prompt: string,
  dataModel: Record<string, unknown>,
  snapshotDir: string = SNAPSHOT_DIR,
): Promise<string> {
  const snapshotPath = resolve(snapshotDir, `${id}.dsl`);

  if (!isSnapshotRegenEnabled() && existsSync(snapshotPath)) {
    return readFileSync(snapshotPath, "utf-8") as string;
  }

  const apiKey = process.env["LLM_API_KEY"];
  if (!apiKey) {
    throw new Error(
      `Snapshot missing for "${id}" and LLM_API_KEY is not set. ` +
        `Run: REGEN_SNAPSHOTS=1 LLM_API_KEY=<key> pnpm test:e2e:regen`,
    );
  }

  const dsl = await callLLM(prompt, dataModel, apiKey);
  mkdirSync(snapshotDir, { recursive: true });
  writeFileSync(snapshotPath, dsl, "utf-8");
  return dsl;
}
```

- [ ] **Step 2: Run existing e2e unit tests to verify no regression**

```bash
cd packages/react-ui-dsl
pnpm vitest run src/__tests__/e2e/llm.test.ts
```

Expected: all 4 tests pass.

- [ ] **Step 3: Commit**

```bash
git add packages/react-ui-dsl/src/__tests__/e2e/llm.ts
git commit -m "feat(react-ui-dsl): add snapshotDir param to loadOrGenerate"
```

---

### Task 3: Implement `inferFuzzPrompt` with unit tests (TDD)

**Files:**
- Create: `packages/react-ui-dsl/src/__tests__/e2e/fuzz-loader.ts`
- Create: `packages/react-ui-dsl/src/__tests__/e2e/fuzz-loader.test.ts`

- [ ] **Step 1: Write the failing tests**

Create `packages/react-ui-dsl/src/__tests__/e2e/fuzz-loader.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { inferFuzzPrompt } from "./fuzz-loader";

describe("inferFuzzPrompt", () => {
  it("infers Table prompt from table- prefix", () => {
    expect(inferFuzzPrompt("table-employee-list")).toBe(
      "Show a Table for the given data",
    );
  });

  it("infers LineChart prompt from linechart- prefix", () => {
    expect(inferFuzzPrompt("linechart-bandwidth")).toBe(
      "Show a LineChart for the given data",
    );
  });

  it("infers HorizontalBarChart from hbar- shorthand", () => {
    expect(inferFuzzPrompt("hbar-interface-traffic")).toBe(
      "Show a HorizontalBarChart for the given data",
    );
  });

  it("infers Card prompt from card- prefix", () => {
    expect(inferFuzzPrompt("card-device-status")).toBe(
      "Show a Card for the given data",
    );
  });

  it("falls back to generic prompt for unknown hint", () => {
    expect(inferFuzzPrompt("unknown-something")).toBe(
      "Show an appropriate component for the given data",
    );
  });

  it("is case-insensitive for the hint", () => {
    expect(inferFuzzPrompt("TABLE-employees")).toBe(
      "Show a Table for the given data",
    );
  });

  it("handles filename with no dash (hint only)", () => {
    expect(inferFuzzPrompt("card")).toBe("Show a Card for the given data");
  });
});
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd packages/react-ui-dsl
pnpm vitest run src/__tests__/e2e/fuzz-loader.test.ts
```

Expected: FAIL — `Cannot find module './fuzz-loader'`.

- [ ] **Step 3: Implement `fuzz-loader.ts`**

Create `packages/react-ui-dsl/src/__tests__/e2e/fuzz-loader.ts`:

```ts
const COMPONENT_HINTS: Record<string, string> = {
  table: "Table",
  piechart: "PieChart",
  linechart: "LineChart",
  barchart: "BarChart",
  gaugechart: "GaugeChart",
  hbar: "HorizontalBarChart",
  horizontalbarchart: "HorizontalBarChart",
  area: "AreaChart",
  areachart: "AreaChart",
  radar: "RadarChart",
  radarchart: "RadarChart",
  heatmap: "HeatmapChart",
  heatmapchart: "HeatmapChart",
  treemap: "TreeMapChart",
  treemapchart: "TreeMapChart",
  scatter: "ScatterChart",
  scatterchart: "ScatterChart",
  series: "Series",
  vlayout: "VLayout",
  hlayout: "HLayout",
  text: "Text",
  button: "Button",
  select: "Select",
  image: "Image",
  link: "Link",
  card: "Card",
  descriptions: "Descriptions",
  list: "List",
  form: "Form",
  timeline: "TimeLine",
  tabs: "Tabs",
};

export function inferFuzzPrompt(id: string): string {
  const hint = (id.split("-")[0] ?? "").toLowerCase();
  const component = COMPONENT_HINTS[hint];
  return component
    ? `Show a ${component} for the given data`
    : "Show an appropriate component for the given data";
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
cd packages/react-ui-dsl
pnpm vitest run src/__tests__/e2e/fuzz-loader.test.ts
```

Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add packages/react-ui-dsl/src/__tests__/e2e/fuzz-loader.ts \
        packages/react-ui-dsl/src/__tests__/e2e/fuzz-loader.test.ts
git commit -m "feat(react-ui-dsl): add fuzz prompt inference helper"
```

---

### Task 4: Write `dsl-fuzz.test.tsx` and verify empty run

**Files:**
- Create: `packages/react-ui-dsl/src/__tests__/e2e/dsl-fuzz.test.tsx`

- [ ] **Step 1: Create the test file**

Create `packages/react-ui-dsl/src/__tests__/e2e/dsl-fuzz.test.tsx`:

```tsx
// @vitest-environment jsdom
import { readdirSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { cleanup, render } from "@testing-library/react";
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as echarts from "echarts";
import { createParser } from "@openuidev/lang-core";
import { Renderer } from "@openuidev/react-lang";
import { dslLibrary } from "../../genui-lib/dslLibrary";
import { loadOrGenerate } from "./llm";
import { inferFuzzPrompt } from "./fuzz-loader";

vi.mock("echarts", () => ({
  init: vi.fn(() => ({
    setOption: vi.fn(),
    dispose: vi.fn(),
    resize: vi.fn(),
  })),
  registerTheme: vi.fn(),
}));

afterEach(() => {
  cleanup();
  vi.mocked(echarts.init).mockClear();
});

const __dirname = dirname(fileURLToPath(import.meta.url));
const FUZZ_DATA_DIR = resolve(__dirname, "fuzz-data");
const FUZZ_SNAPSHOTS_DIR = resolve(__dirname, "fuzz-snapshots");

const parser = createParser(dslLibrary.toJSONSchema());

type FuzzCase = { id: string; prompt: string; dataModel: Record<string, unknown> };

function loadFuzzCases(): FuzzCase[] {
  let files: string[];
  try {
    files = readdirSync(FUZZ_DATA_DIR).filter((f) => f.endsWith(".json"));
  } catch {
    return [];
  }
  return files.map((filename) => {
    const id = filename.replace(/\.json$/, "");
    return {
      id,
      prompt: inferFuzzPrompt(id),
      dataModel: JSON.parse(
        readFileSync(resolve(FUZZ_DATA_DIR, filename), "utf-8"),
      ) as Record<string, unknown>,
    };
  });
}

const fuzzCases = loadFuzzCases();

describe.skipIf(fuzzCases.length === 0)("DSL fuzz", () => {
  it.each(fuzzCases)("$id: parse and render without errors", async ({ id, prompt, dataModel }) => {
    const dsl = await loadOrGenerate(id, prompt, dataModel, FUZZ_SNAPSHOTS_DIR);

    const parsed = parser.parse(dsl);
    expect(parsed.meta.errors, `parse errors in ${id}:\n${dsl}`).toHaveLength(0);

    expect(() =>
      render(
        <Renderer library={dslLibrary} response={dsl} dataModel={dataModel} />,
      ),
    ).not.toThrow();
  });
});
```

- [ ] **Step 2: Run `test:fuzz` with empty `fuzz-data/` — expect zero tests**

```bash
cd packages/react-ui-dsl
pnpm test:fuzz
```

Expected output: suite skips (0 tests run, no failures). The `describe.skipIf` fires because `fuzzCases.length === 0`.

- [ ] **Step 3: Commit**

```bash
git add packages/react-ui-dsl/src/__tests__/e2e/dsl-fuzz.test.tsx
git commit -m "feat(react-ui-dsl): add DSL fuzz test runner"
```

---

### Task 5: Add first fuzz data file and verify end-to-end

This task verifies the full pipeline: drop a JSON file → regen snapshot → replay passes.

**Files:**
- Create: `packages/react-ui-dsl/src/__tests__/e2e/fuzz-data/table-sample.json`
- Create: `packages/react-ui-dsl/src/__tests__/e2e/fuzz-snapshots/table-sample.dsl` (generated)

- [ ] **Step 1: Create a sample fuzz data file**

Create `packages/react-ui-dsl/src/__tests__/e2e/fuzz-data/table-sample.json`:

```json
{
  "employees": [
    { "name": "Alice", "department": "Engineering", "salary": 95000 },
    { "name": "Bob",   "department": "Marketing",   "salary": 82000 }
  ]
}
```

- [ ] **Step 2: Regen the snapshot (requires LLM API key)**

```bash
cd packages/react-ui-dsl
LLM_API_KEY=<your-key> pnpm test:fuzz:regen
```

Expected: 1 test runs and passes. A file `fuzz-snapshots/table-sample.dsl` is created.

- [ ] **Step 3: Run in replay mode (no LLM key needed)**

```bash
cd packages/react-ui-dsl
pnpm test:fuzz
```

Expected: 1 test passes, no LLM call made.

- [ ] **Step 4: Commit the sample JSON and generated snapshot**

```bash
git add packages/react-ui-dsl/src/__tests__/e2e/fuzz-data/table-sample.json \
        packages/react-ui-dsl/src/__tests__/e2e/fuzz-snapshots/table-sample.dsl
git commit -m "test(react-ui-dsl): add first fuzz data sample and snapshot"
```

---

## Usage After Implementation

To add more fuzz cases from your API data:

1. Drop a `.json` file into `fuzz-data/` — name it `{component}-{description}.json`
2. Run: `LLM_API_KEY=<key> pnpm test:fuzz:regen`
3. Commit both the `.json` and the generated `.dsl` snapshot
4. Future runs: `pnpm test:fuzz` replays snapshots with no LLM call

Supported component hint prefixes: `table`, `linechart`, `barchart`, `piechart`, `gaugechart`, `hbar`, `area`, `radar`, `heatmap`, `treemap`, `scatter`, `series`, `vlayout`, `hlayout`, `text`, `button`, `select`, `image`, `link`, `card`, `descriptions`, `list`, `form`, `timeline`, `tabs`.
