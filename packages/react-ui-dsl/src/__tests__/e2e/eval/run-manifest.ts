import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { dirname } from "node:path";
import type { RunManifest, RunState, VerificationSummaryData } from "./types.ts";

const __dirname = dirname(fileURLToPath(import.meta.url));
export const RUNS_DIR = resolve(__dirname, "runs");

export function generateRunId(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  const ts = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  const rand = Math.random().toString(36).slice(2, 6);
  return `${ts}_${rand}`;
}

export function getRunDir(runId: string): string {
  return resolve(RUNS_DIR, runId);
}

export function getRunJsonPath(runId: string): string {
  return resolve(getRunDir(runId), "run.json");
}

export function getReportDataPath(runId: string): string {
  return resolve(getRunDir(runId), "report-data.json");
}

export function getTaskBundlePath(runId: string): string {
  return resolve(getRunDir(runId), "task-bundle");
}

export function getResultBundlePath(runId: string): string {
  return resolve(getRunDir(runId), "result-bundle");
}

export function getHistoryPath(runId: string): string {
  return resolve(getRunDir(runId), "eval-history.json");
}

export function getCorrectionsPath(runId: string): string {
  return resolve(getRunDir(runId), "corrections.json");
}

export function createRunWorkspace(runId: string, regen: boolean): RunManifest {
  const runDir = getRunDir(runId);
  mkdirSync(runDir, { recursive: true });
  mkdirSync(resolve(runDir, "task-bundle", "screenshots"), { recursive: true });
  mkdirSync(resolve(runDir, "task-bundle", "adapters"), { recursive: true });
  mkdirSync(resolve(runDir, "result-bundle"), { recursive: true });

  const now = new Date().toISOString();
  const manifest: RunManifest = {
    runId,
    state: "created",
    createdAt: now,
    updatedAt: now,
    regen,
    reportDataPath: getReportDataPath(runId),
    taskBundlePath: getTaskBundlePath(runId),
    resultBundlePath: getResultBundlePath(runId),
    historyPath: getHistoryPath(runId),
    degraded: false,
  };

  writeRunManifest(manifest);
  return manifest;
}

export function writeRunManifest(manifest: RunManifest): void {
  const path = getRunJsonPath(manifest.runId);
  writeFileSync(path, JSON.stringify(manifest, null, 2), "utf-8");
}

export function readRunManifest(runId: string): RunManifest {
  const path = getRunJsonPath(runId);
  if (!existsSync(path)) {
    throw new Error(`Run not found: ${runId}. Expected run.json at ${path}`);
  }
  return JSON.parse(readFileSync(path, "utf-8")) as RunManifest;
}

export function updateRunState(
  runId: string,
  state: RunState,
  extra?: Partial<Pick<RunManifest, "degraded" | "verificationSummary">>,
): RunManifest {
  const manifest = readRunManifest(runId);
  const updated: RunManifest = {
    ...manifest,
    ...extra,
    state,
    updatedAt: new Date().toISOString(),
  };
  writeRunManifest(updated);
  return updated;
}

export function listRunIds(): string[] {
  if (!existsSync(RUNS_DIR)) return [];
  return readdirSync(RUNS_DIR).filter((name) => existsSync(getRunJsonPath(name)));
}
