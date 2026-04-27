import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import type { FailingPattern, JudgeScore } from "./types.ts";
import { getTaskBundlePath } from "./run-manifest.ts";

export interface TaskBundleInput {
  runId: string;
  overallScore: number;
  judgeScores: JudgeScore[];
  failingPatterns: FailingPattern[];
  snapshotsDir: string;
  pendingPromptCorrections?: Array<{ fixtureId?: string; text_feedback: string }>;
}

interface TargetFixture {
  fixtureId: string;
  overall: number;
  component_fit: number;
  data_completeness: number;
  format_quality: number;
  layout_coherence: number;
  feedback: string;
  screenshotPath: string | null;
  snapshotPath: string | null;
}

export function writeTaskBundle(input: TaskBundleInput): void {
  const bundleDir = getTaskBundlePath(input.runId);
  mkdirSync(resolve(bundleDir, "adapters"), { recursive: true });
  mkdirSync(resolve(bundleDir, "screenshots"), { recursive: true });
  mkdirSync(resolve(bundleDir, "fixtures"), { recursive: true });

  const targets: TargetFixture[] = input.judgeScores.map((score) => {
    const snapshotPath = resolve(input.snapshotsDir, `${score.fixtureId}.dsl`);
    const bundleFixturePath = resolve(bundleDir, "fixtures", `${score.fixtureId}.dsl`);

    if (existsSync(snapshotPath)) {
      copyFileSync(snapshotPath, bundleFixturePath);
    }

    if (score.screenshotPath && existsSync(score.screenshotPath)) {
      const dest = resolve(bundleDir, "screenshots", `${score.fixtureId}.png`);
      copyFileSync(score.screenshotPath, dest);
    }

    return {
      fixtureId: score.fixtureId,
      overall: score.overall,
      component_fit: score.component_fit,
      data_completeness: score.data_completeness,
      format_quality: score.format_quality,
      layout_coherence: score.layout_coherence,
      feedback: score.feedback,
      screenshotPath: score.screenshotPath ? `screenshots/${score.fixtureId}.png` : null,
      snapshotPath: existsSync(snapshotPath) ? `fixtures/${score.fixtureId}.dsl` : null,
    };
  });

  writeFileSync(
    resolve(bundleDir, "targets.json"),
    JSON.stringify(targets, null, 2),
    "utf-8",
  );

  writeFileSync(
    resolve(bundleDir, "failing-patterns.json"),
    JSON.stringify(input.failingPatterns, null, 2),
    "utf-8",
  );

  writeFileSync(resolve(bundleDir, "summary.md"), buildSummary(input), "utf-8");
  writeFileSync(resolve(bundleDir, "constraints.md"), buildConstraints(), "utf-8");
  writeFileSync(resolve(bundleDir, "adapters", "codex.md"), buildCodexAdapter(input.runId), "utf-8");
  writeFileSync(resolve(bundleDir, "adapters", "claude-code.md"), buildClaudeCodeAdapter(input.runId), "utf-8");
  writeFileSync(resolve(bundleDir, "adapters", "opencode.md"), buildOpenCodeAdapter(input.runId), "utf-8");
}

function buildSummary(input: TaskBundleInput): string {
  const worstFixtures = [...input.judgeScores]
    .sort((a, b) => a.overall - b.overall)
    .slice(0, 5)
    .map((s) => `- **${s.fixtureId}** overall=${s.overall}/10: ${s.feedback}`)
    .join("\n");

  const patterns = input.failingPatterns
    .map((p) => `- **${p.pattern}** (affects ${p.affected_fixtures.length} fixtures): ${p.agent_hint}`)
    .join("\n");

  const promptCorrections =
    input.pendingPromptCorrections && input.pendingPromptCorrections.length > 0
      ? [
          "",
          "## Human Feedback (Prompt Corrections)",
          ...input.pendingPromptCorrections.map(
            (c) => `- ${c.fixtureId ? `[${c.fixtureId}] ` : ""}${c.text_feedback}`,
          ),
        ].join("\n")
      : "";

  return `# Optimization Task — Run ${input.runId}

## Current Quality Score

Overall: **${input.overallScore.toFixed(1)}/10** across ${input.judgeScores.length} fixtures

## Worst Performing Fixtures

${worstFixtures}

## Identified Failing Patterns

${patterns || "No dominant patterns identified."}
${promptCorrections}

## How to Proceed

1. Review \`targets.json\` for per-fixture scores and feedback
2. Review \`failing-patterns.json\` for root cause hypotheses
3. Review DSL snapshots in \`fixtures/\` and screenshots in \`screenshots/\`
4. Edit source files in \`packages/react-ui-dsl/src/\`
5. Write your changes to \`result-bundle/\` (see constraints.md for contract details)
6. Run \`pnpm eval verify ${input.runId}\` to verify your changes
`;
}

function buildConstraints(): string {
  return `# Constraints

## What NOT to break

- Do not modify any files in \`src/__tests__/e2e/snapshots/\` (curated golden fixtures)
- Do not remove or weaken existing vitest test assertions
- Do not change the public API of exported DSL components
- Do not introduce new peer dependency requirements

## Result Bundle Contract

When you finish, write to \`result-bundle/\`:

### result-bundle/result.json
\`\`\`json
{
  "runId": "<run-id>",
  "completedAt": "<ISO timestamp>",
  "agentType": "codex|claude-code|opencode|manual",
  "notes": "<optional freeform notes>"
}
\`\`\`

### result-bundle/change-summary.md
Human-readable description of what you changed and why.

### result-bundle/touched-files.json
\`\`\`json
["packages/react-ui-dsl/src/genui-lib/dslLibrary.ts", "..."]
\`\`\`

### result-bundle/claimed-affected-fixtures.json
\`\`\`json
["table-basic", "table-sortable-date", "..."]
\`\`\`
`;
}

function buildCodexAdapter(runId: string): string {
  return `# Codex Instructions — Run ${runId}

Open this workspace in Codex and provide the following as your task prompt:

---

You are optimizing the react-ui-dsl generative UI library to improve rendering quality.

Read \`task-bundle/summary.md\` for context, \`task-bundle/targets.json\` for per-fixture scores, and \`task-bundle/failing-patterns.json\` for root cause hints.

Source files to edit are under \`packages/react-ui-dsl/src/\`.

When done, write these files to \`task-bundle/result-bundle/\`:
- \`result.json\` — machine summary (see constraints.md for schema)
- \`change-summary.md\` — what you changed and why
- \`touched-files.json\` — list of files you modified
- \`claimed-affected-fixtures.json\` — fixture IDs you believe improved
`;
}

function buildClaudeCodeAdapter(runId: string): string {
  return `# Claude Code Instructions — Run ${runId}

Run Claude Code from the repo root with this prompt:

---

Read \`packages/react-ui-dsl/src/__tests__/e2e/eval/runs/${runId}/task-bundle/summary.md\` and implement improvements to the react-ui-dsl DSL generation quality.

Focus on the failing patterns in \`failing-patterns.json\` and the per-fixture feedback in \`targets.json\`.

When complete, write result files to \`runs/${runId}/result-bundle/\` per the schema in \`task-bundle/constraints.md\`.
`;
}

function buildOpenCodeAdapter(runId: string): string {
  return `# opencode Instructions — Run ${runId}

Open opencode in this repo and use the following starter prompt:

---

Improve DSL rendering quality in react-ui-dsl. See:
- \`packages/react-ui-dsl/src/__tests__/e2e/eval/runs/${runId}/task-bundle/summary.md\`
- \`task-bundle/targets.json\` for per-fixture scores
- \`task-bundle/failing-patterns.json\` for root causes

Write your result bundle to \`runs/${runId}/result-bundle/\` per \`task-bundle/constraints.md\`.
`;
}
