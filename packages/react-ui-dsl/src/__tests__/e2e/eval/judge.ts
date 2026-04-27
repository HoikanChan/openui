import type { JudgeScore } from "./types.ts";
import { buildJudgeSystemPrompt } from "./rubric.ts";
import { invokeRunner, resolveRunnerType } from "./judge-runner.ts";

interface JudgeInput {
  fixtureId: string;
  dsl: string;
  dataModel: Record<string, unknown>;
  screenshotPath: string | null;
  rubricOverride?: string;
}

interface RawJudgeResponse {
  component_fit: number;
  data_completeness: number;
  format_quality: number;
  layout_coherence: number;
  overall: number;
  feedback: string;
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

export async function judgeFixture(input: JudgeInput): Promise<JudgeScore> {
  const systemPrompt = buildJudgeSystemPrompt(input.rubricOverride);
  const userText = buildUserText(input);
  const runnerType = resolveRunnerType();

  const responseText = await invokeRunner(runnerType, {
    systemPrompt,
    userText,
    screenshotPath: input.screenshotPath,
    fixtureId: input.fixtureId,
  });

  const raw = parseJudgeResponse(responseText, input.fixtureId);

  return {
    fixtureId: input.fixtureId,
    component_fit: clamp(raw.component_fit, 0, 3),
    data_completeness: clamp(raw.data_completeness, 0, 3),
    format_quality: clamp(raw.format_quality, 0, 3),
    layout_coherence: clamp(raw.layout_coherence, 0, 3),
    overall: clamp(raw.overall, 0, 10),
    feedback: raw.feedback ?? "",
    screenshotPath: input.screenshotPath,
    degraded: input.screenshotPath === null,
  };
}

export async function judgeFixtures(
  inputs: JudgeInput[],
  onProgress?: (done: number, total: number) => void,
): Promise<JudgeScore[]> {
  const results: JudgeScore[] = [];
  for (let i = 0; i < inputs.length; i++) {
    results.push(await judgeFixture(inputs[i]!));
    onProgress?.(i + 1, inputs.length);
  }
  return results;
}
