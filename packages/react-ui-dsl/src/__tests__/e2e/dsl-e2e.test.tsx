// @vitest-environment jsdom
import { render } from "@testing-library/react";
import React from "react";
import { describe, expect, it, vi } from "vitest";
import { createParser } from "@openuidev/lang-core";
import { Renderer } from "@openuidev/react-lang";
import { dslLibrary } from "../../genui-lib/dslLibrary";
import { fixtures } from "./fixtures";
import { loadOrGenerate } from "./llm";

vi.mock("echarts", () => ({
  init: vi.fn(() => ({
    setOption: vi.fn(),
    dispose: vi.fn(),
    resize: vi.fn(),
  })),
  registerTheme: vi.fn(),
}));

const parser = createParser(dslLibrary.toJSONSchema());
const spec = dslLibrary.toSpec();

describe.each(Object.entries(fixtures))("%s", (_component, scenarios) => {
  it.each(scenarios)("$id", async ({ id, prompt, dataModel, assert }) => {
    const dsl = await loadOrGenerate(id, prompt, dataModel, spec);

    const parsed = parser.parse(dsl);
    expect(parsed.meta.errors, `parse errors in ${id}:\n${dsl}`).toHaveLength(0);

    const { container } = render(
      <Renderer library={dslLibrary} response={dsl} dataModel={dataModel} />,
    );

    for (const text of assert.contains) {
      expect(container.innerHTML).toContain(text);
    }
  });
});
