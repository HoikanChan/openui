#!/usr/bin/env node
/*
 * md-to-repair-cases.mjs — smartcanvas 失败语料 → repair-bench case 包。
 *
 * 用法(在 packages/genui-java-sdk/ 下):
 *   node scripts/md-to-repair-cases.mjs [--dataset=DIR] [--out=DIR] [--contract=FILE]
 *                                       [--env=FILE] [--model=ID] [--no-llm] [--refresh-intents]
 *
 * 输入: <dataset>/eval-failure-report/evaluations/llm-errors/*.md(空文件跳过并记录)
 * 输出: <out>/contract.json            首次生成时从 base-contract.json 拷贝;已存在则不动
 *                                    (包契约由人维护——builtin 格式等随主线单独同步)
 *       <out>/case-NN_<id>/case.json | dsl.txt [| issues.json]
 *       <out>/CONVERSION-GAPS.md       缺口全清单
 *
 * 约定:
 *  - dsl.txt 逐字落盘(不 strip,CRLF 原样)——存证优先
 *  - 渲染数据标题带（结构）→ 非真实数据,按缺失处理;`...` 省略号做最小修复并记录
 *  - md 未记 originalIntent 的案例由 LLM 反推一句中文;重跑复用已落地值(--refresh-intents 重推)
 *  - md issueCodes 非空才写 issues.json(code 必带,message 有则带)
 *  - fixtureId 跨文件去重(1b 与 3a 是同一文件的拷贝),先到先得
 *  - 案例数漂移(≠25)直接终止:语料或解析器变了,人工核对后再放行
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const MODULE_DIR = path.dirname(SCRIPT_DIR);
const REPO_ROOT = path.dirname(path.dirname(MODULE_DIR));

const EXPECTED_CASES = 25;

/** 个别案例的语料备注(人读,进 CONVERSION-GAPS.md)。 */
const CASE_NOTES = {
  'GEN-00088': 'gloden-dataset.md 有同期 queryInterfaceMetrics 真值,可作回填候选',
};

// ---------- 参数 ----------

function parseArgs(argv) {
  const opts = {
    dataset: 'C:\\workspace\\smartcanvas-dataset',
    out: path.join(MODULE_DIR, 'repair-cases'),
    contract: path.join(MODULE_DIR, 'src', 'main', 'resources', 'openui', 'base-contract.json'),
    env: path.join(REPO_ROOT, '.env'),
    model: null,
    noLlm: false,
    refreshIntents: false,
  };
  for (const arg of argv) {
    if (arg.startsWith('--dataset=')) opts.dataset = arg.slice('--dataset='.length);
    else if (arg.startsWith('--out=')) opts.out = arg.slice('--out='.length);
    else if (arg.startsWith('--contract=')) opts.contract = arg.slice('--contract='.length);
    else if (arg.startsWith('--env=')) opts.env = arg.slice('--env='.length);
    else if (arg.startsWith('--model=')) opts.model = arg.slice('--model='.length);
    else if (arg === '--no-llm') opts.noLlm = true;
    else if (arg === '--refresh-intents') opts.refreshIntents = true;
    else { console.error(`未知参数: ${arg}`); process.exit(2); }
  }
  return opts;
}

function fail(message) {
  console.error(`[md-to-repair-cases] ${message}`);
  process.exit(1);
}

// ---------- LLM 配置(与 bench BenchEnv 同语义:真实环境变量优先,.env 补缺) ----------

function readDotEnv(file) {
  const values = {};
  if (!fs.existsSync(file)) return values;
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const t = line.trim();
    if (!t || t.startsWith('#')) continue;
    const eq = t.indexOf('=');
    if (eq <= 0) continue;
    values[t.slice(0, eq).trim()] = t.slice(eq + 1).trim();
  }
  return values;
}

function resolveLlmConfig(opts) {
  const file = readDotEnv(opts.env);
  const get = (key) => {
    const envValue = process.env[key];
    if (envValue && envValue.trim()) return envValue.trim();
    return file[key] || null;
  };
  const baseUrl = get('LLM_BASE_URL');
  const apiKey = get('LLM_API_KEY');
  return {
    baseUrl,
    apiKey,
    model: opts.model || get('LLM_MODEL'),
    ready: Boolean(baseUrl && apiKey),
  };
}

async function chat(cfg, system, user) {
  const url = cfg.baseUrl.replace(/\/+$/, '') + '/chat/completions';
  let lastError;
  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${cfg.apiKey}` },
        body: JSON.stringify({
          model: cfg.model,
          temperature: 0,
          messages: [{ role: 'system', content: system }, { role: 'user', content: user }],
        }),
        signal: AbortSignal.timeout(120_000),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}: ${(await res.text()).slice(0, 300)}`);
      const data = await res.json();
      let content = data?.choices?.[0]?.message?.content;
      if (typeof content !== 'string' || !content.trim()) throw new Error('empty completion');
      content = content.trim();
      if (content.startsWith('```')) content = content.replace(/^```[^\n]*\n?/, '').replace(/```\s*$/, '');
      return content.trim();
    } catch (error) {
      lastError = error;
      await new Promise((resolve) => setTimeout(resolve, 2000 * attempt));
    }
  }
  throw lastError;
}

// ---------- md 解析 ----------

/** 按行首 `## ` 切小节(保留节内原始字节,含 CRLF)。 */
function splitSections(text) {
  const re = /^## (.*)$/gm;
  const marks = [];
  let m;
  while ((m = re.exec(text)) !== null) marks.push({ title: m[1].trim(), start: m.index, end: re.lastIndex });
  const sections = [];
  for (let i = 0; i < marks.length; i++) {
    const bodyEnd = i + 1 < marks.length ? marks[i + 1].start : text.length;
    sections.push({ title: marks[i].title, body: text.slice(marks[i].end, bodyEnd) });
  }
  return sections;
}

/** 节内按 `###`/`####` 切子块;首段(meta)是元数据列表。 */
function splitParts(body) {
  const re = /^#{3,4} (.*)$/gm;
  const marks = [];
  let m;
  while ((m = re.exec(body)) !== null) marks.push({ heading: m[1].trim(), start: m.index, end: re.lastIndex });
  const meta = marks.length ? body.slice(0, marks[0].start) : body;
  const parts = [];
  for (let i = 0; i < marks.length; i++) {
    const bodyEnd = i + 1 < marks.length ? marks[i + 1].start : body.length;
    parts.push({ heading: marks[i].heading, body: body.slice(marks[i].end, bodyEnd) });
  }
  return { meta, parts };
}

function parseMeta(meta) {
  const out = {};
  const re = /^\s*-\s*\*\*([^*]+)\*\*\s*:\s*(.*)$/gm;
  let m;
  while ((m = re.exec(meta)) !== null) out[m[1].trim()] = m[2].trim();
  for (const key of ['issueCodes', 'issueMessages']) {
    if (typeof out[key] === 'string') {
      try { out[key] = JSON.parse(out[key]); } catch { delete out[key]; }
    }
  }
  return out;
}

/** 提取全部 fence,label 为空 = DSL,label json = 数据模型;内容逐字(含 CRLF)。 */
function fences(body) {
  const out = [];
  const re = /```([^\r\n]*)\r?\n([\s\S]*?)```/g;
  let m;
  while ((m = re.exec(body)) !== null) out.push({ label: m[1].trim(), content: m[2] });
  return out;
}

/** `...` 省略号最小修复:去掉省略号及其残留逗号,让 JSON 可解析。 */
function repairEllipsis(text) {
  let out = text;
  const actions = [];
  if (/(\.\.\.|…)/.test(out)) {
    out = out
      .replace(/,?\s*(?:\.\.\.|…)\s*(?=[,\]\}])/g, '')
      .replace(/,\s*,/g, ',')
      .replace(/,\s*\]/g, ']')
      .replace(/,\s*\}/g, '}');
    actions.push('removed "..." ellipsis markers (and commas they left)');
  }
  return { text: out, actions };
}

const ID_RE = /^[A-Za-z][\w-]*(?:#\d+)?$/;

/** 一个 `## ` 小节 → 案例数组(4b 的 "GEN-00232 / GEN-00233 / GEN-00349" 一节三例);非案例小节返回 null。 */
function parseCaseSection(section, file, categoryRootCause) {
  const ids = section.title.split('/').map((s) => s.trim()).filter(Boolean);
  if (!ids.length || ids.some((id) => !ID_RE.test(id))) return null;

  const { meta, parts } = splitParts(section.body);
  const metaInfo = parseMeta(meta);
  const finalIds = ids.length === 1 && metaInfo.fixtureId ? [metaInfo.fixtureId] : ids;

  let intent = null;
  let dataModel = null;
  let dataModelStatus = 'missing'; // ok | repaired | structural | unparseable | missing
  let repairActions = [];
  const dslByLabel = new Map();
  let sharedDsl = null;

  for (const part of parts) {
    const h = part.heading;
    if (/渲染意图/.test(h)) {
      intent = part.body.replace(/```[\s\S]*?```/g, '').replace(/\r\n/g, '\n').trim() || null;
    } else if (/渲染数据/.test(h)) {
      if (/（结构）|\(结构\)/.test(h)) {
        dataModelStatus = 'structural';
      } else {
        const fence = fences(part.body).find((f) => f.label === 'json');
        if (fence) {
          try {
            dataModel = JSON.parse(fence.content);
            dataModelStatus = 'ok';
          } catch {
            const fixed = repairEllipsis(fence.content);
            try {
              dataModel = JSON.parse(fixed.text);
              dataModelStatus = 'repaired';
              repairActions = fixed.actions;
            } catch {
              dataModelStatus = 'unparseable';
              repairActions = ['JSON unparseable even after ellipsis repair'];
            }
          }
        }
      }
    } else if (/生成结果/.test(h)) {
      const fence = fences(part.body).find((f) => f.label === '');
      if (fence) {
        const label = h.match(/[—–-]\s*([A-Za-z][\w-]*(?:#\d+)?)\s*$/);
        if (label) dslByLabel.set(label[1], fence.content);
        else sharedDsl = fence.content;
      }
    }
  }

  return finalIds.map((id) => ({
    id,
    file,
    rootCause: metaInfo['根因定位'] || categoryRootCause || null,
    run: metaInfo.run || null,
    errorDetail: metaInfo.errorDetail || null,
    issueCodes: Array.isArray(metaInfo.issueCodes) ? metaInfo.issueCodes : null,
    issueMessages: Array.isArray(metaInfo.issueMessages) ? metaInfo.issueMessages : null,
    hasNoCodesRecord: !Array.isArray(metaInfo.issueCodes),
    intent,
    intentSource: intent ? 'md' : null,
    dataModel,
    dataModelStatus,
    repairActions,
    dsl: dslByLabel.get(id) ?? sharedDsl,
  }));
}

// ---------- 落盘 ----------

function dirNameFor(seq, id) {
  return `case-${String(seq).padStart(2, '0')}_${id.replace(/#/g, '-')}`;
}

function writeCase(dir, c) {
  fs.mkdirSync(dir, { recursive: true });
  const caseJson = { id: c.id };
  if (c.intent) caseJson.originalIntent = c.intent;
  if (c.dataModel != null) caseJson.dataModel = c.dataModel;
  if (c.rootCause) caseJson.rootCause = c.rootCause;
  caseJson.source = { file: c.file };
  if (c.run) caseJson.source.run = c.run;
  if (c.errorDetail) caseJson.source.errorDetail = c.errorDetail;
  fs.writeFileSync(path.join(dir, 'case.json'), JSON.stringify(caseJson, null, 2) + '\n');
  fs.writeFileSync(path.join(dir, 'dsl.txt'), c.dsl);
  const issuesPath = path.join(dir, 'issues.json');
  if (c.issueCodes && c.issueCodes.length) {
    const entries = c.issueCodes.map((code, i) => {
      const entry = { code };
      if (c.issueMessages && typeof c.issueMessages[i] === 'string') entry.message = c.issueMessages[i];
      return entry;
    });
    fs.writeFileSync(issuesPath, JSON.stringify(entries, null, 2) + '\n');
  } else if (fs.existsSync(issuesPath)) {
    fs.rmSync(issuesPath);
  }
}

function renderGaps(opts, cases, duplicates, emptyFiles, contractVersion) {
  const lines = [];
  const p = (s) => lines.push(s);
  const row = (c) => `| ${c.id} | ${c.dirName} | ${c.file} |`;

  p('# CONVERSION-GAPS — smartcanvas 语料 → repair-bench case 包');
  p('');
  p(`生成: ${new Date().toISOString()}`);
  p(`脚本: packages/genui-java-sdk/scripts/md-to-repair-cases.mjs`);
  p(`语料: ${opts.dataset}/eval-failure-report/evaluations/llm-errors/`);
  p(`契约: src/main/resources/openui/base-contract.json(contractVersion ${contractVersion})逐字节拷贝`);
  p('');

  const byStatus = (s) => cases.filter((c) => c.dataModelStatus === s);
  const backfilled = cases.filter((c) => c.intentSource === 'llm-backfill');
  const reused = cases.filter((c) => c.intentSource === 'reused');
  const backfillFailed = cases.filter((c) => !c.intent);
  const emptyCodes = cases.filter((c) => c.issueCodes && c.issueCodes.length === 0);
  const noCodesRecord = cases.filter((c) => c.hasNoCodesRecord);
  const withIssues = cases.filter((c) => c.issueCodes && c.issueCodes.length);

  p('## 总览');
  p('');
  p('| 项 | 数 |');
  p('|---|---|');
  p(`| 案例 | ${cases.length} |`);
  p(`| dataModel 完整 | ${byStatus('ok').length} |`);
  p(`| dataModel 省略号最小修复 | ${byStatus('repaired').length} |`);
  p(`| dataModel 结构性跳过(按缺失) | ${byStatus('structural').length} |`);
  p(`| dataModel 缺失(待内部回填) | ${byStatus('missing').length + byStatus('unparseable').length} |`);
  p(`| intent 来自 md 原文 | ${cases.length - backfilled.length - reused.length - backfillFailed.length} |`);
  p(`| intent LLM 反推(待核对) | ${backfilled.length + reused.length} |`);
  p(`| intent 仍缺失 | ${backfillFailed.length} |`);
  p(`| issues.json 透传 | ${withIssues.length} |`);
  p('');

  const section = (title, list, note) => {
    p(`## ${title}`);
    p('');
    if (note) { p(note); p(''); }
    if (!list.length) { p('(无)'); p(''); return; }
    p('| case | 目录 | md 来源 |');
    p('|---|---|---|');
    for (const c of list) {
      const extra = CASE_NOTES[c.id] ? ` — ${CASE_NOTES[c.id]}` : '';
      p(`| ${c.id} | ${c.dirName} | ${c.file}${extra} |`);
    }
    p('');
  };

  section('缺 dataModel — 待内部回填', [...byStatus('missing'), ...byStatus('unparseable')],
    'md 里没有渲染数据块(或不可解析),case.json 不带 dataModel:validation 时类型校验关闭,相关根因不会复现。');
  section('结构性 dataModel 跳过', byStatus('structural'),
    'md 标注（结构）,是示意不是真实数据,按缺失处理,同样待内部回填。');
  section('dataModel 省略号最小修复', byStatus('repaired'),
    '仅移除 `...` 及其残留逗号让 JSON 可解析,保留 md 中出现的行;真实行集待内部核对。');

  p('## LLM 反推 intent — 待人工核对');
  p('');
  if (!backfilled.length && !reused.length) {
    p('(无)'); p('');
  } else {
    p('| case | 目录 | 反推 intent |');
    p('|---|---|---|');
    for (const c of [...backfilled, ...reused]) p(`| ${c.id} | ${c.dirName} | ${c.intent} |`);
    p('');
  }
  if (backfillFailed.length) {
    p('## intent 仍缺失(LLM 反推失败或 --no-llm)');
    p('');
    for (const c of backfillFailed) p(`- ${c.id}(${c.dirName})`);
    p('');
  }

  section('md issueCodes 为空(parser 静默崩溃)', emptyCodes,
    '原始记录 codes 为空,未写 issues.json;T3 对比时归入"无原始记录"。');
  section('md 未记录 issueCodes', noCodesRecord,
    '小节没有 issueCodes 元数据(4b 四例),未写 issues.json;T3 对比时归入"无原始记录"。');

  p('## 空分类文件 — 语料缺口,反馈内部');
  p('');
  if (!emptyFiles.length) { p('(无)'); p(''); } else {
    for (const f of emptyFiles) p(`- ${f}`);
    p('');
  }

  p('## 跨文件重复');
  p('');
  if (!duplicates.length) { p('(无)'); } else {
    for (const d of duplicates) p(`- ${d.id}:首见 ${d.first},重复于 ${d.dup}(内容相同,收录一次)`);
  }
  p('');
  return lines.join('\n');
}

// ---------- 主流程 ----------

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  const errorsDir = path.join(opts.dataset, 'eval-failure-report', 'evaluations', 'llm-errors');
  if (!fs.existsSync(errorsDir)) fail(`语料目录不存在: ${errorsDir}`);

  const allMd = fs.readdirSync(errorsDir).filter((f) => f.endsWith('.md')).sort();
  const nonEmpty = [];
  const emptyFiles = [];
  for (const f of allMd) {
    (fs.statSync(path.join(errorsDir, f)).size === 0 ? emptyFiles : nonEmpty).push(f);
  }

  const cases = [];
  const duplicates = [];
  const seen = new Map();
  for (const file of nonEmpty) {
    const text = fs.readFileSync(path.join(errorsDir, file), 'utf8');
    const header = text.match(/^#\s.*\r?\n\r?\n>\s*根因:\s*(.*)$/m);
    const categoryRootCause = header ? header[1].trim() : null;
    for (const section of splitSections(text)) {
      const parsed = parseCaseSection(section, file, categoryRootCause);
      if (!parsed) continue;
      for (const c of parsed) {
        if (seen.has(c.id)) { duplicates.push({ id: c.id, first: seen.get(c.id), dup: file }); continue; }
        if (c.dsl == null) fail(`案例 ${c.id}(${file})没有可提取的 DSL`);
        seen.set(c.id, file);
        cases.push(c);
      }
    }
  }
  if (cases.length !== EXPECTED_CASES) {
    fail(`解析到 ${cases.length} 个案例,预期 ${EXPECTED_CASES} —— 语料或解析器漂移,先人工核对`);
  }
  cases.forEach((c, i) => { c.seq = i + 1; c.dirName = dirNameFor(i + 1, c.id); });

  // intent 反推:已有 case.json 里的值优先复用(重跑稳定 + 不覆盖人工核对结果)
  const llm = resolveLlmConfig(opts);
  const needBackfill = cases.filter((c) => !c.intent);
  if (!needBackfill.length) {
    // nothing
  } else if (opts.noLlm || !llm.ready) {
    if (!opts.noLlm) console.error(`[md-to-repair-cases] LLM 配置不完整(${opts.env} 与环境变量都没有),跳过 intent 反推`);
    for (const c of needBackfill) console.error(`  intent 缺失: ${c.id}`);
  } else {
    const systemPrompt = '你是数据标注员。给你一段 GenUI DSL(用代码描述的页面),请推断当初让 LLM 生成这个页面的用户原始需求,用一句中文写出来(想查询什么数据、大概怎么展示)。只输出这一句话,不要任何前后缀。';
    for (const c of needBackfill) {
      const existingPath = path.join(opts.out, c.dirName, 'case.json');
      let existing = null;
      if (!opts.refreshIntents && fs.existsSync(existingPath)) {
        try { existing = JSON.parse(fs.readFileSync(existingPath, 'utf8')).originalIntent || null; } catch { existing = null; }
      }
      if (existing) {
        c.intent = existing;
        c.intentSource = 'reused';
        console.log(`intent 复用已落地值: ${c.id}`);
        continue;
      }
      try {
        c.intent = await chat(llm, systemPrompt, c.dsl);
        c.intentSource = 'llm-backfill';
        console.log(`intent 反推完成: ${c.id}`);
      } catch (error) {
        console.error(`intent 反推失败: ${c.id} — ${error.message}`);
      }
    }
  }

  // 写包（始终从权威 base-contract 同步 contract.json）
  fs.mkdirSync(opts.out, { recursive: true });
  const packContract = path.join(opts.out, 'contract.json');
  fs.copyFileSync(opts.contract, packContract);
  console.log(`已同步包契约: ${packContract} <- ${opts.contract}`);
  const contractVersion = JSON.parse(fs.readFileSync(packContract, 'utf8')).contractVersion;
  const written = new Set();
  for (const c of cases) {
    writeCase(path.join(opts.out, c.dirName), c);
    written.add(c.dirName);
  }
  // 清掉本脚本旧产物中已不在本次集合里的案例目录
  for (const entry of fs.readdirSync(opts.out, { withFileTypes: true })) {
    if (entry.isDirectory() && fs.existsSync(path.join(opts.out, entry.name, 'case.json')) && !written.has(entry.name)) {
      fs.rmSync(path.join(opts.out, entry.name), { recursive: true });
      console.log(`移除过期案例目录: ${entry.name}`);
    }
  }
  fs.writeFileSync(path.join(opts.out, 'CONVERSION-GAPS.md'), renderGaps(opts, cases, duplicates, emptyFiles, contractVersion));

  // 汇总
  const stat = (s) => cases.filter((c) => c.dataModelStatus === s).length;
  console.log('');
  console.log(`案例: ${cases.length} | dataModel ok=${stat('ok')} repaired=${stat('repaired')} structural=${stat('structural')} missing=${stat('missing') + stat('unparseable')}`);
  console.log(`issues.json: ${cases.filter((c) => c.issueCodes && c.issueCodes.length).length} | intent md=${cases.filter((c) => c.intentSource === 'md').length} 反推=${cases.filter((c) => c.intentSource === 'llm-backfill' || c.intentSource === 'reused').length} 缺=${cases.filter((c) => !c.intent).length}`);
  console.log(`输出: ${opts.out}`);
}

main().catch((error) => fail(error.stack || error.message));
