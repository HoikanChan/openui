import type { DeltaSummary, JudgeScore, RegressionEntry, VerificationSummaryData } from "./types.ts";
import { computeOverallScore } from "./failing-patterns.ts";

const REGRESSION_THRESHOLD = -0.5;

export interface DeltaVerifierInput {
  baselineScores: JudgeScore[];
  currentScores: JudgeScore[];
}

export interface DeltaVerifierResult {
  delta: DeltaSummary;
  regressions: RegressionEntry[];
  hasRegression: boolean;
  baselineOverall: number;
  currentOverall: number;
}

export function computeDelta(input: DeltaVerifierInput): DeltaVerifierResult {
  const { baselineScores, currentScores } = input;

  const baselineMap = new Map(baselineScores.map((s) => [s.fixtureId, s]));
  const currentMap = new Map(currentScores.map((s) => [s.fixtureId, s]));

  const perFixture: Record<string, number> = {};
  const regressions: RegressionEntry[] = [];

  for (const [fixtureId, current] of currentMap) {
    const baseline = baselineMap.get(fixtureId);
    if (!baseline) continue;

    const delta = current.overall - baseline.overall;
    perFixture[fixtureId] = Math.round(delta * 10) / 10;

    if (delta < REGRESSION_THRESHOLD) {
      regressions.push({
        fixtureId,
        scoreBefore: baseline.overall,
        scoreAfter: current.overall,
        delta: Math.round(delta * 10) / 10,
      });
    }
  }

  const baselineOverall = computeOverallScore(baselineScores);
  const currentOverall = computeOverallScore(currentScores);
  const overallDelta = Math.round((currentOverall - baselineOverall) * 10) / 10;

  return {
    delta: { overall: overallDelta, per_fixture: perFixture },
    regressions,
    hasRegression: regressions.length > 0,
    baselineOverall,
    currentOverall,
  };
}

export function pickVerificationOutcome(
  deltaResult: DeltaVerifierResult,
): "success" | "review-needed" {
  if (deltaResult.hasRegression) return "review-needed";
  return "success";
}
