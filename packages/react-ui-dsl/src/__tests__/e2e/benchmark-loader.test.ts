import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { loadBenchmarkCases } from "./benchmark-loader";

const TMP_DIR = resolve(__dirname, "__benchmark_loader_tmp__");

function writeCase(filename: string, content: unknown): void {
  writeFileSync(resolve(TMP_DIR, filename), JSON.stringify(content), "utf-8");
}

beforeEach(() => {
  mkdirSync(TMP_DIR, { recursive: true });
});

afterEach(() => {
  rmSync(TMP_DIR, { recursive: true, force: true });
});

describe("loadBenchmarkCases", () => {
  it("loads a valid case with all fields", () => {
    writeCase("my-case.json", {
      meta: { prompt: "Show a table", taxonomy: ["named-list", "homogeneous"], evalHints: ["must show all rows"] },
      data: { items: [{ id: 1 }] },
    });
    const cases = loadBenchmarkCases(TMP_DIR);
    expect(cases).toHaveLength(1);
    expect(cases[0]!.id).toBe("my-case");
    expect(cases[0]!.prompt).toBe("Show a table");
    expect(cases[0]!.evalHints).toEqual(["must show all rows"]);
    expect(cases[0]!.taxonomy).toEqual(["named-list", "homogeneous"]);
    expect(cases[0]!.dataModel).toEqual({ items: [{ id: 1 }] });
  });

  it("throws when meta.prompt is missing", () => {
    writeCase("bad.json", { meta: { taxonomy: ["flat-object"] }, data: {} });
    expect(() => loadBenchmarkCases(TMP_DIR)).toThrow(/missing meta\.prompt/);
  });

  it("returns empty evalHints when field is absent", () => {
    writeCase("no-hints.json", {
      meta: { prompt: "Show something", taxonomy: ["flat-object"] },
      data: {},
    });
    const cases = loadBenchmarkCases(TMP_DIR);
    expect(cases[0]!.evalHints).toEqual([]);
  });

  it("returns empty evalHints when field is empty array", () => {
    writeCase("empty-hints.json", {
      meta: { prompt: "Show something", taxonomy: ["flat-object"], evalHints: [] },
      data: {},
    });
    const cases = loadBenchmarkCases(TMP_DIR);
    expect(cases[0]!.evalHints).toEqual([]);
  });

  it("handles data as array", () => {
    writeCase("array-data.json", {
      meta: { prompt: "Show list", taxonomy: ["bare-array"], evalHints: ["must render all items"] },
      data: [{ id: 1 }, { id: 2 }],
    });
    const cases = loadBenchmarkCases(TMP_DIR);
    expect(cases[0]!.dataModel).toEqual([{ id: 1 }, { id: 2 }]);
  });

  it("returns empty array when directory is empty", () => {
    expect(loadBenchmarkCases(TMP_DIR)).toEqual([]);
  });

  it("ignores non-JSON files", () => {
    writeFileSync(resolve(TMP_DIR, "OUTLINE.md"), "# outline", "utf-8");
    writeCase("valid.json", {
      meta: { prompt: "ok", taxonomy: ["flat-object"], evalHints: ["must show data"] },
      data: {},
    });
    const cases = loadBenchmarkCases(TMP_DIR);
    expect(cases).toHaveLength(1);
  });
});
