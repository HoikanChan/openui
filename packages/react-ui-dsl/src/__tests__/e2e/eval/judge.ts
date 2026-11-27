import type { JudgeScore, VisualIssueTag } from "./types.ts";
import { VISUAL_ISSUE_TAGS } from "./types.ts";
import { buildJudgeSystemPrompt } from "./rubric.ts";
import { invokeRunner, resolveRunnerType, resolveModel } from "./judge-runner.ts";
import {
  computeJudgeCacheKey,
  readJudgeCache,
  writeJudgeCache,
} from "./judge-cache.ts";

interface JudgeInput {
  fixtureId: string;
  dsl: string;
  dataModel: Record<string, unknown>;
  screenshotPath: string | null;
  rubricOverride?: string;
  evalHints?: string[];
}

interface RawJudgeResponse {
  component_fit: number;
  data_completeness: number;
  format_quality: number;
  layout_coherence: number;
  overall: number;
  feedback: string;
  visual_issues?: unknown;
}

function buildUserText(input: JudgeInput): string {
  return [
    `Fixture ID: ${input.fixtureId}`,
    ``,
    `Data model (JSON):`,
    "```json",
    JSON.stringify(input.dataModel, null, 2),
    "```",
    ``,
    `Generated DSL:`,
    "```",
    input.dsl,
    "```",
  ].join("\n");
}

// Walk backwards through the text to find the last well-formed JSON object.
// Handles any preamble that agentic runners may emit before the JSON answer.
function extractLastJsonObject(text: string): string | null {
  for (let i = text.length - 1; i >= 0; i--) {
    if (text[i] !== "}") continue;
    let depth = 0;
    for (let j = i; j >= 0; j--) {
      if (text[j] === "}") depth++;
      if (text[j] === "{") {
        depth--;
        if (depth === 0) return text.slice(j, i + 1);
      }
    }
  }
  return null;
}

function parseJudgeResponse(content: string, fixtureId: string): RawJudgeResponse {
  const stripped = content.trim().replace(/^```json\s*/i, "").replace(/```\s*$/, "");
  try {
    return JSON.parse(stripped) as RawJudgeResponse;
  } catch {
    const json = extractLastJsonObject(stripped);
    if (json) {
      try { return JSON.parse(json) as RawJudgeResponse; } catch {}
    }
    throw new Error(
      `Judge returned unparseable response for ${fixtureId}: ${content.slice(0, 200)}`,
    );
  }
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, Math.round(value)));
}

function normalizeVisualIssues(value: unknown): VisualIssueTag[] {
  if (!Array.isArray(value)) return [];

  const allowed = new Set<string>(VISUAL_ISSUE_TAGS);
  const normalized: VisualIssueTag[] = [];
  const seen = new Set<VisualIssueTag>();

  for (const item of value) {
    if (typeof item !== "string") continue;
    const tag = item.trim().toLowerCase();
    if (!allowed.has(tag) || seen.has(tag as VisualIssueTag)) continue;
    normalized.push(tag as VisualIssueTag);
    seen.add(tag as VisualIssueTag);
  }

  return normalized;
}

export async function judgeFixture(input: JudgeInput): Promise<JudgeScore> {
  const systemPrompt = buildJudgeSystemPrompt(input.rubricOverride, input.evalHints);
  const userText = buildUserText(input);
  const runnerType = resolveRunnerType();
  const judgeModel = resolveModel(runnerType);

  // Check cache first
  const cacheKey = computeJudgeCacheKey({
    dsl: input.dsl,
    userText,
    screenshotPath: input.screenshotPath,
    rubricText: systemPrompt,
    judgeModel,
  });

  const cached = readJudgeCache(cacheKey);
  if (cached) {
    console.log(`[judge] Cache hit for ${input.fixtureId}`);
    return cached;
  }

  // Call runner if not cached
  const responseText = await invokeRunner(runnerType, {
    systemPrompt,
    userText,
    screenshotPath: input.screenshotPath,
    fixtureId: input.fixtureId,
  });

  const raw = parseJudgeResponse(responseText, input.fixtureId);

  const score: JudgeScore = {
    fixtureId: input.fixtureId,
    component_fit: clamp(raw.component_fit, 0, 3),
    data_completeness: clamp(raw.data_completeness, 0, 3),
    format_quality: clamp(raw.format_quality, 0, 3),
    layout_coherence: clamp(raw.layout_coherence, 0, 3),
    overall: clamp(raw.overall, 0, 10),
    feedback: raw.feedback ?? "",
    visual_issues: normalizeVisualIssues(raw.visual_issues),
    screenshotPath: input.screenshotPath,
    degraded: input.screenshotPath === null,
  };

  // Write to cache
  writeJudgeCache(cacheKey, score);

  return score;
}

// Bounded-concurrency pool. Default 6 keeps a benchmark of ~44 fixtures under
// the Bash 10-min ceiling (~4 min at 30s/fixture). Override with
// EVAL_JUDGE_CONCURRENCY when an upstream API rate-limits or a host is weak.
function resolveConcurrency(totalInputs: number): number {
  const raw = process.env["EVAL_JUDGE_CONCURRENCY"];
  const parsed = raw === undefined ? 6 : Number(raw);
  const safe = Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : 6;
  return Math.max(1, Math.min(safe, totalInputs));
}

export async function judgeFixtures(
  inputs: JudgeInput[],
  onProgress?: (done: number, total: number) => void,
): Promise<JudgeScore[]> {
  if (inputs.length === 0) return [];

  const concurrency = resolveConcurrency(inputs.length);
  const results: JudgeScore[] = new Array(inputs.length);
  let cursor = 0;
  let completed = 0;

  async function worker(): Promise<void> {
    while (true) {
      const i = cursor++;
      if (i >= inputs.length) return;
      results[i] = await judgeFixture(inputs[i]!);
      completed++;
      onProgress?.(completed, inputs.length);
    }
  }

  await Promise.all(Array.from({ length: concurrency }, () => worker()));
  return results;
}

/**
 * Incremental judge: yields each score as it completes via onFixtureComplete callback.
 * Use for crash recovery - write progress after each fixture.
 */
export async function judgeFixturesIncremental(
  inputs: JudgeInput[],
  onFixtureComplete: (score: JudgeScore, done: number, total: number) => Promise<void>,
  onProgress?: (done: number, total: number) => void,
): Promise<JudgeScore[]> {
  if (inputs.length === 0) return [];

  const concurrency = resolveConcurrency(inputs.length);
  const results: JudgeScore[] = new Array(inputs.length);
  let cursor = 0;
  let completed = 0;

  async function worker(): Promise<void> {
    while (true) {
      const i = cursor++;
      if (i >= inputs.length) return;
      const score = await judgeFixture(inputs[i]!);
      results[i] = score;
      completed++;
      onProgress?.(completed, inputs.length);
      // Write progress after each fixture
      await onFixtureComplete(score, completed, inputs.length);
    }
  }

  await Promise.all(Array.from({ length: concurrency }, () => worker()));
  return results;
}

export function makeFailedFixtureScore(
  fixtureId: string,
  screenshotPath: string | null,
  failureReason: string | undefined,
): JudgeScore {
  const reason = failureReason?.trim() ? failureReason.trim() : "parse or render failure";
  return {
    fixtureId,
    component_fit: 0,
    data_completeness: 0,
    format_quality: 0,
    layout_coherence: 0,
    overall: 0,
    feedback: `[benchmark gate] ${reason}`,
    visual_issues: [],
    screenshotPath,
    degraded: false,
  };
}
