import { createHash } from "node:crypto";

export const DEFAULT_RUBRIC = `You are a UI quality judge evaluating AI-generated declarative UI component outputs.

Score each fixture on exactly these four dimensions (0–3 integer) and an overall score (0–10 integer):

- component_fit (0–3): Is the chosen component type appropriate for the data?
  0 = Wrong component entirely (e.g. table for a single scalar metric)
  1 = Plausible but clearly suboptimal
  2 = Good match with minor issues
  3 = Perfect component choice for this data

- data_completeness (0–3): Are all important fields from the data model surfaced in the output?
  0 = Most data missing or ignored
  1 = Some data shown, significant gaps remain
  2 = Most data present, minor omissions
  3 = All important data correctly surfaced

- format_quality (0–3): Are dates, numbers, currencies, and values formatted appropriately?
  0 = Formatting errors or raw unformatted data passed through
  1 = Basic formatting with significant issues remaining
  2 = Mostly correct, minor formatting issues
  3 = Well-formatted throughout

- layout_coherence (0–3): Is the layout logically organized and visually clear?
  0 = Cluttered, confusing, or broken layout
  1 = Functional but unclear organization
  2 = Clear layout with minor issues
  3 = Well-organized and easy to scan

- overall (0–10): Holistic quality score accounting for all dimensions.

Respond with ONLY valid JSON in exactly this shape, no markdown fences:
{
  "component_fit": <integer 0-3>,
  "data_completeness": <integer 0-3>,
  "format_quality": <integer 0-3>,
  "layout_coherence": <integer 0-3>,
  "overall": <integer 0-10>,
  "feedback": "<one concise sentence describing the main quality issue or strength>"
}`;

export function buildJudgeSystemPrompt(rubricOverride?: string, evalHints?: string[]): string {
  const base = rubricOverride ?? DEFAULT_RUBRIC;
  if (!evalHints || evalHints.length === 0) return base;
  return `${base}\n\n## Case-specific hints\n${evalHints.map((h) => `- ${h}`).join("\n")}`;
}

export function hashRubric(rubric: string): string {
  return createHash("sha256").update(rubric).digest("hex").slice(0, 16);
}
