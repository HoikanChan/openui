import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { rmSync, existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));

const { invokeRunner } = vi.hoisted(() => ({
  invokeRunner: vi.fn(),
}));

vi.mock("./judge-runner.ts", () => ({
  invokeRunner,
  resolveRunnerType: vi.fn(() => "codex"),
  resolveModel: vi.fn(() => "test-model"),
}));

import { judgeFixture, judgeFixtures, makeFailedFixtureScore } from "./judge.ts";
import { DEFAULT_RUBRIC, buildJudgeSystemPrompt } from "./rubric.ts";

describe("makeFailedFixtureScore", () => {
  it("synthesizes a zero score with the failure reason in feedback", () => {
    const score = makeFailedFixtureScore("paginated-list", "/tmp/shot.png", "expected 0 errors but got 5");
    expect(score).toEqual({
      fixtureId: "paginated-list",
      component_fit: 0,
      data_completeness: 0,
      format_quality: 0,
      layout_coherence: 0,
      overall: 0,
      feedback: "[benchmark gate] expected 0 errors but got 5",
      visual_issues: [],
      screenshotPath: "/tmp/shot.png",
      degraded: false,
    });
  });

  it("falls back to a generic feedback string when no reason is provided", () => {
    const score = makeFailedFixtureScore("schema-x", null, undefined);
    expect(score.feedback).toBe("[benchmark gate] parse or render failure");
    expect(score.screenshotPath).toBeNull();
  });
});

describe("buildJudgeSystemPrompt", () => {
  it("documents visual issue diagnostics and screenshot-based layout penalties", () => {
    expect(DEFAULT_RUBRIC).toContain("\"visual_issues\"");
    expect(DEFAULT_RUBRIC).toContain("overlap");
    expect(DEFAULT_RUBRIC).toContain("clipped");
    expect(DEFAULT_RUBRIC).toContain("layout_coherence must be 2 or below");
    expect(DEFAULT_RUBRIC).toContain("cannot receive layout_coherence = 3");
  });

  it("penalizes vertical stacking with excessive whitespace and requires specific feedback", () => {
    expect(DEFAULT_RUBRIC).toContain("vertical stacking");
    expect(DEFAULT_RUBRIC).toContain("excessive whitespace");
    expect(DEFAULT_RUBRIC).toContain("poor grouping");
    expect(DEFAULT_RUBRIC).toContain("overall must be 5 or below");
    expect(DEFAULT_RUBRIC).toContain("feedback must explicitly mention");
  });

  it("documents component-agnostic missing-data penalties for empty renders", () => {
    expect(DEFAULT_RUBRIC).toContain("wrong data path");
    expect(DEFAULT_RUBRIC).toContain("hinted path as required evidence");
    expect(DEFAULT_RUBRIC).toContain("Do not infer that data was surfaced from DSL intent");
    expect(DEFAULT_RUBRIC).toContain("renders an empty state");
    expect(DEFAULT_RUBRIC).toContain("hard missing-data failure");
    expect(DEFAULT_RUBRIC).toContain("data_completeness must be 0");
    expect(DEFAULT_RUBRIC).toContain("format_quality must be 0");
    expect(DEFAULT_RUBRIC).toContain("overall must be 3 or below");
    expect(DEFAULT_RUBRIC).toContain("Column headers, field labels");
    expect(DEFAULT_RUBRIC).toContain("component-agnostic");
    expect(DEFAULT_RUBRIC).toContain("tables, lists, cards, descriptions, charts, dashboards");
  });

  it("appends case-specific hints after the base rubric", () => {
    const prompt = buildJudgeSystemPrompt(undefined, ["Use Mbps for bandwidth", "Prefer vertical card scan order"]);
    expect(prompt).toContain("## Case-specific hints");
    expect(prompt).toContain("- Use Mbps for bandwidth");
    expect(prompt).toContain("- Prefer vertical card scan order");
  });
});

describe("judge cache", () => {
  const cacheDir = resolve(__dirname, ".judge-cache");

  beforeEach(() => {
    invokeRunner.mockReset();
    if (existsSync(cacheDir)) rmSync(cacheDir, { recursive: true, force: true });
  });

  afterEach(() => {
    if (existsSync(cacheDir)) rmSync(cacheDir, { recursive: true, force: true });
  });

  const okResponse = JSON.stringify({
    component_fit: 3, data_completeness: 3, format_quality: 3,
    layout_coherence: 3, overall: 8, feedback: "good",
  });

  it("returns cached result on second identical call without invoking runner again", async () => {
    invokeRunner.mockResolvedValue(okResponse);
    const input = { fixtureId: "x", dsl: "root = Gauge()", dataModel: {}, screenshotPath: null };
    await judgeFixture(input);
    await judgeFixture(input);
    expect(invokeRunner).toHaveBeenCalledTimes(1);
  });

  it("misses cache when rubric changes (different evalHints)", async () => {
    invokeRunner.mockResolvedValue(okResponse);
    const base = { fixtureId: "y", dsl: "root = Gauge()", dataModel: {}, screenshotPath: null };
    await judgeFixture({ ...base, evalHints: ["hint-A"] });
    await judgeFixture({ ...base, evalHints: ["hint-B"] });
    expect(invokeRunner).toHaveBeenCalledTimes(2);
  });

  it("misses cache when DSL changes", async () => {
    invokeRunner.mockResolvedValue(okResponse);
    const base = { fixtureId: "z", dataModel: {}, screenshotPath: null };
    await judgeFixture({ ...base, dsl: "root = A()" });
    await judgeFixture({ ...base, dsl: "root = B()" });
    expect(invokeRunner).toHaveBeenCalledTimes(2);
  });
});

describe("judgeFixture", () => {
  const cacheDir = resolve(__dirname, ".judge-cache");

  beforeEach(() => {
    invokeRunner.mockReset();
    if (existsSync(cacheDir)) rmSync(cacheDir, { recursive: true, force: true });
  });

  afterEach(() => {
    if (existsSync(cacheDir)) rmSync(cacheDir, { recursive: true, force: true });
  });

  it("normalizes visual_issues against the supported allowlist", async () => {
    invokeRunner.mockResolvedValue(`{
      "component_fit": 3,
      "data_completeness": 2,
      "format_quality": 2,
      "layout_coherence": 1,
      "overall": 4,
      "feedback": "Labels overlap in the chart center.",
      "visual_issues": [" overlap ", "bad-tag", "clipped", 42]
    }`);

    const score = await judgeFixture({
      fixtureId: "percentage-as-decimal",
      dsl: "root = Gauge()",
      dataModel: {},
      screenshotPath: null,
    });

    expect(score.visual_issues).toEqual(["overlap", "clipped"]);
  });

  it("runs judges concurrently with a bounded pool, preserving input order", async () => {
    const previousConcurrency = process.env["EVAL_JUDGE_CONCURRENCY"];
    process.env["EVAL_JUDGE_CONCURRENCY"] = "3";

    let active = 0;
    let peak = 0;

    invokeRunner.mockImplementation(async (_type: unknown, runnerInput: { fixtureId: string }) => {
      active++;
      peak = Math.max(peak, active);
      await new Promise((r) => setTimeout(r, 20));
      active--;
      return JSON.stringify({
        component_fit: 3,
        data_completeness: 3,
        format_quality: 3,
        layout_coherence: 3,
        overall: Number(runnerInput.fixtureId.replace("f", "")),
        feedback: runnerInput.fixtureId,
      });
    });

    const inputs = Array.from({ length: 8 }, (_, i) => ({
      fixtureId: `f${i}`,
      dsl: `root = X${i}()`, // unique per fixture so each gets its own cache key
      dataModel: {},
      screenshotPath: null,
    }));

    const results = await judgeFixtures(inputs);

    expect(results.map((r) => r.fixtureId)).toEqual(["f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7"]);
    expect(peak).toBeGreaterThan(1);
    expect(peak).toBeLessThanOrEqual(3);

    if (previousConcurrency === undefined) delete process.env["EVAL_JUDGE_CONCURRENCY"];
    else process.env["EVAL_JUDGE_CONCURRENCY"] = previousConcurrency;
  });

  it("defaults visual_issues to an empty array when omitted", async () => {
    invokeRunner.mockResolvedValue(`{
      "component_fit": 3,
      "data_completeness": 3,
      "format_quality": 3,
      "layout_coherence": 2,
      "overall": 8,
      "feedback": "Readable with minor spacing issues."
    }`);

    const score = await judgeFixture({
      fixtureId: "byte-large-values",
      dsl: "root = Dashboard()",
      dataModel: {},
      screenshotPath: null,
    });

    expect(score.visual_issues).toEqual([]);
  });
});
