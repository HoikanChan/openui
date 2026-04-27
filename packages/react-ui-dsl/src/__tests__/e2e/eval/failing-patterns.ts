import type { FailingPattern, JudgeScore } from "./types.ts";

type Dimension = "component_fit" | "data_completeness" | "format_quality" | "layout_coherence";

const DIMENSION_META: Record<
  Dimension,
  { pattern: string; likely_cause: string; agent_hint: string }
> = {
  component_fit: {
    pattern: "Poor component selection",
    likely_cause: "The prompt or schema does not guide the model toward appropriate component types for the data shape.",
    agent_hint:
      "Review the component selection logic in the DSL generation prompt. Add examples or explicit rules for when to use Table vs Card vs Chart.",
  },
  data_completeness: {
    pattern: "Missing data fields",
    likely_cause: "The model omits fields from the data model, possibly due to prompt length limits or unclear field importance signals.",
    agent_hint:
      "Ensure all top-level data model fields are mentioned in the DSL. Update the prompt to explicitly require including all provided fields.",
  },
  format_quality: {
    pattern: "Poor value formatting",
    likely_cause: "Dates, numbers, or currency values are not being formatted by the DSL, possibly because FormatDate/FormatNumber are not used.",
    agent_hint:
      "Check that FormatDate, FormatNumber, and FormatBytes are applied where appropriate. Update the prompt to require explicit formatting for numeric and date fields.",
  },
  layout_coherence: {
    pattern: "Incoherent layout",
    likely_cause: "The model produces layouts that do not organize information logically, possibly missing grouping or hierarchy cues.",
    agent_hint:
      "Consider adding layout guidance to the prompt. Use VLayout, HLayout, or Tabs to group related fields.",
  },
};

const LOW_SCORE_THRESHOLD: Record<Dimension, number> = {
  component_fit: 1,
  data_completeness: 1,
  format_quality: 1,
  layout_coherence: 1,
};

const OVERALL_LOW_THRESHOLD = 5;

export function aggregateFailingPatterns(scores: JudgeScore[]): FailingPattern[] {
  if (scores.length === 0) return [];

  const patterns: FailingPattern[] = [];

  for (const dim of Object.keys(DIMENSION_META) as Dimension[]) {
    const affected = scores.filter((s) => s[dim] <= LOW_SCORE_THRESHOLD[dim]);
    if (affected.length === 0) continue;

    const allScores = scores.map((s) => s[dim]);
    const passingAvg = allScores.filter((v) => v > LOW_SCORE_THRESHOLD[dim]).reduce((a, b) => a + b, 0);
    const passingCount = allScores.filter((v) => v > LOW_SCORE_THRESHOLD[dim]).length;
    const avgPassing = passingCount > 0 ? passingAvg / passingCount : 3;
    const avgFailing = affected.reduce((a, s) => a + s[dim], 0) / affected.length;
    const avgImpact = avgPassing - avgFailing;

    patterns.push({
      pattern: DIMENSION_META[dim].pattern,
      affected_fixtures: affected.map((s) => s.fixtureId),
      avg_score_impact: Math.round(avgImpact * 10) / 10,
      likely_cause: DIMENSION_META[dim].likely_cause,
      agent_hint: DIMENSION_META[dim].agent_hint,
    });
  }

  const overallLow = scores.filter((s) => s.overall <= OVERALL_LOW_THRESHOLD);
  if (overallLow.length > 0) {
    const dominantDim = findDominantWeakDimension(overallLow);
    const alreadyCovered = patterns.some((p) =>
      p.affected_fixtures.some((id) => overallLow.some((s) => s.fixtureId === id)),
    );

    if (!alreadyCovered || dominantDim === null) {
      patterns.push({
        pattern: "Overall low quality",
        affected_fixtures: overallLow.map((s) => s.fixtureId),
        avg_score_impact: OVERALL_LOW_THRESHOLD - overallLow.reduce((a, s) => a + s.overall, 0) / overallLow.length,
        likely_cause: "Multiple quality dimensions are degraded simultaneously.",
        agent_hint:
          "Focus on the highest-impact individual dimension first. Re-run after each targeted fix to identify remaining issues.",
      });
    }
  }

  return patterns.sort((a, b) => b.avg_score_impact - a.avg_score_impact);
}

function findDominantWeakDimension(scores: JudgeScore[]): Dimension | null {
  const dims: Dimension[] = ["component_fit", "data_completeness", "format_quality", "layout_coherence"];
  let worstDim: Dimension | null = null;
  let lowestAvg = Infinity;

  for (const dim of dims) {
    const avg = scores.reduce((a, s) => a + s[dim], 0) / scores.length;
    if (avg < lowestAvg) {
      lowestAvg = avg;
      worstDim = dim;
    }
  }

  return worstDim;
}

export function computeOverallScore(scores: JudgeScore[]): number {
  if (scores.length === 0) return 0;
  return scores.reduce((a, s) => a + s.overall, 0) / scores.length;
}
