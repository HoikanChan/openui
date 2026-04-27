import { existsSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

const __dirname = dirname(fileURLToPath(import.meta.url));
import { writeTaskBundle } from "./task-bundle-writer.ts";
import { ResultBundleError, hasResultBundle, readResultBundle } from "./result-bundle-reader.ts";
import { createRunWorkspace, getResultBundlePath, getRunDir, getTaskBundlePath } from "./run-manifest.ts";
import type { JudgeScore } from "./types.ts";

function testRunId(): string {
  return `__test__${Date.now()}`;
}

function fakeScore(fixtureId: string): JudgeScore {
  return {
    fixtureId,
    component_fit: 2,
    data_completeness: 2,
    format_quality: 2,
    layout_coherence: 2,
    overall: 7,
    feedback: "looks ok",
    screenshotPath: null,
    degraded: false,
  };
}

describe("writeTaskBundle", () => {
  let runId: string;

  beforeEach(() => {
    runId = testRunId();
    createRunWorkspace(runId, false);
  });

  afterEach(() => {
    rmSync(getRunDir(runId), { recursive: true, force: true });
  });

  it("creates summary.md, constraints.md, targets.json, failing-patterns.json, and adapter docs", () => {
    const bundleDir = getTaskBundlePath(runId);

    writeTaskBundle({
      runId,
      overallScore: 7,
      judgeScores: [fakeScore("table-basic"), fakeScore("bar-chart")],
      failingPatterns: [],
      snapshotsDir: resolve(__dirname, "../snapshots"),
    });

    expect(existsSync(resolve(bundleDir, "summary.md"))).toBe(true);
    expect(existsSync(resolve(bundleDir, "constraints.md"))).toBe(true);
    expect(existsSync(resolve(bundleDir, "targets.json"))).toBe(true);
    expect(existsSync(resolve(bundleDir, "failing-patterns.json"))).toBe(true);
    expect(existsSync(resolve(bundleDir, "adapters", "codex.md"))).toBe(true);
    expect(existsSync(resolve(bundleDir, "adapters", "claude-code.md"))).toBe(true);
    expect(existsSync(resolve(bundleDir, "adapters", "opencode.md"))).toBe(true);
  });

  it("writes all fixture IDs to targets.json", () => {
    writeTaskBundle({
      runId,
      overallScore: 7,
      judgeScores: [fakeScore("a"), fakeScore("b")],
      failingPatterns: [],
      snapshotsDir: resolve(__dirname, "../snapshots"),
    });
    const targets = JSON.parse(
      readFileSync(resolve(getTaskBundlePath(runId), "targets.json"), "utf-8"),
    );
    expect(targets).toHaveLength(2);
    expect(targets.map((t: { fixtureId: string }) => t.fixtureId)).toEqual(["a", "b"]);
  });
});

describe("readResultBundle", () => {
  let runId: string;

  beforeEach(() => {
    runId = testRunId();
    createRunWorkspace(runId, false);
  });

  afterEach(() => {
    rmSync(getRunDir(runId), { recursive: true, force: true });
  });

  it("throws ResultBundleError when result.json is missing", () => {
    expect(() => readResultBundle(runId)).toThrow(ResultBundleError);
  });

  it("throws ResultBundleError when runId mismatches", () => {
    const resultPath = resolve(getResultBundlePath(runId), "result.json");
    writeFileSync(
      resultPath,
      JSON.stringify({ runId: "other", completedAt: new Date().toISOString() }),
    );
    expect(() => readResultBundle(runId)).toThrow(ResultBundleError);
  });

  it("reads a valid result bundle", () => {
    const bundleDir = getResultBundlePath(runId);
    writeFileSync(
      resolve(bundleDir, "result.json"),
      JSON.stringify({ runId, completedAt: new Date().toISOString(), agentType: "claude-code" }),
    );
    writeFileSync(resolve(bundleDir, "touched-files.json"), JSON.stringify(["src/foo.ts"]));
    writeFileSync(
      resolve(bundleDir, "claimed-affected-fixtures.json"),
      JSON.stringify(["table-basic"]),
    );
    writeFileSync(resolve(bundleDir, "change-summary.md"), "Fixed table formatting.");

    const bundle = readResultBundle(runId);
    expect(bundle.result.runId).toBe(runId);
    expect(bundle.result.agentType).toBe("claude-code");
    expect(bundle.touchedFiles).toEqual(["src/foo.ts"]);
    expect(bundle.claimedAffectedFixtures).toEqual(["table-basic"]);
    expect(bundle.changeSummary).toContain("Fixed");
  });

  it("hasResultBundle returns false when no result.json", () => {
    expect(hasResultBundle(runId)).toBe(false);
  });

  it("hasResultBundle returns true when result.json exists", () => {
    writeFileSync(
      resolve(getResultBundlePath(runId), "result.json"),
      JSON.stringify({ runId, completedAt: new Date().toISOString() }),
    );
    expect(hasResultBundle(runId)).toBe(true);
  });
});
