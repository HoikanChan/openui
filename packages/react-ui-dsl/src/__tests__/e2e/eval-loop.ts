#!/usr/bin/env tsx
import { createReadStream, existsSync, mkdirSync, readFileSync, rmSync, statSync, writeFileSync, renameSync } from "node:fs";
import { createServer } from "node:http";
import { dirname, extname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { build } from "vite";
import type { E2EReportData, E2EReportEntry } from "./report";
import {
  createRunWorkspace,
  generateRunId,
  getReportDataPath,
  getRunDir,
  getTaskBundlePath,
  listRunIds,
  readRunManifest,
  updateRunState,
  markPhaseDone,
  isPhaseComplete,
} from "./eval/run-manifest";
import type { PhaseProgress } from "./eval/types";
import { captureFixtureScreenshots } from "./eval/screenshot";
import { judgeFixtures, judgeFixturesIncremental, makeFailedFixtureScore } from "./eval/judge";
import type { JudgeScore } from "./eval/types";
import { aggregateFailingPatterns, computeOverallScore } from "./eval/failing-patterns";
import { regenFixtures } from "./eval/regen";
import { loadBenchmarkCases } from "./benchmark-loader";
import { clearJudgeCache } from "./eval/judge-cache";
import { clearReportAppCache, computeReportAppCacheKey, restoreReportAppCache, saveReportAppCache } from "./eval/report-app-cache";
import { writeTaskBundle } from "./eval/task-bundle-writer";
import { readResultBundle, hasResultBundle, ResultBundleError } from "./eval/result-bundle-reader";
import { computeDelta, pickVerificationOutcome } from "./eval/delta-verifier";
import { buildVerificationSummary, printVerificationSummary } from "./eval/verification-summary";
import { appendIteration, formatHistorySummary, readEvalHistory } from "./eval/eval-history";
import {
  forwardPromptCorrections,
  getPendingJudgeCorrections,
  readCorrections,
  runCalibration,
  writeCorrections,
} from "./eval/calibration-verifier";

const __dirname = dirname(fileURLToPath(import.meta.url));
const packageRoot = resolve(__dirname, "../../..");
const workspaceRoot = resolve(packageRoot, "../..");
const reportAppRoot = resolve(__dirname, "report-app");

// Load .env from package root so EVAL_JUDGE_RUNNER, LLM_API_KEY, etc. are available
// without requiring the caller to source the file manually.
(function loadDotEnv() {
  const envPath = resolve(packageRoot, ".env");
  if (!existsSync(envPath)) return;
  for (const rawLine of readFileSync(envPath, "utf-8").split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const sep = line.indexOf("=");
    if (sep <= 0) continue;
    const key = line.slice(0, sep).trim();
    if (!key || process.env[key] !== undefined) continue;
    const val = line.slice(sep + 1).trim().replace(/^(['"])(.*)\1$/, "$2");
    process.env[key] = val;
  }
}());

type EvalSuite = "e2e" | "fuzz" | "benchmark";

function snapshotsDirForSuite(suite: EvalSuite): string {
  if (suite === "fuzz") return resolve(__dirname, "fuzz-snapshots");
  if (suite === "benchmark") return resolve(__dirname, "benchmark-snapshots");
  return resolve(__dirname, "snapshots");
}

const CONTENT_TYPES: Record<string, string> = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
};

// ── Vitest runner ──────────────────────────────────────────────────────────

function runVitest(reportDir: string, regen: boolean, suite: EvalSuite = "e2e", fixtureFilter?: string): void {
  const testPath =
    suite === "fuzz"
      ? "src/__tests__/e2e/dsl-fuzz.test.tsx"
      : suite === "benchmark"
        ? "src/__tests__/e2e/dsl-benchmark.test.tsx"
        : "src/__tests__/e2e";
  // Call vitest binary directly to avoid pnpm's deps-status check which
  // runs `pnpm install` and triggers all workspace prepare scripts.
  const vitestBin = process.platform === "win32"
    ? resolve(packageRoot, "node_modules/.bin/vitest.cmd")
    : resolve(packageRoot, "node_modules/.bin/vitest");
  const args = ["run", testPath, "--config", resolve(packageRoot, "vitest.config.ts")];
  if (fixtureFilter) args.push("-t", fixtureFilter);
  const env: NodeJS.ProcessEnv = {
    ...process.env,
    REACT_UI_DSL_E2E_REPORT: "1",
    REACT_UI_DSL_E2E_REPORT_DIR: reportDir,
    REGEN_SNAPSHOTS: regen ? "1" : "0",
    REACT_UI_DSL_E2E_SUITE: suite,
  };

  const result = spawnSync(vitestBin, args, {
    cwd: packageRoot,
    env,
    stdio: "inherit",
    shell: process.platform === "win32",
  });

  if (result.error) throw result.error;
}

// ── Report app builder ─────────────────────────────────────────────────────

function injectReportData(reportDir: string): void {
  const reportDataPath = resolve(reportDir, "report-data.json");
  const reportData = readFileSync(reportDataPath, "utf-8")
    .replace(/</g, "\\u003c")
    .replace(/<\/script/gi, "<\\/script");
  const htmlPath = resolve(reportDir, "index.html");
  const html = readFileSync(htmlPath, "utf-8")
    .replace(/<script id="e2e-report-data" type="application\/json">[\s\S]*?<\/script>/g, "")
    .replace(
      "</body>",
      `<script id="e2e-report-data" type="application/json">${reportData}</script></body>`,
    );
  writeFileSync(htmlPath, html, "utf-8");
}

async function buildReportApp(reportDir: string): Promise<void> {
  const cacheKey = computeReportAppCacheKey(reportAppRoot, packageRoot);

  if (restoreReportAppCache(reportDir, cacheKey)) {
    console.log(`[build] Report app restored from cache.`);
    injectReportData(reportDir);
    return;
  }

  console.log(`[build] Building report app (cache miss)…`);
  await build({
    appType: "spa",
    base: "./",
    configFile: false,
    publicDir: false,
    root: reportAppRoot,
    resolve: {
      alias: {
        "@openuidev/lang-core": resolve(workspaceRoot, "packages/lang-core/src/index"),
        "@openuidev/react-lang": resolve(workspaceRoot, "packages/react-lang/src/index"),
        "@openuidev/react-ui-dsl": resolve(packageRoot, "src/index"),
      },
    },
    build: { emptyOutDir: false, outDir: reportDir },
  });

  saveReportAppCache(reportDir, cacheKey);
  injectReportData(reportDir);
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

// ── Phase context ─────────────────────────────────────────────────────────

type Strictness = "standard" | "strict";

interface PhaseContext {
  runId: string;
  suite: EvalSuite;
  strictness: Strictness;
  reportDir: string;
  fixtureIds: string[];
  snapshotsDir: string;
}

// ── Phase functions ───────────────────────────────────────────────────────

async function runRegenPhase(ctx: PhaseContext): Promise<void> {
  if (isPhaseComplete(ctx.runId, "regen")) {
    console.log(`[regen] Phase already complete, skipping.`);
    return;
  }

  const dataDir = resolve(__dirname, ctx.suite === "benchmark" ? "fuzz-data/benchmark" : "fuzz-data");
  const fixtures = loadBenchmarkCases(dataDir);
  const relevantFixtures = ctx.fixtureIds.length > 0
    ? fixtures.filter((f) => ctx.fixtureIds.includes(f.id))
    : fixtures;

  if (relevantFixtures.length === 0) {
    console.log(`[regen] No fixtures to regenerate.`);
    markPhaseDone(ctx.runId, "regen");
    return;
  }

  console.log(`[regen] Generating DSL for ${relevantFixtures.length} fixtures.`);
  const missingFixtures = relevantFixtures.filter(
    (f) => !existsSync(resolve(ctx.snapshotsDir, `${f.id}.dsl`)),
  );

  if (missingFixtures.length === 0) {
    console.log(`[regen] All snapshots already exist.`);
    markPhaseDone(ctx.runId, "regen");
    return;
  }

  await regenFixtures(missingFixtures, {
    runId: ctx.runId,
    suite: ctx.suite,
    strictness: ctx.strictness,
  }, (done, total) => process.stdout.write(`\r  ${done}/${total}`));
  console.log();
  markPhaseDone(ctx.runId, "regen");
}

async function runRenderPhase(ctx: PhaseContext, fixtureFilter?: string): Promise<E2EReportData> {
  if (isPhaseComplete(ctx.runId, "render")) {
    console.log(`[render] Phase already complete, skipping.`);
    return readReportData(ctx.reportDir);
  }

  const filterLabel = fixtureFilter ? ` (filter: ${fixtureFilter})` : "";
  console.log(`\n[render] Running vitest ${ctx.suite} tests${filterLabel}.`);
  runVitest(ctx.reportDir, false, ctx.suite, fixtureFilter);

  if (!existsSync(resolve(ctx.reportDir, "report-data.json"))) {
    throw new Error("Vitest did not produce report-data.json.");
  }

  const reportData = readReportData(ctx.reportDir);
  markPhaseDone(ctx.runId, "render");
  return reportData;
}

async function runScreenshotPhase(ctx: PhaseContext): Promise<{ results: { fixtureId: string; screenshotPath: string | null }[]; degraded: boolean }> {
  const screenshotsDir = resolve(getTaskBundlePath(ctx.runId), "screenshots");
  const hasScreenshots = ctx.fixtureIds.every((id) =>
    existsSync(resolve(screenshotsDir, `${id}.png`)),
  );

  if (hasScreenshots) {
    console.log(`[screenshot] Screenshots already exist, skipping.`);
    if (!isPhaseComplete(ctx.runId, "screenshot")) {
      markPhaseDone(ctx.runId, "screenshot");
    }
    return { results: ctx.fixtureIds.map((id) => ({ fixtureId: id, screenshotPath: resolve(screenshotsDir, `${id}.png`) })), degraded: false };
  }

  console.log(`[screenshot] Building report app.`);
  await buildReportApp(ctx.reportDir);
  const server = await startReportServer(ctx.reportDir);
  try {
    console.log(`[screenshot] Taking ${ctx.fixtureIds.length} screenshots.`);
    const { results, degraded } = await captureFixtureScreenshots(
      { reportUrl: `${server.origin}/index.html`, screenshotsDir, fixtureIds: ctx.fixtureIds },
      (done, total) => process.stdout.write(`\r  ${done}/${total}`),
    );
    console.log();
    markPhaseDone(ctx.runId, "screenshot");
    return { results, degraded };
  } finally {
    await server.close();
  }
}

async function runJudgePhase(
  ctx: PhaseContext,
  reportData: E2EReportData,
  screenshotResults: { fixtureId: string; screenshotPath: string | null }[],
  degraded: boolean,
): Promise<E2EReportData> {
  const { systemPromptPath, systemPromptHash } = readRunManifest(ctx.runId);
  const forceRejudge = process.env["EVAL_FORCE_REJUDGE"] === "1";
  const existingScores = reportData.judge_scores ?? [];
  const judgedFixtureIds = new Set(existingScores.map((s) => s.fixtureId));
  const needsJudge = screenshotResults.filter((r) => forceRejudge || !judgedFixtureIds.has(r.fixtureId));

  if (needsJudge.length === 0) {
    console.log(`[judge] All fixtures already judged, skipping.`);
    if (!isPhaseComplete(ctx.runId, "judge")) {
      markPhaseDone(ctx.runId, "judge");
    }
    return reportData;
  }

  console.log(`[judge] Judging ${needsJudge.length} fixtures.`);
  const judgeInputs: { fixtureId: string; dsl: string; dataModel: Record<string, unknown>; screenshotPath: string | null; evalHints?: string[] }[] = [];
  const failedScores: JudgeScore[] = [];

  for (const r of needsJudge) {
    const entry = reportData.entries.find((e) => e.id === r.fixtureId)!;
    if (entry.status === "failed") {
      failedScores.push(makeFailedFixtureScore(r.fixtureId, r.screenshotPath, entry.failureReason));
      continue;
    }
    const snapshotPath = resolve(ctx.snapshotsDir, `${r.fixtureId}.dsl`);
    const dsl = existsSync(snapshotPath) ? readFileSync(snapshotPath, "utf-8") : entry.dsl ?? "";
    judgeInputs.push({ fixtureId: r.fixtureId, dsl, dataModel: entry.dataModel, screenshotPath: r.screenshotPath, evalHints: entry.evalHints });
  }

  if (failedScores.length > 0) {
    console.log(`[judge] Skipping ${failedScores.length} failed fixtures.`);
  }

  // Mutex: serialize incremental reads/writes so concurrent workers don't clobber each other
  let mutex = Promise.resolve();
  const accumulatedScores: JudgeScore[] = [...existingScores.filter((s) => !forceRejudge)];

  const liveScores = await judgeFixturesIncremental(
    judgeInputs,
    async (score) => {
      mutex = mutex.then(async () => {
        accumulatedScores.push(score);
        const overallScore = computeOverallScore([...accumulatedScores, ...failedScores]);
        const failingPatterns = aggregateFailingPatterns([...accumulatedScores, ...failedScores]);
        const judgeMap = new Map([...accumulatedScores, ...failedScores].map((s) => [s.fixtureId, s]));
        const snapshot: E2EReportData = {
          ...reportData,
          runId: ctx.runId,
          degraded,
          summary: { ...reportData.summary, overallScore },
          entries: reportData.entries.map((e) => ({ ...e, judgeScore: judgeMap.get(e.id) })),
          judge_scores: [...accumulatedScores, ...failedScores],
          failing_patterns: failingPatterns,
          systemPromptPath,
          systemPromptHash,
        };
        // Atomic write: .tmp + rename
        const dataPath = resolve(ctx.reportDir, "report-data.json");
        const tmpPath = `${dataPath}.tmp`;
        writeFileSync(tmpPath, JSON.stringify(snapshot, null, 2), "utf-8");
        renameSync(tmpPath, dataPath);
        if (existsSync(resolve(ctx.reportDir, "index.html"))) {
          injectReportData(ctx.reportDir);
        }
      });
      await mutex;
    },
    (done, total) => process.stdout.write(`\r  ${done}/${total}`),
  );
  await mutex; // ensure all callbacks have settled
  console.log();

  const allScores = [...existingScores.filter((s) => !forceRejudge && !liveScores.some((n) => n.fixtureId === s.fixtureId) && !failedScores.some((f) => f.fixtureId === s.fixtureId)), ...liveScores, ...failedScores];
  const overallScore = computeOverallScore(allScores);
  const failingPatterns = aggregateFailingPatterns(allScores);
  const judgeMap = new Map(allScores.map((s) => [s.fixtureId, s]));
  const enhanced: E2EReportData = {
    ...reportData,
    runId: ctx.runId,
    degraded,
    summary: { ...reportData.summary, overallScore },
    entries: reportData.entries.map((e) => ({ ...e, judgeScore: judgeMap.get(e.id) })),
    judge_scores: allScores,
    failing_patterns: failingPatterns,
    systemPromptPath,
    systemPromptHash,
  };
  const dataPath = resolve(ctx.reportDir, "report-data.json");
  const tmpPath = `${dataPath}.tmp`;
  writeFileSync(tmpPath, JSON.stringify(enhanced, null, 2), "utf-8");
  renameSync(tmpPath, dataPath);
  markPhaseDone(ctx.runId, "judge");
  return enhanced;
}

// ── Commands ──────────────────────────────────────────────────────────────

async function cmdStart(argv: string[]): Promise<void> {
  const regen = argv.includes("--regen");
  const suiteArg = argv.find((a) => a.startsWith("--suite="))?.split("=")[1]
    ?? (argv.includes("--suite") ? argv[argv.indexOf("--suite") + 1] : undefined);
  const suite: EvalSuite = suiteArg === "fuzz" ? "fuzz" : suiteArg === "benchmark" ? "benchmark" : "e2e";

  const fixtureArg = argv.find((a) => a.startsWith("--fixture="))?.split("=")[1]
    ?? (argv.includes("--fixture") ? argv[argv.indexOf("--fixture") + 1] : undefined);

  const strictnessArg = argv.find((a) => a.startsWith("--strictness="))?.split("=")[1]
    ?? (argv.includes("--strictness") ? argv[argv.indexOf("--strictness") + 1] : undefined);
  if (strictnessArg !== undefined && strictnessArg !== "standard" && strictnessArg !== "strict") {
    console.error(`Invalid --strictness value: "${strictnessArg}". Must be "standard" or "strict".`);
    process.exit(1);
  }
  const strictness: Strictness = strictnessArg === "strict" ? "strict" : "standard";

  const runId = generateRunId();

  console.log(`\nStarting eval run ${runId} (suite=${suite}, strictness=${strictness})…`);
  createRunWorkspace(runId, regen, suite);

  const reportDir = getRunDir(runId);
  const ctx: PhaseContext = {
    runId,
    suite,
    strictness,
    reportDir,
    fixtureIds: [],
    snapshotsDir: snapshotsDirForSuite(suite),
  };

  if (regen) {
    await runRegenPhase(ctx);
  }
  let reportData = await runRenderPhase(ctx, fixtureArg);
  ctx.fixtureIds = reportData.entries.map((e) => e.id);
  const { results, degraded } = await runScreenshotPhase(ctx);
  reportData = await runJudgePhase(ctx, reportData, results, degraded);

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
  if (manifest.systemPromptPath) {
    const absPromptPath = resolve(getRunDir(runId), manifest.systemPromptPath);
    console.log(`  Prompt:   ${absPromptPath}`);
    console.log(`  P.hash:   ${manifest.systemPromptHash}`);
  }

  if (manifest.phases) {
    const phases: Array<keyof PhaseProgress> = ["regen", "render", "screenshot", "judge"];
    const phaseStr = phases.map((p) => `${p}:${manifest.phases?.[p] ?? "pending"}`).join(" | ");
    console.log(`  Phases:   ${phaseStr}`);
  }

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

  const suite: EvalSuite = (manifest.suite as EvalSuite) ?? "e2e";

  console.log(`\n[verify] Running full fixture re-evaluation (regression gate)…`);
  runVitest(verifyDir, false, suite);

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
  const affectedIds = bundle.claimedAffectedFixtures;
  const idsToProcess = affectedIds.length > 0 ? affectedIds.filter(id => fixtureIds.includes(id)) : fixtureIds;
  
  const screenshotsDir = resolve(verifyDir, "screenshots");

  console.log(`[verify] Taking screenshots for ${idsToProcess.length} affected fixtures…`);
  const { results } = await captureFixtureScreenshots(
    { reportUrl: `${verifyServer.origin}/index.html`, screenshotsDir, fixtureIds: idsToProcess },
    (done, total) => process.stdout.write(`\r  ${done}/${total}`),
  );
  console.log();
  await verifyServer.close();

  console.log(`[verify] Judging affected fixtures…`);
  const snapshotsDir = snapshotsDirForSuite((manifest.suite ?? "e2e") as EvalSuite);
  const verifyJudgeInputs: { fixtureId: string; dsl: string; dataModel: Record<string, unknown>; screenshotPath: string | null }[] = [];
  const verifyFailedScores: JudgeScore[] = [];
  for (const r of results) {
    const entry = verifyReportData.entries.find((e) => e.id === r.fixtureId)!;
    if (entry.status === "failed") {
      verifyFailedScores.push(makeFailedFixtureScore(r.fixtureId, r.screenshotPath, entry.failureReason));
      continue;
    }
    const snapshotPath = resolve(snapshotsDir, `${r.fixtureId}.dsl`);
    const dsl = existsSync(snapshotPath) ? readFileSync(snapshotPath, "utf-8") : entry.dsl ?? "";
    verifyJudgeInputs.push({ fixtureId: r.fixtureId, dsl, dataModel: entry.dataModel, screenshotPath: r.screenshotPath });
  }
  if (verifyFailedScores.length > 0) {
    console.log(`[verify] Skipping judge for ${verifyFailedScores.length} failed fixture(s); assigning 0 score.`);
  }

  const liveVerifyScores = await judgeFixturesIncremental(verifyJudgeInputs, async () => {}, (done, total) =>
    process.stdout.write(`\r  ${done}/${total}`),
  );
  console.log();

  const newlyJudgedScores = [...liveVerifyScores, ...verifyFailedScores];
  const newJudgedMap = new Map(newlyJudgedScores.map(s => [s.fixtureId, s]));
  
  // Merge with baseline
  const currentScores: JudgeScore[] = fixtureIds.map(id => {
    if (newJudgedMap.has(id)) return newJudgedMap.get(id)!;
    const baseline = baselineScores.find(s => s.fixtureId === id);
    if (baseline) return baseline;
    return makeFailedFixtureScore(id, null, "missing from baseline and not re-judged");
  });

  const deltaResult = computeDelta({ baselineScores, currentScores });
  const updatedHistory = appendIteration(runId, {
    timestamp: new Date().toISOString(),
    scoreBefore: deltaResult.baselineOverall,
    scoreAfter: deltaResult.currentOverall,
    outcome: pickVerificationOutcome(deltaResult),
  });
  const summary = buildVerificationSummary(deltaResult, updatedHistory);
  printVerificationSummary(summary, runId);

  const currentJudgeMap = new Map(currentScores.map((s) => [s.fixtureId, s]));
  const updatedReportData: E2EReportData = {
    ...baselineReportData,
    entries: baselineReportData.entries.map((e) => ({ ...e, judgeScore: currentJudgeMap.get(e.id) })),
    judge_scores: currentScores,
    failing_patterns: aggregateFailingPatterns(currentScores),
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

async function cmdJudge(argv: string[]): Promise<void> {
  const runId = argv[0];
  if (!runId) {
    console.error("Usage: pnpm eval judge <run-id>");
    process.exit(1);
  }

  const manifest = readRunManifest(runId);
  const reportDir = dirname(getReportDataPath(runId));
  const reportData = readReportData(reportDir);

  if (!reportData.entries || reportData.entries.length === 0) {
    console.error(`No fixtures found in run ${runId}`);
    process.exit(1);
  }

  const suite: EvalSuite = (manifest.suite as EvalSuite) ?? "e2e";
  const fixtureIds = reportData.entries.map((e) => e.id);
  const screenshotsDir = resolve(getTaskBundlePath(runId), "screenshots");

  // Check if screenshots already exist
  const hasScreenshots = fixtureIds.every((id) =>
    existsSync(resolve(screenshotsDir, `${id}.png`)),
  );

  let screenshotResults: { fixtureId: string; screenshotPath: string | null }[];

  if (hasScreenshots) {
    console.log(`\n[judge] Using existing screenshots from ${screenshotsDir}`);
    screenshotResults = fixtureIds.map((id) => ({
      fixtureId: id,
      screenshotPath: resolve(screenshotsDir, `${id}.png`),
    }));
  } else {
    // Need to capture screenshots first
    console.log(`\n[judge] Building report app…`);
    await buildReportApp(reportDir);

    console.log(`[judge] Starting report server for screenshots…`);
    const server = await startReportServer(reportDir);

    try {
      console.log(`[judge] Taking ${fixtureIds.length} fixture screenshots…`);
      const { results } = await captureFixtureScreenshots(
        { reportUrl: `${server.origin}/index.html`, screenshotsDir, fixtureIds },
        (done, total) => process.stdout.write(`\r  ${done}/${total}`),
      );
      console.log();
      screenshotResults = results;
    } finally {
      await server.close();
    }
  }

  // Run judge on all fixtures
  console.log(`[judge] Judging ${fixtureIds.length} fixtures…`);
  const judgeInputs: { fixtureId: string; dsl: string; dataModel: Record<string, unknown>; screenshotPath: string | null; evalHints?: string[] }[] = [];
  const failedScores: JudgeScore[] = [];

  for (const r of screenshotResults) {
    const entry = reportData.entries.find((e) => e.id === r.fixtureId)!;
    if (entry.status === "failed") {
      failedScores.push(makeFailedFixtureScore(r.fixtureId, r.screenshotPath, entry.failureReason));
      continue;
    }
    const snapshotPath = resolve(snapshotsDirForSuite(suite), `${r.fixtureId}.dsl`);
    const dsl = existsSync(snapshotPath) ? readFileSync(snapshotPath, "utf-8") : entry.dsl ?? "";
    judgeInputs.push({
      fixtureId: r.fixtureId,
      dsl,
      dataModel: entry.dataModel,
      screenshotPath: r.screenshotPath,
      evalHints: entry.evalHints,
    });
  }

  if (failedScores.length > 0) {
    console.log(`[judge] Skipping judge for ${failedScores.length} failed fixture(s); assigning 0 score.`);
  }

  let mutex = Promise.resolve();
  const existingScores = reportData.judge_scores ?? [];
  const forceRejudge = process.env["EVAL_FORCE_REJUDGE"] === "1";
  const accumulatedScores: JudgeScore[] = [...existingScores.filter((s) => !forceRejudge)];

  const liveScores = await judgeFixturesIncremental(
    judgeInputs,
    async (score) => {
      mutex = mutex.then(async () => {
        accumulatedScores.push(score);
        const allSoFar = [...accumulatedScores, ...failedScores];
        const snapshot: E2EReportData = {
          ...reportData,
          summary: { ...reportData.summary, overallScore: computeOverallScore(allSoFar) },
          entries: reportData.entries.map((e) => ({ ...e, judgeScore: new Map(allSoFar.map((s) => [s.fixtureId, s])).get(e.id) })),
          judge_scores: allSoFar,
          failing_patterns: aggregateFailingPatterns(allSoFar),
          systemPromptPath: manifest.systemPromptPath,
          systemPromptHash: manifest.systemPromptHash,
        };
        const tmpPath = `${resolve(reportDir, "report-data.json")}.tmp`;
        writeFileSync(tmpPath, JSON.stringify(snapshot, null, 2), "utf-8");
        renameSync(tmpPath, resolve(reportDir, "report-data.json"));
        if (existsSync(resolve(reportDir, "index.html"))) injectReportData(reportDir);
      });
      await mutex;
    },
    (done, total) => process.stdout.write(`\r  ${done}/${total}`),
  );
  await mutex;
  console.log();

  const judgeScores: JudgeScore[] = [
    ...existingScores.filter((s) => !forceRejudge && !liveScores.some((n) => n.fixtureId === s.fixtureId) && !failedScores.some((f) => f.fixtureId === s.fixtureId)),
    ...liveScores,
    ...failedScores,
  ];
  const overallScore = computeOverallScore(judgeScores);
  const failingPatterns = aggregateFailingPatterns(judgeScores);
  const judgeMap = new Map(judgeScores.map((s) => [s.fixtureId, s]));

  const enhanced: E2EReportData = {
    ...reportData,
    summary: { ...reportData.summary, overallScore },
    entries: reportData.entries.map((e) => ({ ...e, judgeScore: judgeMap.get(e.id) })),
    judge_scores: judgeScores,
    failing_patterns: failingPatterns,
    systemPromptPath: manifest.systemPromptPath,
    systemPromptHash: manifest.systemPromptHash,
  };

  const finalTmp = `${resolve(reportDir, "report-data.json")}.tmp`;
  writeFileSync(finalTmp, JSON.stringify(enhanced, null, 2), "utf-8");
  renameSync(finalTmp, resolve(reportDir, "report-data.json"));
  markPhaseDone(runId, "judge");
  if (existsSync(resolve(reportDir, "index.html"))) injectReportData(reportDir);

  // Write task bundle
  writeTaskBundle({
    runId,
    overallScore,
    judgeScores,
    failingPatterns,
    snapshotsDir: snapshotsDirForSuite(suite),
    pendingPromptCorrections: [],
  });

  console.log(`\n✓ Judge complete for run ${runId}`);
  console.log(`  Overall score: ${overallScore.toFixed(1)}/10`);
  console.log(`  Total fixtures: ${fixtureIds.length}`);
  console.log(`  Passed: ${reportData.summary.passed ?? fixtureIds.length - failedScores.length}`);
  console.log(`  Failed: ${failedScores.length}`);
  if (failingPatterns.length > 0) {
    console.log(`  Failing patterns: ${failingPatterns.length}`);
  }
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

  const result = await runCalibration({
    runId,
    pendingCorrections: pendingJudge,
    updatedRubric,
    baselineScores,
    baselineEntries: baselineReportData.entries,
  });

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

async function cmdRegen(argv: string[]): Promise<void> {
  const suiteArg = argv.find((a) => a.startsWith("--suite="))?.split("=")[1]
    ?? (argv.includes("--suite") ? argv[argv.indexOf("--suite") + 1] : undefined);
  const suite: EvalSuite = suiteArg === "fuzz" ? "fuzz" : suiteArg === "benchmark" ? "benchmark" : "e2e";
  const suiteValueIdx = argv.includes("--suite") ? argv.indexOf("--suite") + 1 : -1;
  const runIdArg = argv.find((a, i) => !a.startsWith("--") && i !== suiteValueIdx);

  let runId: string;
  if (runIdArg) {
    runId = runIdArg;
    readRunManifest(runId); // validate exists
  } else {
    runId = generateRunId();
    createRunWorkspace(runId, true, suite);
    console.log(`\nStarted new run: ${runId}`);
  }

  const manifest = readRunManifest(runId);
  const effectiveSuite: EvalSuite = (manifest.suite as EvalSuite) ?? suite;
  const ctx: PhaseContext = {
    runId,
    suite: effectiveSuite,
    strictness: (manifest.strictness as Strictness) ?? "standard",
    reportDir: getRunDir(runId),
    fixtureIds: [],
    snapshotsDir: snapshotsDirForSuite(effectiveSuite),
  };

  await runRegenPhase(ctx);
  console.log(`\n✓ Regen phase complete for run ${runId}`);
  console.log(`  Next: pnpm eval render ${runId}`);
}

async function cmdRender(argv: string[]): Promise<void> {
  const runIdArg = argv.find((a) => !a.startsWith("--"));
  if (!runIdArg) {
    console.error("Usage: pnpm eval render <run-id>");
    process.exit(1);
  }

  const manifest = readRunManifest(runIdArg);
  const suite: EvalSuite = (manifest.suite as EvalSuite) ?? "e2e";
  const ctx: PhaseContext = {
    runId: runIdArg,
    suite,
    strictness: (manifest.strictness as Strictness) ?? "standard",
    reportDir: getRunDir(runIdArg),
    fixtureIds: [],
    snapshotsDir: snapshotsDirForSuite(suite),
  };

  const reportData = await runRenderPhase(ctx);
  ctx.fixtureIds = reportData.entries.map((e) => e.id);

  console.log(`\n✓ Render phase complete for run ${runIdArg}`);
  console.log(`  Fixtures: ${ctx.fixtureIds.length}`);
  console.log(`  Next: pnpm eval judge ${runIdArg}`);
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
    case "judge":
      await cmdJudge(rest);
      break;
    case "verify":
      await cmdVerify(rest);
      break;
    case "calibrate":
      await cmdCalibrate(rest);
      break;
    case "regen":
      await cmdRegen(rest);
      break;
    case "render":
      await cmdRender(rest);
      break;
    case "cache:clear":
      clearJudgeCache();
      clearReportAppCache();
      console.log("Judge and report-app caches cleared.");
      break;
    default:
      console.log("Usage: pnpm eval <command> [options]");
      console.log("");
      console.log("Commands:");
      console.log("  start [--regen] [--suite=<suite>]   Run full eval pipeline and generate task bundle");
      console.log("  status [<run-id>]                   Show run status or list all runs");
      console.log("  regen [<run-id>] [--suite=<suite>]  Regenerate DSL snapshots (new or existing run)");
      console.log("  render <run-id>                     Run vitest render phase on existing run");
      console.log("  judge <run-id>                      Run judge phase (with or without screenshots)");
      console.log("  verify <run-id>                     Verify agent result bundle");
      console.log("  calibrate <run-id>                  Process pending judge corrections");
      console.log("  cache:clear                         Clear the judge hash cache");
      process.exit(cmd ? 1 : 0);
  }
}

main().catch((err) => {
  console.error(err instanceof Error ? err.message : String(err));
  process.exit(1);
});
