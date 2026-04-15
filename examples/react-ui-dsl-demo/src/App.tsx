import { Renderer } from "@openuidev/react-lang";
import { useState } from "react";
import { dslLibrary } from "./lib/placeholderLibrary";
import { useGenerate } from "./useGenerate";

export function App() {
  const { response, isStreaming, error, generate, reset } = useGenerate();
  const [prompt, setPrompt] = useState("");
  const [dataModelRaw, setDataModelRaw] = useState("{}");
  const [dataModelError, setDataModelError] = useState<string | null>(null);

  function parseDataModel(): Record<string, unknown> | undefined {
    const trimmed = dataModelRaw.trim();
    if (!trimmed || trimmed === "{}") return undefined;
    try {
      const parsed = JSON.parse(trimmed);
      setDataModelError(null);
      return parsed as Record<string, unknown>;
    } catch {
      setDataModelError("Invalid JSON");
      return undefined;
    }
  }

  const dataModel = parseDataModel();

  function handleGenerate() {
    if (!prompt.trim() || isStreaming || dataModelError) return;
    reset();
    void generate(prompt);
  }

  return (
    <div style={{ display: "flex", height: "100vh", fontFamily: "sans-serif" }}>
      {/* Left column */}
      <div style={{ flex: 1, display: "flex", flexDirection: "column", borderRight: "1px solid #ddd", overflow: "hidden" }}>
        {/* DSL code viewer — top half */}
        <div style={{ flex: 1, overflow: "auto", background: "#1e1e1e", padding: 16 }}>
          <pre style={{ margin: 0, color: "#d4d4d4", fontSize: 13, whiteSpace: "pre-wrap", wordBreak: "break-all" }}>
            {response || <span style={{ color: "#666" }}>DSL will appear here as it streams…</span>}
          </pre>
        </div>

        {/* Renderer preview — bottom half */}
        <div style={{ flex: 1, overflow: "auto", padding: 16, background: "#fff", borderTop: "1px solid #ddd" }}>
          {error && (
            <div style={{ color: "red", marginBottom: 8 }}>Error: {error}</div>
          )}
          {response ? (
            <Renderer
              response={response}
              library={dslLibrary}
              isStreaming={isStreaming}
              dataModel={dataModel}
            />
          ) : (
            <span style={{ color: "#999" }}>Preview will render here…</span>
          )}
        </div>
      </div>

      {/* Right column */}
      <div style={{ width: 320, display: "flex", flexDirection: "column", gap: 12, padding: 16, background: "#f9f9f9" }}>
        <label style={{ fontWeight: 600, fontSize: 14 }}>Prompt</label>
        <textarea
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder="Describe the UI you want to generate…"
          rows={8}
          style={{ resize: "vertical", padding: 8, fontSize: 13, borderRadius: 4, border: "1px solid #ccc" }}
        />

        <label style={{ fontWeight: 600, fontSize: 14 }}>dataModel (JSON)</label>
        <textarea
          value={dataModelRaw}
          onChange={(e) => setDataModelRaw(e.target.value)}
          placeholder="{}"
          rows={6}
          style={{
            resize: "vertical", padding: 8, fontSize: 13, borderRadius: 4,
            border: `1px solid ${dataModelError ? "red" : "#ccc"}`,
            fontFamily: "monospace",
          }}
        />
        {dataModelError && <span style={{ color: "red", fontSize: 12 }}>{dataModelError}</span>}

        <button
          onClick={handleGenerate}
          disabled={isStreaming || !prompt.trim()}
          style={{
            marginTop: "auto", padding: "10px 0", borderRadius: 4, border: "none",
            background: isStreaming ? "#aaa" : "#0070f3", color: "#fff",
            fontWeight: 600, fontSize: 14, cursor: isStreaming ? "not-allowed" : "pointer",
          }}
        >
          {isStreaming ? "Generating…" : "Generate"}
        </button>
      </div>
    </div>
  );
}
