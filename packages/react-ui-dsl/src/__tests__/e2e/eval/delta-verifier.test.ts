import { describe, expect, it } from "vitest";
import { computeDelta, pickVerificationOutcome } from "./delta-verifier.ts";
import type { JudgeScore } from "./types.ts";

function makeScore(fixtureId: string, overall: number): JudgeScore {
  return {
    fixtureId,
    component_fit: 3,
    data_completeness: 3,
    format_quality: 3,
    layout_coherence: 3,
    overall,
    feedback: "",
    visual_issues: [],
    screenshotPath: null,
    degraded: false,
  };
}

describe("computeDelta", () => {
  it("returns zero delta when scores are unchanged", () => {
    const scores = [makeScore("a", 7), makeScore("b", 8)];
    const result = computeDelta({ baselineScores: scores, currentScores: scores });
    expect(result.delta.overall).toBe(0);
    expect(result.regressions).toHaveLength(0);
    expect(result.hasRegression).toBe(false);
  });

  it("computes positive delta when scores improve", () => {
    const baseline = [makeScore("a", 6), makeScore("b", 7)];
    const current = [makeScore("a", 8), makeScore("b", 9)];
    const result = computeDelta({ baselineScores: baseline, currentScores: current });
    expect(result.delta.overall).toBe(2);
    expect(result.hasRegression).toBe(false);
  });

  it("computes negative delta and reports regression when a fixture drops significantly", () => {
    const baseline = [makeScore("a", 8), makeScore("b", 8)];
    const current = [makeScore("a", 4), makeScore("b", 8)];
    const result = computeDelta({ baselineScores: baseline, currentScores: current });
    expect(result.delta.overall).toBe(-2);
    expect(result.hasRegression).toBe(true);
    expect(result.regressions).toHaveLength(1);
    expect(result.regressions[0]!.fixtureId).toBe("a");
    expect(result.regressions[0]!.delta).toBe(-4);
  });

  it("does not report regression for small score drops within tolerance", () => {
    const baseline = [makeScore("a", 8)];
    const current = [makeScore("a", 7.6)];
    const result = computeDelta({ baselineScores: baseline, currentScores: current });
    expect(result.hasRegression).toBe(false);
  });

  it("populates per_fixture delta for each fixture", () => {
    const baseline = [makeScore("a", 5), makeScore("b", 7)];
    const current = [makeScore("a", 7), makeScore("b", 6)];
    const result = computeDelta({ baselineScores: baseline, currentScores: current });
    expect(result.delta.per_fixture["a"]).toBe(2);
    expect(result.delta.per_fixture["b"]).toBe(-1);
  });

  it("skips fixtures present in baseline but missing in current", () => {
    const baseline = [makeScore("a", 7), makeScore("ghost", 8)];
    const current = [makeScore("a", 7)];
    const result = computeDelta({ baselineScores: baseline, currentScores: current });
    expect(result.delta.per_fixture["ghost"]).toBeUndefined();
  });
});

describe("pickVerificationOutcome", () => {
  it("returns success when no regression", () => {
    const result = computeDelta({
      baselineScores: [makeScore("a", 7)],
      currentScores: [makeScore("a", 8)],
    });
    expect(pickVerificationOutcome(result)).toBe("success");
  });

  it("returns review-needed when regression exists", () => {
    const result = computeDelta({
      baselineScores: [makeScore("a", 8)],
      currentScores: [makeScore("a", 3)],
    });
    expect(pickVerificationOutcome(result)).toBe("review-needed");
  });
});
