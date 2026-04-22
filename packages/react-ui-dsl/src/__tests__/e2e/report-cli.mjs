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

  const updateSnapshotIndex = argv.indexOf("--update-snapshot");
  if (updateSnapshotIndex === -1) {
    return { mode: "run" };
  }

  const updateSnapshotFixtureId = argv[updateSnapshotIndex + 1];
  if (!updateSnapshotFixtureId || updateSnapshotFixtureId.startsWith("--")) {
    throw new Error("`--update-snapshot` requires a fixture id, for example `--update-snapshot table-basic`.");
  }

  return {
    mode: "run",
    updateSnapshotFixtureId,
  };
}

export function buildVitestRunConfig({ reportDir, updateSnapshotFixtureId, baseEnv = process.env }) {
  const args = updateSnapshotFixtureId
    ? ["exec", "vitest", "run", "src/__tests__/e2e/dsl-e2e.test.tsx", "-t", updateSnapshotFixtureId]
    : ["exec", "vitest", "run", "src/__tests__/e2e"];

  const env = {
    ...baseEnv,
    [REPORT_FLAG]: "1",
    [REPORT_DIR_FLAG]: reportDir,
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
    resolve: {
      alias: {
        "@openuidev/lang-core": resolve(workspaceRoot, "packages/lang-core/src/index.ts"),
        "@openuidev/react-lang": resolve(workspaceRoot, "packages/react-lang/src/index.ts"),
        "@openuidev/react-ui-dsl": resolve(packageRoot, "src/index.ts"),
      },
    },
    build: {
      emptyOutDir: false,
      outDir: reportDir,
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
