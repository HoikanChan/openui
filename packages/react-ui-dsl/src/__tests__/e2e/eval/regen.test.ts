import { existsSync, rmSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const __dirname = dirname(fileURLToPath(import.meta.url));

// DSL generation runs through the Eval Generation CLI (Java SDK path).
// Mock that boundary; regenFixtures still owns staging + atomic rename on top.
const { runGenerationCli } = vi.hoisted(() => ({ runGenerationCli: vi.fn() }));

vi.mock("./generation-cli.ts", () => ({ runGenerationCli }));
vi.mock("./run-manifest.ts", () => ({ markPhaseDone: vi.fn() }));

import type { BenchmarkCase } from "../benchmark-loader.ts";
import { regenFixtures, snapshotsDirForSuite } from "./regen.ts";

interface CliResult {
  id: string;
  status: "ok" | "error";
  dsl?: string;
  error?: string;
}

const stagingBase = resolve(__dirname, ".regen-staging");
const snapshotsDir = snapshotsDirForSuite("e2e");

function makeFixture(id: string): BenchmarkCase {
  return { id, prompt: `render ${id}`, dataModel: {}, evalHints: [], taxonomy: [] };
}

/** Simulate the CLI: emit one result per case (streamed via onResult) and return the map. */
function mockCli(resultFor: (id: string) => Omit<CliResult, "id">): void {
  runGenerationCli.mockImplementation(
    async (cases: Array<{ id: string }>, options: { onResult?: (r: CliResult) => void }) => {
      const results = new Map<string, CliResult>();
      for (const c of cases) {
        const result: CliResult = { id: c.id, ...resultFor(c.id) };
        results.set(c.id, result);
        options.onResult?.(result);
      }
      return results;
    },
  );
}

describe("regenFixtures", () => {
  const testRunId = `test_${Date.now()}`;

  beforeEach(() => {
    vi.clearAllMocks();
    process.env["LLM_API_KEY"] = "test-key";
  });

  afterEach(() => {
    const stagingDir = resolve(stagingBase, testRunId);
    if (existsSync(stagingDir)) rmSync(stagingDir, { recursive: true, force: true });
    delete process.env["LLM_API_KEY"];
    delete process.env["EVAL_REGEN_CONCURRENCY"];
  });

  it("writes all generated DSL files to snapshots dir on full success", async () => {
    const fixtures = [makeFixture("f-a"), makeFixture("f-b")];
    mockCli(() => ({ status: "ok", dsl: "root = Gauge()" }));

    const snapshotA = resolve(snapshotsDir, "f-a.dsl");
    const snapshotB = resolve(snapshotsDir, "f-b.dsl");
    [snapshotA, snapshotB].forEach((p) => {
      if (existsSync(p)) rmSync(p);
    });

    try {
      const { success } = await regenFixtures(fixtures, { runId: testRunId, suite: "e2e" });
      expect(success).toBe(true);
      expect(existsSync(snapshotA)).toBe(true);
      expect(existsSync(snapshotB)).toBe(true);
    } finally {
      [snapshotA, snapshotB].forEach((p) => {
        if (existsSync(p)) rmSync(p);
      });
    }
  });

  it("throws and preserves staging when any fixture fails", async () => {
    const fixtures = [makeFixture("f-ok"), makeFixture("f-fail")];
    mockCli((id) =>
      id === "f-fail"
        ? { status: "error", error: "api error" }
        : { status: "ok", dsl: "root = Gauge()" },
    );

    const stagingDir = resolve(stagingBase, testRunId);
    await expect(regenFixtures(fixtures, { runId: testRunId, suite: "e2e" })).rejects.toThrow(
      "f-fail",
    );
    // staging kept for debugging
    expect(existsSync(stagingDir)).toBe(true);
    // snapshot for f-ok must NOT have been written (atomic: all-or-nothing)
    expect(existsSync(resolve(snapshotsDir, "f-ok.dsl"))).toBe(false);
  });

  it("passes the EVAL_REGEN_CONCURRENCY cap through to the CLI", async () => {
    // Concurrency is now enforced inside the Java CLI; the Node side only forwards it.
    process.env["EVAL_REGEN_CONCURRENCY"] = "2";
    mockCli(() => ({ status: "ok", dsl: "root = X()" }));

    const fixtures = Array.from({ length: 6 }, (_, i) => makeFixture(`fc-${i}`));
    const snapshotPaths = fixtures.map((f) => resolve(snapshotsDir, `${f.id}.dsl`));

    try {
      await regenFixtures(fixtures, { runId: testRunId, suite: "e2e" });
      expect(runGenerationCli).toHaveBeenCalledOnce();
      const options = runGenerationCli.mock.calls[0]?.[1] as { concurrency?: number };
      expect(options.concurrency).toBe(2);
    } finally {
      snapshotPaths.forEach((p) => {
        if (existsSync(p)) rmSync(p);
      });
    }
  });
});
