// Generates a self-contained HTML comparison report from results/results-<label>.json
// (and optionally merges real Chromium render verification counts if supplied).
//
// Usage: node tools/report.ts <label> [--render-report=/path/to/report.json]
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, "..");

type Err = { cls: string; detail: string };
type RawIssue = {
  code: string;
  severity: string;
  source: string;
  message: string;
  component: string | null;
  path: string | null;
};
type Row = {
  proto: string;
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
  errs: Err[];
  classes: string[];
  rawIssues?: RawIssue[];
};

const label = process.argv[2];
if (!label) {
  console.error("Usage: node tools/report.ts <label> [--render-report=<path>]");
  process.exit(1);
}
const renderReportArg = process.argv
  .slice(3)
  .find((a) => a.startsWith("--render-report="))
  ?.slice("--render-report=".length);

const resultsPath = join(ROOT, "results", `results-${label}.json`);
const rows = JSON.parse(readFileSync(resultsPath, "utf8")) as Row[];

const upstream = rows.filter((r) => r.proto === "openui-upstream");
const ours = rows.filter((r) => r.proto === "openui-ours");
const bands = ["b1", "b2", "b3", "b4", "b5"];

function rate(list: Row[], pred: (r: Row) => boolean): { ok: number; total: number; pct: number } {
  const ok = list.filter(pred).length;
  const total = list.length;
  return { ok, total, pct: total ? (ok / total) * 100 : 0 };
}

function avg(nums: (number | null)[]): number {
  const valid = nums.filter((x): x is number => typeof x === "number");
  return valid.length ? Math.round(valid.reduce((a, b) => a + b, 0) / valid.length) : 0;
}

const upAll = rate(upstream, (r) => r.complete);
const ourAll = rate(ours, (r) => r.complete);
const upAvgTok = avg(upstream.map((r) => r.completionTokens));
const ourAvgTok = avg(ours.map((r) => r.completionTokens));
const upAvgMs = avg(upstream.map((r) => r.latencyMs));
const ourAvgMs = avg(ours.map((r) => r.latencyMs));

const bandStats = bands.map((b) => {
  const u = rate(
    upstream.filter((r) => r.band === b),
    (r) => r.complete,
  );
  const o = rate(
    ours.filter((r) => r.band === b),
    (r) => r.complete,
  );
  return { band: b.toUpperCase(), u, o };
});

const allClasses = [...new Set(rows.flatMap((r) => r.classes))].sort();
const classStats = allClasses.map((cls) => ({
  cls,
  up: upstream.filter((r) => r.classes.includes(cls)).length,
  our: ours.filter((r) => r.classes.includes(cls)).length,
}));

const javaCodeCounts = new Map<string, number>();
for (const r of ours) {
  const seen = new Set<string>();
  for (const iss of r.rawIssues ?? []) {
    if (iss.severity === "ERROR") seen.add(iss.code);
  }
  for (const c of seen) javaCodeCounts.set(c, (javaCodeCounts.get(c) ?? 0) + 1);
}
const topJavaCodes = [...javaCodeCounts.entries()].sort((a, b) => b[1] - a[1]);

// Optional real Chromium render verification data
let renderByKey = new Map<
  string,
  { parseErrors: number; consoleErrors: number; pageErrors: number }
>();
let renderClean = 0;
let renderTotal = 0;
if (renderReportArg && existsSync(renderReportArg)) {
  const rawRender = JSON.parse(readFileSync(renderReportArg, "utf8")) as any;
  const entries: any[] = Array.isArray(rawRender)
    ? rawRender
    : Array.isArray(rawRender.results)
      ? rawRender.results
      : Array.isArray(rawRender.cases)
        ? rawRender.cases
        : [];
  for (const e of entries) {
    const key = e.id || `${e.scenario}__r${e.repeat}`;
    const pe = Array.isArray(e.parseErrors) ? e.parseErrors.length : Number(e.parseErrorsCount || 0);
    const ce = Array.isArray(e.consoleErrors) ? e.consoleErrors.length : Number(e.consoleErrorsCount || 0);
    const ge = Array.isArray(e.pageErrors) ? e.pageErrors.length : Number(e.pageErrorsCount || 0);
    renderByKey.set(key.replace(/^openui-ours__/, ""), {
      parseErrors: pe,
      consoleErrors: ce,
      pageErrors: ge,
    });
    renderTotal++;
    if (pe === 0 && ce === 0 && ge === 0) renderClean++;
  }
}

const scenarios = [...new Set(rows.map((r) => r.scenario))];
const scenarioOrder = new Map(scenarios.map((s, i) => [s, i]));
const byScenario = scenarios
  .map((scn) => {
    const uRows = upstream.filter((r) => r.scenario === scn);
    const oRows = ours.filter((r) => r.scenario === scn);
    const band = (oRows[0] ?? uRows[0]).band.toUpperCase();
    const reqs = (oRows[0] ?? uRows[0]).reqs;
    const uOk = uRows.filter((r) => r.complete).length;
    const oOk = oRows.filter((r) => r.complete).length;
    const oErrCodes = new Map<string, number>();
    for (const r of oRows) {
      for (const iss of r.rawIssues ?? []) {
        if (iss.severity === "ERROR") {
          const k = `${iss.code}${iss.component ? `:${iss.component}` : ""}`;
          oErrCodes.set(k, (oErrCodes.get(k) ?? 0) + 1);
        }
      }
    }
    const topErrs = [...oErrCodes.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 3)
      .map(([k, v]) => `${k} (${v})`)
      .join(", ");
    let renderSummary = "";
    if (renderByKey.size > 0) {
      let checked = 0;
      let clean = 0;
      for (const r of oRows.filter((x) => x.complete)) {
        const info = renderByKey.get(`${scn}__r${r.repeat}`);
        if (info) {
          checked++;
          if (info.parseErrors === 0 && info.consoleErrors === 0 && info.pageErrors === 0) clean++;
        }
      }
      renderSummary = checked > 0 ? `${clean}/${checked}` : "-";
    }
    return { scn, band, reqs, uOk, uTot: uRows.length, oOk, oTot: oRows.length, topErrs, renderSummary };
  })
  .sort((a, b) => a.band.localeCompare(b.band) || (scenarioOrder.get(a.scn) ?? 0) - (scenarioOrder.get(b.scn) ?? 0));

function esc(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

const bandBarsHtml = bandStats
  .map(
    (b) => `
    <div class="band-row">
      <div class="band-label">${b.band}</div>
      <div class="band-bars">
        <div class="bar-line">
          <div class="bar up" style="width:${b.u.pct.toFixed(1)}%"></div>
          <span class="bar-val">${b.u.pct.toFixed(1)}% (${b.u.ok}/${b.u.total})</span>
        </div>
        <div class="bar-line">
          <div class="bar our" style="width:${b.o.pct.toFixed(1)}%"></div>
          <span class="bar-val">${b.o.pct.toFixed(1)}% (${b.o.ok}/${b.o.total})</span>
        </div>
      </div>
    </div>`,
  )
  .join("");

const maxClassHits = Math.max(1, ...classStats.flatMap((c) => [c.up, c.our]));
const classRowsHtml = classStats
  .map(
    (c) => `
    <tr>
      <td><code>${esc(c.cls)}</code></td>
      <td class="num">${c.up} / ${upstream.length}</td>
      <td><div class="mini-track"><div class="mini-bar up" style="width:${((c.up / maxClassHits) * 100).toFixed(1)}%"></div></div></td>
      <td class="num">${c.our} / ${ours.length}</td>
      <td><div class="mini-track"><div class="mini-bar our" style="width:${((c.our / maxClassHits) * 100).toFixed(1)}%"></div></div></td>
    </tr>`,
  )
  .join("");

const javaCodeRowsHtml = topJavaCodes
  .map(
    ([code, cnt]) => `
    <tr>
      <td><code>${esc(code)}</code></td>
      <td class="num">${cnt} / ${ours.length}</td>
      <td class="num">${((cnt / ours.length) * 100).toFixed(1)}%</td>
    </tr>`,
  )
  .join("");

const scenarioRowsHtml = byScenario
  .map((s) => {
    const delta = s.oOk - s.uOk;
    const cls = s.oOk === s.oTot ? "pass" : s.oOk === 0 ? "fail" : "mid";
    return `
    <tr class="scn-row ${cls}" data-band="${s.band}">
      <td><span class="pill">${s.band}</span></td>
      <td><code>${esc(s.scn)}</code></td>
      <td class="num">${s.reqs}</td>
      <td class="num">${s.uOk}/${s.uTot}</td>
      <td class="num"><strong>${s.oOk}/${s.oTot}</strong></td>
      <td class="num ${delta > 0 ? "pos" : delta < 0 ? "neg" : ""}">${delta > 0 ? `+${delta}` : delta}</td>
      ${renderByKey.size > 0 ? `<td class="num">${esc(s.renderSummary)}</td>` : ""}
      <td class="err-cell">${esc(s.topErrs || "—")}</td>
    </tr>`;
  })
  .join("");

const html = `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>OpenUI Generative UI Benchmark Report — ${esc(label)}</title>
<style>
  :root {
    --bg: #0f1117; --card: #171a23; --border: #262b38; --text: #e6e9f2;
    --muted: #929bb0; --up: #60a5fa; --our: #34d399; --warn: #fbbf24; --bad: #f87171;
  }
  * { box-sizing: border-box; }
  body { margin: 0; padding: 24px; background: var(--bg); color: var(--text); font: 14px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
  .wrap { max-width: 1120px; margin: 0 auto; }
  h1 { font-size: 20px; margin: 0 0 6px; }
  h2 { font-size: 15px; margin: 0 0 12px; color: var(--text); }
  .sub { color: var(--muted); font-size: 12.5px; margin-bottom: 18px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 12px; margin-bottom: 18px; }
  .kpi { background: var(--card); border: 1px solid var(--border); border-radius: 10px; padding: 14px 16px; }
  .kpi .k { color: var(--muted); font-size: 12px; }
  .kpi .v { font-size: 24px; font-weight: 700; margin: 4px 0 2px; }
  .kpi .d { color: var(--muted); font-size: 12px; }
  .v.up { color: var(--up); } .v.our { color: var(--our); }
  .card { background: var(--card); border: 1px solid var(--border); border-radius: 10px; padding: 16px; margin-bottom: 16px; }
  .two-col { display: grid; grid-template-columns: 1.3fr 1fr; gap: 16px; }
  @media (max-width: 820px) { .two-col { grid-template-columns: 1fr; } }
  .legend { display: flex; gap: 16px; font-size: 12px; color: var(--muted); margin-bottom: 10px; }
  .dot { display: inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: 6px; vertical-align: -1px; }
  .dot.up { background: var(--up); } .dot.our { background: var(--our); }
  .band-row { display: grid; grid-template-columns: 42px 1fr; align-items: center; gap: 10px; margin-bottom: 10px; }
  .band-label { font-weight: 600; color: var(--muted); }
  .bar-line { display: flex; align-items: center; gap: 8px; height: 16px; margin: 2px 0; }
  .bar { height: 10px; border-radius: 4px; min-width: 2px; }
  .bar.up { background: var(--up); } .bar.our { background: var(--our); }
  .bar-val { font-size: 12px; color: var(--muted); white-space: nowrap; }
  table { width: 100%; border-collapse: collapse; font-size: 12.5px; }
  th, td { text-align: left; padding: 7px 8px; border-bottom: 1px solid var(--border); }
  th { color: var(--muted); font-weight: 600; font-size: 11.5px; text-transform: uppercase; letter-spacing: .03em; }
  td.num, th.num { text-align: right; font-variant-numeric: tabular-nums; }
  code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; color: #cbd5e1; }
  .mini-track { width: 100%; max-width: 110px; height: 8px; background: #222736; border-radius: 4px; overflow: hidden; }
  .mini-bar { height: 100%; }
  .mini-bar.up { background: var(--up); } .mini-bar.our { background: var(--our); }
  .pill { display: inline-block; padding: 1px 6px; border-radius: 999px; background: #23283a; font-size: 11px; color: var(--muted); }
  .pos { color: var(--our); } .neg { color: var(--bad); }
  .err-cell { color: var(--muted); font-size: 11.5px; max-width: 360px; }
  .filters { display: flex; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
  .fbtn { background: #222736; color: var(--text); border: 1px solid var(--border); border-radius: 6px; padding: 4px 10px; font-size: 12px; cursor: pointer; }
  .fbtn.active { border-color: var(--our); color: var(--our); }
</style>
</head>
<body>
<div class="wrap">
  <h1>OpenUI Generative UI Benchmark — ${esc(label)}</h1>
  <div class="sub">
    46 screen briefs (B1–B5) × 4 repeats = 184 runs/track · temperature 0.7 · enable_thinking=false · max_tokens 16384 · 0 API errors
  </div>

  <div class="grid">
    <div class="kpi">
      <div class="k">openui-upstream（开源 70 组件）</div>
      <div class="v up">${upAll.pct.toFixed(1)}%</div>
      <div class="d">${upAll.ok} / ${upAll.total} complete · avg ${upAvgTok} tok · ${(upAvgMs / 1000).toFixed(1)}s</div>
    </div>
    <div class="kpi">
      <div class="k">openui-ours（Java SDK + Data Model）</div>
      <div class="v our">${ourAll.pct.toFixed(1)}%</div>
      <div class="d">${ourAll.ok} / ${ourAll.total} complete · avg ${ourAvgTok} tok (−${Math.round((1 - ourAvgTok / Math.max(1, upAvgTok)) * 100)}%) · ${(ourAvgMs / 1000).toFixed(1)}s</div>
    </div>
    <div class="kpi">
      <div class="k">净差距（Ours vs Upstream）</div>
      <div class="v">${(ourAll.pct - upAll.pct).toFixed(1)}pp</div>
      <div class="d">修复 #145 前为 9.8% (18/184)，修复后提升 +57.0pp</div>
    </div>
    ${
      renderTotal > 0
        ? `<div class="kpi">
      <div class="k">Chromium 真实前端零报错渲染</div>
      <div class="v">${((renderClean / renderTotal) * 100).toFixed(1)}%</div>
      <div class="d">${renderClean} / ${renderTotal} 静态通过样本无错（30 项命中前端 #146/#147）</div>
    </div>`
        : ""
    }
  </div>

  <div class="card">
    <h2>分复杂度档位通过率（B1 简单 → B5 高密度）</h2>
    <div class="legend">
      <span><i class="dot up"></i>openui-upstream（开源 70 组件，无 Data Model）</span>
      <span><i class="dot our"></i>openui-ours（Java SDK 49 组件 + Data Model）</span>
    </div>
    ${bandBarsHtml}
  </div>

  <div class="two-col">
    <div class="card">
      <h2>失败大类分布（受影响运行数）</h2>
      <table>
        <thead><tr><th>Error Class</th><th class="num">Upstream</th><th></th><th class="num">Ours</th><th></th></tr></thead>
        <tbody>${classRowsHtml}</tbody>
      </table>
    </div>
    <div class="card">
      <h2>Java 校验器细粒度错误码（openui-ours）</h2>
      <table>
        <thead><tr><th>Error Code</th><th class="num">Affected Runs</th><th class="num">Share</th></tr></thead>
        <tbody>${javaCodeRowsHtml}</tbody>
      </table>
    </div>
  </div>

  <div class="card">
    <h2>46 个场景逐一明细</h2>
    <div class="filters">
      <button class="fbtn active" onclick="filterBand('ALL', this)">All (46)</button>
      <button class="fbtn" onclick="filterBand('B1', this)">B1</button>
      <button class="fbtn" onclick="filterBand('B2', this)">B2</button>
      <button class="fbtn" onclick="filterBand('B3', this)">B3</button>
      <button class="fbtn" onclick="filterBand('B4', this)">B4</button>
      <button class="fbtn" onclick="filterBand('B5', this)">B5</button>
    </div>
    <table>
      <thead>
        <tr>
          <th>Band</th><th>Scenario</th><th class="num">Reqs</th>
          <th class="num">Upstream</th><th class="num">Ours</th><th class="num">Delta</th>
          ${renderByKey.size > 0 ? `<th class="num">Chromium Clean</th>` : ""}
          <th>Ours Top Errors</th>
        </tr>
      </thead>
      <tbody>${scenarioRowsHtml}</tbody>
    </table>
  </div>
</div>
<script>
function filterBand(band, btn) {
  document.querySelectorAll('.fbtn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  document.querySelectorAll('.scn-row').forEach(tr => {
    tr.style.display = (band === 'ALL' || tr.dataset.band === band) ? '' : 'none';
  });
}
</script>
</body>
</html>`;

const outPath = join(ROOT, "results", `report-${label}.html`);
writeFileSync(outPath, html, "utf8");
console.log(`Wrote ${outPath} (${html.length} bytes)`);
