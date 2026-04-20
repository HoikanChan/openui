import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("node:fs");
vi.mock("openai");

import { loadOrGenerate } from "./llm";

const MOCK_SPEC = { root: "VLayout", components: {} } as object;

describe("loadOrGenerate", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    delete process.env.REGEN_SNAPSHOTS;
    delete process.env.OPENAI_API_KEY;
  });

  afterEach(() => {
    vi.resetAllMocks();
  });

  it("returns snapshot file content when file exists and REGEN_SNAPSHOTS is unset", async () => {
    vi.mocked(existsSync).mockReturnValue(true);
    vi.mocked(readFileSync).mockReturnValue("root = Table([], [])");

    const result = await loadOrGenerate("table-basic", "Show a table", {}, MOCK_SPEC);

    expect(result).toBe("root = Table([], [])");
    expect(writeFileSync).not.toHaveBeenCalled();
  });

  it("throws a helpful error when snapshot is missing and OPENAI_API_KEY is unset", async () => {
    vi.mocked(existsSync).mockReturnValue(false);

    await expect(
      loadOrGenerate("table-basic", "Show a table", {}, MOCK_SPEC),
    ).rejects.toThrow('Snapshot missing for "table-basic" and OPENAI_API_KEY is not set');
  });

  it("calls LLM, saves snapshot, and returns DSL when file is missing and key is set", async () => {
    vi.mocked(existsSync).mockReturnValue(false);
    vi.mocked(mkdirSync).mockReturnValue(undefined);
    vi.mocked(writeFileSync).mockReturnValue(undefined);
    process.env.OPENAI_API_KEY = "sk-test";

    const { default: OpenAI } = await import("openai");
    const mockCreate = vi.fn().mockResolvedValue({
      choices: [{ message: { content: "root = Table([], [])" } }],
    });
    vi.mocked(OpenAI).mockImplementation(function () {
      return { chat: { completions: { create: mockCreate } } };
    } as any);

    const result = await loadOrGenerate("table-basic", "Show a table", { rows: [] }, MOCK_SPEC);

    expect(mockCreate).toHaveBeenCalledOnce();
    expect(writeFileSync).toHaveBeenCalledOnce();
    expect(result).toBe("root = Table([], [])");
  });

  it("re-generates even when file exists if REGEN_SNAPSHOTS is set", async () => {
    vi.mocked(existsSync).mockReturnValue(true);
    vi.mocked(mkdirSync).mockReturnValue(undefined);
    vi.mocked(writeFileSync).mockReturnValue(undefined);
    process.env.REGEN_SNAPSHOTS = "1";
    process.env.OPENAI_API_KEY = "sk-test";

    const { default: OpenAI } = await import("openai");
    const mockCreate = vi.fn().mockResolvedValue({
      choices: [{ message: { content: "root = Table([], [])" } }],
    });
    vi.mocked(OpenAI).mockImplementation(function () {
      return { chat: { completions: { create: mockCreate } } };
    } as any);

    await loadOrGenerate("table-basic", "Show a table", {}, MOCK_SPEC);

    expect(readFileSync).not.toHaveBeenCalled();
    expect(mockCreate).toHaveBeenCalledOnce();
    expect(writeFileSync).toHaveBeenCalledOnce();
  });
});
