import type { DeltaVerifierResult } from "./delta-verifier.ts";
import type { EvalHistory, VerificationSummaryData } from "./types.ts";
import { isStalled } from "./eval-history.ts";

export function buildVerificationSummary(
  deltaResult: DeltaVerifierResult,
  history: EvalHistory,
): VerificationSummaryData {
  const { baselineOverall, currentOverall, delta, regressions } = deltaResult;
  const stalled = isStalled(history);

  if (stalled) {
    return {
      outcome: "stalled",
      scoreBefore: baselineOverall,
      scoreAfter: currentOverall,
      delta: delta.overall,
      regressions,
      stallMessage: buildStallMessage(history.stallCounter, currentOverall),
    };
  }

  if (deltaResult.hasRegression) {
    return {
      outcome: "review-needed",
      scoreBefore: baselineOverall,
      scoreAfter: currentOverall,
      delta: delta.overall,
      regressions,
    };
  }

  return {
    outcome: "success",
    scoreBefore: baselineOverall,
    scoreAfter: currentOverall,
    delta: delta.overall,
    regressions: [],
    commitRecommendation: buildCommitRecommendation(baselineOverall, currentOverall, delta.overall),
  };
}

export function printVerificationSummary(summary: VerificationSummaryData, runId: string): void {
  const sign = summary.delta >= 0 ? "+" : "";
  console.log(`\nVerification complete for run ${runId}`);
  console.log(`  Score: ${summary.scoreBefore.toFixed(1)} → ${summary.scoreAfter.toFixed(1)} (${sign}${summary.delta.toFixed(1)})`);
  console.log(`  Outcome: ${summary.outcome.toUpperCase()}`);

  if (summary.regressions.length > 0) {
    console.log(`\n  Regressions (${summary.regressions.length}):`);
    for (const r of summary.regressions) {
      console.log(`    ${r.fixtureId}: ${r.scoreBefore} → ${r.scoreAfter} (${r.delta})`);
    }
  }

  if (summary.stallMessage) {
    console.log(`\n  ${summary.stallMessage}`);
  }

  if (summary.commitRecommendation) {
    console.log(`\n  ${summary.commitRecommendation}`);
  }
}

function buildCommitRecommendation(before: number, after: number, delta: number): string {
  const sign = delta >= 0 ? "+" : "";
  return (
    `Quality improved: ${before.toFixed(1)} → ${after.toFixed(1)} (${sign}${delta.toFixed(1)}). ` +
    `Review the changes and commit when satisfied:\n` +
    `  git add -p && git commit -m "improve: DSL quality +${delta.toFixed(1)} overall score"`
  );
}

function buildStallMessage(stallCounter: number, currentScore: number): string {
  return (
    `No quality improvement for ${stallCounter} consecutive verified iterations ` +
    `(current score: ${currentScore.toFixed(1)}/10). ` +
    `Consider a different optimization strategy, rubric calibration, or manual intervention.`
  );
}
