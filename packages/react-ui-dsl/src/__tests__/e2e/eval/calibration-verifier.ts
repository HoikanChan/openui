import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import type { CorrectionEntry, JudgeScore } from "./types.ts";
import { getCorrectionsPath } from "./run-manifest.ts";
import { judgeFixture } from "./judge.ts";
import { hashRubric } from "./rubric.ts";

const CALIBRATION_TOLERANCE = 1;

export interface CalibrationInput {
  runId: string;
  pendingCorrections: CorrectionEntry[];
  updatedRubric: string;
  baselineScores: JudgeScore[];
}

export interface CalibrationResult {
  appliedCount: number;
  failedCount: number;
  updatedCorrections: CorrectionEntry[];
}

export function readCorrections(runId: string): CorrectionEntry[] {
  const path = getCorrectionsPath(runId);
  if (!existsSync(path)) return [];
  return JSON.parse(readFileSync(path, "utf-8")) as CorrectionEntry[];
}

export function writeCorrections(runId: string, corrections: CorrectionEntry[]): void {
  writeFileSync(getCorrectionsPath(runId), JSON.stringify(corrections, null, 2), "utf-8");
}

export function getPendingJudgeCorrections(corrections: CorrectionEntry[]): CorrectionEntry[] {
  return corrections.filter((c) => c.target === "judge" && c.state === "pending");
}

export function getPendingPromptCorrections(corrections: CorrectionEntry[]): CorrectionEntry[] {
  return corrections.filter((c) => c.target === "prompt" && c.state === "pending");
}

export async function runCalibration(input: CalibrationInput): Promise<CalibrationResult> {
  const { pendingCorrections, updatedRubric, baselineScores } = input;
  const rubricHash = hashRubric(updatedRubric);

  const baselineMap = new Map(baselineScores.map((s) => [s.fixtureId, s]));

  const updatedCorrections: CorrectionEntry[] = [];
  let appliedCount = 0;
  let failedCount = 0;

  for (const correction of pendingCorrections) {
    if (!correction.fixtureId || !correction.score_corrections) {
      updatedCorrections.push({ ...correction, state: "applied", updatedAt: new Date().toISOString() });
      appliedCount++;
      continue;
    }

    const baseline = baselineMap.get(correction.fixtureId);
    if (!baseline) {
      updatedCorrections.push({
        ...correction,
        state: "failed",
        failureReason: `No baseline score found for fixture ${correction.fixtureId}`,
        updatedAt: new Date().toISOString(),
      });
      failedCount++;
      continue;
    }

    let newScore: JudgeScore;
    try {
      newScore = await judgeFixture({
        fixtureId: baseline.fixtureId,
        dsl: "",
        dataModel: {},
        screenshotPath: baseline.screenshotPath,
        rubricOverride: updatedRubric,
      });
    } catch (err) {
      updatedCorrections.push({
        ...correction,
        state: "failed",
        failureReason: err instanceof Error ? err.message : String(err),
        updatedAt: new Date().toISOString(),
      });
      failedCount++;
      continue;
    }

    const diverged = checkDivergence(newScore, correction.score_corrections);
    if (diverged) {
      updatedCorrections.push({
        ...correction,
        state: "failed",
        rubric_hash: rubricHash,
        failureReason: `Updated rubric still diverges from human corrections beyond tolerance (±${CALIBRATION_TOLERANCE})`,
        updatedAt: new Date().toISOString(),
      });
      failedCount++;
    } else {
      updatedCorrections.push({
        ...correction,
        state: "applied",
        rubric_hash: rubricHash,
        updatedAt: new Date().toISOString(),
      });
      appliedCount++;
    }
  }

  return { appliedCount, failedCount, updatedCorrections };
}

function checkDivergence(
  judged: JudgeScore,
  corrections: Partial<Pick<JudgeScore, "component_fit" | "data_completeness" | "format_quality" | "layout_coherence" | "overall">>,
): boolean {
  for (const [key, expected] of Object.entries(corrections) as Array<[keyof typeof corrections, number]>) {
    const actual = judged[key as keyof JudgeScore];
    if (typeof actual === "number" && Math.abs(actual - expected) > CALIBRATION_TOLERANCE) {
      return true;
    }
  }
  return false;
}

export function forwardPromptCorrections(
  corrections: CorrectionEntry[],
): { forwarded: CorrectionEntry[]; toInclude: Array<{ fixtureId?: string; text_feedback: string }> } {
  const pending = getPendingPromptCorrections(corrections);
  const toInclude = pending
    .filter((c) => c.text_feedback)
    .map((c) => ({ fixtureId: c.fixtureId, text_feedback: c.text_feedback! }));

  const forwarded = pending.map((c) => ({
    ...c,
    state: "forwarded_to_optimizer" as const,
    updatedAt: new Date().toISOString(),
  }));

  return { forwarded, toInclude };
}
