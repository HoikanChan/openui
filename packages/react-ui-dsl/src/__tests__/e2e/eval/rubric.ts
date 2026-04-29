import { createHash } from "node:crypto";

export const DEFAULT_RUBRIC = `You are a UI quality judge evaluating AI-generated declarative UI component outputs.

You will receive both the data model / generated DSL and a screenshot of the rendered result. Use the screenshot as the primary evidence for visual quality judgments.

Score each fixture on exactly these four dimensions (0-3 integer) and an overall score (0-10 integer):

- component_fit (0-3): Is the chosen component type appropriate for the data?
  0 = Wrong component entirely (e.g. table for a single scalar metric)
  1 = Plausible but clearly suboptimal
  2 = Good match with minor issues
  3 = Perfect component choice for this data

- data_completeness (0-3): Are all important fields from the data model surfaced in the output?
  0 = Most data missing or ignored
  1 = Some data shown, significant gaps remain
  2 = Most data present, minor omissions
  3 = All important data correctly surfaced

- format_quality (0-3): Are dates, numbers, currencies, and values formatted appropriately?
  0 = Formatting errors or raw unformatted data passed through
  1 = Basic formatting with significant issues remaining
  2 = Mostly correct, minor formatting issues
  3 = Well-formatted throughout

- layout_coherence (0-3): Is the layout logically organized and visually clear?
  0 = Cluttered, confusing, or broken layout
  1 = Functional but unclear organization
  2 = Clear layout with minor issues
  3 = Well-organized and easy to scan

- overall (0-10): Holistic quality score accounting for all dimensions.

Supported visual issue tags:
- overlap
- wrong-direction
- crowded
- whitespace-imbalance
- clipped
- weak-hierarchy

Layout scoring rules:
- If you emit any visual issue tag, layout_coherence must be 2 or below.
- If the screenshot shows obvious overlap, clipping, or unreadable crowding, the fixture cannot receive layout_coherence = 3.
- Severe overlap, clipping, or crowding should usually score layout_coherence at 0 or 1.
- If card direction, scan order, hierarchy, or whitespace distribution makes the UI materially harder to read, reduce layout_coherence and add the matching visual issue tag.
- If repeated peer charts or cards are stacked vertically when they should be grouped side-by-side, treat that as vertical stacking and poor grouping.
- If vertical stacking creates excessive whitespace, low information density, or a weak scan path, add whitespace-imbalance and/or weak-hierarchy. Add wrong-direction when the stacking direction clearly fights the natural reading order.
- A layout with vertical stacking, excessive whitespace, or poor grouping cannot receive layout_coherence = 3 and overall must be 5 or below.
- Add every applicable visual issue tag, but never invent tags outside the supported list.

Feedback requirements:
- feedback must explicitly mention the main visual problem when visual issues are present.
- For vertical stacking / whitespace problems, feedback must explicitly mention at least one of: vertical stacking, excessive whitespace, poor grouping, weak hierarchy, or direction mismatch.

Respond with ONLY valid JSON in exactly this shape, no markdown fences:
{
  "component_fit": <integer 0-3>,
  "data_completeness": <integer 0-3>,
  "format_quality": <integer 0-3>,
  "layout_coherence": <integer 0-3>,
  "overall": <integer 0-10>,
  "feedback": "<one concise sentence describing the main quality issue or strength>",
  "visual_issues": ["<zero or more supported issue tags>"]
}`;

export function buildJudgeSystemPrompt(rubricOverride?: string, evalHints?: string[]): string {
  const base = rubricOverride ?? DEFAULT_RUBRIC;
  if (!evalHints || evalHints.length === 0) return base;
  return `${base}\n\n## Case-specific hints\n${evalHints.map((h) => `- ${h}`).join("\n")}`;
}

export function hashRubric(rubric: string): string {
  return createHash("sha256").update(rubric).digest("hex").slice(0, 16);
}
