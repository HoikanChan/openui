import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// The only generation pipeline is the Eval Generation CLI (Java SDK path).
// Mock that boundary — there is no TS prompt-assembly / OpenAI path anymore.
const { runGenerationCli } = vi.hoisted(() => ({ runGenerationCli: vi.fn() }));

vi.mock("node:fs");
vi.mock("./eval/generation-cli.ts", () => ({ runGenerationCli }));

import { getConfiguredLlmModel, loadOrGenerate } from "./llm";

function mockCliOk(dsl: string): void {
  runGenerationCli.mockImplementation(async (cases: Array<{ id: string }>) => {
    const results = new Map<string, unknown>();
    for (const c of cases) results.set(c.id, { id: c.id, status: "ok", dsl });
    return results;
  });
}

describe("loadOrGenerate", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    delete process.env.REGEN_SNAPSHOTS;
    delete process.env.LLM_API_KEY;
    delete process.env.LLM_MODEL;
    // generateDslViaCli allocates a temp workdir; give the mocked fs a value.
    vi.mocked(mkdtempSync).mockReturnValue("/tmp/genui-eval-fallback-test" as unknown as string);
  });

  afterEach(() => {
    vi.resetAllMocks();
  });

  it("returns snapshot file content when file exists and REGEN_SNAPSHOTS is unset", async () => {
    vi.mocked(existsSync).mockReturnValue(true);
    vi.mocked(readFileSync).mockReturnValue("root = Table([], [])" as unknown as Buffer);

    const result = await loadOrGenerate("table-basic", "Show a table", {});

    expect(result).toBe("root = Table([], [])");
    expect(runGenerationCli).not.toHaveBeenCalled();
    expect(writeFileSync).not.toHaveBeenCalled();
  });

  it("throws a helpful error when snapshot is missing and LLM_API_KEY is unset", async () => {
    vi.mocked(existsSync).mockReturnValue(false);

    await expect(loadOrGenerate("table-basic", "Show a table", {})).rejects.toThrow(
      'Snapshot missing for "table-basic" and LLM_API_KEY is not set',
    );
    expect(runGenerationCli).not.toHaveBeenCalled();
  });

  it("throws directing to `pnpm eval regen` when snapshot missing in REGEN_SNAPSHOTS mode", async () => {
    vi.mocked(existsSync).mockReturnValue(false);
    process.env.REGEN_SNAPSHOTS = "1";
    process.env.LLM_API_KEY = "sk-test";

    await expect(loadOrGenerate("table-basic", "Show a table", {})).rejects.toThrow(
      "pnpm eval regen",
    );
    // regen mode never generates on the fly — it defers to the standalone command
    expect(runGenerationCli).not.toHaveBeenCalled();
  });

  it("generates via the CLI, saves snapshot, and returns DSL when file is missing and key is set", async () => {
    vi.mocked(existsSync).mockReturnValue(false);
    vi.mocked(mkdirSync).mockReturnValue(undefined);
    vi.mocked(writeFileSync).mockReturnValue(undefined);
    process.env.LLM_API_KEY = "sk-test";
    mockCliOk("root = Table([], [])");

    const result = await loadOrGenerate("table-basic", "Show a table", { rows: [] });

    expect(runGenerationCli).toHaveBeenCalledOnce();
    expect(writeFileSync).toHaveBeenCalled();
    expect(result).toBe("root = Table([], [])");
  });

  it("re-generates even when file exists if REGEN_SNAPSHOTS is set", async () => {
    vi.mocked(existsSync).mockReturnValue(true);
    vi.mocked(mkdirSync).mockReturnValue(undefined);
    vi.mocked(writeFileSync).mockReturnValue(undefined);
    process.env.REGEN_SNAPSHOTS = "1";
    process.env.LLM_API_KEY = "sk-test";
    mockCliOk("root = Table([], [])");

    await loadOrGenerate("table-basic", "Show a table", {});

    expect(readFileSync).not.toHaveBeenCalled();
    expect(runGenerationCli).toHaveBeenCalledOnce();
  });

  it("returns the default model when LLM_MODEL is unset", () => {
    expect(getConfiguredLlmModel()).toBe("deepseek-chat");
  });

  it("returns the configured model when LLM_MODEL is set", () => {
    process.env.LLM_MODEL = "gpt-4.1-mini";

    expect(getConfiguredLlmModel()).toBe("gpt-4.1-mini");
  });
});
