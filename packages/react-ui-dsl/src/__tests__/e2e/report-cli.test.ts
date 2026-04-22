import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";

import {
  buildReportUrl,
  buildVitestRunConfig,
  parseReportCliArgs,
  startStaticReportServer,
} from "./report-cli.mjs";

const tempDirs: string[] = [];
const closers: Array<() => Promise<void>> = [];

afterEach(async () => {
  while (closers.length > 0) {
    await closers.pop()?.();
  }

  while (tempDirs.length > 0) {
    rmSync(tempDirs.pop()!, { recursive: true, force: true });
  }
});

describe("report-cli", () => {
  it("parses a single-fixture snapshot update request", () => {
    expect(parseReportCliArgs(["--update-snapshot", "table-basic"])).toEqual({
      mode: "run",
      updateSnapshotFixtureId: "table-basic",
    });
  });

  it("builds a targeted vitest run config when updating one fixture snapshot", () => {
    const config = buildVitestRunConfig({
      baseEnv: { LLM_API_KEY: "sk-test" },
      reportDir: "/tmp/react-ui-dsl-report",
      updateSnapshotFixtureId: "table-basic",
    });

    expect(config.args).toEqual([
      "exec",
      "vitest",
      "run",
      "src/__tests__/e2e/dsl-e2e.test.tsx",
      "-t",
      "table-basic",
    ]);
    expect(config.env.REACT_UI_DSL_E2E_REPORT).toBe("1");
    expect(config.env.REACT_UI_DSL_E2E_REPORT_DIR).toBe("/tmp/react-ui-dsl-report");
    expect(config.env.REGEN_SNAPSHOTS).toBe("1");
    expect(config.env.LLM_API_KEY).toBe("sk-test");
  });

  it("builds an http report url instead of a file path", () => {
    expect(buildReportUrl("http://127.0.0.1:4173")).toBe("http://127.0.0.1:4173/index.html");
  });

  it("serves the generated report over http", async () => {
    const reportDir = mkdtempSync(join(tmpdir(), "react-ui-dsl-report-server-"));
    tempDirs.push(reportDir);

    writeFileSync(join(reportDir, "index.html"), "<!doctype html><h1>Fixture previews</h1>", "utf-8");
    writeFileSync(join(reportDir, "report-data.json"), JSON.stringify({ ok: true }), "utf-8");

    const server = await startStaticReportServer(reportDir, 0);
    closers.push(server.close);

    const indexResponse = await fetch(buildReportUrl(server.origin));
    const reportResponse = await fetch(`${server.origin}/report-data.json`);

    expect(indexResponse.status).toBe(200);
    expect(await indexResponse.text()).toContain("Fixture previews");
    expect(reportResponse.status).toBe(200);
    expect(JSON.parse(await reportResponse.text())).toEqual(JSON.parse(readFileSync(join(reportDir, "report-data.json"), "utf-8")));
  });
});
