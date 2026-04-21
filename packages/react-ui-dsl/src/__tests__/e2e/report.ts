import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import type { Fixture } from "./fixtures";

const REPORT_FLAG = "REACT_UI_DSL_E2E_REPORT";
const REPORT_DIR_FLAG = "REACT_UI_DSL_E2E_REPORT_DIR";
const REPORT_DATA_FILE = "report-data.json";

export type E2EReportEntryStatus = "passed" | "failed";

export interface E2EReportEntry {
  component: string;
  id: string;
  prompt: string;
  expectedDescription: string;
  dataModel: Record<string, unknown>;
  dsl?: string;
  status?: E2EReportEntryStatus;
  failureReason?: string;
}

export interface E2EReportData {
  generatedAt: string;
  summary: {
    total: number;
    passed: number;
    failed: number;
  };
  entries: E2EReportEntry[];
}

const entries: E2EReportEntry[] = [];
let finalized = false;

export function isE2EReportEnabled(): boolean {
  return process.env[REPORT_FLAG] === "1";
}

export function getE2EReportDir(): string | null {
  const reportDir = process.env[REPORT_DIR_FLAG];
  return reportDir ? reportDir : null;
}

export function getE2EReportDataPath(reportDir = getE2EReportDir()): string | null {
  if (!reportDir) {
    return null;
  }

  return resolve(reportDir, REPORT_DATA_FILE);
}

export function resetE2EReportState(): void {
  entries.length = 0;
  finalized = false;
}

export function beginE2EReportEntry(component: string, fixture: Fixture): E2EReportEntry | null {
  if (!isE2EReportEnabled()) {
    return null;
  }

  const entry: E2EReportEntry = {
    component,
    id: fixture.id,
    prompt: fixture.prompt,
    expectedDescription: fixture.expectedDescription,
    dataModel: fixture.dataModel,
  };

  entries.push(entry);
  return entry;
}

export function setE2EReportEntryDsl(entry: E2EReportEntry | null, dsl: string): void {
  if (!entry) {
    return;
  }

  entry.dsl = dsl;
}

export function passE2EReportEntry(entry: E2EReportEntry | null): void {
  if (!entry) {
    return;
  }

  entry.status = "passed";
  entry.failureReason = undefined;
}

export function failE2EReportEntry(entry: E2EReportEntry | null, error: unknown): void {
  if (!entry) {
    return;
  }

  entry.status = "failed";
  entry.failureReason = getFailureReason(error);
}

export function finalizeE2EReport(): string | null {
  if (!isE2EReportEnabled() || finalized) {
    return getE2EReportDataPath();
  }

  const reportPath = getE2EReportDataPath();
  if (!reportPath) {
    finalized = true;
    return null;
  }

  mkdirSync(dirname(reportPath), { recursive: true });
  writeFileSync(reportPath, JSON.stringify(buildE2EReportData(entries), null, 2), "utf-8");
  finalized = true;
  return reportPath;
}

export function buildE2EReportData(reportEntries: E2EReportEntry[]): E2EReportData {
  const summary = {
    total: reportEntries.length,
    passed: reportEntries.filter((entry) => entry.status === "passed").length,
    failed: reportEntries.filter((entry) => entry.status === "failed").length,
  };

  return {
    generatedAt: new Date().toISOString(),
    summary,
    entries: reportEntries,
  };
}

function getFailureReason(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}
