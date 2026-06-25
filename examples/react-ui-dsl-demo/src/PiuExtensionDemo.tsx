import Prel from "../../../mock/febs/prel-mock.mjs";
import { DSLRenderer, canvasStore, initDslPiu } from "@openuidev/react-ui-dsl";
import { useEffect, useMemo, useState } from "react";
import { GENERATE_URL, registerGeneration } from "./genuiService";
import {
  ALARM_EXTENSION_ID,
  DEFAULT_ALARM_DATA_MODEL,
  DEFAULT_ALARM_PROMPT,
  generationExtension,
  runtimeExtension,
} from "./piu-extension-demo/AlarmExtension";

type DemoStatus = "booting" | "ready" | "generating" | "error";

function waitForRuntimeExtension(extensionId: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const started = performance.now();
    const timeoutMs = 3000;

    const check = () => {
      const found = canvasStore
        .getSnapshot()
        .extensions.some((extension) => extension.extensionId === extensionId);

      if (found) {
        resolve();
        return;
      }

      if (performance.now() - started > timeoutMs) {
        reject(new Error(`Runtime extension ${extensionId} was not registered by Piu`));
        return;
      }

      window.setTimeout(check, 40);
    };

    check();
  });
}

async function bootPiuRuntime() {
  Prel.__reset();
  window.Prel = Prel;
  window.__OPENUI_ALARM_RUNTIME_EXTENSION__ = runtimeExtension;

  const host = Prel.start("piu-extension-demo-host", "1.0.0", [], () => {});
  host.setup({
    session: {
      value: { userId: "demo-user", tenantId: "demo-tenant" },
      publicWritable: true,
    },
    locale: {
      value: "zh-cn",
      publicWritable: true,
    },
  });

  await initDslPiu();

  Prel.define({
    "alarm-business-piu": {
      version: "1.0.0",
      js: ["/piu/alarm-business-piu.js"],
      css: [],
    },
  });

  await Prel.autoLoad("alarm-business-piu", { fresh: true });
  await waitForRuntimeExtension(ALARM_EXTENSION_ID);
}

// Boot must run exactly once. React StrictMode double-invokes effects in dev,
// and a second bootPiuRuntime() would call Prel.__reset() — wiping the
// dsl-engine piu out of the Prel registry while initDslPiu() stays cached
// and never re-registers it. The business piu's cross-piu emit then has no
// dsl-engine in the registry to dispatch to. Caching the boot promise keeps the
// dsl-engine piu registered for the lifetime of the page.
let bootPromise: Promise<void> | null = null;

function ensurePiuRuntimeBooted(): Promise<void> {
  if (!bootPromise) {
    bootPromise = bootPiuRuntime().catch((err) => {
      bootPromise = null; // allow a later mount to retry a failed boot
      throw err;
    });
  }
  return bootPromise;
}

async function generateDsl(prompt: string, dataModel: Record<string, unknown>) {
  const res = await fetch(GENERATE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      prompt,
      dataModel,
      extensionId: ALARM_EXTENSION_ID,
    }),
  });

  if (!res.ok || !res.body) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }

  return res.body.getReader();
}

export function PiuExtensionDemo() {
  const [status, setStatus] = useState<DemoStatus>("booting");
  const [error, setError] = useState<string | null>(null);
  const [prompt, setPrompt] = useState(DEFAULT_ALARM_PROMPT);
  const [dataModelRaw, setDataModelRaw] = useState(() =>
    JSON.stringify(DEFAULT_ALARM_DATA_MODEL, null, 2),
  );
  const [dsl, setDsl] = useState("");
  const [registrationSummary, setRegistrationSummary] = useState<string>("not registered");

  const dataModel = useMemo(() => {
    try {
      return JSON.parse(dataModelRaw) as Record<string, unknown>;
    } catch {
      return null;
    }
  }, [dataModelRaw]);

  useEffect(() => {
    let cancelled = false;

    async function boot() {
      try {
        await ensurePiuRuntimeBooted();
        const summary = await registerGeneration(ALARM_EXTENSION_ID, generationExtension);
        if (cancelled) return;
        setRegistrationSummary(
          `${summary.extensionId} / components: ${summary.componentCount ?? 0} / tools: ${summary.toolCount ?? 0}`,
        );
        setStatus("ready");
      } catch (err) {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : "Demo boot failed");
        setStatus("error");
      }
    }

    void boot();

    return () => {
      cancelled = true;
    };
  }, []);

  async function handleGenerate() {
    if (!dataModel || status === "generating") return;

    setStatus("generating");
    setError(null);
    setDsl("");

    try {
      const reader = await generateDsl(prompt, dataModel);
      const decoder = new TextDecoder();

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        const chunk = decoder.decode(value, { stream: true });
        setDsl((prev) => prev + chunk);
      }

      const trailing = decoder.decode();
      if (trailing) {
        setDsl((prev) => prev + trailing);
      }

      setStatus("ready");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Generate failed");
      setStatus("error");
    }
  }

  const canGenerate = status === "ready" && !!dataModel;

  return (
    <div style={{ display: "grid", gridTemplateColumns: "380px 1fr", height: "100vh", background: "#f8fafc" }}>
      <aside style={{ borderRight: "1px solid #dbe3ef", padding: 18, background: "#ffffff", overflow: "auto" }}>
        <h1 style={{ margin: "0 0 6px", fontSize: 18 }}>Piu Extension Demo</h1>
        <p style={{ margin: "0 0 16px", color: "#64748b", fontSize: 13 }}>
          GenUI Service sees the extension contract. DSLRenderer receives the runtime implementation through Piu.
        </p>

        <div style={{ marginBottom: 14, fontSize: 12, color: "#475569" }}>
          <strong>Runtime:</strong> <span data-testid="runtime-status">{status}</span>
          <br />
          <strong>Generation:</strong> <span data-testid="registration-summary">{registrationSummary}</span>
        </div>

        {error && (
          <pre data-testid="demo-error" style={{ whiteSpace: "pre-wrap", background: "#fff1f0", color: "#b42318", padding: 10, borderRadius: 6 }}>
            {error}
          </pre>
        )}

        <label style={{ display: "block", fontSize: 12, fontWeight: 700, marginBottom: 6 }}>Prompt</label>
        <textarea
          value={prompt}
          onChange={(event) => setPrompt(event.target.value)}
          rows={8}
          style={{ width: "100%", boxSizing: "border-box", marginBottom: 14, fontSize: 13, lineHeight: 1.5 }}
        />

        <label style={{ display: "block", fontSize: 12, fontWeight: 700, marginBottom: 6 }}>Data Model</label>
        <textarea
          value={dataModelRaw}
          onChange={(event) => setDataModelRaw(event.target.value)}
          rows={7}
          style={{ width: "100%", boxSizing: "border-box", marginBottom: 14, fontFamily: "monospace", fontSize: 12 }}
        />

        <button
          type="button"
          data-testid="generate-button"
          disabled={!canGenerate}
          onClick={handleGenerate}
          style={{
            width: "100%",
            border: "none",
            borderRadius: 6,
            padding: "10px 14px",
            background: canGenerate ? "#2563eb" : "#cbd5e1",
            color: "#ffffff",
            fontWeight: 700,
            cursor: canGenerate ? "pointer" : "not-allowed",
          }}
        >
          {status === "generating" ? "Generating..." : "Generate with Extension"}
        </button>
      </aside>

      <main style={{ display: "grid", gridTemplateRows: "minmax(180px, 32%) 1fr", minWidth: 0 }}>
        <section style={{ borderBottom: "1px solid #dbe3ef", background: "#0f172a", color: "#d1e7ff", overflow: "auto" }}>
          <pre data-testid="dsl-output" style={{ margin: 0, padding: 16, whiteSpace: "pre-wrap", fontSize: 12, lineHeight: 1.55 }}>
            {dsl || "// generated openui-lang will stream here"}
          </pre>
        </section>
        <section style={{ overflow: "auto", padding: 24 }}>
          {dsl ? (
            <DSLRenderer response={dsl} isStreaming={status === "generating"} dataModel={dataModel ?? undefined} />
          ) : (
            <div style={{ color: "#64748b" }}>Preview will appear after generation.</div>
          )}
        </section>
      </main>
    </div>
  );
}
