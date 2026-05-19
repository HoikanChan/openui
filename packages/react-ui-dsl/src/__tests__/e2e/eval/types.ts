export const VISUAL_ISSUE_TAGS = [
  "overlap",
  "wrong-direction",
  "crowded",
  "whitespace-imbalance",
  "clipped",
  "weak-hierarchy",
] as const;

export type VisualIssueTag = typeof VISUAL_ISSUE_TAGS[number];

export interface JudgeScore {
  fixtureId: string;
  component_fit: number;
  data_completeness: number;
  format_quality: number;
  layout_coherence: number;
  overall: number;
  feedback: string;
  visual_issues: VisualIssueTag[];
  screenshotPath: string | null;
  degraded: boolean;
}

export interface FailingPattern {
  pattern: string;
  issue_tag?: VisualIssueTag;
  affected_fixtures: string[];
  avg_score_impact: number;
  likely_cause: string;
  agent_hint: string;
}

export type RunState =
  | "created"
  | "waiting_for_agent"
  | "verifying"
  | "verified"
  | "stalled";

export type PhaseStatus = "done" | "failed";

export interface PhaseProgress {
  regen?: PhaseStatus;
  render?: PhaseStatus;
  screenshot?: PhaseStatus;
  judge?: PhaseStatus;
}

export type VerificationOutcome = "success" | "review-needed" | "stalled";

export interface DeltaSummary {
  overall: number;
  per_fixture: Record<string, number>;
}

export interface RegressionEntry {
  fixtureId: string;
  scoreBefore: number;
  scoreAfter: number;
  delta: number;
}

export interface VerificationSummaryData {
  outcome: VerificationOutcome;
  scoreBefore: number;
  scoreAfter: number;
  delta: number;
  regressions: RegressionEntry[];
  commitRecommendation?: string;
  stallMessage?: string;
}

export interface RunManifest {
  runId: string;
  state: RunState;
  createdAt: string;
  updatedAt: string;
  regen: boolean;
  suite?: "e2e" | "fuzz" | "benchmark";
  /** React UI DSL prompt strictness used for this run. */
  strictness?: "standard" | "strict";
  reportDataPath: string;
  taskBundlePath: string;
  resultBundlePath: string;
  historyPath: string;
  degraded: boolean;
  /** Phase completion status for pipeline recovery. */
  phases?: PhaseProgress;
  verificationSummary?: VerificationSummaryData;
  /** Run-relative path to the canonical system prompt artifact (e.g. "system-prompt.txt"). */
  canonicalPromptPath?: string;
  /** SHA-256 hex hash of the canonical system prompt content. */
  canonicalPromptHash?: string;
}

export interface IterationRecord {
  timestamp: string;
  scoreBefore: number;
  scoreAfter: number;
  outcome: VerificationOutcome;
  stallCounter: number;
}

export interface EvalHistory {
  runId: string;
  iterations: IterationRecord[];
  lastKnownScore: number;
  stallCounter: number;
}

export type CorrectionState = "pending" | "applied" | "failed" | "forwarded_to_optimizer";

export type CorrectionTarget = "judge" | "prompt";

export interface CorrectionEntry {
  id: string;
  target: CorrectionTarget;
  state: CorrectionState;
  fixtureId?: string;
  score_corrections?: Partial<
    Pick<JudgeScore, "component_fit" | "data_completeness" | "format_quality" | "layout_coherence" | "overall">
  >;
  text_feedback?: string;
  rubric_hash?: string;
  createdAt: string;
  updatedAt: string;
  failureReason?: string;
}

export interface ResultBundle {
  result: ResultJson;
  changeSummary: string;
  touchedFiles: string[];
  claimedAffectedFixtures: string[];
}

export interface ResultJson {
  runId: string;
  completedAt: string;
  agentType?: string;
  notes?: string;
}
