import { existsSync, readFileSync, writeFileSync } from "node:fs";
import type { EvalHistory, IterationRecord, VerificationOutcome } from "./types.ts";
import { getHistoryPath } from "./run-manifest.ts";

const STALL_THRESHOLD = 3;

export function readEvalHistory(runId: string): EvalHistory {
  const path = getHistoryPath(runId);
  if (!existsSync(path)) {
    return { runId, iterations: [], lastKnownScore: 0, stallCounter: 0 };
  }
  return JSON.parse(readFileSync(path, "utf-8")) as EvalHistory;
}

export function appendIteration(
  runId: string,
  record: Omit<IterationRecord, "stallCounter">,
): EvalHistory {
  const history = readEvalHistory(runId);
  const improved = record.scoreAfter > record.scoreBefore;
  const stallCounter = improved ? 0 : history.stallCounter + 1;

  const iteration: IterationRecord = { ...record, stallCounter };
  const updated: EvalHistory = {
    runId,
    iterations: [...history.iterations, iteration],
    lastKnownScore: record.scoreAfter,
    stallCounter,
  };

  writeFileSync(getHistoryPath(runId), JSON.stringify(updated, null, 2), "utf-8");
  return updated;
}

export function isStalled(history: EvalHistory): boolean {
  return history.stallCounter >= STALL_THRESHOLD;
}

export function formatHistorySummary(history: EvalHistory): string {
  if (history.iterations.length === 0) return "No verified iterations yet.";

  const lines = history.iterations.map((iter, i) => {
    const delta = iter.scoreAfter - iter.scoreBefore;
    const sign = delta >= 0 ? "+" : "";
    return `  Iteration ${i + 1}: ${iter.scoreBefore.toFixed(1)} → ${iter.scoreAfter.toFixed(1)} (${sign}${delta.toFixed(1)}) [${iter.outcome}]`;
  });

  const stalledMsg = isStalled(history)
    ? `\n  ⚠ STALLED — no improvement for ${history.stallCounter} consecutive iterations`
    : "";

  return [`Iteration history (${history.iterations.length} total):`, ...lines, stalledMsg]
    .filter(Boolean)
    .join("\n");
}
