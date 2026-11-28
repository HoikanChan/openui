// Offline scorer: evaluates committed/generated raws for both upstream OpenUI
// (@openuidev/lang-core@0.2.16) and our Java SDK (DefaultOpenuiLangValidator +
// AST reachable component count), writes results/results-<label>.json, and prints
// a band-by-band and error-class comparison table.
//
// Usage: node score.ts <label> [label ...]
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { SCENARIOS } from "./briefs/briefs.ts";
import { loadDataModels } from "./briefs/data-models.ts";
import { evaluate as evalUpstream, type BenchError } from "./protocols/openui-upstream/validator.ts";
import { evaluateOursBatch, type RawValidationIssue, type ValidateBatchItem } from "./protocols/openui-ours/validator.ts";

const __dirname = dirname(fileURLToPath(import.meta.url));
const RAW_DIR = join(__dirname, "raw");
const RESULTS_DIR = join(__dirname, "results");
const briefByName = new Map(SCENARIOS.map((s) => [s.name, s]));
const MAXREP = Number(process.env.BENCH_MAX_REP) || 4;

type Proto = "openui-upstream" | "openui-ours" | "openui-ours-nodata";

type ResultRow = {
  proto: Proto;
  scenario: string;
  band: string;
  reqs: number;
  repeat: number;
  promptTokens: number | null;
  completionTokens: number | null;
  latencyMs: number | null;
  renderable: boolean;
  complete: boolean;
  n: number;
  errs: BenchError[];
  classes: string[];
  rawIssues?: RawValidationIssue[];
};

const PROTO_RANK: Record<Proto, number> = {
  "openui-upstream": 0,
  "openui-ours-nodata": 1,
  "openui-ours": 2,
};

function scoreLabel(label: string): ResultRow[] {
  const dir = join(RAW_DIR, label);
  if (!existsSync(dir)) {
    throw new Error(`No raw directory found at ${dir}`);
  }
  const truncPath = join(dir, "truncated.json");
  const usagePath = join(dir, "usage.json");
  const truncated = new Set<string>(
    existsSync(truncPath) ? JSON.parse(readFileSync(truncPath, "utf8")) : [],
  );
  const usage: Record<
    string,
    { promptTokens: number | null; completionTokens: number | null; latencyMs: number | null }
  > = existsSync(usagePath) ? JSON.parse(readFileSync(usagePath, "utf8")) : {};

  const files = readdirSync(dir)
    .map((f) => ({
      f,
      m: f.match(/^(openui-upstream|openui-ours-nodata|openui-ours)__(.+)__r(\d+)\.txt$/),
    }))
    .filter((x): x is { f: string; m: RegExpMatchArray } => x.m !== null)
    .sort((a, b) => PROTO_RANK[a.m[1] as Proto] - PROTO_RANK[b.m[1] as Proto] || a.f.localeCompare(b.f));

  const dataModels = loadDataModels();
  const oursBatch: ValidateBatchItem[] = [];
  const parsedFiles: {
    proto: Proto;
    scenario: string;
    repeat: number;
    id: string;
    text: string;
    reqs: number;
    band: string;
  }[] = [];

  for (const { f, m } of files) {
    const proto = m[1] as Proto;
    const scenario = m[2];
    const repeat = Number(m[3]);
    if (repeat > MAXREP) continue;
    const brief = briefByName.get(scenario);
    if (!brief) continue;
    const id = `${proto}__${scenario}__r${repeat}`;
    const text = readFileSync(join(dir, f), "utf8");
    parsedFiles.push({ proto, scenario, repeat, id, text, reqs: brief.reqs, band: brief.band });
    if (proto === "openui-ours" || proto === "openui-ours-nodata") {
      oursBatch.push({
        id,
        text,
        dataModel: proto === "openui-ours" ? (dataModels[scenario] ?? null) : null,
        truncated: truncated.has(id),
        reqs: brief.reqs,
      });
    }
  }

  const oursVerdicts = evaluateOursBatch(oursBatch);
  const rows: ResultRow[] = [];

  for (const item of parsedFiles) {
    const u = usage[item.id];
    if (item.proto === "openui-upstream") {
      const res = evalUpstream(item.text, {
        truncated: truncated.has(item.id),
        reqs: item.reqs,
      });
      rows.push({
        proto: item.proto,
        scenario: item.scenario,
        band: item.band,
        reqs: item.reqs,
        repeat: item.repeat,
        promptTokens: u?.promptTokens ?? null,
        completionTokens: u?.completionTokens ?? null,
        latencyMs: u?.latencyMs ?? null,
        renderable: res.renderable,
        complete: res.complete,
        n: res.n,
        errs: res.errs,
        classes: [...new Set(res.errs.map((e) => e.cls))],
      });
    } else {
      const res = oursVerdicts.get(item.id)!;
      rows.push({
        proto: item.proto,
        scenario: item.scenario,
        band: item.band,
        reqs: item.reqs,
        repeat: item.repeat,
        promptTokens: u?.promptTokens ?? null,
        completionTokens: u?.completionTokens ?? null,
        latencyMs: u?.latencyMs ?? null,
        renderable: res.renderable,
        complete: res.complete,
        n: res.n,
        errs: res.errs,
        classes: [...new Set(res.errs.map((e) => e.cls))],
        rawIssues: res.rawIssues,
      });
    }
  }
  return rows;
}

function pct(num: number, den: number): string {
  if (den === 0) return "n/a";
  return `${((num / den) * 100).toFixed(1)}%`;
}

function printSummary(label: string, rows: ResultRow[]) {
  console.log(`\n=== Results: ${label} (${rows.length} runs) ===`);
  const protos = [...new Set(rows.map((r) => r.proto))];
  const bands = ["b1", "b2", "b3", "b4", "b5"];

  console.log("\nOverall & By Band (complete / total):");
  const header = ["Protocol", "Renderable", "Complete", ...bands.map((b) => b.toUpperCase()), "AvgTok", "AvgMs"];
  console.log(header.join("\t"));
  for (const proto of protos) {
    const pRows = rows.filter((r) => r.proto === proto);
    const rend = pRows.filter((r) => r.renderable).length;
    const comp = pRows.filter((r) => r.complete).length;
    const bandCells = bands.map((b) => {
      const bRows = pRows.filter((r) => r.band === b);
      const bComp = bRows.filter((r) => r.complete).length;
      return `${bComp}/${bRows.length}(${pct(bComp, bRows.length)})`;
    });
    const toks = pRows.map((r) => r.completionTokens).filter((x): x is number => x !== null);
    const ms = pRows.map((r) => r.latencyMs).filter((x): x is number => x !== null);
    const avgTok = toks.length ? Math.round(toks.reduce((a, b) => a + b, 0) / toks.length) : "-";
    const avgMs = ms.length ? Math.round(ms.reduce((a, b) => a + b, 0) / ms.length) : "-";
    console.log(
      [
        proto,
        `${rend}/${pRows.length}(${pct(rend, pRows.length)})`,
        `${comp}/${pRows.length}(${pct(comp, pRows.length)})`,
        ...bandCells,
        String(avgTok),
        String(avgMs),
      ].join("\t"),
    );
  }

  console.log("\nFailure Classes (runs with >=1 error in class):");
  const allClasses = [...new Set(rows.flatMap((r) => r.classes))].sort();
  if (allClasses.length === 0) {
    console.log("  (none)");
  } else {
    console.log(["ErrorClass", ...protos].join("\t"));
    for (const cls of allClasses) {
      const counts = protos.map((p) => {
        const pRows = rows.filter((r) => r.proto === p);
        const hits = pRows.filter((r) => r.classes.includes(cls)).length;
        return `${hits}/${pRows.length}`;
      });
      console.log([cls, ...counts].join("\t"));
    }
  }

  for (const proto of protos.filter((p) => p !== "openui-upstream")) {
    const codeCounts = new Map<string, number>();
    for (const r of rows.filter((x) => x.proto === proto)) {
      const seenInRun = new Set<string>();
      for (const issue of r.rawIssues ?? []) {
        if (issue.severity === "ERROR") seenInRun.add(issue.code);
      }
      for (const code of seenInRun) {
        codeCounts.set(code, (codeCounts.get(code) ?? 0) + 1);
      }
    }
    if (codeCounts.size > 0) {
      console.log(`\nTop Java Validator Error Codes (${proto}, by affected runs):`);
      const sorted = [...codeCounts.entries()].sort((a, b) => b[1] - a[1]);
      for (const [code, cnt] of sorted.slice(0, 15)) {
        console.log(`  ${code}\t${cnt}`);
      }
    }
  }
}

const labels = process.argv.slice(2);
const targetLabels =
  labels.length > 0
    ? labels
    : existsSync(RAW_DIR)
      ? readdirSync(RAW_DIR).filter((d) => !d.startsWith("."))
      : [];

if (targetLabels.length === 0) {
  console.error("Usage: node score.ts <label> [label ...]");
  process.exit(1);
}

mkdirSync(RESULTS_DIR, { recursive: true });
for (const label of targetLabels) {
  const rows = scoreLabel(label);
  const outPath = join(RESULTS_DIR, `results-${label}.json`);
  writeFileSync(outPath, JSON.stringify(rows, null, 2));
  printSummary(label, rows);
  console.log(`\nWrote ${outPath}`);
}
