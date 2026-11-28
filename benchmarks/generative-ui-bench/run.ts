// Standalone generative-ui-bench runner supporting both upstream OpenUI (track A)
// and our Java SDK + Data Model track (openui-ours, openui-ours-nodata).
//
// Usage:
//   BENCH_API_URL=https://.../v1 BENCH_API_KEY=... BENCH_MODEL=qwen3.7-plus \
//   BENCH_LABEL=qwen37plus node run.ts openui-upstream openui-ours
//
// Env:
//   BENCH_API_URL          OpenAI-compatible base URL (or OPENUI_BENCH_API_URL / LLM_BASE_URL)
//   BENCH_API_KEY          API key (or OPENUI_BENCH_API_KEY / LLM_API_KEY)
//   BENCH_MODEL            Model id (or OPENUI_BENCH_MODEL / LLM_MODEL)
//   BENCH_LABEL            Output dir label under raw/ and results/ (required)
//   BENCH_REPEATS          Generations per brief (default 4)
//   BENCH_MAX_TOKENS       Output ceiling (default 16384)
//   BENCH_CONCURRENCY      Parallel requests (default 6)
//   BENCH_TIMEOUT_MS       Per-request timeout ms (default 240000)
//   BENCH_TEMP             Sampling temperature (default 0.7)
//   BENCH_ENABLE_THINKING  "false" | "true" | "omit" (default "false")
//   BENCH_REASONING_EFFORT Optional reasoning_effort override ("none" | "minimal" | "low" | ...)
//   BENCH_ONLY             Comma-separated brief names for targeted runs
//
// Raws are idempotent: existing non-empty files are never regenerated.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { SCENARIOS, type Brief } from "./briefs/briefs.ts";
import { loadDataModels } from "./briefs/data-models.ts";
import { systemPrompt as upstreamSystemPrompt } from "./protocols/openui-upstream/prompt.ts";
import { assemblePromptsBatch } from "./protocols/openui-ours/prompt.ts";

const __dirname = dirname(fileURLToPath(import.meta.url));

const BASE_URL = (
  process.env.BENCH_API_URL ||
  process.env.OPENUI_BENCH_API_URL ||
  process.env.LLM_BASE_URL ||
  ""
).replace(/\/+$/, "");
const API_KEY =
  process.env.BENCH_API_KEY ||
  process.env.OPENUI_BENCH_API_KEY ||
  process.env.LLM_API_KEY ||
  "";
const MODEL =
  process.env.BENCH_MODEL ||
  process.env.OPENUI_BENCH_MODEL ||
  process.env.LLM_MODEL ||
  "";
const LABEL = process.env.BENCH_LABEL || "";

if (!BASE_URL || !MODEL || !LABEL) {
  console.error("BENCH_API_URL (or OPENUI_BENCH_API_URL), BENCH_MODEL, and BENCH_LABEL are required");
  process.exit(1);
}

const API_URL = BASE_URL.endsWith("/chat/completions")
  ? BASE_URL
  : `${BASE_URL}/chat/completions`;

const MAX_TOKENS = Number(process.env.BENCH_MAX_TOKENS) || 16384;
const REPEATS = Number(process.env.BENCH_REPEATS) || 4;
const CONCURRENCY = Number(process.env.BENCH_CONCURRENCY) || 6;
const TIMEOUT_MS = Number(process.env.BENCH_TIMEOUT_MS) || 240000;
const TEMP = process.env.BENCH_TEMP !== undefined ? Number(process.env.BENCH_TEMP) : 0.7;
const ENABLE_THINKING = process.env.BENCH_ENABLE_THINKING ?? "false";
const REASONING_EFFORT = process.env.BENCH_REASONING_EFFORT;
const ONLY = process.env.BENCH_ONLY?.split(",").map((s) => s.trim()).filter(Boolean);
const BRIEFS = ONLY && ONLY.length > 0 ? SCENARIOS.filter((s) => ONLY.includes(s.name)) : SCENARIOS;

const VALID_PROTOCOLS = new Set(["openui-upstream", "openui-ours", "openui-ours-nodata"]);
const requestedProtocols = process.argv.slice(2).filter((p) => VALID_PROTOCOLS.has(p));
if (requestedProtocols.length === 0) {
  console.error(`Pass one or more protocols: ${[...VALID_PROTOCOLS].join(" ")}`);
  process.exit(1);
}

type PromptPair = { system: string; user: string };
const promptsByProto = new Map<string, Map<string, PromptPair>>();

if (requestedProtocols.includes("openui-upstream")) {
  const sys = upstreamSystemPrompt();
  const map = new Map<string, PromptPair>();
  for (const brief of BRIEFS) {
    map.set(brief.name, { system: sys, user: brief.prompt });
  }
  promptsByProto.set("openui-upstream", map);
  console.log(`openui-upstream: system prompt ${sys.length} chars (shared across ${BRIEFS.length} briefs)`);
}

if (requestedProtocols.includes("openui-ours")) {
  const dataModels = loadDataModels();
  const assembled = assemblePromptsBatch(
    BRIEFS.map((brief) => {
      const dm = dataModels[brief.name];
      if (!dm) throw new Error(`Missing dataModel for brief: ${brief.name}`);
      return { brief, dataModel: dm };
    }),
  );
  const map = new Map<string, PromptPair>();
  let totalChars = 0;
  for (const brief of BRIEFS) {
    const entry = assembled.get(brief.name)!;
    totalChars += entry.systemPrompt.length;
    map.set(brief.name, { system: entry.systemPrompt, user: entry.userPrompt });
  }
  promptsByProto.set("openui-ours", map);
  console.log(
    `openui-ours: avg system prompt ${Math.round(totalChars / BRIEFS.length)} chars across ${BRIEFS.length} briefs (with Data Model)`,
  );
}

if (requestedProtocols.includes("openui-ours-nodata")) {
  const assembled = assemblePromptsBatch(BRIEFS.map((brief) => ({ brief, dataModel: null })));
  const map = new Map<string, PromptPair>();
  for (const brief of BRIEFS) {
    const entry = assembled.get(brief.name)!;
    map.set(brief.name, { system: entry.systemPrompt, user: entry.userPrompt });
  }
  promptsByProto.set("openui-ours-nodata", map);
  const firstLen = map.get(BRIEFS[0].name)?.system.length ?? 0;
  console.log(`openui-ours-nodata: system prompt ${firstLen} chars (no Data Model)`);
}

type GenResult = {
  text: string;
  truncated: boolean;
  promptTokens: number | null;
  completionTokens: number | null;
  latencyMs: number;
};

async function generate(systemText: string, userText: string): Promise<GenResult> {
  const body: Record<string, unknown> = {
    model: MODEL,
    temperature: TEMP,
    max_tokens: MAX_TOKENS,
    messages: [
      { role: "system", content: systemText },
      { role: "user", content: userText },
    ],
  };
  if (ENABLE_THINKING === "false") {
    body.enable_thinking = false;
  } else if (ENABLE_THINKING === "true") {
    body.enable_thinking = true;
  }
  if (REASONING_EFFORT) {
    body.reasoning_effort = REASONING_EFFORT;
  }

  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (API_KEY) {
    headers.Authorization = `Bearer ${API_KEY}`;
  }

  for (let attempt = 0; ; attempt++) {
    const started = Date.now();
    let res: Response;
    try {
      res = await fetch(API_URL, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
    } catch (e) {
      if (attempt === 3) throw e;
      await new Promise((r) => setTimeout(r, 4000 * (attempt + 1)));
      continue;
    }
    const rawText = await res.text();
    let json: any;
    try {
      json = JSON.parse(rawText);
    } catch {
      if (attempt < 3) {
        await new Promise((r) => setTimeout(r, 4000 * (attempt + 1)));
        continue;
      }
      throw new Error(`Non-JSON HTTP ${res.status}: ${rawText.slice(0, 200)}`);
    }
    if (!res.ok || json?.error) {
      if (attempt < 3 && (res.status === 429 || res.status >= 500)) {
        await new Promise((r) => setTimeout(r, 6000 * (attempt + 1)));
        continue;
      }
      throw new Error(json?.error?.message || `HTTP ${res.status}: ${rawText.slice(0, 200)}`);
    }
    const choice = json.choices?.[0];
    return {
      text: choice?.message?.content ?? "",
      truncated: choice?.finish_reason === "length",
      promptTokens: json.usage?.prompt_tokens ?? null,
      completionTokens: json.usage?.completion_tokens ?? null,
      latencyMs: Date.now() - started,
    };
  }
}

const rawDir = join(__dirname, "raw", LABEL);
mkdirSync(rawDir, { recursive: true });

const truncPath = join(rawDir, "truncated.json");
const metaPath = join(rawDir, "usage.json");
const truncatedSet = new Set<string>(
  existsSync(truncPath) ? JSON.parse(readFileSync(truncPath, "utf8")) : [],
);
const usageMap: Record<
  string,
  { promptTokens: number | null; completionTokens: number | null; latencyMs: number }
> = existsSync(metaPath) ? JSON.parse(readFileSync(metaPath, "utf8")) : {};

function persistMeta() {
  writeFileSync(truncPath, JSON.stringify([...truncatedSet].sort(), null, 2));
  writeFileSync(metaPath, JSON.stringify(usageMap, null, 2));
}

type Task = { proto: string; brief: Brief; r: number };
const tasks: Task[] = [];
for (const proto of requestedProtocols) {
  for (const brief of BRIEFS) {
    for (let r = 1; r <= REPEATS; r++) {
      tasks.push({ proto, brief, r });
    }
  }
}

const total = tasks.length;
let done = 0;
let skipped = 0;
let errors = 0;

async function worker() {
  for (;;) {
    const t = tasks.shift();
    if (!t) return;
    const id = `${t.proto}__${t.brief.name}__r${t.r}`;
    const file = join(rawDir, `${id}.txt`);
    const n = ++done;
    if (existsSync(file) && readFileSync(file, "utf8").trim().length > 0) {
      skipped++;
      continue;
    }
    const pair = promptsByProto.get(t.proto)!.get(t.brief.name)!;
    try {
      const gen = await generate(pair.system, pair.user);
      writeFileSync(file, gen.text, "utf8");
      if (gen.truncated) truncatedSet.add(id);
      usageMap[id] = {
        promptTokens: gen.promptTokens,
        completionTokens: gen.completionTokens,
        latencyMs: gen.latencyMs,
      };
      persistMeta();
      console.log(
        `[${n}/${total}] ${id} ${gen.text.length}c ${gen.completionTokens ?? "?"}tok ${gen.latencyMs}ms${gen.truncated ? " (truncated)" : ""}`,
      );
    } catch (e) {
      errors++;
      console.log(`[${n}/${total}] ${id} ERROR ${(e as Error).message}`);
    }
  }
}

console.log(
  `Running ${total} tasks (protocols=${requestedProtocols.join(",")}, briefs=${BRIEFS.length}, repeats=${REPEATS}, concurrency=${CONCURRENCY}, model=${MODEL})`,
);
await Promise.all(Array.from({ length: Math.min(CONCURRENCY, tasks.length) }, worker));
persistMeta();
console.log(
  `Done: total=${total}, skipped=${skipped}, errors=${errors}. Score with: node score.ts ${LABEL}`,
);
