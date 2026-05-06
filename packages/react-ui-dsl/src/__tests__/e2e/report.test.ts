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
  runE2EReportEntry,
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
    vi.stubEnv("LLM_MODEL", "gpt-4.1-mini");

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

    expect(report.model).toBe("gpt-4.1-mini");
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

  it("keeps captured dsl when the fixture fails after generation", async () => {
    const reportDir = mkdtempSync(join(tmpdir(), "react-ui-dsl-report-"));
    vi.stubEnv(REPORT_FLAG, "1");
    vi.stubEnv(REPORT_DIR_FLAG, reportDir);

    await expect(
      runE2EReportEntry(
        "Card",
        {
          id: "card-kpi",
          prompt: "prompt",
          expectedDescription: "description",
          dataModel: {},
          assert: { contains: ["Q1 Performance"] },
        },
        async (entry) => {
          setE2EReportEntryDsl(entry, 'root = Card("Q1 Performance")');
          throw new Error("render blew up");
        },
      ),
    ).rejects.toThrow("render blew up");

    const reportPath = finalizeE2EReport();
    const report = JSON.parse(readFileSync(reportPath!, "utf-8")) as ReturnType<typeof buildE2EReportData>;

    expect(report.summary).toEqual({ total: 1, passed: 0, failed: 1 });
    expect(report.entries[0]).toMatchObject({
      component: "Card",
      id: "card-kpi",
      dsl: 'root = Card("Q1 Performance")',
      status: "failed",
      failureReason: "render blew up",
    });

    rmSync(reportDir, { recursive: true, force: true });
  });

  it("preserves judge visual issue tags in report data and tolerates older scores without them", () => {
    const report = buildE2EReportData([
      {
        component: "Gauge",
        id: "percentage-as-decimal",
        prompt: "prompt",
        expectedDescription: "description",
        dataModel: {},
        status: "passed",
        judgeScore: {
          fixtureId: "percentage-as-decimal",
          component_fit: 3,
          data_completeness: 3,
          format_quality: 3,
          layout_coherence: 1,
          overall: 4,
          feedback: "Center labels overlap.",
          visual_issues: ["overlap", "crowded"],
          screenshotPath: null,
          degraded: false,
        },
      },
      {
        component: "Card",
        id: "legacy-run",
        prompt: "prompt",
        expectedDescription: "description",
        dataModel: {},
        status: "failed",
        judgeScore: {
          fixtureId: "legacy-run",
          component_fit: 2,
          data_completeness: 2,
          format_quality: 2,
          layout_coherence: 2,
          overall: 6,
          feedback: "Old report entry.",
          screenshotPath: null,
          degraded: false,
        } as never,
      },
    ]);

    expect(report.entries[0]?.judgeScore?.visual_issues).toEqual(["overlap", "crowded"]);
    expect(report.entries[1]?.judgeScore).toMatchObject({
      fixtureId: "legacy-run",
      feedback: "Old report entry.",
    });
  });

  it("uses the default configured model name when LLM_MODEL is unset", () => {
    const report = buildE2EReportData([]);

    expect(report.model).toBe("deepseek-chat");
  });
});
