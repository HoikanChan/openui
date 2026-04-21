import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync, spawn } from "node:child_process";
import { build } from "vite";

const __dirname = dirname(fileURLToPath(import.meta.url));
const packageRoot = resolve(__dirname, "../../..");
const workspaceRoot = resolve(packageRoot, "../..");
const reportAppRoot = resolve(__dirname, "report-app");

const REPORT_FLAG = "REACT_UI_DSL_E2E_REPORT";
const REPORT_DIR_FLAG = "REACT_UI_DSL_E2E_REPORT_DIR";

async function main() {
  const timestamp = formatReportTimestamp(new Date());
  const reportDir = resolve(__dirname, "reports", timestamp);
  const reportDataPath = resolve(reportDir, "report-data.json");
  mkdirSync(reportDir, { recursive: true });

  const vitestResult = spawnSync(
    "pnpm exec vitest run src/__tests__/e2e",
    [],
    {
      cwd: packageRoot,
      env: {
        ...process.env,
        [REPORT_FLAG]: "1",
        [REPORT_DIR_FLAG]: reportDir,
      },
      shell: true,
      stdio: "inherit",
    },
  );

  if (vitestResult.error) {
    throw vitestResult.error;
  }

  if (existsSync(reportDataPath)) {
    await buildReportApp(reportDir, reportDataPath);
    console.log(`E2E report: ${resolve(reportDir, "index.html")}`);
    openReport(resolve(reportDir, "index.html"));
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

void main();
