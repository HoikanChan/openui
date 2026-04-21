import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  beginE2EReportEntry,
  buildE2EReportData,
  failE2EReportEntry,
  finalizeE2EReport,
  getE2EReportDataPath,
  isE2EReportEnabled,
  passE2EReportEntry,
  resetE2EReportState,
  setE2EReportEntryDsl,
} from "./report";

const REPORT_FLAG = "REACT_UI_DSL_E2E_REPORT";
const REPORT_DIR_FLAG = "REACT_UI_DSL_E2E_REPORT_DIR";

afterEach(() => {
  vi.unstubAllEnvs();
  resetE2EReportState();
});

describe("e2e report helpers", () => {
  it("stays disabled by default", () => {
    vi.stubEnv(REPORT_FLAG, "0");
    vi.stubEnv(REPORT_DIR_FLAG, "");

    expect(isE2EReportEnabled()).toBe(false);
    expect(getE2EReportDataPath()).toBeNull();
  });

  it("collects entry state and writes report data when enabled", () => {
    const reportDir = mkdtempSync(join(tmpdir(), "react-ui-dsl-report-"));
    vi.stubEnv(REPORT_FLAG, "1");
    vi.stubEnv(REPORT_DIR_FLAG, reportDir);

    const entry = beginE2EReportEntry("Button", {
      id: "button-primary",
      prompt: "prompt",
      expectedDescription: "description",
      dataModel: {},
      assert: { contains: ["Submit Report"] },
    });

    setE2EReportEntryDsl(entry, 'root = Button("Submit Report", "primary")');
    passE2EReportEntry(entry);

    const reportPath = finalizeE2EReport();
    const report = JSON.parse(readFileSync(reportPath!, "utf-8")) as ReturnType<typeof buildE2EReportData>;

    expect(report.summary).toEqual({ total: 1, passed: 1, failed: 0 });
    expect(report.entries[0]).toMatchObject({
      component: "Button",
      id: "button-primary",
      expectedDescription: "description",
      status: "passed",
    });

    rmSync(reportDir, { recursive: true, force: true });
  });

  it("records lightweight failure reasons", () => {
    vi.stubEnv(REPORT_FLAG, "1");
    const entry = beginE2EReportEntry("Table", {
      id: "table-basic",
      prompt: "prompt",
      expectedDescription: "description",
      dataModel: {},
      assert: { contains: ["Region"] },
    });

    failE2EReportEntry(entry, new Error("parse errors in table-basic"));

    expect(entry).toMatchObject({
      status: "failed",
      failureReason: "parse errors in table-basic",
    });
  });
});
