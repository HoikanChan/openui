import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { Renderer } from "@cloudsop/openui-react-lang";
import type { ActionEvent, OpenUIError, ParseResult } from "@cloudsop/openui-lang-core";
import { dslLibrary } from "@cloudsop/openui-react-ui-dsl";
import "virtual:react-ui-dsl-view-styles";
import "./styles.css";

interface FixturePayload {
  id: string;
  scenario: string;
  band: string;
  repeat: number;
  dsl: string;
  dataModel: Record<string, unknown>;
}

interface BenchState {
  id: string | null;
  ready: boolean;
  loadError: string | null;
  boundaryError: string | null;
  parseMeta: ParseResult["meta"] | null;
  hasRoot: boolean;
  rendererErrors: OpenUIError[];
  actions: ActionEvent[];
  stateUpdates: Record<string, unknown>[];
}

declare global {
  interface Window {
    __BENCH_STATE: BenchState;
  }
}

window.__BENCH_STATE = {
  id: null,
  ready: false,
  loadError: null,
  boundaryError: null,
  parseMeta: null,
  hasRoot: false,
  rendererErrors: [],
  actions: [],
  stateUpdates: [],
};

class TopErrorBoundary extends React.Component<
  React.PropsWithChildren<{ id: string }>,
  { hasError: boolean; message: string }
> {
  override state = { hasError: false, message: "" };

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, message: error?.message || String(error) };
  }

  override componentDidCatch(error: Error) {
    window.__BENCH_STATE.boundaryError = error?.stack || error?.message || String(error);
  }

  override render() {
    if (this.state.hasError) {
      return (
        <div className="preview-error" data-testid="top-boundary-error">
          Preview failed: {this.state.message}
        </div>
      );
    }
    return this.props.children;
  }
}

function App() {
  const params = new URLSearchParams(window.location.search);
  const id = params.get("id");
  const [fixture, setFixture] = useState<FixturePayload | null>(null);
  const [loadErr, setLoadErr] = useState<string | null>(null);

  useEffect(() => {
    window.__BENCH_STATE.id = id;
    if (!id) {
      setLoadErr("Missing ?id= query parameter");
      window.__BENCH_STATE.loadError = "Missing ?id=";
      window.__BENCH_STATE.ready = true;
      return;
    }
    fetch(`/fixtures/${encodeURIComponent(id)}.json`)
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json() as Promise<FixturePayload>;
      })
      .then((data) => {
        setFixture(data);
      })
      .catch((err) => {
        const msg = err instanceof Error ? err.message : String(err);
        setLoadErr(msg);
        window.__BENCH_STATE.loadError = msg;
        window.__BENCH_STATE.ready = true;
      });
  }, [id]);

  if (loadErr) {
    return <div className="preview-error">Load error: {loadErr}</div>;
  }
  if (!fixture) {
    return <div data-testid="loading">Loading...</div>;
  }

  return (
    <div id="preview-shell" className="preview-shell" data-fixture-id={fixture.id}>
      <TopErrorBoundary id={fixture.id}>
        <Renderer
          library={dslLibrary}
          response={fixture.dsl}
          dataModel={fixture.dataModel}
          onParseResult={(pr) => {
            window.__BENCH_STATE.parseMeta = pr?.meta ?? null;
            window.__BENCH_STATE.hasRoot = Boolean(pr?.root);
            window.__BENCH_STATE.ready = true;
          }}
          onError={(errs) => {
            window.__BENCH_STATE.rendererErrors = errs;
          }}
          onAction={(evt) => {
            window.__BENCH_STATE.actions.push(evt);
          }}
          onStateUpdate={(st) => {
            window.__BENCH_STATE.stateUpdates.push(st);
          }}
        />
      </TopErrorBoundary>
    </div>
  );
}

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
