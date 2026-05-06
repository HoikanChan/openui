import React from "react";
import { createRoot } from "react-dom/client";
import { Renderer } from "@openuidev/react-lang";
import { dslLibrary } from "@openuidev/react-ui-dsl";
import "./styles.css";

type JudgeScore = {
  fixtureId: string;
  component_fit: number;
  data_completeness: number;
  format_quality: number;
  layout_coherence: number;
  overall: number;
  feedback: string;
  visual_issues?: string[];
  screenshotPath: string | null;
  degraded: boolean;
};

type FailingPattern = {
  pattern: string;
  affected_fixtures: string[];
  avg_score_impact: number;
  likely_cause: string;
  agent_hint: string;
};

type ReportEntry = {
  component: string;
  id: string;
  prompt: string;
  expectedDescription: string;
  dataModel: Record<string, unknown>;
  dsl?: string;
  status?: "passed" | "failed";
  failureReason?: string;
  judgeScore?: JudgeScore;
};

type ReportData = {
  generatedAt: string;
  model: string;
  runId?: string;
  degraded?: boolean;
  summary: {
    total: number;
    passed: number;
    failed: number;
    overallScore?: number;
  };
  entries: ReportEntry[];
  judge_scores?: JudgeScore[];
  failing_patterns?: FailingPattern[];
  delta?: { overall: number; per_fixture: Record<string, number> };
};

class PreviewErrorBoundary extends React.Component<
  React.PropsWithChildren<{ entry: ReportEntry }>,
  { hasError: boolean; message: string }
> {
  override state = { hasError: false, message: "" };

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, message: error.message };
  }

  override render() {
    if (this.state.hasError) {
      return (
        <div className="preview-error">
          Preview failed: {this.state.message || `Unable to render ${this.props.entry.id}.`}
        </div>
      );
    }

    return this.props.children;
  }
}

function readReportData(): ReportData {
  const node = document.getElementById("e2e-report-data");
  if (!(node instanceof HTMLScriptElement) || !node.textContent) {
    throw new Error("Missing report data");
  }

  return JSON.parse(node.textContent) as ReportData;
}

function formatJson(value: unknown): string {
  return JSON.stringify(value, null, 2);
}

function ScoreBar({ label, value, max }: { label: string; value: number; max: number }) {
  const pct = Math.round((value / max) * 100);
  return (
    <div className="score-bar">
      <span className="score-label">{label}</span>
      <div className="score-track">
        <div className="score-fill" style={{ width: `${pct}%` }} />
      </div>
      <span className="score-value">{value}/{max}</span>
    </div>
  );
}

function JudgeScorePanel({ score }: { score: JudgeScore }) {
  const visualIssues = score.visual_issues ?? [];

  return (
    <details className="entry-details judge-scores">
      <summary>Judge Scores (overall: {score.overall}/10{score.degraded ? " [degraded]" : ""})</summary>
      <p className="judge-feedback">{score.feedback}</p>
      {visualIssues.length > 0 ? (
        <div className="issue-list">
          {visualIssues.map((issue) => (
            <span key={issue} className="issue-chip">{issue}</span>
          ))}
        </div>
      ) : null}
      <ScoreBar label="Component fit" value={score.component_fit} max={3} />
      <ScoreBar label="Data completeness" value={score.data_completeness} max={3} />
      <ScoreBar label="Format quality" value={score.format_quality} max={3} />
      <ScoreBar label="Layout coherence" value={score.layout_coherence} max={3} />
    </details>
  );
}

function PreviewCard({ entry, delta }: { entry: ReportEntry; delta?: number }) {
  const deltaLabel =
    delta !== undefined ? (delta >= 0 ? ` (+${delta.toFixed(1)})` : ` (${delta.toFixed(1)})`) : "";

  return (
    <article className="entry-card" data-fixture-id={entry.id}>
      <header className="entry-header">
        <div>
          <div className="eyebrow">{entry.component}</div>
          <h2>
            {entry.id}
            {deltaLabel && <span className={`delta ${(delta ?? 0) >= 0 ? "delta-up" : "delta-down"}`}>{deltaLabel}</span>}
          </h2>
        </div>
        <span className={`status status-${entry.status ?? "failed"}`}>{entry.status ?? "failed"}</span>
      </header>

      <p className="prompt">{entry.prompt}</p>
      <p className="expected">{entry.expectedDescription}</p>
      {entry.failureReason ? <p className="failure">Failure: {entry.failureReason}</p> : null}

      <div className="preview-shell">
        {entry.dsl ? (
          <PreviewErrorBoundary entry={entry}>
            <Renderer library={dslLibrary} response={entry.dsl} dataModel={entry.dataModel} />
          </PreviewErrorBoundary>
        ) : (
          <div className="preview-error">No DSL was captured for this fixture.</div>
        )}
      </div>

      {entry.judgeScore && <JudgeScorePanel score={entry.judgeScore} />}

      <details className="entry-details">
        <summary>Data Model</summary>
        <pre>{formatJson(entry.dataModel)}</pre>
      </details>

      <details className="entry-details">
        <summary>DSL</summary>
        <pre>{entry.dsl ?? "No DSL captured."}</pre>
      </details>
    </article>
  );
}

function App() {
  const report = readReportData();
  const deltaMap: Record<string, number> = report.delta?.per_fixture ?? {};

  return (
    <main className="page">
      <header className="page-header">
        <div>
          <p className="eyebrow">react-ui-dsl e2e report</p>
          <h1>Fixture previews</h1>
          <p className="generated-at">Generated at {new Date(report.generatedAt).toLocaleString()}</p>
          <p className="generated-at">Model: {report.model}</p>
          {report.runId && <p className="run-id">Run: {report.runId}{report.degraded ? " [degraded]" : ""}</p>}
        </div>

        <div className="summary-grid">
          <div>
            <span>Total</span>
            <strong>{report.summary.total}</strong>
          </div>
          <div>
            <span>Passed</span>
            <strong>{report.summary.passed}</strong>
          </div>
          <div>
            <span>Failed</span>
            <strong>{report.summary.failed}</strong>
          </div>
          {report.summary.overallScore !== undefined && (
            <div>
              <span>Quality</span>
              <strong>{report.summary.overallScore.toFixed(1)}/10</strong>
            </div>
          )}
          {report.delta && (
            <div>
              <span>Delta</span>
              <strong className={report.delta.overall >= 0 ? "delta-up" : "delta-down"}>
                {report.delta.overall >= 0 ? "+" : ""}{report.delta.overall.toFixed(1)}
              </strong>
            </div>
          )}
        </div>
      </header>

      {report.failing_patterns && report.failing_patterns.length > 0 && (
        <section className="failing-patterns">
          <h2>Failing Patterns</h2>
          {report.failing_patterns.map((p) => (
            <details key={p.pattern} className="pattern-entry">
              <summary>
                {p.pattern} - {p.affected_fixtures.length} fixture(s), avg impact -{p.avg_score_impact}
              </summary>
              <p><strong>Likely cause:</strong> {p.likely_cause}</p>
              <p><strong>Agent hint:</strong> {p.agent_hint}</p>
              <p><strong>Affected:</strong> {p.affected_fixtures.join(", ")}</p>
            </details>
          ))}
        </section>
      )}

      <section className="entries">
        {report.entries.map((entry) => (
          <PreviewCard key={entry.id} entry={entry} delta={deltaMap[entry.id]} />
        ))}
      </section>
    </main>
  );
}

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
