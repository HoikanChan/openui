// Standalone runner for tc-* benchmark fixtures — plain node (via tsx), no vitest.
//
//   pnpm test:tc:node                 run all tc-* fixtures, write report-data.json
//   pnpm test:tc:node --report        also build the report app and serve it
//   pnpm test:tc:node --filter tc-013 run a subset
//
// Mirrors dsl-benchmark.test.tsx with one substitution: instead of a jsdom
// render (which needs vitest's environment and the echarts mock), each fixture
// is rendered with react-dom/server. SSR executes every component's render
// path — catching parse errors and bad data bindings — but skips effects, so
// echarts never initialises and no DOM globals are required.
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { createElement } from "react";
import { renderToString } from "react-dom/server";
import { createParser } from "@cloudsop/openui-lang-core";
import { Renderer } from "@cloudsop/openui-react-lang";
import { dslLibrary } from "../../genui-lib/dslLibrary";
import { loadBenchmarkCases } from "./benchmark-loader";
import { loadRegenEnvIfNeeded } from "./env";
import { loadOrGenerate } from "./llm";
import {
  finalizeE2EReport,
  resetE2EReportState,
  runE2EReportEntry,
  setE2EReportEntryDsl,
} from "./report";

const __dirname = dirname(fileURLToPath(import.meta.url));
const BENCHMARK_DATA_DIR = resolve(__dirname, "fuzz-data/benchmark");
const BENCHMARK_SNAPSHOTS_DIR = resolve(__dirname, "benchmark-snapshots");

function parseArgs(argv: string[]): { filter: string; report: boolean } {
  const filterIndex = argv.indexOf("--filter");
  let filter = "tc-";
  if (filterIndex !== -1) {
    const value = argv[filterIndex + 1];
    if (!value || value.startsWith("--")) {
      throw new Error("`--filter` requires a fixture-id substring, for example `--filter tc-013`.");
    }
    filter = value;
  }
  return { filter, report: argv.includes("--report") };
}

function reportTimestamp(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}_${pad(date.getHours())}-${pad(date.getMinutes())}-${pad(date.getSeconds())}`;
}

async function main(): Promise<void> {
  const { filter, report } = parseArgs(process.argv.slice(2));

  // "0" keeps loadOrGenerate in reuse-or-generate mode (existing snapshots are
  // read back; missing ones are generated via the .env LLM config).
  process.env["REGEN_SNAPSHOTS"] ??= "0";
  loadRegenEnvIfNeeded();

  const reportDir = resolve(__dirname, "reports", reportTimestamp(new Date()));
  process.env["REACT_UI_DSL_E2E_REPORT"] = "1";
  process.env["REACT_UI_DSL_E2E_REPORT_DIR"] = reportDir;
  process.env["REACT_UI_DSL_E2E_SUITE"] ??= "benchmark";

  const cases = loadBenchmarkCases(BENCHMARK_DATA_DIR).filter(({ id }) => id.includes(filter));
  if (cases.length === 0) {
    console.error(`No benchmark fixtures match filter "${filter}".`);
    process.exit(1);
  }

  const parser = createParser(dslLibrary.toJSONSchema(), undefined, { externalRefs: ["data"] });
  resetE2EReportState();

  let failed = 0;
  for (const { id, prompt, dataModel, evalHints } of cases) {
    try {
      await runE2EReportEntry(
        "Benchmark",
        {
          id,
          prompt,
          expectedDescription: "Generated benchmark case should parse and render without errors",
          dataModel: dataModel as Record<string, unknown>,
          assert: { contains: [] },
        },
        async (entry) => {
          if (entry) entry.evalHints = evalHints;

          const dsl = await loadOrGenerate(
            id,
            prompt,
            dataModel as Record<string, unknown>,
            BENCHMARK_SNAPSHOTS_DIR,
          );
          setE2EReportEntryDsl(entry, dsl);

          const parsed = parser.parse(dsl);
          if (parsed.meta.errors.length > 0) {
            throw new Error(`parse errors in ${id}:\n${parsed.meta.errors.map((e) => JSON.stringify(e)).join("\n")}`);
          }

          renderToString(
            createElement(Renderer, {
              library: dslLibrary,
              response: dsl,
              dataModel: dataModel as Record<string, unknown>,
            }),
          );
        },
      );
      console.log(`  ✓ ${id}`);
    } catch (error) {
      failed++;
      const reason = error instanceof Error ? error.message.split("\n")[0] : String(error);
      console.log(`  ✗ ${id} — ${reason}`);
    }
  }

  const reportPath = finalizeE2EReport();
  console.log(`\n${cases.length - failed}/${cases.length} passed.`);
  if (reportPath) console.log(`Report data: ${reportPath}`);

  if (report && reportPath) {
    const { buildReportApp, startStaticReportServer, buildReportUrl } = await import("./report-cli.mjs");
    await buildReportApp(reportDir, reportPath);
    const server = await startStaticReportServer(reportDir);
    console.log(`E2E report: ${buildReportUrl(server.origin)} (Ctrl+C to stop)`);
    return; // keep the process alive while serving
  }

  process.exit(failed > 0 ? 1 : 0);
}

await main();
