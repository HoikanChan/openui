import { createReadStream, existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { createServer } from "node:http";
import { dirname, extname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { spawnSync, spawn } from "node:child_process";
import { build } from "vite";

const __dirname = dirname(fileURLToPath(import.meta.url));
const packageRoot = resolve(__dirname, "../../..");
const workspaceRoot = resolve(packageRoot, "../..");
const reportAppRoot = resolve(__dirname, "report-app");
const REPORT_SERVER_HOST = "127.0.0.1";
const REPORT_SERVER_DEFAULT_PORT = 4173;

const REPORT_FLAG = "REACT_UI_DSL_E2E_REPORT";
const REPORT_DIR_FLAG = "REACT_UI_DSL_E2E_REPORT_DIR";
const REGEN_SNAPSHOTS_FLAG = "REGEN_SNAPSHOTS";
const SUITE_FLAG = "REACT_UI_DSL_E2E_SUITE";

// JS mirror of `reactUiDslViewTargetPlugin` in ../../../view-target.config.ts,
// extended with the report-app's per-target global stylesheet. This file runs
// under plain `node`, which cannot import the `.ts` config, so the eview
// view-target remap is reproduced here for the report-app vite build.
// Keep VIEW_COMPONENTS in sync with view-target.config.ts.
const VIEW_COMPONENTS = ["Button", "Form", "Input", "Link", "Select", "Table", "Tabs", "Tag", "TimeLine"];

// Global stylesheet imported by report-app/main.tsx via the virtual module below.
// antd v5 is CSS-in-JS (no global import); eview needs its design-system styles.
const EVIEW_STYLE_ENTRY = "@cloudsop/eview-ui/style/aui3.1.less";
const VIRTUAL_VIEW_STYLES = "virtual:react-ui-dsl-view-styles";
const RESOLVED_VIEW_STYLES = "\0" + VIRTUAL_VIEW_STYLES;

function getReactUiDslViewTarget(env = process.env) {
  return env.REACT_UI_DSL_VIEW_TARGET === "eview" ? "eview" : "antd";
}

function normalizeIdForMatch(id) {
  let normalized = id.replace(/[?#].*$/, "").replace(/\\/g, "/");
  if (process.platform === "win32") normalized = normalized.toLowerCase();
  return normalized;
}

function reactUiDslViewTargetPlugin(rootDir, target = getReactUiDslViewTarget()) {
  const isEview = target === "eview";

  const lookup = new Map();
  if (isEview) {
    for (const component of VIEW_COMPONENTS) {
      const viewDir = resolve(rootDir, "src", "genui-lib", component, "view");
      const replacement = resolve(viewDir, "eview.tsx");
      lookup.set(normalizeIdForMatch(resolve(viewDir, "index.tsx")), replacement);
      lookup.set(normalizeIdForMatch(resolve(viewDir, "index")), replacement);
    }
  }

  return {
    name: "react-ui-dsl-view-target",
    enforce: "pre",
    async resolveId(source, importer, options) {
      if (source === VIRTUAL_VIEW_STYLES) return RESOLVED_VIEW_STYLES;
      if (!isEview) return null;
      const resolved = await this.resolve(source, importer, { ...options, skipSelf: true });
      if (!resolved) return null;
      return lookup.get(normalizeIdForMatch(resolved.id)) ?? null;
    },
    load(id) {
      if (id !== RESOLVED_VIEW_STYLES) return null;
      return isEview ? `import ${JSON.stringify(EVIEW_STYLE_ENTRY)};\n` : "export {};\n";
    },
  };
}
const CONTENT_TYPES = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
};

export function parseReportCliArgs(argv) {
  if (argv[0] === "--serve-report-dir") {
    return {
      mode: "serve",
      reportDir: argv[1],
      preferredPort: Number.parseInt(argv[2] ?? "", 10),
    };
  }

  const suite = argv.includes("--fuzz") ? "fuzz" : "e2e";
  const updateSnapshotIndex = argv.indexOf("--update-snapshot");
  if (updateSnapshotIndex === -1) {
    return { mode: "run", suite };
  }

  const updateSnapshotFixtureId = argv[updateSnapshotIndex + 1];
  if (!updateSnapshotFixtureId || updateSnapshotFixtureId.startsWith("--")) {
    throw new Error("`--update-snapshot` requires a fixture id, for example `--update-snapshot table-basic`.");
  }

  return {
    mode: "run",
    suite,
    updateSnapshotFixtureId,
  };
}

export function buildVitestRunConfig({ reportDir, suite = "e2e", updateSnapshotFixtureId, baseEnv = process.env }) {
  const targetFile = suite === "fuzz" ? "src/__tests__/e2e/dsl-fuzz.test.tsx" : "src/__tests__/e2e/dsl-e2e.test.tsx";
  const args = updateSnapshotFixtureId
    ? ["exec", "vitest", "run", targetFile, "-t", updateSnapshotFixtureId]
    : suite === "fuzz"
      ? ["exec", "vitest", "run", targetFile]
      : ["exec", "vitest", "run", "src/__tests__/e2e"];

  const env = {
    ...baseEnv,
    [REPORT_FLAG]: "1",
    [REPORT_DIR_FLAG]: reportDir,
    [SUITE_FLAG]: suite,
  };

  if (updateSnapshotFixtureId) {
    env[REGEN_SNAPSHOTS_FLAG] = "1";
  }

  return {
    command: process.platform === "win32" ? "pnpm.cmd" : "pnpm",
    args,
    env,
  };
}

async function main(argv = process.argv.slice(2)) {
  const cliArgs = parseReportCliArgs(argv);
  const timestamp = formatReportTimestamp(new Date());
  const reportDir = resolve(__dirname, "reports", timestamp);
  const reportDataPath = resolve(reportDir, "report-data.json");
  mkdirSync(reportDir, { recursive: true });

  const vitestRunConfig = buildVitestRunConfig({
    reportDir,
    suite: cliArgs.mode === "run" ? cliArgs.suite : undefined,
    updateSnapshotFixtureId: cliArgs.mode === "run" ? cliArgs.updateSnapshotFixtureId : undefined,
  });

  const vitestResult = spawnSync(vitestRunConfig.command, vitestRunConfig.args, {
    cwd: packageRoot,
    env: vitestRunConfig.env,
    stdio: "inherit",
    shell: true,
  });

  if (vitestResult.error) {
    throw vitestResult.error;
  }

  if (existsSync(reportDataPath)) {
    await buildReportApp(reportDir, reportDataPath);
    const reportServer = await ensureReportServer(reportDir);
    console.log(`E2E report: ${buildReportUrl(reportServer.origin)}`);
    openReport(buildReportUrl(reportServer.origin));
  } else {
    console.warn("E2E report data was not generated.");
  }

  process.exit(vitestResult.status ?? 1);
}

export function formatReportTimestamp(date) {
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}_${pad(date.getHours())}-${pad(date.getMinutes())}-${pad(date.getSeconds())}`;
}

export function getOpenCommand(targetPath) {
  if (process.platform === "win32") {
    return { command: "cmd", args: ["/c", "start", "", targetPath] };
  }

  if (process.platform === "darwin") {
    return { command: "open", args: [targetPath] };
  }

  return { command: "xdg-open", args: [targetPath] };
}

async function buildReportApp(reportDir, reportDataPath) {
  await build({
    appType: "spa",
    base: "./",
    configFile: false,
    publicDir: false,
    root: reportAppRoot,
    plugins: [reactUiDslViewTargetPlugin(packageRoot)].filter(Boolean),
    resolve: {
      alias: {
        "@cloudsop/openui-lang-core": resolve(workspaceRoot, "packages/lang-core/src/index.ts"),
        "@cloudsop/openui-react-lang": resolve(workspaceRoot, "packages/react-lang/src/index.ts"),
        "@cloudsop/openui-react-ui-dsl": resolve(packageRoot, "src/index.ts"),
      },
    },
    build: {
      emptyOutDir: false,
      outDir: reportDir,
      // The eview global stylesheet is a side-effect-only `.less` import. Libraries
      // that ship `"sideEffects": false` (e.g. @cloudsop/eview-ui) would otherwise
      // let rollup tree-shake it away, leaving eview components unstyled. This is a
      // report artifact — bundle size is irrelevant — so keep all side effects.
      rollupOptions: {
        treeshake: { moduleSideEffects: true },
      },
    },
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

export function buildReportUrl(origin) {
  return `${origin.replace(/\/$/, "")}/index.html`;
}

export async function startStaticReportServer(reportDir, preferredPort = REPORT_SERVER_DEFAULT_PORT) {
  const server = createServer((request, response) => {
    const requestPath = request.url === "/" ? "/index.html" : request.url ?? "/index.html";
    const filePath = resolve(reportDir, `.${decodeURIComponent(requestPath.split("?")[0])}`);

    if (!filePath.startsWith(reportDir)) {
      response.writeHead(403, { "Content-Type": "text/plain; charset=utf-8" });
      response.end("Forbidden");
      return;
    }

    if (!existsSync(filePath) || statSync(filePath).isDirectory()) {
      response.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      response.end("Not Found");
      return;
    }

    response.writeHead(200, {
      "Content-Type": CONTENT_TYPES[extname(filePath)] ?? "application/octet-stream",
      "Cache-Control": "no-store",
    });
    createReadStream(filePath).pipe(response);
  });

  const address = await listen(server, preferredPort);
  const origin = `http://${REPORT_SERVER_HOST}:${address.port}`;

  return {
    origin,
    close: () =>
      new Promise((resolveClose, rejectClose) => {
        server.close((error) => {
          if (error) {
            rejectClose(error);
            return;
          }

          resolveClose();
        });
      }),
  };
}

async function ensureReportServer(reportDir) {
  if (process.env.REACT_UI_DSL_E2E_REPORT_SERVER === "inline") {
    return startStaticReportServer(reportDir);
  }

  const port = Number.parseInt(process.env.REACT_UI_DSL_E2E_REPORT_SERVER_PORT ?? "", 10);
  const preferredPort = Number.isFinite(port) ? port : REPORT_SERVER_DEFAULT_PORT;
  const availablePort = await resolveAvailablePort(preferredPort);
  const child = spawn(
    process.execPath,
    [fileURLToPath(import.meta.url), "--serve-report-dir", reportDir, String(availablePort)],
    {
      detached: true,
      stdio: "ignore",
    },
  );
  child.unref();

  const origin = `http://${REPORT_SERVER_HOST}:${availablePort}`;
  return { origin };
}

function listen(server, preferredPort) {
  return new Promise((resolveListen, rejectListen) => {
    const handleError = (error) => {
      if (error?.code === "EADDRINUSE" && preferredPort !== 0) {
        server.off("error", handleError);
        server.listen(0, REPORT_SERVER_HOST);
        return;
      }

      rejectListen(error);
    };

    server.once("error", handleError);
    server.listen(preferredPort, REPORT_SERVER_HOST, () => {
      server.off("error", handleError);
      resolveListen(server.address());
    });
  });
}

async function resolveAvailablePort(preferredPort) {
  const probe = createServer();

  try {
    const address = await listen(probe, preferredPort);
    return address.port;
  } finally {
    await new Promise((resolveClose) => {
      probe.close(() => resolveClose());
    });
  }
}

async function maybeServeReportDirFromArgs() {
  const cliArgs = parseReportCliArgs(process.argv.slice(2));
  if (cliArgs.mode !== "serve") {
    return false;
  }

  await startStaticReportServer(
    cliArgs.reportDir,
    Number.isFinite(cliArgs.preferredPort) ? cliArgs.preferredPort : REPORT_SERVER_DEFAULT_PORT,
  );
  return true;
}

function openReport(targetPath) {
  try {
    const { command, args } = getOpenCommand(targetPath);
    const child = spawn(command, args, {
      detached: true,
      stdio: "ignore",
    });
    child.unref();
  } catch (error) {
    console.warn(`Unable to open report automatically: ${error instanceof Error ? error.message : String(error)}`);
  }
}

if (pathToFileURL(process.argv[1] ?? "").href === import.meta.url) {
  const served = await maybeServeReportDirFromArgs();
  if (!served) {
    await main();
  }
}
