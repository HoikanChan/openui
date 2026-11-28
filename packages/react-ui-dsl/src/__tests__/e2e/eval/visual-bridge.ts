#!/usr/bin/env tsx
/**
 * Render + Playwright Screenshot + Multimodal VLM Visual Judge + LangSmith Upload Bridge.
 *
 * Bridges Java evaluation outputs (`eval-repair` JSONL or `eval-ab` metrics JSON) into the
 * real React UI renderer (`report-app`), captures Chromium PNG screenshots, scores actual
 * rendered results with a multimodal judge, writes an interactive offline HTML report, and
 * attaches both the PNG screenshots (via LangSmith multipart attachments) and all visual
 * judge scores/comments back onto the original LangSmith experiment runs.
 *
 * Usage:
 *   pnpm --filter @cloudsop/openui-react-ui-dsl eval:visual-bridge \
 *     --repair-jsonl=../../target/live-repair-3.jsonl \
 *     --cases=../../packages/genui-java-sdk/repair-cases \
 *     --out-dir=../../target/live-repair-visual
 *
 *   pnpm --filter @cloudsop/openui-react-ui-dsl eval:visual-bridge \
 *     --ab-metrics=../../target/live-ab-on/metrics-on.json \
 *     --fixtures=/path/to/ab-fixtures \
 *     --out-dir=../../target/live-ab-on-visual
 */
import {
  createReadStream,
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { createServer } from "node:http";
import { dirname, extname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { build } from "vite";
import type { E2EReportData, E2EReportEntry } from "../report.ts";
import { reactUiDslViewTargetPlugin } from "../report-cli.mjs";
import { aggregateFailingPatterns, computeOverallScore } from "./failing-patterns.ts";
import { judgeFixturesIncremental, makeFailedFixtureScore } from "./judge.ts";
import { pushVisualJudgeFeedback, writeLangSmithRunMap } from "./langsmith-feedback.ts";
import type { JudgeScore } from "./types.ts";

const __dirname = dirname(fileURLToPath(import.meta.url));
const packageRoot = resolve(__dirname, "../../../..");
const workspaceRoot = resolve(packageRoot, "../..");
const reportAppRoot = resolve(__dirname, "../report-app");

const CONTENT_TYPES: Record<string, string> = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".svg": "image/svg+xml",
};

function loadDotEnvFiles(): void {
  const candidates = [resolve(packageRoot, ".env"), resolve(workspaceRoot, ".env")];
  for (const envPath of candidates) {
    if (!existsSync(envPath)) continue;
    for (const rawLine of readFileSync(envPath, "utf-8").split(/\r?\n/)) {
      const line = rawLine.trim();
      if (!line || line.startsWith("#")) continue;
      const sep = line.indexOf("=");
      if (sep <= 0) continue;
      const key = line.slice(0, sep).trim();
      if (!key || process.env[key] !== undefined) continue;
      const val = line
        .slice(sep + 1)
        .trim()
        .replace(/^(['"])(.*)\1$/, "$2");
      process.env[key] = val;
    }
  }
}

interface BridgeCase {
  id: string;
  component: string;
  prompt: string;
  dataModel: Record<string, unknown>;
  dsl: string;
  langsmithRunId?: string;
}

interface LoadedBridgeInput {
  model: string;
  sessionId?: string;
  cases: BridgeCase[];
}

function getArgValue(argv: string[], flag: string): string | undefined {
  const eqPrefix = `${flag}=`;
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]!;
    if (arg.startsWith(eqPrefix)) return arg.slice(eqPrefix.length);
    if (arg === flag && i + 1 < argv.length) return argv[i + 1];
  }
  return undefined;
}

function loadRepairCasesIndex(
  casesDir: string,
): Map<string, { intent: string; dataModel: Record<string, unknown> }> {
  const index = new Map<string, { intent: string; dataModel: Record<string, unknown> }>();
  for (const entry of readdirSync(casesDir, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const caseJsonPath = resolve(casesDir, entry.name, "case.json");
    if (!existsSync(caseJsonPath)) continue;
    const meta = JSON.parse(readFileSync(caseJsonPath, "utf-8")) as {
      id?: string;
      originalIntent?: string;
      dataModel?: unknown;
    };
    if (!meta.id) continue;
    let dataModel: Record<string, unknown> = {};
    if (meta.dataModel && typeof meta.dataModel === "object" && !Array.isArray(meta.dataModel)) {
      dataModel = meta.dataModel as Record<string, unknown>;
    } else {
      const dmPath = resolve(casesDir, entry.name, "dataModel.json");
      if (existsSync(dmPath)) {
        dataModel = JSON.parse(readFileSync(dmPath, "utf-8")) as Record<string, unknown>;
      }
    }
    index.set(meta.id, { intent: meta.originalIntent ?? "", dataModel });
  }
  return index;
}

function loadFromRepairJsonl(jsonlPath: string, casesDir: string): LoadedBridgeInput {
  const caseIndex = loadRepairCasesIndex(casesDir);
  const lines = readFileSync(jsonlPath, "utf-8")
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);

  let model = process.env["LLM_MODEL"] ?? "unknown";
  let sessionId: string | undefined;
  const cases: BridgeCase[] = [];

  for (const line of lines) {
    const row = JSON.parse(line) as Record<string, unknown>;
    if (row["type"] === "summary") {
      if (typeof row["model"] === "string") model = row["model"];
      if (typeof row["langsmithSessionId"] === "string") sessionId = row["langsmithSessionId"];
      continue;
    }
    if (row["type"] !== "case") continue;
    const id = String(row["caseId"] ?? "");
    if (!id) continue;
    const dsl = String(row["finalDsl"] ?? row["outputDsl"] ?? "");
    const ref = caseIndex.get(id);
    cases.push({
      id,
      component: `repair:${String(row["status"] ?? "unknown")}`,
      prompt: ref?.intent ?? "",
      dataModel: ref?.dataModel ?? {},
      dsl,
      langsmithRunId:
        typeof row["langsmithRunId"] === "string" ? row["langsmithRunId"] : undefined,
    });
  }
  return { model, sessionId, cases };
}

function loadFromAbMetrics(metricsPath: string, fixturesDir: string): LoadedBridgeInput {
  const metrics = JSON.parse(readFileSync(metricsPath, "utf-8")) as {
    arm?: string;
    model?: string;
    langsmithSessionId?: string;
    fixtures?: Array<{
      id: string;
      status?: string;
      langsmithRunId?: string;
    }>;
  };
  const outDir = dirname(metricsPath);
  const cases: BridgeCase[] = [];
  for (const fx of metrics.fixtures ?? []) {
    const fixtureFile = resolve(fixturesDir, `${fx.id}.json`);
    let prompt = "";
    let dataModel: Record<string, unknown> = {};
    if (existsSync(fixtureFile)) {
      const parsed = JSON.parse(readFileSync(fixtureFile, "utf-8")) as {
        meta?: { prompt?: string };
        data?: unknown;
      };
      prompt = parsed.meta?.prompt ?? "";
      if (parsed.data && typeof parsed.data === "object" && !Array.isArray(parsed.data)) {
        dataModel = parsed.data as Record<string, unknown>;
      }
    }
    const dslPath = resolve(outDir, `${fx.id}.dsl`);
    const dsl = existsSync(dslPath) ? readFileSync(dslPath, "utf-8") : "";
    cases.push({
      id: fx.id,
      component: `ab-${metrics.arm ?? "unknown"}:${fx.status ?? "NONE"}`,
      prompt,
      dataModel,
      dsl,
      langsmithRunId: fx.langsmithRunId,
    });
  }
  return {
    model: metrics.model ?? process.env["LLM_MODEL"] ?? "unknown",
    sessionId: metrics.langsmithSessionId,
    cases,
  };
}

function injectReportData(reportDir: string, reportData: E2EReportData): void {
  const jsonText = JSON.stringify(reportData, null, 2);
  writeFileSync(resolve(reportDir, "report-data.json"), jsonText, "utf-8");
  const escaped = jsonText.replace(/</g, "\\u003c").replace(/<\/script/gi, "<\\/script");
  const htmlPath = resolve(reportDir, "index.html");
  const html = readFileSync(htmlPath, "utf-8")
    .replace(/<script id="e2e-report-data" type="application\/json">[\s\S]*?<\/script>/g, "")
    .replace(
      "</body>",
      `<script id="e2e-report-data" type="application/json">${escaped}</script></body>`,
    );
  writeFileSync(htmlPath, html, "utf-8");
}

async function buildReportApp(outDir: string): Promise<void> {
  await build({
    appType: "spa",
    base: "./",
    configFile: false,
    publicDir: false,
    root: reportAppRoot,
    plugins: [reactUiDslViewTargetPlugin(packageRoot)],
    resolve: {
      alias: {
        "@cloudsop/openui-lang-core": resolve(workspaceRoot, "packages/lang-core/src/index"),
        "@cloudsop/openui-react-lang": resolve(workspaceRoot, "packages/react-lang/src/index"),
        "@cloudsop/openui-react-ui-dsl": resolve(packageRoot, "src/index"),
      },
    },
    build: { emptyOutDir: false, outDir },
    logLevel: "warn",
  });
}

async function startStaticServer(dir: string): Promise<{ origin: string; close: () => Promise<void> }> {
  const server = createServer((req, res) => {
    const p = req.url === "/" ? "/index.html" : (req.url ?? "/index.html");
    const filePath = resolve(dir, `.${decodeURIComponent(p.split("?")[0]!)}`);
    if (!filePath.startsWith(dir) || !existsSync(filePath) || statSync(filePath).isDirectory()) {
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
  const address = await new Promise<{ port: number }>((res, rej) => {
    server.once("error", rej);
    server.listen(0, "127.0.0.1", () => res(server.address() as { port: number }));
  });
  return {
    origin: `http://127.0.0.1:${address.port}`,
    close: () => new Promise((res, rej) => server.close((e) => (e ? rej(e) : res()))),
  };
}

interface CapturedRender {
  fixtureId: string;
  screenshotPath: string | null;
  renderPassed: boolean;
  renderError?: string;
}

async function renderAndCaptureAll(
  reportUrl: string,
  screenshotsDir: string,
  fixtureIds: string[],
): Promise<CapturedRender[]> {
  mkdirSync(screenshotsDir, { recursive: true });
  const playwright = await import("playwright");
  const browser = await playwright.chromium.launch({ headless: true });
  const results: CapturedRender[] = [];
  try {
    const page = await browser.newPage();
    await page.setViewportSize({ width: 1280, height: 900 });
    const pageErrors: string[] = [];
    page.on("pageerror", (err) => pageErrors.push(err.message));

    await page.goto(reportUrl, { waitUntil: "networkidle" });
    await page.waitForTimeout(800);

    for (const fixtureId of fixtureIds) {
      const screenshotPath = resolve(screenshotsDir, `${fixtureId}.png`);
      const shell = page.locator(`[data-fixture-id="${fixtureId}"] .preview-shell`);
      if ((await shell.count()) === 0) {
        results.push({
          fixtureId,
          screenshotPath: null,
          renderPassed: false,
          renderError: "Preview container not found",
        });
        continue;
      }

      const errorBanner = shell.locator(".preview-error");
      let renderPassed = true;
      let renderError: string | undefined;
      if ((await errorBanner.count()) > 0) {
        renderPassed = false;
        renderError = (await errorBanner.first().innerText()).trim();
      } else {
        const box = await shell.boundingBox();
        if (!box || box.width < 20 || box.height < 20) {
          renderPassed = false;
          renderError = `Rendered container too small (${box?.width ?? 0}x${box?.height ?? 0})`;
        }
      }

      try {
        await shell.screenshot({ path: screenshotPath });
        results.push({ fixtureId, screenshotPath, renderPassed, renderError });
      } catch (err) {
        results.push({
          fixtureId,
          screenshotPath: null,
          renderPassed: false,
          renderError: err instanceof Error ? err.message : String(err),
        });
      }
    }
    if (pageErrors.length > 0) {
      console.warn(`[visual-bridge] browser pageerror(s): ${pageErrors.join(" | ")}`);
    }
  } finally {
    await browser.close();
  }
  return results;
}

async function main(): Promise<void> {
  loadDotEnvFiles();
  const argv = process.argv.slice(2);
  const repairJsonl = getArgValue(argv, "--repair-jsonl");
  const casesDir = getArgValue(argv, "--cases");
  const abMetrics = getArgValue(argv, "--ab-metrics");
  const fixturesDir = getArgValue(argv, "--fixtures");
  const outDirArg = getArgValue(argv, "--out-dir");
  const judgeModelArg = getArgValue(argv, "--judge-model");
  const limitArg = getArgValue(argv, "--limit");

  if (judgeModelArg) {
    process.env["LLM_JUDGE_MODEL"] = judgeModelArg;
  }

  let loaded: LoadedBridgeInput;
  if (repairJsonl && casesDir) {
    loaded = loadFromRepairJsonl(resolve(repairJsonl), resolve(casesDir));
  } else if (abMetrics && fixturesDir) {
    loaded = loadFromAbMetrics(resolve(abMetrics), resolve(fixturesDir));
  } else {
    throw new Error(
      "Usage: visual-bridge (--repair-jsonl=<path> --cases=<dir> | --ab-metrics=<path> --fixtures=<dir>) [--out-dir=<dir>] [--judge-model=<model>] [--limit=N]",
    );
  }

  if (limitArg) {
    const limit = Number.parseInt(limitArg, 10);
    if (Number.isFinite(limit) && limit > 0) {
      loaded.cases = loaded.cases.slice(0, limit);
    }
  }

  const outDir = resolve(outDirArg ?? "visual-bridge-report");
  const screenshotsDir = resolve(outDir, "screenshots");
  mkdirSync(outDir, { recursive: true });

  const initialEntries: E2EReportEntry[] = loaded.cases.map((c) => ({
    component: c.component,
    id: c.id,
    prompt: c.prompt,
    expectedDescription: c.prompt,
    dataModel: c.dataModel,
    dsl: c.dsl,
    status: c.dsl.trim().length > 0 ? "passed" : "failed",
    failureReason: c.dsl.trim().length > 0 ? undefined : "Empty DSL output",
  }));

  const initialReport: E2EReportData = {
    generatedAt: new Date().toISOString(),
    model: loaded.model,
    summary: {
      total: initialEntries.length,
      passed: initialEntries.filter((e) => e.status === "passed").length,
      failed: initialEntries.filter((e) => e.status === "failed").length,
    },
    entries: initialEntries,
  };

  console.log(`[visual-bridge] Building React report app for ${loaded.cases.length} case(s)...`);
  await buildReportApp(outDir);
  injectReportData(outDir, initialReport);

  const server = await startStaticServer(outDir);
  let captured: CapturedRender[];
  try {
    console.log(`[visual-bridge] Rendering & capturing Playwright screenshots...`);
    captured = await renderAndCaptureAll(
      `${server.origin}/index.html`,
      screenshotsDir,
      loaded.cases.map((c) => c.id),
    );
  } finally {
    await server.close();
  }

  const renderMap: Record<string, { passed: boolean; error?: string }> = {};
  const judgeInputs: Array<{
    fixtureId: string;
    dsl: string;
    dataModel: Record<string, unknown>;
    screenshotPath: string | null;
  }> = [];
  const failedScores: JudgeScore[] = [];

  for (const cap of captured) {
    renderMap[cap.fixtureId] = { passed: cap.renderPassed, error: cap.renderError };
    const item = loaded.cases.find((c) => c.id === cap.fixtureId)!;
    if (!cap.renderPassed || !item.dsl.trim()) {
      failedScores.push(
        makeFailedFixtureScore(
          cap.fixtureId,
          cap.screenshotPath,
          cap.renderError ?? "Empty DSL or render error",
        ),
      );
      continue;
    }
    judgeInputs.push({
      fixtureId: item.id,
      dsl: item.dsl,
      dataModel: item.dataModel,
      screenshotPath: cap.screenshotPath,
    });
  }

  console.log(
    `[visual-bridge] Running multimodal VLM judge on ${judgeInputs.length} rendered case(s) (model=${process.env["LLM_JUDGE_MODEL"] ?? process.env["LLM_MODEL"] ?? "default"})...`,
  );
  const judgedScores =
    judgeInputs.length > 0
      ? await judgeFixturesIncremental(judgeInputs, async (score) => {
          console.log(
            `[visual-bridge] judged ${score.fixtureId}: overall=${score.overall}/10 ` +
              `(fit=${score.component_fit}, data=${score.data_completeness}, ` +
              `fmt=${score.format_quality}, layout=${score.layout_coherence})`,
          );
        })
      : [];
  const allScores = [...judgedScores, ...failedScores];
  const overallScore = computeOverallScore(allScores);
  const failingPatterns = aggregateFailingPatterns(allScores);
  const scoreMap = new Map(allScores.map((s) => [s.fixtureId, s]));

  const finalEntries: E2EReportEntry[] = initialEntries.map((e) => {
    const r = renderMap[e.id];
    return {
      ...e,
      status: r?.passed ? "passed" : "failed",
      failureReason: r?.error ?? e.failureReason,
      judgeScore: scoreMap.get(e.id),
    };
  });

  const finalReport: E2EReportData = {
    generatedAt: new Date().toISOString(),
    model: loaded.model,
    degraded: allScores.some((s) => s.degraded),
    summary: {
      total: finalEntries.length,
      passed: finalEntries.filter((e) => e.status === "passed").length,
      failed: finalEntries.filter((e) => e.status === "failed").length,
      overallScore,
    },
    entries: finalEntries,
    judge_scores: allScores,
    failing_patterns: failingPatterns,
  };
  injectReportData(outDir, finalReport);

  const runIdMap: Record<string, string> = {};
  for (const c of loaded.cases) {
    if (c.langsmithRunId) runIdMap[c.id] = c.langsmithRunId;
  }
  let pushed = { uploadedRuns: 0, uploadedScreenshots: 0, summaryUploaded: false };
  if (Object.keys(runIdMap).length > 0) {
    writeLangSmithRunMap(outDir, { sessionId: loaded.sessionId, runs: runIdMap });
    pushed = await pushVisualJudgeFeedback({
      runDir: outDir,
      scores: allScores,
      overallScore,
      renderResults: renderMap,
    });
  }

  const summary = {
    total: allScores.length,
    renderPassed: Object.values(renderMap).filter((r) => r.passed).length,
    overallScore,
    langsmithSessionId: loaded.sessionId ?? null,
    langsmithUploaded: pushed,
    reportHtml: resolve(outDir, "index.html"),
    screenshotsDir,
    scores: allScores.map((s) => ({
      fixtureId: s.fixtureId,
      renderPassed: renderMap[s.fixtureId]?.passed ?? false,
      overall: s.overall,
      component_fit: s.component_fit,
      data_completeness: s.data_completeness,
      format_quality: s.format_quality,
      layout_coherence: s.layout_coherence,
      visual_issues: s.visual_issues,
      feedback: s.feedback,
      screenshotPath: s.screenshotPath,
    })),
  };
  writeFileSync(resolve(outDir, "visual-summary.json"), JSON.stringify(summary, null, 2), "utf-8");
  console.log(JSON.stringify(summary, null, 2));
}

main().catch((err) => {
  console.error("[visual-bridge] failed:", err);
  process.exit(1);
});
