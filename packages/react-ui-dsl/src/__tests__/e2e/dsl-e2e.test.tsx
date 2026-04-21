// @vitest-environment jsdom
import { cleanup, render } from "@testing-library/react";
import React from "react";
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import * as echarts from "echarts";
import { createParser } from "@openuidev/lang-core";
import { Renderer } from "@openuidev/react-lang";
import { dslLibrary } from "../../genui-lib/dslLibrary";
import { fixtures } from "./fixtures";
import { loadOrGenerate } from "./llm";
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

beforeAll(() => {
  resetE2EReportState();
});

afterAll(() => {
  finalizeE2EReport();
});

const parser = createParser(dslLibrary.toJSONSchema());

describe.each(Object.entries(fixtures))("%s", (component, scenarios) => {
  it.each(scenarios)("$id", async (fixture) => {
    const { id, prompt, dataModel, assert } = fixture;

    await runE2EReportEntry(component, fixture, async (entry) => {
      const dsl = await loadOrGenerate(id, prompt, dataModel);
      setE2EReportEntryDsl(entry, dsl);

      const parsed = parser.parse(dsl);
      expect(parsed.meta.errors, `parse errors in ${id}:\n${dsl}`).toHaveLength(0);

      const { container } = render(
        <Renderer library={dslLibrary} response={dsl} dataModel={dataModel} />,
      );

      for (const text of assert.contains) {
        expect(container.innerHTML).toContain(text);
      }

      for (const text of assert.notContains ?? []) {
        expect(container.innerHTML, `${id}: raw value "${text}" should not appear (check formatting)`).not.toContain(text);
      }

      assert.verify?.(container, { echartsInit: vi.mocked(echarts.init) });
    });
  });
});
