import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import type { ResultBundle, ResultJson } from "./types.ts";
import { getResultBundlePath } from "./run-manifest.ts";

export class ResultBundleError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ResultBundleError";
  }
}

export function readResultBundle(runId: string): ResultBundle {
  const bundleDir = getResultBundlePath(runId);

  const resultJsonPath = resolve(bundleDir, "result.json");
  const changeSummaryPath = resolve(bundleDir, "change-summary.md");
  const touchedFilesPath = resolve(bundleDir, "touched-files.json");
  const claimedFixturesPath = resolve(bundleDir, "claimed-affected-fixtures.json");

  if (!existsSync(resultJsonPath)) {
    throw new ResultBundleError(
      `result-bundle/result.json not found for run ${runId}. ` +
        `The agent must write this file before verification can proceed.`,
    );
  }

  const result = parseResultJson(resultJsonPath, runId);

  if (result.runId !== runId) {
    throw new ResultBundleError(
      `result.json runId mismatch: expected "${runId}", got "${result.runId}"`,
    );
  }

  const changeSummary = existsSync(changeSummaryPath)
    ? readFileSync(changeSummaryPath, "utf-8")
    : "";

  const touchedFiles = existsSync(touchedFilesPath)
    ? parseTouchedFiles(touchedFilesPath)
    : [];

  const claimedAffectedFixtures = existsSync(claimedFixturesPath)
    ? parseClaimedFixtures(claimedFixturesPath)
    : [];

  return { result, changeSummary, touchedFiles, claimedAffectedFixtures };
}

function parseResultJson(path: string, runId: string): ResultJson {
  let raw: unknown;
  try {
    raw = JSON.parse(readFileSync(path, "utf-8"));
  } catch {
    throw new ResultBundleError(`result-bundle/result.json is not valid JSON for run ${runId}`);
  }

  if (typeof raw !== "object" || raw === null) {
    throw new ResultBundleError(`result-bundle/result.json must be a JSON object`);
  }

  const obj = raw as Record<string, unknown>;

  if (typeof obj["runId"] !== "string") {
    throw new ResultBundleError(`result.json must have a "runId" string field`);
  }
  if (typeof obj["completedAt"] !== "string") {
    throw new ResultBundleError(`result.json must have a "completedAt" string field`);
  }

  return {
    runId: obj["runId"],
    completedAt: obj["completedAt"],
    agentType: typeof obj["agentType"] === "string" ? obj["agentType"] : undefined,
    notes: typeof obj["notes"] === "string" ? obj["notes"] : undefined,
  };
}

function parseTouchedFiles(path: string): string[] {
  try {
    const parsed = JSON.parse(readFileSync(path, "utf-8"));
    if (!Array.isArray(parsed)) throw new Error("not an array");
    return parsed.filter((x): x is string => typeof x === "string");
  } catch {
    throw new ResultBundleError(`result-bundle/touched-files.json must be a JSON array of strings`);
  }
}

function parseClaimedFixtures(path: string): string[] {
  try {
    const parsed = JSON.parse(readFileSync(path, "utf-8"));
    if (!Array.isArray(parsed)) throw new Error("not an array");
    return parsed.filter((x): x is string => typeof x === "string");
  } catch {
    throw new ResultBundleError(
      `result-bundle/claimed-affected-fixtures.json must be a JSON array of strings`,
    );
  }
}

export function hasResultBundle(runId: string): boolean {
  const bundleDir = getResultBundlePath(runId);
  return existsSync(resolve(bundleDir, "result.json"));
}
