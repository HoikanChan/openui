import { describe, expect, it } from "vitest";
import { aggregateFailingPatterns, computeOverallScore } from "./failing-patterns.ts";
import type { JudgeScore } from "./types.ts";

function makeScore(fixtureId: string, overrides: Partial<JudgeScore> = {}): JudgeScore {
  return {
    fixtureId,
    component_fit: 3,
    data_completeness: 3,
    format_quality: 3,
    layout_coherence: 3,
    overall: 10,
    feedback: "great",
    screenshotPath: null,
    degraded: false,
    ...overrides,
  };
}

describe("aggregateFailingPatterns", () => {
  it("returns empty array when no scores", () => {
    expect(aggregateFailingPatterns([])).toEqual([]);
  });

  it("returns empty array when all scores are high", () => {
    const scores = [makeScore("a"), makeScore("b"), makeScore("c")];
    expect(aggregateFailingPatterns(scores)).toEqual([]);
  });

  it("identifies component_fit pattern when multiple fixtures have low fit scores", () => {
    const scores = [
      makeScore("a", { component_fit: 0, overall: 4 }),
      makeScore("b", { component_fit: 1, overall: 5 }),
      makeScore("c"),
    ];
    const patterns = aggregateFailingPatterns(scores);
    const fitPattern = patterns.find((p) => p.pattern === "Poor component selection");
    expect(fitPattern).toBeDefined();
    expect(fitPattern!.affected_fixtures).toContain("a");
    expect(fitPattern!.affected_fixtures).toContain("b");
    expect(fitPattern!.affected_fixtures).not.toContain("c");
  });

  it("identifies data_completeness pattern", () => {
    const scores = [
      makeScore("x", { data_completeness: 0, overall: 3 }),
      makeScore("y"),
    ];
    const patterns = aggregateFailingPatterns(scores);
    const completenessPattern = patterns.find((p) => p.pattern === "Missing data fields");
    expect(completenessPattern).toBeDefined();
    expect(completenessPattern!.affected_fixtures).toEqual(["x"]);
  });

  it("identifies format_quality pattern", () => {
    const scores = [makeScore("fmt", { format_quality: 1, overall: 6 })];
    const patterns = aggregateFailingPatterns(scores);
    expect(patterns.some((p) => p.pattern === "Poor value formatting")).toBe(true);
  });

  it("identifies layout_coherence pattern", () => {
    const scores = [makeScore("layout", { layout_coherence: 0, overall: 4 })];
    const patterns = aggregateFailingPatterns(scores);
    expect(patterns.some((p) => p.pattern === "Incoherent layout")).toBe(true);
  });

  it("sorts patterns by avg_score_impact descending", () => {
    const scores = [
      makeScore("a", { component_fit: 0, data_completeness: 0, overall: 2 }),
    ];
    const patterns = aggregateFailingPatterns(scores);
    for (let i = 1; i < patterns.length; i++) {
      expect(patterns[i - 1]!.avg_score_impact).toBeGreaterThanOrEqual(patterns[i]!.avg_score_impact);
    }
  });

  it("includes agent_hint and likely_cause for each pattern", () => {
    const scores = [makeScore("a", { component_fit: 0, overall: 3 })];
    const patterns = aggregateFailingPatterns(scores);
    for (const p of patterns) {
      expect(p.agent_hint).toBeTruthy();
      expect(p.likely_cause).toBeTruthy();
    }
  });
});

describe("computeOverallScore", () => {
  it("returns 0 for empty array", () => {
    expect(computeOverallScore([])).toBe(0);
  });

  it("returns the single score for one fixture", () => {
    expect(computeOverallScore([makeScore("a", { overall: 7 })])).toBe(7);
  });

  it("returns the average for multiple fixtures", () => {
    const scores = [makeScore("a", { overall: 6 }), makeScore("b", { overall: 8 })];
    expect(computeOverallScore(scores)).toBe(7);
  });
});
