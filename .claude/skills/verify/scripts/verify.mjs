#!/usr/bin/env node

import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { cp, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { parseSpec, assertMeasurements, MeasurementSchema } from "./check-spec.mjs";
import { chromium } from "../../../../packages/react-ui-dsl/node_modules/playwright/index.mjs";

const usage = `node .claude/skills/verify/scripts/verify.mjs --spec <path> --evidence <directory> [--headed] [--doctor] [--smoke-test]

Run OpenUI DSL scenarios in real Chromium. Input paths and evidence directories may be absolute.
Exit 0 means all assertions passed. Exit 1 means a failed or inconclusive check. Never use --smoke-test to turn a failed product check into a pass.`;

const options = { headed: false, doctor: false, smokeTest: false };
for (let index = 2; index < process.argv.length; index += 1) {
  const argument = process.argv[index];
  if (argument === "--spec" || argument === "--evidence") options[argument.slice(2)] = process.argv[++index];
  else if (argument === "--headed" || argument === "--doctor") options[argument.slice(2)] = true;
  else if (argument === "--smoke-test") options.smokeTest = true;
  else if (argument === "--help") { console.log(usage); process.exit(0); }
  else throw new Error(`Unknown argument: ${argument}\n${usage}`);
}
if (!options.spec || !options.evidence) throw new Error(usage);

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const packageDirectory = path.resolve(scriptDirectory, "../../../../packages/react-ui-dsl");
const specPath = path.resolve(options.spec);
const evidence = path.resolve(options.evidence);
const rawSpec = JSON.parse(await readFile(specPath, "utf8"));
const spec = parseSpec(rawSpec);
const failures = [];
const results = [];
let server;
let browser;
let scratch;
let cleaning = false;

async function cleanup() {
  if (cleaning) return;
  cleaning = true;
  if (browser) await browser.close().catch(() => {});
  if (server) {
    server.kill("SIGTERM");
    await Promise.race([
      new Promise((resolve) => server.once("exit", resolve)),
      new Promise((resolve) => setTimeout(resolve, 5000)),
    ]);
  }
  if (scratch) await rm(scratch, { recursive: true, force: true });
}
process.on("SIGINT", async () => { await cleanup(); process.exit(130); });
process.on("SIGTERM", async () => { await cleanup(); process.exit(143); });

function serverOutput(chunk) {
  process.stderr.write(chunk);
}

async function findPort() {
  const socket = createServer();
  await new Promise((resolve, reject) => socket.listen(0, "127.0.0.1", resolve).once("error", reject));
  const address = socket.address();
  const port = typeof address === "object" && address ? address.port : 0;
  await new Promise((resolve, reject) => socket.close((error) => error ? reject(error) : resolve()));
  return port;
}

try {
  await mkdir(evidence, { recursive: true });
  await writeFile(path.join(evidence, "input.json"), JSON.stringify(spec, null, 2));
  const sourceHash = createHash("sha256").update(JSON.stringify(spec)).digest("hex");
  const scratchParent = path.join(evidence, "tmp");
  await mkdir(scratchParent, { recursive: true });
  scratch = await mkdtemp(path.join(scratchParent, "run-"));
  await cp(path.join(scriptDirectory, "../assets/harness.tsx"), path.join(scratch, "main.tsx"));
  await writeFile(path.join(scratch, "index.html"), '<!doctype html><html><head><meta charset="UTF-8"><title>OpenUI verification</title></head><body><div id="root"></div><script type="module" src="/main.tsx"></script></body></html>');
  await writeFile(path.join(scratch, "openui-verification-cases.json"), JSON.stringify(spec.scenarios));
  const port = await findPort();
  const url = `http://127.0.0.1:${port}`;
  server = spawn(process.execPath, [
    path.join(packageDirectory, "node_modules/vite/bin/vite.js"),
    "--config", path.join(scriptDirectory, "vite.config.mjs"),
    "--host", "127.0.0.1", "--port", String(port), "--strictPort",
  ], {
    cwd: scratch,
    env: { ...process.env, OPENUI_VERIFY_ROOT: scratch },
    stdio: ["ignore", "pipe", "pipe"],
  });
  server.stdout.on("data", serverOutput);
  server.stderr.on("data", serverOutput);

  const deadline = Date.now() + 30000;
  let ready = false;
  while (Date.now() < deadline && !ready) {
    if (server.exitCode !== null) throw new Error("Vite exited before readiness");
    try {
      const response = await fetch(url);
      ready = response.ok && (await response.text()).includes("OpenUI verification");
    } catch {}
    if (!ready) await new Promise((resolve) => setTimeout(resolve, 200));
  }
  if (!ready) throw new Error("Verification app did not become ready within 30 seconds");

  browser = await chromium.launch({ headless: !options.headed });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const pageErrors = [];
  const consoleErrors = [];
  const consoleWarnings = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
    if (message.type() === "warning") consoleWarnings.push(message.text());
  });
  await page.goto(url, { waitUntil: "networkidle" });
  await page.waitForFunction(() => Boolean(window.openUIVerification));
  const actualSource = await page.locator("title").textContent();
  if (actualSource !== "OpenUI verification") throw new Error("Unexpected page identity");

  const doctor = {
    ok: server.exitCode === null && actualSource === "OpenUI verification",
    url,
    sourceHash,
    package: "packages/react-ui-dsl",
    packageVersion: JSON.parse(await readFile(path.join(packageDirectory, "package.json"), "utf8")).version,
    sourceRevision: await new Promise((resolve) => {
      const git = spawn("git", ["rev-parse", "HEAD"], { cwd: path.resolve(scriptDirectory, "../../../..") });
      let stdout = "";
      git.stdout.on("data", (chunk) => { stdout += chunk; });
      git.on("exit", (code) => resolve(code === 0 ? stdout.trim() : "unavailable"));
    }),
    browser: await browser.version(),
    scratchOwnedByRun: true,
  };
  await writeFile(path.join(evidence, "doctor.json"), JSON.stringify(doctor, null, 2));
  console.log(JSON.stringify({ doctor }));
  if (options.doctor) {
    await cleanup();
    process.exit(doctor.ok ? 0 : 1);
  }

  for (const scenario of spec.scenarios) {
    await page.setViewportSize(scenario.viewport ?? { width: Math.max(1440, scenario.width + 80), height: 1000 });
    const locator = page.locator(`[data-case="${scenario.id}"]`);
    await locator.waitFor({ state: "visible" });
    await page.waitForTimeout(scenario.readyDelayMs);
    const parseErrors = await locator.locator("[data-errors]").evaluate((element) => JSON.parse(element.textContent ?? "[]"));
    const runtimeErrors = await page.evaluate((id) => window.openUIVerification.errors[id] ?? [], scenario.id);
    const render = locator.locator("[data-render]");
    const layout = await page.evaluate(({ id, sameRow, childrenFillWidth }) => window.openUIVerification.collectLayout({ id, sameRow, childrenFillWidth }), scenario);
    const text = await render.textContent() ?? "";
    const box = await render.evaluate((element) => {
      const rect = element.getBoundingClientRect();
      return { x: rect.x, y: rect.y, width: rect.width, height: rect.height, right: rect.right, bottom: rect.bottom };
    });
    const elements = await render.locator("*").evaluateAll((nodes) => nodes.map((element) => {
      const rect = element.getBoundingClientRect();
      const style = getComputedStyle(element);
      return {
        x: rect.x, y: rect.y, width: rect.width, height: rect.height, right: rect.right, bottom: rect.bottom,
        scrollWidth: element.scrollWidth, scrollHeight: element.scrollHeight,
        clientWidth: element.clientWidth, clientHeight: element.clientHeight,
        overflowX: style.overflowX, overflowY: style.overflowY, whiteSpace: style.whiteSpace,
      };
    }));
    const measurement = MeasurementSchema.parse({
      found: (await render.locator("*").count()) > 0,
      text, box, elements,
      children: layout?.children ?? [],
      parents: layout?.parents ?? {},
      viewportWidth: page.viewportSize().width,
      documentScrollWidth: await page.evaluate(() => document.documentElement.scrollWidth),
    });
    const scenarioErrors = [];
    assertMeasurements(scenario, measurement, scenarioErrors);
    if (parseErrors.length && !scenario.allowParserErrors) scenarioErrors.push(`${scenario.id}: parser errors ${JSON.stringify(parseErrors)}`);
    if (runtimeErrors.length && !scenario.allowRuntimeErrors) scenarioErrors.push(`${scenario.id}: Renderer errors ${JSON.stringify(runtimeErrors)}`);
    await locator.screenshot({ path: path.join(evidence, `${scenario.id}.png`) });
    const result = { id: scenario.id, parseErrors, runtimeErrors, measurement, failures: scenarioErrors };
    results.push(result);
    failures.push(...scenarioErrors);
  }
  const scopedConsoleErrors = consoleErrors.filter((message) => !message.includes("[vite]"));
  for (const scenario of spec.scenarios) {
    const unexpected = scopedConsoleErrors.filter((message) => !scenario.allowedConsoleErrors.includes(message));
    if (unexpected.length) failures.push(`${scenario.id}: console errors: ${JSON.stringify(unexpected)}`);
  }
  failures.push(...pageErrors.map((message) => `page error: ${message}`));
  await page.screenshot({ path: path.join(evidence, "page.png"), fullPage: true });
  await writeFile(path.join(evidence, "report.json"), JSON.stringify({ source: spec.source, results, pageErrors, consoleWarnings, consoleErrors: scopedConsoleErrors, failures }, null, 2));
  console.log(JSON.stringify({ results: results.map(({ id, parseErrors, runtimeErrors, failures }) => ({ id, parseErrors, runtimeErrors, failures })), pageErrors, consoleErrors: scopedConsoleErrors, failures, evidence }, null, 2));

  if (options.smokeTest) {
    if (!failures.length || !failures.some((failure) => failure.includes('missing text "99"'))) throw new Error("Smoke test requires a known-bad expectation for missing text 99");
    console.log("Smoke test passed: the checker rejected the known-missing text 99.");
    await cleanup();
    process.exit(0);
  }
  await cleanup();
  process.exit(failures.length ? 1 : 0);
} catch (error) {
  await mkdir(evidence, { recursive: true });
  await writeFile(path.join(evidence, "report.json"), JSON.stringify({ failures: [...failures, error instanceof Error ? error.stack : String(error)], inconclusive: true }, null, 2));
  console.error(error);
  await cleanup();
  process.exit(1);
}
