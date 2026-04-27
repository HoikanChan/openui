#!/usr/bin/env tsx
import { createReadStream, existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { createServer } from "node:http";
import { dirname, extname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { build } from "vite";
import type { E2EReportData, E2EReportEntry } from "./report.ts";
import {
  createRunWorkspace,
  generateRunId,
  getReportDataPath,
  getTaskBundlePath,
  listRunIds,
  readRunManifest,
  updateRunState,
} from "./eval/run-manifest.ts";
import { captureFixtureScreenshots } from "./eval/screenshot.ts";
import { judgeFixtures } from "./eval/judge.ts";
import { aggregateFailingPatterns, computeOverallScore } from "./eval/failing-patterns.ts";
import { writeTaskBundle } from "./eval/task-bundle-writer.ts";
import { readResultBundle, hasResultBundle, ResultBundleError } from "./eval/result-bundle-reader.ts";
import { computeDelta, pickVerificationOutcome } from "./eval/delta-verifier.ts";
import { buildVerificationSummary, printVerificationSummary } from "./eval/verification-summary.ts";
import { appendIteration, formatHistorySummary, readEvalHistory } from "./eval/eval-history.ts";
import {
  forwardPromptCorrections,
  getPendingJudgeCorrections,
  readCorrections,
  runCalibration,
  writeCorrections,
} from "./eval/calibration-verifier.ts";

const __dirname = dirname(fileURLToPath(import.meta.url));
const packageRoot = resolve(__dirname, "../../..");
const workspaceRoot = resolve(packageRoot, "../..");
const reportAppRoot = resolve(__dirname, "report-app");

type EvalSuite = "e2e" | "fuzz";

function snapshotsDirForSuite(suite: EvalSuite): string {
  return resolve(__dirname, suite === "fuzz" ? "fuzz-snapshots" : "snapshots");
}

const CONTENT_TYPES: Record<string, string> = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
};

// ── Vitest runner ──────────────────────────────────────────────────────────

function runVitest(reportDir: string, regen: boolean, suite: EvalSuite = "e2e"): void {
  const testPath =
    suite === "fuzz"
      ? "src/__tests__/e2e/dsl-fuzz.test.tsx"
      : "src/__tests__/e2e";
  const args = ["exec", "vitest", "run", testPath];
  const env: NodeJS.ProcessEnv = {
    ...process.env,
    REACT_UI_DSL_E2E_REPORT: "1",
    REACT_UI_DSL_E2E_REPORT_DIR: reportDir,
    REGEN_SNAPSHOTS: regen ? "1" : "0",
    REACT_UI_DSL_E2E_SUITE: suite,
  };

  const result = spawnSync(process.platform === "win32" ? "pnpm.cmd" : "pnpm", args, {
    cwd: packageRoot,
    env,
    stdio: "inherit",
    shell: true,
  });

  if (result.error) throw result.error;
}

// ── Report app builder ─────────────────────────────────────────────────────

async function buildReportApp(reportDir: string): Promise<void> {
  const reportDataPath = resolve(reportDir, "report-data.json");

  await build({
    appType: "spa",
    base: "./",
    configFile: false,
    publicDir: false,
    root: reportAppRoot,
    resolve: {
      alias: {
        "@openuidev/lang-core": resolve(workspaceRoot, "packages/lang-core/src/index.ts"),
        "@openuidev/react-lang": resolve(workspaceRoot, "packages/react-lang/src/index.ts"),
        "@openuidev/react-ui-dsl": resolve(packageRoot, "src/index.ts"),
      },
    },
    build: { emptyOutDir: false, outDir: reportDir },
  });

  const reportData = readFileSync(reportDataPath, "utf-8")
    .replace(/</g, "\\u003c")
    .replace(/<\/script/gi, "<\\/script");

  const htmlPath = resolve(reportDir, "index.html");
  const html = readFileSync(htmlPath, "utf-8").replace(
    "</body>",
    `<script id="e2e-report-data" type="application/json">${reportData}</script></body>`,
  );
  writeFileSync(htmlPath, html, "utf-8");
}

// ── Static server ──────────────────────────────────────────────────────────

type ReportServer = { origin: string; close: () => Promise<void> };

async function startReportServer(reportDir: string, port = 0): Promise<ReportServer> {
  const server = createServer((req, res) => {
    const p = req.url === "/" ? "/index.html" : (req.url ?? "/index.html");
    const filePath = resolve(reportDir, `.${decodeURIComponent(p.split("?")[0]!)}`);

    if (!filePath.startsWith(reportDir) || !existsSync(filePath) || statSync(filePath).isDirectory()) {
      res.writeHead(existsSync(filePath) ? 403 : 404);
      res.end();
      return;
    }

    res.writeHead(200, {
      "Content-Type": CONTENT_TYPES[extname(filePath)] ?? "application/octet-stream",
      "Cache-Control": "no-store",
    });
    createReadStream(filePath).pipe(res);
  });

  const address = await new Promise<{ port: number }>((resolve, reject) => {
    server.once("error", reject);
    server.listen(port, "127.0.0.1", () => resolve(server.address() as { port: number }));
  });

  const origin = `http://127.0.0.1:${address.port}`;
  return {
    origin,
    close: () => new Promise((res, rej) => server.close((e) => (e ? rej(e) : res()))),
  };
}

// ── Report data helpers ───────────────────────────────────────────────────

function readReportData(reportDir: string): E2EReportData {
  const path = resolve(reportDir, "report-data.json");
  if (!existsSync(path)) throw new Error(`report-data.json not found at ${path}`);
  return JSON.parse(readFileSync(path, "utf-8")) as E2EReportData;
}

function writeReportData(reportDir: string, data: E2EReportData): void {
  writeFileSync(resolve(reportDir, "report-data.json"), JSON.stringify(data, null, 2), "utf-8");
}

// ── Eval core ─────────────────────────────────────────────────────────────

async function runEval(runId: string, regen: boolean, suite: EvalSuite = "e2e"): Promise<E2EReportData> {
  const reportDir = resolve(dirname(getReportDataPath(runId)));
  mkdirSync(reportDir, { recursive: true });

  console.log(`\n[eval] Running vitest ${suite} tests (regen=${regen})…`);
  runVitest(reportDir, regen, suite);

  if (!existsSync(resolve(reportDir, "report-data.json"))) {
    throw new Error("Vitest did not produce report-data.json — check for test failures.");
  }

  const reportData = readReportData(reportDir);
  const fixtureIds = reportData.entries.map((e) => e.id);

  console.log(`[eval] Building report app…`);
  await buildReportApp(reportDir);

  console.log(`[eval] Starting report server for screenshots…`);
  const server = await startReportServer(reportDir);

  const screenshotsDir = resolve(getTaskBundlePath(runId), "screenshots");
  let degraded = false;

  try {
    console.log(`[eval] Taking ${fixtureIds.length} fixture screenshots…`);
    const { results, degraded: d } = await captureFixtureScreenshots(
      { reportUrl: `${server.origin}/index.html`, screenshotsDir, fixtureIds },
      (done, total) => process.stdout.write(`\r  ${done}/${total}`),
    );
    console.log();
    degraded = d;

    console.log(`[eval] Judging fixtures…`);
    const judgeInputs = results.map((r) => {
      const entry = reportData.entries.find((e) => e.id === r.fixtureId)!;
      const snapshotPath = resolve(snapshotsDirForSuite(suite), `${r.fixtureId}.dsl`);
      const dsl = existsSync(snapshotPath) ? readFileSync(snapshotPath, "utf-8") : entry.dsl ?? "";
      return {
        fixtureId: r.fixtureId,
        dsl,
        dataModel: entry.dataModel,
        screenshotPath: r.screenshotPath,
      };
    });

    const judgeScores = await judgeFixtures(judgeInputs, (done, total) =>
      process.stdout.write(`\r  ${done}/${total}`),
    );
    console.log();

    const failingPatterns = aggregateFailingPatterns(judgeScores);
    const overallScore = computeOverallScore(judgeScores);

    const judgeMap = new Map(judgeScores.map((s) => [s.fixtureId, s]));
    const enrichedEntries: E2EReportEntry[] = reportData.entries.map((e) => ({
      ...e,
      judgeScore: judgeMap.get(e.id),
    }));

    const enhanced: E2EReportData = {
      ...reportData,
      runId,
      degraded,
      summary: { ...reportData.summary, overallScore },
      entries: enrichedEntries,
      judge_scores: judgeScores,
      failing_patterns: failingPatterns,
    };

    writeReportData(reportDir, enhanced);
    return enhanced;
  } finally {
    await server.close();
  }
}

// ── Commands ──────────────────────────────────────────────────────────────

async function cmdStart(argv: string[]): Promise<void> {
  const regen = argv.includes("--regen");
  const suiteArg = argv.find((a) => a.startsWith("--suite="))?.split("=")[1]
    ?? (argv.includes("--suite") ? argv[argv.indexOf("--suite") + 1] : undefined);
  const suite: EvalSuite = suiteArg === "fuzz" ? "fuzz" : "e2e";
  const runId = generateRunId();

  console.log(`\nStarting eval run ${runId} (suite=${suite})…`);
  createRunWorkspace(runId, regen);

  const reportData = await runEval(runId, regen, suite);
  const judgeScores = reportData.judge_scores ?? [];
  const failingPatterns = reportData.failing_patterns ?? [];
  const overallScore = computeOverallScore(judgeScores);

  const corrections = readCorrections(runId);
  const { forwarded, toInclude } = forwardPromptCorrections(corrections);
  if (forwarded.length > 0) {
    writeCorrections(runId, [
      ...corrections.filter((c) => c.target !== "prompt" || c.state !== "pending"),
      ...forwarded,
    ]);
    console.log(`[eval] Forwarded ${forwarded.length} prompt correction(s) into task bundle.`);
  }

  console.log(`\n[eval] Generating task bundle…`);
  writeTaskBundle({ runId, overallScore, judgeScores, failingPatterns, snapshotsDir: snapshotsDirForSuite(suite), pendingPromptCorrections: toInclude });

  updateRunState(runId, "waiting_for_agent", { degraded: reportData.degraded ?? false });

  console.log(`\n✓ Run ${runId} complete.`);
  console.log(`  Overall score: ${overallScore.toFixed(1)}/10`);
  console.log(`  Failing patterns: ${failingPatterns.length}`);
  console.log(`  Degraded: ${reportData.degraded ? "yes (screenshot failures)" : "no"}`);
  console.log(`\nNext steps:`);
  console.log(`  1. Review task bundle: ${getTaskBundlePath(runId)}`);
  console.log(`  2. Choose an agent and follow adapters/<agent>.md`);
  console.log(`  3. Agent writes result-bundle/ when done`);
  console.log(`  4. Run: pnpm eval verify ${runId}`);
}

async function cmdStatus(argv: string[]): Promise<void> {
  const runId = argv[0];

  if (!runId) {
    const ids = listRunIds();
    if (ids.length === 0) {
      console.log("No eval runs found. Run: pnpm eval start");
      return;
    }
    console.log("Recent eval runs:");
    for (const id of ids.slice(-10).reverse()) {
      const m = readRunManifest(id);
      console.log(`  ${id}  [${m.state}]${m.degraded ? " ⚠ degraded" : ""}`);
    }
    return;
  }

  const manifest = readRunManifest(runId);
  const history = readEvalHistory(runId);
  const reportData = existsSync(getReportDataPath(runId))
    ? readReportData(dirname(getReportDataPath(runId)))
    : null;

  console.log(`\nRun: ${runId}`);
  console.log(`  State:    ${manifest.state}`);
  console.log(`  Created:  ${manifest.createdAt}`);
  console.log(`  Updated:  ${manifest.updatedAt}`);
  console.log(`  Regen:    ${manifest.regen}`);
  console.log(`  Degraded: ${manifest.degraded ? "yes" : "no"}`);

  if (reportData?.summary.overallScore !== undefined) {
    console.log(`  Score:    ${reportData.summary.overallScore.toFixed(1)}/10`);
  }

  if (manifest.verificationSummary) {
    const s = manifest.verificationSummary;
    console.log(`\n  Last verification: ${s.outcome}`);
    console.log(`    ${s.scoreBefore.toFixed(1)} → ${s.scoreAfter.toFixed(1)} (${s.delta >= 0 ? "+" : ""}${s.delta.toFixed(1)})`);
    if (s.regressions.length > 0) {
      console.log(`    Regressions: ${s.regressions.map((r) => r.fixtureId).join(", ")}`);
    }
  }

  if (history.iterations.length > 0) {
    console.log(`\n${formatHistorySummary(history)}`);
  }

  const resultBundleReady = hasResultBundle(runId);
  console.log(`\n  Result bundle: ${resultBundleReady ? "ready" : "not yet written by agent"}`);

  if (manifest.state === "waiting_for_agent" && resultBundleReady) {
    console.log(`\nNext step: pnpm eval verify ${runId}`);
  } else if (manifest.state === "waiting_for_agent") {
    console.log(`\nNext step: choose an agent and follow ${getTaskBundlePath(runId)}/adapters/<agent>.md`);
  }
}

async function cmdVerify(argv: string[]): Promise<void> {
  const runId = argv[0];
  if (!runId) {
    console.error("Usage: pnpm eval verify <run-id>");
    process.exit(1);
  }

  const manifest = readRunManifest(runId);

  if (!hasResultBundle(runId)) {
    console.error(`No result-bundle found for run ${runId}. The agent must write result-bundle/ first.`);
    process.exit(1);
  }

  console.log(`\nVerifying run ${runId}…`);

  let bundle;
  try {
    bundle = readResultBundle(runId);
  } catch (err) {
    if (err instanceof ResultBundleError) {
      console.error(`Result bundle error: ${err.message}`);
      process.exit(1);
    }
    throw err;
  }

  console.log(`  Agent: ${bundle.result.agentType ?? "unknown"}`);
  console.log(`  Touched files: ${bundle.touchedFiles.length}`);
  console.log(`  Claimed affected fixtures: ${bundle.claimedAffectedFixtures.join(", ") || "none"}`);

  const baselineReportData = readReportData(dirname(getReportDataPath(runId)));
  const baselineScores = baselineReportData.judge_scores ?? [];

  updateRunState(runId, "verifying");

  const verifyDir = resolve(dirname(getReportDataPath(runId)), "verify");
  mkdirSync(verifyDir, { recursive: true });

  console.log(`\n[verify] Running full fixture re-evaluation (regression gate)…`);
  runVitest(verifyDir, false);

  const verifyReportData = existsSync(resolve(verifyDir, "report-data.json"))
    ? (JSON.parse(readFileSync(resolve(verifyDir, "report-data.json"), "utf-8")) as E2EReportData)
    : null;

  if (!verifyReportData) {
    console.error("Verification vitest run did not produce report-data.json.");
    process.exit(1);
  }

  await buildReportApp(verifyDir);
  const verifyServer = await startReportServer(verifyDir);
  const fixtureIds = verifyReportData.entries.map((e) => e.id);
  const screenshotsDir = resolve(verifyDir, "screenshots");

  console.log(`[verify] Taking screenshots…`);
  const { results } = await captureFixtureScreenshots(
    { reportUrl: `${verifyServer.origin}/index.html`, screenshotsDir, fixtureIds },
    (done, total) => process.stdout.write(`\r  ${done}/${total}`),
  );
  console.log();
  await verifyServer.close();

  console.log(`[verify] Judging all fixtures…`);
  const judgeInputs = results.map((r) => {
    const entry = verifyReportData.entries.find((e) => e.id === r.fixtureId)!;
    const snapshotPath = resolve(snapshotsDir, `${r.fixtureId}.dsl`);
    const dsl = existsSync(snapshotPath) ? readFileSync(snapshotPath, "utf-8") : entry.dsl ?? "";
    return { fixtureId: r.fixtureId, dsl, dataModel: entry.dataModel, screenshotPath: r.screenshotPath };
  });

  const currentScores = await judgeFixtures(judgeInputs, (done, total) =>
    process.stdout.write(`\r  ${done}/${total}`),
  );
  console.log();

  const deltaResult = computeDelta({ baselineScores, currentScores });
  const updatedHistory = appendIteration(runId, {
    timestamp: new Date().toISOString(),
    scoreBefore: deltaResult.baselineOverall,
    scoreAfter: deltaResult.currentOverall,
    outcome: pickVerificationOutcome(deltaResult),
  });
  const summary = buildVerificationSummary(deltaResult, updatedHistory);
  printVerificationSummary(summary, runId);

  const updatedReportData: E2EReportData = {
    ...baselineReportData,
    judge_scores: currentScores,
    delta: deltaResult.delta,
    summary: {
      ...baselineReportData.summary,
      overallScore: deltaResult.currentOverall,
    },
  };
  writeReportData(dirname(getReportDataPath(runId)), updatedReportData);

  updateRunState(runId, summary.outcome === "stalled" ? "stalled" : "verified", {
    verificationSummary: summary,
  });
}

async function cmdCalibrate(argv: string[]): Promise<void> {
  const runId = argv[0];
  if (!runId) {
    console.error("Usage: pnpm eval calibrate <run-id>");
    process.exit(1);
  }

  readRunManifest(runId);

  const corrections = readCorrections(runId);
  const pendingJudge = getPendingJudgeCorrections(corrections);

  if (pendingJudge.length === 0) {
    console.log(`No pending judge corrections found for run ${runId}.`);
    console.log(`Add corrections to: ${resolve(dirname(getReportDataPath(runId)), "../corrections.json")}`);
    return;
  }

  console.log(`\nCalibrating judge for run ${runId}…`);
  console.log(`  Pending judge corrections: ${pendingJudge.length}`);

  const rubricOverridePath = resolve(dirname(getReportDataPath(runId)), "calibrated-rubric.md");
  if (!existsSync(rubricOverridePath)) {
    console.log(`\nTo calibrate, an agent must first produce an updated rubric at:`);
    console.log(`  ${rubricOverridePath}`);
    console.log(`\nPending corrections:`);
    for (const c of pendingJudge) {
      console.log(`  [${c.fixtureId ?? "global"}] ${c.text_feedback ?? JSON.stringify(c.score_corrections)}`);
    }
    return;
  }

  const updatedRubric = readFileSync(rubricOverridePath, "utf-8");
  const baselineReportData = readReportData(dirname(getReportDataPath(runId)));
  const baselineScores = baselineReportData.judge_scores ?? [];

  const result = await runCalibration({ runId, pendingCorrections: pendingJudge, updatedRubric, baselineScores });

  const remaining = corrections.filter(
    (c) => !pendingJudge.some((p) => p.id === c.id),
  );
  writeCorrections(runId, [...remaining, ...result.updatedCorrections]);

  console.log(`\nCalibration complete:`);
  console.log(`  Applied:  ${result.appliedCount}`);
  console.log(`  Failed:   ${result.failedCount}`);

  if (result.failedCount > 0) {
    const failed = result.updatedCorrections.filter((c) => c.state === "failed");
    console.log(`\nFailed corrections (rubric does not yet align within tolerance):`);
    for (const f of failed) {
      console.log(`  [${f.fixtureId ?? "global"}] ${f.failureReason}`);
    }
  }
}

// ── Entry point ───────────────────────────────────────────────────────────

async function main(): Promise<void> {
  const [cmd, ...rest] = process.argv.slice(2);

  switch (cmd) {
    case "start":
      await cmdStart(rest);
      break;
    case "status":
      await cmdStatus(rest);
      break;
    case "verify":
      await cmdVerify(rest);
      break;
    case "calibrate":
      await cmdCalibrate(rest);
      break;
    default:
      console.log("Usage: pnpm eval <command> [options]");
      console.log("");
      console.log("Commands:");
      console.log("  start [--regen]       Run baseline eval and generate task bundle");
      console.log("  status [<run-id>]     Show run status (or list all runs)");
      console.log("  verify <run-id>       Verify agent result bundle with full re-evaluation");
      console.log("  calibrate <run-id>    Process pending judge corrections");
      process.exit(cmd ? 1 : 0);
  }
}

main().catch((err) => {
  console.error(err instanceof Error ? err.message : String(err));
  process.exit(1);
});
