// @vitest-environment jsdom
import { createParser } from "@cloudsop/openui-lang-core";
import { Renderer } from "@cloudsop/openui-react-lang";
import { cleanup, render } from "@testing-library/react";
import * as echarts from "echarts";
import { existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { dslLibrary } from "../../genui-lib/dslLibrary";
import { loadBenchmarkCases } from "./benchmark-loader";
import { loadOrGenerate } from "./llm";
import {
  finalizeE2EReport,
  resetE2EReportState,
  runE2EReportEntry,
  setE2EReportEntryDsl,
} from "./report";

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
const BENCHMARK_DATA_DIR = resolve(__dirname, "fuzz-data/benchmark");
const BENCHMARK_SNAPSHOTS_DIR = resolve(__dirname, "benchmark-snapshots");

const parser = createParser(dslLibrary.toJSONSchema(), undefined, { externalRefs: ["data"] });

const benchmarkCases = loadBenchmarkCases(BENCHMARK_DATA_DIR);
const runnableBenchmarkCases =
  process.env.REGEN_SNAPSHOTS === "1" || process.env.LLM_API_KEY
    ? benchmarkCases
    : benchmarkCases.filter(({ id }) => existsSync(resolve(BENCHMARK_SNAPSHOTS_DIR, `${id}.dsl`)));

beforeAll(() => {
  resetE2EReportState();
});

afterAll(() => {
  finalizeE2EReport();
});

describe.skipIf(runnableBenchmarkCases.length === 0)("DSL benchmark", () => {
  it.each(runnableBenchmarkCases)(
    "$id: parse and render without errors",
    async ({ id, prompt, dataModel, evalHints }) => {
      await runE2EReportEntry(
        "Benchmark",
        {
          id,
          prompt,
          expectedDescription: "Generated benchmark case should parse and render without errors",
          dataModel: dataModel as Record<string, unknown>,
          assert: { contains: [] },
        },
        async (entry) => {
          if (entry) entry.evalHints = evalHints;

          const dsl = await loadOrGenerate(
            id,
            prompt,
            dataModel as Record<string, unknown>,
            BENCHMARK_SNAPSHOTS_DIR,
          );
          setE2EReportEntryDsl(entry, dsl);

          const parsed = parser.parse(dsl);
          expect(parsed.meta.errors, `parse errors in ${id}:\n${dsl}`).toHaveLength(0);

          expect(() =>
            render(
              <Renderer
                library={dslLibrary}
                response={dsl}
                dataModel={dataModel as Record<string, unknown>}
              />,
            ),
          ).not.toThrow();
        },
      );
    },
  );
});
