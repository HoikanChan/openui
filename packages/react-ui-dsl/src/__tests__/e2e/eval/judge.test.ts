import { beforeEach, describe, expect, it, vi } from "vitest";

const { invokeRunner } = vi.hoisted(() => ({
  invokeRunner: vi.fn(),
}));

vi.mock("./judge-runner.ts", () => ({
  invokeRunner,
  resolveRunnerType: vi.fn(() => "codex"),
}));

import { judgeFixture } from "./judge.ts";
import { DEFAULT_RUBRIC, buildJudgeSystemPrompt } from "./rubric.ts";

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

  it("appends case-specific hints after the base rubric", () => {
    const prompt = buildJudgeSystemPrompt(undefined, ["Use Mbps for bandwidth", "Prefer vertical card scan order"]);
    expect(prompt).toContain("## Case-specific hints");
    expect(prompt).toContain("- Use Mbps for bandwidth");
    expect(prompt).toContain("- Prefer vertical card scan order");
  });
});

describe("judgeFixture", () => {
  beforeEach(() => {
    invokeRunner.mockReset();
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
