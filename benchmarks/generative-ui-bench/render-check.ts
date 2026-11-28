// L2 deterministic real-browser render verification for openui-ours benchmark outputs.
// Builds the local packages/react-ui-dsl + packages/react-lang + packages/lang-core
// sources into a temporary Vite bundle, renders each fixture in headless Chromium via
// Playwright, and audits parseErrors, console.error, uncaught pageerrors, empty roots,
// empty tables, blank ECharts canvases, broken images, literal leaks, and overflow.
//
// Usage:
//   node render-check.ts <label> [--only=b1-uptime,b2-sales] [--limit=N] [--screenshots] [--all]
import {
  createReadStream,
  existsSync,
  mkdirSync,
  readFileSync,
  rmSync,
  statSync,
  symlinkSync,
  writeFileSync,
} from "node:fs";
import { createServer } from "node:http";
import { dirname, extname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { loadDataModels } from "./briefs/data-models.ts";

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, "../..");
const PACKAGE_ROOT = join(REPO_ROOT, "packages/react-ui-dsl");
const HARNESS_ROOT = join(__dirname, "render-harness");
const DIST_DIR = join(__dirname, ".render-dist");
const FIXTURES_DIR = join(DIST_DIR, "fixtures");
const RESULTS_DIR = join(__dirname, "results");

const CONTENT_TYPES: Record<string, string> = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".svg": "image/svg+xml",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
};

interface ResultRow {
  proto: string;
  scenario: string;
  band: string;
  reqs: number;
  repeat: number;
  complete: boolean;
  renderable: boolean;
}

function parseCliArgs(argv: string[]) {
  const label = argv.find((a) => !a.startsWith("--"));
  if (!label) {
    console.error(
      "Usage: node render-check.ts <label> [--only=b1-uptime,b2-sales] [--limit=N] [--screenshots] [--all]",
    );
    process.exit(1);
  }
  const onlyArg = argv.find((a) => a.startsWith("--only="))?.slice("--only=".length);
  const limitArg = argv.find((a) => a.startsWith("--limit="))?.slice("--limit=".length);
  return {
    label,
    only: onlyArg ? new Set(onlyArg.split(",").map((s) => s.trim()).filter(Boolean)) : null,
    limit: limitArg ? Number(limitArg) : null,
    screenshots: argv.includes("--screenshots"),
    includeInvalid: argv.includes("--all"),
  };
}

async function startStaticServer(
  dir: string,
): Promise<{ origin: string; close: () => Promise<void> }> {
  const server = createServer((req, res) => {
    const rawUrl = req.url ?? "/index.html";
    const pathname = decodeURIComponent(rawUrl.split("?")[0]!);
    const rel = pathname === "/" ? "/index.html" : pathname;
    const filePath = resolve(dir, `.${rel}`);
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
  const addr = await new Promise<{ port: number }>((res, rej) => {
    server.once("error", rej);
    server.listen(0, "127.0.0.1", () => res(server.address() as { port: number }));
  });
  return {
    origin: `http://127.0.0.1:${addr.port}`,
    close: () => new Promise((res, rej) => server.close((e) => (e ? rej(e) : res()))),
  };
}

async function buildHarnessApp() {
  const viteEntry = join(PACKAGE_ROOT, "node_modules/vite/dist/node/index.js");
  if (!existsSync(viteEntry)) {
    throw new Error(
      `Missing frontend dependencies in ${PACKAGE_ROOT}. Run "pnpm install" at repo root first.`,
    );
  }
  const { build } = await import(viteEntry);
  const { reactUiDslViewTargetPlugin } = await import(
    join(PACKAGE_ROOT, "src/__tests__/e2e/report-cli.mjs")
  );

  // Allow Vite inside render-harness to resolve react/antd/etc. from packages/react-ui-dsl/node_modules
  const harnessNodeModules = join(HARNESS_ROOT, "node_modules");
  const pkgNodeModules = join(PACKAGE_ROOT, "node_modules");
  if (!existsSync(harnessNodeModules)) {
    symlinkSync(pkgNodeModules, harnessNodeModules, "dir");
  }

  rmSync(DIST_DIR, { recursive: true, force: true });
  mkdirSync(FIXTURES_DIR, { recursive: true });

  const vendorCssPlugin = {
    name: "resolve-vendor-css",
    resolveId(source: string) {
      if (source === "./vendor/hljs-github.css") {
        return join(PACKAGE_ROOT, "node_modules/highlight.js/styles/github.css");
      }
      if (source === "./vendor/github-markdown.css") {
        return join(PACKAGE_ROOT, "node_modules/github-markdown-css/github-markdown-light.css");
      }
      return null;
    },
  };

  console.log("[render-check] Building Vite harness against workspace frontend sources...");
  await build({
    appType: "spa",
    base: "./",
    configFile: false,
    publicDir: false,
    root: HARNESS_ROOT,
    plugins: [vendorCssPlugin, reactUiDslViewTargetPlugin(PACKAGE_ROOT)],
    resolve: {
      alias: {
        "@cloudsop/openui-lang-core": join(REPO_ROOT, "packages/lang-core/src/index"),
        "@cloudsop/openui-react-lang": join(REPO_ROOT, "packages/react-lang/src/index"),
        "@cloudsop/openui-react-ui-dsl": join(PACKAGE_ROOT, "src/index"),
      },
    },
    build: { emptyOutDir: false, outDir: DIST_DIR },
    logLevel: "error",
  });
}

async function main() {
  const opts = parseCliArgs(process.argv.slice(2));
  const resultsPath = join(RESULTS_DIR, `results-${opts.label}.json`);
  if (!existsSync(resultsPath)) {
    throw new Error(`Missing ${resultsPath}. Run "node score.ts ${opts.label}" first.`);
  }

  const playwrightEntry = join(PACKAGE_ROOT, "node_modules/playwright/index.mjs");
  if (!existsSync(playwrightEntry)) {
    throw new Error(`Missing playwright in ${PACKAGE_ROOT}. Run "pnpm install" at repo root.`);
  }
  const playwright = await import(playwrightEntry);

  await buildHarnessApp();

  const results = JSON.parse(readFileSync(resultsPath, "utf8")) as ResultRow[];
  let targets = results.filter(
    (r) => r.proto === "openui-ours" && (opts.includeInvalid || r.complete),
  );
  if (opts.only) {
    targets = targets.filter((r) => opts.only!.has(r.scenario) || opts.only!.has(`${r.scenario}__r${r.repeat}`));
  }
  if (opts.limit !== null && opts.limit > 0) {
    targets = targets.slice(0, opts.limit);
  }

  const dataModels = loadDataModels();
  const screenshotsDir = join(RESULTS_DIR, `screenshots-${opts.label}`);
  if (opts.screenshots) mkdirSync(screenshotsDir, { recursive: true });

  const fixtures = targets.map((row) => {
    const id = `${row.scenario}__r${row.repeat}`;
    const dslPath = join(__dirname, "raw", opts.label, `openui-ours__${id}.txt`);
    const dsl = readFileSync(dslPath, "utf8");
    const payload = {
      id,
      scenario: row.scenario,
      band: row.band,
      repeat: row.repeat,
      dsl,
      dataModel: dataModels[row.scenario] ?? {},
    };
    writeFileSync(join(FIXTURES_DIR, `${id}.json`), JSON.stringify(payload), "utf8");
    return payload;
  });

  console.log(`[render-check] Verifying ${fixtures.length} openui-ours fixtures in Chromium...`);

  const server = await startStaticServer(DIST_DIR);
  const browser = await playwright.chromium.launch({ headless: true });
  const reports: any[] = [];

  try {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    const page = await context.newPage();

    for (let i = 0; i < fixtures.length; i++) {
      const fx = fixtures[i]!;
      const pageErrors: string[] = [];
      const consoleErrors: string[] = [];
      const consoleWarnings: string[] = [];

      const onPageError = (err: Error) => pageErrors.push(err.message || String(err));
      const onConsole = (msg: any) => {
        const type = msg.type();
        const text = msg.text();
        if (type === "error") consoleErrors.push(text);
        else if (type === "warning") consoleWarnings.push(text);
      };

      page.on("pageerror", onPageError);
      page.on("console", onConsole);

      await page.goto(`${server.origin}/index.html?id=${encodeURIComponent(fx.id)}`, {
        waitUntil: "domcontentloaded",
      });
      await page.waitForFunction(() => Boolean((window as any).__BENCH_STATE?.ready), {
        timeout: 10000,
      });
      await page.waitForTimeout(150);

      const hasCanvas = await page.locator("#preview-shell canvas").count();
      await page.waitForTimeout(hasCanvas > 0 ? 1050 : 100);

      const benchState = await page.evaluate(() => (window as any).__BENCH_STATE);
      const domAudit = await page.evaluate(() => {
        const shell = document.getElementById("preview-shell");
        if (!shell) return { missingShell: true };
        const rect = shell.getBoundingClientRect();
        const text = (shell.innerText || "").trim();

        const literalTokens: string[] = [];
        if (/\bundefined\b/.test(text)) literalTokens.push("undefined");
        if (/\bnull\b/.test(text)) literalTokens.push("null");
        if (/\[object Object\]/.test(text)) literalTokens.push("[object Object]");
        if (/\bNaN\b/.test(text)) literalTokens.push("NaN");
        if (/\{\{[^}]+\}\}/.test(text)) literalTokens.push("mustache");

        const tables = Array.from(shell.querySelectorAll(".ant-table, table")).map((tbl) => ({
          rows: tbl.querySelectorAll(
            "tbody tr:not(.ant-table-measure-row):not(.ant-table-placeholder)",
          ).length,
          headers: tbl.querySelectorAll("thead th").length,
          hasEmptyPlaceholder: Boolean(tbl.querySelector(".ant-empty, .ant-table-placeholder")),
        }));

        const canvases = Array.from(shell.querySelectorAll("canvas")).map((c) => {
          const r = c.getBoundingClientRect();
          let nonEmptyPixels = 0;
          try {
            const ctx = c.getContext("2d");
            if (ctx && c.width > 0 && c.height > 0) {
              const stepX = Math.max(1, Math.floor(c.width / 32));
              const stepY = Math.max(1, Math.floor(c.height / 32));
              const data = ctx.getImageData(0, 0, c.width, c.height).data;
              for (let y = 0; y < c.height; y += stepY) {
                for (let x = 0; x < c.width; x += stepX) {
                  if ((data[(y * c.width + x) * 4 + 3] ?? 0) > 0) nonEmptyPixels++;
                }
              }
            }
          } catch {
            nonEmptyPixels = -1;
          }
          return {
            cssWidth: Math.round(r.width),
            cssHeight: Math.round(r.height),
            nonEmptyPixels,
          };
        });

        const images = Array.from(shell.querySelectorAll("img")).map((img) => ({
          src: img.getAttribute("src"),
          complete: img.complete,
          naturalWidth: img.naturalWidth,
        }));

        return {
          missingShell: false,
          width: Math.round(rect.width),
          height: Math.round(rect.height),
          textLength: text.length,
          literalTokens,
          tables,
          canvases,
          images,
          horizontalOverflow: shell.scrollWidth > shell.clientWidth + 2,
        };
      });

      let screenshotPath: string | null = null;
      if (opts.screenshots) {
        const shellLocator = page.locator("#preview-shell");
        if ((await shellLocator.count()) > 0) {
          screenshotPath = join(screenshotsDir, `${fx.id}.png`);
          await shellLocator.screenshot({ path: screenshotPath });
        }
      }

      reports.push({
        id: fx.id,
        scenario: fx.scenario,
        band: fx.band,
        repeat: fx.repeat,
        pageErrors,
        consoleErrors,
        consoleWarningsCount: consoleWarnings.length,
        boundaryError: benchState.boundaryError,
        hasRoot: benchState.hasRoot,
        parseErrors: benchState.parseMeta?.errors ?? [],
        rendererErrors: benchState.rendererErrors ?? [],
        domAudit,
        screenshotPath,
      });

      page.off("pageerror", onPageError);
      page.off("console", onConsole);

      if ((i + 1) % 20 === 0 || i === fixtures.length - 1) {
        console.log(`[render-check] Progress: ${i + 1}/${fixtures.length}`);
      }
    }
  } finally {
    await browser.close();
    await server.close();
  }

  mkdirSync(RESULTS_DIR, { recursive: true });
  const outJson = join(RESULTS_DIR, `render-${opts.label}.json`);
  writeFileSync(outJson, JSON.stringify(reports, null, 2), "utf8");

  const clean = reports.filter(
    (r) =>
      r.hasRoot &&
      !r.boundaryError &&
      r.pageErrors.length === 0 &&
      r.consoleErrors.length === 0 &&
      r.parseErrors.length === 0 &&
      !r.domAudit.missingShell &&
      r.domAudit.literalTokens.length === 0 &&
      !r.domAudit.horizontalOverflow,
  ).length;
  const withParseErr = reports.filter((r) => r.parseErrors.length > 0).length;
  const withConsoleErr = reports.filter((r) => r.consoleErrors.length > 0).length;
  const withPageErr = reports.filter((r) => r.pageErrors.length > 0 || r.boundaryError).length;
  const withLeaks = reports.filter((r) => (r.domAudit.literalTokens?.length ?? 0) > 0).length;
  const withEmptyTables = reports.filter((r) =>
    (r.domAudit.tables ?? []).some((t: any) => t.rows === 0 || t.hasEmptyPlaceholder),
  ).length;
  const withBlankCanvas = reports.filter((r) =>
    (r.domAudit.canvases ?? []).some((c: any) => c.cssWidth < 10 || c.nonEmptyPixels === 0),
  ).length;

  console.log(`\n=== L2 Chromium Render Check: ${opts.label} (${reports.length} fixtures) ===`);
  console.log(`Clean (zero errors/leaks/overflow): ${clean}/${reports.length} (${((clean / Math.max(1, reports.length)) * 100).toFixed(1)}%)`);
  console.log(`  pageErrors / boundaryError: ${withPageErr}`);
  console.log(`  lang-core parseErrors:      ${withParseErr}`);
  console.log(`  console.error:              ${withConsoleErr}`);
  console.log(`  literal token leaks:        ${withLeaks}`);
  console.log(`  empty tables:               ${withEmptyTables}`);
  console.log(`  blank/broken canvases:      ${withBlankCanvas}`);
  console.log(`Wrote ${outJson}`);
}

main().catch((err) => {
  console.error("[render-check] Fatal error:", err);
  process.exit(1);
});
