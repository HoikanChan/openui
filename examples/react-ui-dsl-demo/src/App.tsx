import { Renderer, createParser } from "@openuidev/react-lang";
import { dslLibrary } from "@openuidev/react-ui-dsl";
import type { KeyboardEvent, ReactNode } from "react";
import { useEffect, useMemo, useState } from "react";
import { useGenerate } from "./useGenerate";
import { useLocalStorage } from "./useLocalStorage";

const C = {
  headerBg: "#111111",
  headerText: "#f0ece6",
  headerMuted: "#6b6560",
  codeBg: "#16213e",
  codeText: "#a8dba8",
  codeMuted: "#4a5568",
  previewBg: "#ffffff",
  sidebarBg: "#f0ece6",
  sidebarBorder: "#e0d9d0",
  panelHeader: "#f7f4f0",
  panelHeaderText: "#6b6560",
  panelBorder: "#e0d9d0",
  inputBg: "#ffffff",
  inputBorder: "#d4cdc5",
  inputBorderFocus: "#ff6b35",
  accent: "#ff6b35",
  accentHover: "#e85d2a",
  accentDisabled: "#c9c5bf",
  text: "#1a1523",
  textMuted: "#7a7270",
  errorBg: "#fff0ee",
  errorText: "#cc3300",
  errorBorder: "#ffb8a8",
  divider: "#d4cdc5",
};

const mono = "'JetBrains Mono', 'Fira Code', monospace";
const sans = "'DM Sans', system-ui, sans-serif";

function PanelLabel({ children, right }: { children: ReactNode; right?: ReactNode }) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "0 16px",
        height: 32,
        background: C.panelHeader,
        borderBottom: `1px solid ${C.panelBorder}`,
        flexShrink: 0,
      }}
    >
      <span
        style={{
          fontFamily: mono,
          fontSize: 10,
          fontWeight: 500,
          letterSpacing: "0.08em",
          textTransform: "uppercase",
          color: C.panelHeaderText,
        }}
      >
        {children}
      </span>
      {right}
    </div>
  );
}

function Cursor() {
  return (
    <span
      style={{
        display: "inline-block",
        width: 7,
        height: 14,
        background: "#a8dba8",
        marginLeft: 2,
        verticalAlign: "text-bottom",
        animation: "blink 1s step-end infinite",
      }}
    />
  );
}

function StatusDot({ streaming }: { streaming: boolean }) {
  return (
    <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
      <span
        style={{
          width: 7,
          height: 7,
          borderRadius: "50%",
          background: streaming ? "#4ade80" : "#3b3530",
          boxShadow: streaming ? "0 0 0 2px rgba(74,222,128,0.25)" : "none",
          transition: "all 0.3s",
        }}
      />
      <span style={{ fontFamily: mono, fontSize: 11, color: streaming ? "#4ade80" : C.headerMuted }}>
        {streaming ? "streaming" : "ready"}
      </span>
    </span>
  );
}

function renderLangTab(editedLang: string, isStreaming: boolean, setEditedLang: (value: string) => void) {
  if (isStreaming) {
    return (
      <pre
        style={{
          margin: 0,
          fontFamily: mono,
          fontSize: 12.5,
          lineHeight: 1.65,
          color: C.codeText,
          whiteSpace: "pre-wrap",
          wordBreak: "break-all",
        }}
      >
        {editedLang ? (
          <>
            {editedLang}
            <Cursor />
          </>
        ) : (
          <span style={{ color: C.codeMuted, fontStyle: "italic" }}>Waiting for response...</span>
        )}
      </pre>
    );
  }

  if (editedLang) {
    return (
      <textarea
        value={editedLang}
        onChange={(e) => setEditedLang(e.target.value)}
        spellCheck={false}
        style={{
          display: "block",
          width: "100%",
          height: "100%",
          margin: 0,
          padding: 0,
          border: "none",
          outline: "none",
          resize: "none",
          background: "transparent",
          fontFamily: mono,
          fontSize: 12.5,
          lineHeight: 1.65,
          color: C.codeText,
          whiteSpace: "pre",
          overflowWrap: "normal",
          boxSizing: "border-box",
        }}
      />
    );
  }

  return (
    <pre
      style={{
        margin: 0,
        fontFamily: mono,
        fontSize: 12.5,
        lineHeight: 1.65,
        color: C.codeText,
        whiteSpace: "pre-wrap",
        wordBreak: "break-all",
      }}
    >
      <span style={{ color: C.codeMuted, fontStyle: "italic" }}>// DSL will stream here</span>
    </pre>
  );
}

function renderJsonTab(parsedJson: unknown, isStreaming: boolean) {
  return (
    <pre
      style={{
        margin: 0,
        fontFamily: mono,
        fontSize: 12,
        lineHeight: 1.65,
        color: "#c9d1d9",
        whiteSpace: "pre-wrap",
        wordBreak: "break-all",
      }}
    >
      {parsedJson ? (
        JSON.stringify(parsedJson, null, 2)
      ) : (
        <span style={{ color: C.codeMuted, fontStyle: "italic" }}>
          {isStreaming ? "Parsing..." : "// Parsed JSON will appear here"}
        </span>
      )}
    </pre>
  );
}

function renderSystemPromptTab(
  systemPromptDraft: string,
  isStreaming: boolean,
  onChange: (value: string) => void,
) {
  if (isStreaming) {
    return (
      <pre
        style={{
          margin: 0,
          fontFamily: mono,
          fontSize: 12.5,
          lineHeight: 1.65,
          color: C.codeText,
          whiteSpace: "pre-wrap",
          wordBreak: "break-word",
        }}
      >
        {systemPromptDraft || (
          <span style={{ color: C.codeMuted, fontStyle: "italic" }}>// Prompt will appear here</span>
        )}
      </pre>
    );
  }

  return (
    <textarea
      aria-label="System Prompt"
      value={systemPromptDraft}
      onChange={(e) => onChange(e.target.value)}
      spellCheck={false}
      style={{
        display: "block",
        width: "100%",
        height: "100%",
        margin: 0,
        padding: 0,
        border: "none",
        outline: "none",
        resize: "none",
        background: "transparent",
        fontFamily: mono,
        fontSize: 12.5,
        lineHeight: 1.65,
        color: C.codeText,
        whiteSpace: "pre-wrap",
        wordBreak: "break-word",
        boxSizing: "border-box",
      }}
    />
  );
}

export function App() {
  const { response, isStreaming, error, lastGenerateTime, generate, reset } = useGenerate();
  const [prompt, setPrompt] = useLocalStorage("demo:prompt", "");
  const [dataModelRaw, setDataModelRaw] = useLocalStorage("demo:dataModelRaw", "{}");
  const [sourceTab, setSourceTab] = useState<"lang" | "json" | "prompt">("lang");
  const [editedLang, setEditedLang] = useState("");
  const [debouncedLang, setDebouncedLang] = useState("");
  const [systemPromptDraft, setSystemPromptDraft] = useState("");
  const [isSystemPromptDirty, setIsSystemPromptDirty] = useState(false);

  useEffect(() => {
    if (isStreaming || !response) {
      setEditedLang(response ?? "");
      setDebouncedLang(response ?? "");
    }
  }, [response, isStreaming]);

  useEffect(() => {
    if (isStreaming) return;
    const id = setTimeout(() => setDebouncedLang(editedLang), 500);
    return () => clearTimeout(id);
  }, [editedLang, isStreaming]);

  const parsedJson = useMemo(() => {
    if (!debouncedLang) return null;
    try {
      const parser = createParser(dslLibrary);
      return parser.parse(debouncedLang);
    } catch {
      return null;
    }
  }, [debouncedLang]);

  const { dataModel, dataModelError } = useMemo(() => {
    const trimmed = dataModelRaw.trim();
    if (!trimmed || trimmed === "{}") {
      return { dataModel: undefined, dataModelError: null };
    }

    try {
      return { dataModel: JSON.parse(trimmed) as Record<string, unknown>, dataModelError: null };
    } catch {
      return { dataModel: undefined, dataModelError: "Invalid JSON" };
    }
  }, [dataModelRaw]);

  const defaultSystemPrompt = useMemo(() => {
    if (!dataModel) return dslLibrary.prompt();
    return dslLibrary.prompt({ dataModel: { raw: dataModel } });
  }, [dataModel]);

  useEffect(() => {
    if (!isSystemPromptDirty) {
      setSystemPromptDraft(defaultSystemPrompt);
    }
  }, [defaultSystemPrompt, isSystemPromptDirty]);

  function handleGenerate() {
    if (!prompt.trim() || isStreaming || dataModelError) return;
    reset();
    void generate(prompt, dataModel, systemPromptDraft);
  }

  function handleKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
      handleGenerate();
    }
  }

  function handleSystemPromptChange(value: string) {
    setSystemPromptDraft(value);
    setIsSystemPromptDirty(true);
  }

  const canGenerate = !isStreaming && !!prompt.trim() && !dataModelError;

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100vh", overflow: "hidden" }}>
      <header
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          height: 44,
          paddingInline: 20,
          background: C.headerBg,
          flexShrink: 0,
          borderBottom: "1px solid #2a2520",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span
            style={{
              fontFamily: mono,
              fontSize: 13,
              fontWeight: 500,
              color: C.headerText,
              letterSpacing: "-0.01em",
            }}
          >
            react-ui-dsl
          </span>
          <span
            style={{
              fontFamily: mono,
              fontSize: 11,
              color: "#ff6b35",
              background: "rgba(255,107,53,0.12)",
              padding: "1px 7px",
              borderRadius: 4,
            }}
          >
            demo
          </span>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
          <StatusDot streaming={isStreaming} />
          {!isStreaming && lastGenerateTime !== null && (
            <span
              style={{
                fontFamily: mono,
                fontSize: 11,
                color: C.headerMuted,
              }}
            >
              {(lastGenerateTime / 1000).toFixed(1)}s
            </span>
          )}
        </div>
      </header>

      <div style={{ display: "flex", flex: 1, overflow: "hidden" }}>
        <div
          style={{
            flex: 1,
            display: "flex",
            flexDirection: "column",
            borderRight: `1px solid ${C.divider}`,
            overflow: "hidden",
          }}
        >
          <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden", minHeight: 0 }}>
            <div
              style={{
                display: "flex",
                alignItems: "stretch",
                justifyContent: "space-between",
                height: 35,
                background: C.codeBg,
                borderBottom: "1px solid #0d1829",
                flexShrink: 0,
              }}
            >
              <div style={{ display: "flex" }}>
                {(["lang", "json", "prompt"] as const).map((tab) => {
                  const label = tab === "lang" ? "open lang" : tab === "json" ? "parsed json" : "prompt";
                  const active = sourceTab === tab;

                  return (
                    <button
                      key={tab}
                      onClick={() => setSourceTab(tab)}
                      style={{
                        padding: "0 16px",
                        border: "none",
                        cursor: "pointer",
                        fontFamily: mono,
                        fontSize: 11,
                        background: active ? C.codeBg : "#111d2e",
                        color: active ? C.codeText : C.codeMuted,
                        borderRight: "1px solid #0d1829",
                        borderBottom: active ? `2px solid ${C.accent}` : "2px solid transparent",
                        borderTop: "none",
                        transition: "color 0.15s",
                      }}
                    >
                      {label}
                    </button>
                  );
                })}
              </div>
              {sourceTab === "lang" && editedLang && (
                <span
                  style={{
                    display: "flex",
                    alignItems: "center",
                    paddingRight: 14,
                    fontFamily: mono,
                    fontSize: 10,
                    color: C.codeMuted,
                  }}
                >
                  {editedLang.length} chars
                </span>
              )}
            </div>

            <div style={{ flex: 1, overflow: "auto", background: C.codeBg, padding: "14px 18px" }}>
              {sourceTab === "lang"
                ? renderLangTab(editedLang, isStreaming, setEditedLang)
                : sourceTab === "json"
                  ? renderJsonTab(parsedJson, isStreaming)
                  : renderSystemPromptTab(systemPromptDraft, isStreaming, handleSystemPromptChange)}
            </div>
          </div>

          <div style={{ height: 1, background: C.divider, flexShrink: 0 }} />

          <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden", minHeight: 0 }}>
            <PanelLabel>preview</PanelLabel>
            <div style={{ flex: 1, overflow: "auto", background: C.previewBg, padding: 20 }}>
              {error && (
                <div
                  style={{
                    marginBottom: 16,
                    padding: "10px 14px",
                    borderRadius: 6,
                    background: C.errorBg,
                    border: `1px solid ${C.errorBorder}`,
                    color: C.errorText,
                    fontFamily: mono,
                    fontSize: 12,
                    animation: "fadeIn 0.2s ease",
                  }}
                >
                  WARNING {error}
                </div>
              )}
              {debouncedLang ? (
                <div style={{ animation: "fadeIn 0.25s ease" }}>
                  <Renderer
                    response={debouncedLang}
                    library={dslLibrary}
                    isStreaming={isStreaming}
                    dataModel={dataModel}
                  />
                </div>
              ) : (
                <div
                  style={{
                    height: "100%",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    flexDirection: "column",
                    gap: 8,
                  }}
                >
                  <div
                    style={{
                      width: 40,
                      height: 40,
                      borderRadius: 10,
                      background: "#f0ece6",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      fontSize: 20,
                    }}
                  >
                    ⬡
                  </div>
                  <span style={{ color: C.textMuted, fontSize: 13, fontFamily: sans }}>
                    Rendered UI will appear here
                  </span>
                </div>
              )}
            </div>
          </div>
        </div>

        <div
          style={{
            width: 340,
            display: "flex",
            flexDirection: "column",
            background: C.sidebarBg,
            overflow: "hidden",
          }}
        >
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              padding: "16px 18px",
              borderBottom: `1px solid ${C.sidebarBorder}`,
            }}
          >
            <label
              htmlFor="prompt"
              style={{
                fontFamily: mono,
                fontSize: 10,
                fontWeight: 500,
                letterSpacing: "0.08em",
                textTransform: "uppercase",
                color: C.panelHeaderText,
                marginBottom: 10,
              }}
            >
              Prompt
            </label>
            <textarea
              id="prompt"
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Describe the UI you want to generate..."
              rows={9}
              style={{
                width: "100%",
                resize: "none",
                padding: "10px 12px",
                fontSize: 13,
                lineHeight: 1.6,
                fontFamily: sans,
                background: C.inputBg,
                color: C.text,
                border: `1px solid ${C.inputBorder}`,
                borderRadius: 8,
                transition: "border-color 0.15s",
              }}
              onFocus={(e) => {
                e.target.style.borderColor = C.inputBorderFocus;
              }}
              onBlur={(e) => {
                e.target.style.borderColor = C.inputBorder;
              }}
            />
            <p style={{ marginTop: 6, fontSize: 11, color: C.textMuted, fontFamily: sans }}>Ctrl/Cmd+Enter to generate</p>
          </div>

          <div
            style={{
              display: "flex",
              flexDirection: "column",
              padding: "16px 18px",
              borderBottom: `1px solid ${C.sidebarBorder}`,
              flex: 1,
            }}
          >
            <label
              htmlFor="dataModel"
              style={{
                fontFamily: mono,
                fontSize: 10,
                fontWeight: 500,
                letterSpacing: "0.08em",
                textTransform: "uppercase",
                color: C.panelHeaderText,
                marginBottom: 10,
              }}
            >
              Data Model
            </label>
            <textarea
              id="dataModel"
              value={dataModelRaw}
              onChange={(e) => setDataModelRaw(e.target.value)}
              placeholder="{}"
              style={{
                width: "100%",
                resize: "none",
                flex: 1,
                minHeight: 120,
                padding: "10px 12px",
                fontSize: 12.5,
                lineHeight: 1.6,
                fontFamily: mono,
                background: C.inputBg,
                color: C.text,
                border: `1px solid ${dataModelError ? C.errorBorder : C.inputBorder}`,
                borderRadius: 8,
                transition: "border-color 0.15s",
              }}
              onFocus={(e) => {
                e.target.style.borderColor = dataModelError ? C.errorBorder : C.inputBorderFocus;
              }}
              onBlur={(e) => {
                e.target.style.borderColor = dataModelError ? C.errorBorder : C.inputBorder;
              }}
            />
            {dataModelError && (
              <p style={{ marginTop: 6, fontSize: 11, color: C.errorText, fontFamily: mono }}>{dataModelError}</p>
            )}
          </div>

          <div style={{ padding: "14px 18px", flexShrink: 0 }}>
            <button
              onClick={handleGenerate}
              disabled={!canGenerate}
              style={{
                width: "100%",
                padding: "11px 0",
                borderRadius: 8,
                border: "none",
                background: canGenerate ? C.accent : C.accentDisabled,
                color: canGenerate ? "#fff" : "#a09890",
                fontFamily: sans,
                fontWeight: 600,
                fontSize: 14,
                letterSpacing: "0.01em",
                cursor: canGenerate ? "pointer" : "not-allowed",
                transition: "background 0.15s, transform 0.1s",
              }}
              onMouseEnter={(e) => {
                if (canGenerate) e.currentTarget.style.background = C.accentHover;
              }}
              onMouseLeave={(e) => {
                if (canGenerate) e.currentTarget.style.background = C.accent;
              }}
              onMouseDown={(e) => {
                if (canGenerate) e.currentTarget.style.transform = "scale(0.98)";
              }}
              onMouseUp={(e) => {
                e.currentTarget.style.transform = "scale(1)";
              }}
            >
              {isStreaming ? (
                <span style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 8 }}>
                  <span
                    style={{
                      width: 14,
                      height: 14,
                      border: "2px solid rgba(255,255,255,0.4)",
                      borderTopColor: "transparent",
                      borderRadius: "50%",
                      animation: "spin 0.7s linear infinite",
                      display: "inline-block",
                    }}
                  />
                  Generating...
                </span>
              ) : (
                "Generate UI"
              )}
            </button>
          </div>
        </div>
      </div>

      <style>{`
        @keyframes blink { 50% { opacity: 0; } }
        @keyframes spin { to { transform: rotate(360deg); } }
      `}</style>
    </div>
  );
}
