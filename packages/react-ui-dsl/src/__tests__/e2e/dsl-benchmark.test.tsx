// @vitest-environment jsdom
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { cleanup, render } from "@testing-library/react";
import React from "react";
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import * as echarts from "echarts";
import { createParser } from "@openuidev/lang-core";
import { Renderer } from "@openuidev/react-lang";
import { dslLibrary } from "../../genui-lib/dslLibrary";
import { loadOrGenerate } from "./llm";
import { loadBenchmarkCases } from "./benchmark-loader";
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

const parser = createParser(dslLibrary.toJSONSchema());

const benchmarkCases = loadBenchmarkCases(BENCHMARK_DATA_DIR);

beforeAll(() => {
  resetE2EReportState();
});

afterAll(() => {
  finalizeE2EReport();
});

describe.skipIf(benchmarkCases.length === 0)("DSL benchmark", () => {
  it.each(benchmarkCases)("$id: parse and render without errors", async ({ id, prompt, dataModel, evalHints }) => {
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

        const dsl = await loadOrGenerate(id, prompt, dataModel as Record<string, unknown>, BENCHMARK_SNAPSHOTS_DIR);
        setE2EReportEntryDsl(entry, dsl);

        const parsed = parser.parse(dsl);
        expect(parsed.meta.errors, `parse errors in ${id}:\n${dsl}`).toHaveLength(0);

        expect(() =>
          render(
            <Renderer library={dslLibrary} response={dsl} dataModel={dataModel as Record<string, unknown>} />,
          ),
        ).not.toThrow();
      },
    );
  });
});
