import React from "react";
import { createRoot } from "react-dom/client";
import { Renderer } from "@openuidev/react-lang";
import { dslLibrary } from "@openuidev/react-ui-dsl";
import "./styles.css";

type ReportEntry = {
  component: string;
  id: string;
  prompt: string;
  expectedDescription: string;
  dataModel: Record<string, unknown>;
  dsl?: string;
  status?: "passed" | "failed";
  failureReason?: string;
};

type ReportData = {
  generatedAt: string;
  summary: {
    total: number;
    passed: number;
    failed: number;
  };
  entries: ReportEntry[];
};

class PreviewErrorBoundary extends React.Component<
  React.PropsWithChildren<{ entry: ReportEntry }>,
  { hasError: boolean; message: string }
> {
  state = { hasError: false, message: "" };

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, message: error.message };
  }

  render() {
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

function PreviewCard({ entry }: { entry: ReportEntry }) {
  return (
    <article className="entry-card">
      <header className="entry-header">
        <div>
          <div className="eyebrow">{entry.component}</div>
          <h2>{entry.id}</h2>
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

  return (
    <main className="page">
      <header className="page-header">
        <div>
          <p className="eyebrow">react-ui-dsl e2e report</p>
          <h1>Fixture previews</h1>
          <p className="generated-at">Generated at {new Date(report.generatedAt).toLocaleString()}</p>
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
        </div>
      </header>

      <section className="entries">
        {report.entries.map((entry) => (
          <PreviewCard key={entry.id} entry={entry} />
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
