// @vitest-environment jsdom
import { readdirSync, readFileSync } from "node:fs";
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
import { inferFuzzPrompt } from "./fuzz-loader";
import { finalizeE2EReport, resetE2EReportState, runE2EReportEntry, setE2EReportEntryDsl } from "./report";

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

beforeAll(() => {
  resetE2EReportState();
});

afterAll(() => {
  finalizeE2EReport();
});

describe.skipIf(fuzzCases.length === 0)("DSL fuzz", () => {
  it.each(fuzzCases)("$id: parse and render without errors", async ({ id, prompt, dataModel }) => {
    await runE2EReportEntry(
      "Fuzz",
      {
        id,
        prompt,
        expectedDescription: "Generated fuzz case should parse and render without errors",
        dataModel,
        assert: { contains: [] },
      },
      async (entry) => {
        const dsl = await loadOrGenerate(id, prompt, dataModel, FUZZ_SNAPSHOTS_DIR);
        setE2EReportEntryDsl(entry, dsl);

        const parsed = parser.parse(dsl);
        expect(parsed.meta.errors, `parse errors in ${id}:\n${dsl}`).toHaveLength(0);

        expect(() =>
          render(
            <Renderer library={dslLibrary} response={dsl} dataModel={dataModel} />,
          ),
        ).not.toThrow();
      },
    );
  });
});
