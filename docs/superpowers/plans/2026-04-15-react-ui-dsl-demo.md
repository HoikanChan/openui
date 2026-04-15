# react-ui-dsl-demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `examples/react-ui-dsl-demo/` — a Vite + React frontend with a Node.js Express backend that takes a user prompt, calls OpenAI, streams DSL back, and renders it live using `react-lang`'s `<Renderer>`.

**Architecture:** Two-process dev setup (`concurrently`). Express server on port 3001 with CORS enabled; Vite app on port 5173 fetches the server directly. Frontend has a two-column layout: left column split between a live DSL code viewer (top) and the `<Renderer>` preview (bottom); right column has prompt input, dataModel JSON input, and a Generate button. A local placeholder library (`src/lib/placeholderLibrary.ts`) using `@openuidev/react-ui`'s `openuiLibrary` stands in for `@openuidev/react-ui-dsl` until that package is built.

**Tech Stack:** Vite 6, React 19, TypeScript, Express 5, `cors`, `openai` SDK (streaming), `@openuidev/react-lang` (Renderer), `@openuidev/react-ui` (stand-in library), `concurrently`, `tsx`.

---

## File Map

**Created:**
- `examples/react-ui-dsl-demo/package.json` — project config + scripts
- `examples/react-ui-dsl-demo/tsconfig.json` — TypeScript config (browser + node)
- `examples/react-ui-dsl-demo/vite.config.ts` — Vite React plugin config
- `examples/react-ui-dsl-demo/index.html` — Vite HTML entry
- `examples/react-ui-dsl-demo/.env.example` — documents required env vars
- `examples/react-ui-dsl-demo/server/index.ts` — Express server, POST /api/generate
- `examples/react-ui-dsl-demo/server/systemPrompt.ts` — calls `library.prompt()` to produce the system prompt
- `examples/react-ui-dsl-demo/src/lib/placeholderLibrary.ts` — stand-in dslLibrary using react-ui; swap import here when react-ui-dsl is ready
- `examples/react-ui-dsl-demo/src/useGenerate.ts` — fetch + ReadableStream hook: `{ response, isStreaming, error, generate, reset }`
- `examples/react-ui-dsl-demo/src/App.tsx` — two-column layout wiring everything together
- `examples/react-ui-dsl-demo/src/main.tsx` — React root mount

---

## Task 1: Package Scaffolding

**Files:**
- Create: `examples/react-ui-dsl-demo/package.json`
- Create: `examples/react-ui-dsl-demo/tsconfig.json`
- Create: `examples/react-ui-dsl-demo/vite.config.ts`
- Create: `examples/react-ui-dsl-demo/index.html`
- Create: `examples/react-ui-dsl-demo/.env.example`

- [ ] **Step 1: Create `package.json`**

```json
{
  "name": "react-ui-dsl-demo",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "concurrently \"tsx watch server/index.ts\" \"vite\"",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "@openuidev/react-lang": "workspace:*",
    "@openuidev/react-ui": "workspace:*",
    "react": "19.2.3",
    "react-dom": "19.2.3"
  },
  "devDependencies": {
    "@types/cors": "^2",
    "@types/express": "^5",
    "@types/node": "^20",
    "@types/react": "^19",
    "@types/react-dom": "^19",
    "@vitejs/plugin-react": "^4",
    "concurrently": "^9",
    "cors": "^2",
    "express": "^5",
    "openai": "^4",
    "tsx": "^4",
    "typescript": "^5",
    "vite": "^6"
  }
}
```

- [ ] **Step 2: Create `tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "jsx": "react-jsx",
    "strict": true,
    "skipLibCheck": true,
    "noEmit": true
  },
  "include": ["src", "server", "vite.config.ts"]
}
```

- [ ] **Step 3: Create `vite.config.ts`**

```ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
});
```

- [ ] **Step 4: Create `index.html`**

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>react-ui-dsl demo</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 5: Create `.env.example`**

```
OPENAI_API_KEY=sk-...
```

- [ ] **Step 6: Install dependencies from monorepo root**

```bash
pnpm install
```

Expected: pnpm resolves workspace deps and installs without errors.

- [ ] **Step 7: Commit**

```bash
git add examples/react-ui-dsl-demo/
git commit -m "chore(react-ui-dsl-demo): scaffold package"
```

---

## Task 2: Express Server

**Files:**
- Create: `examples/react-ui-dsl-demo/server/systemPrompt.ts`
- Create: `examples/react-ui-dsl-demo/server/index.ts`

The server calls `library.prompt()` at startup to build the system prompt once, then streams OpenAI completions as plain `text/plain` chunks for each request.

- [ ] **Step 1: Create `server/systemPrompt.ts`**

This file imports the stand-in library and generates the OpenUI system prompt. When `react-ui-dsl` is ready, only the import line changes.

```ts
import { openuiLibrary } from "@openuidev/react-ui";

// TODO: swap this import to dslLibrary from @openuidev/react-ui-dsl when available
export const systemPrompt = openuiLibrary.prompt();
```

- [ ] **Step 2: Create `server/index.ts`**

```ts
import cors from "cors";
import express from "express";
import OpenAI from "openai";
import { systemPrompt } from "./systemPrompt.js";

const app = express();
const PORT = 3001;

app.use(cors({ origin: "http://localhost:5173" }));
app.use(express.json());

const openai = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });

app.post("/api/generate", async (req, res) => {
  const { prompt } = req.body as { prompt: string };

  if (!prompt?.trim()) {
    res.status(400).json({ error: "prompt is required" });
    return;
  }

  res.setHeader("Content-Type", "text/plain; charset=utf-8");
  res.setHeader("Transfer-Encoding", "chunked");
  res.setHeader("Cache-Control", "no-cache");

  try {
    const stream = await openai.chat.completions.create({
      model: "gpt-4o",
      messages: [
        { role: "system", content: systemPrompt },
        { role: "user", content: prompt },
      ],
      stream: true,
    });

    for await (const chunk of stream) {
      const text = chunk.choices[0]?.delta?.content ?? "";
      if (text) res.write(text);
    }
  } catch (err) {
    const msg = err instanceof Error ? err.message : "OpenAI error";
    console.error("[server] OpenAI error:", msg);
    res.write(`\n\n[ERROR: ${msg}]`);
  } finally {
    res.end();
  }
});

app.listen(PORT, () => {
  console.log(`[server] listening on http://localhost:${PORT}`);
});
```

- [ ] **Step 3: Verify server starts**

Make sure `.env` exists with a real `OPENAI_API_KEY`, then:

```bash
cd examples/react-ui-dsl-demo
cp .env.example .env
# fill in OPENAI_API_KEY in .env
tsx server/index.ts
```

Expected output: `[server] listening on http://localhost:3001`

- [ ] **Step 4: Test the endpoint manually**

```bash
curl -X POST http://localhost:3001/api/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt": "show a blue button"}' \
  --no-buffer
```

Expected: DSL text streams back chunk by chunk (not all at once).

- [ ] **Step 5: Commit**

```bash
git add examples/react-ui-dsl-demo/server/
git commit -m "feat(react-ui-dsl-demo): add Express streaming server"
```

---

## Task 3: Placeholder Library

**Files:**
- Create: `examples/react-ui-dsl-demo/src/lib/placeholderLibrary.ts`

This file is the single swap point. When `@openuidev/react-ui-dsl` is ready, replace its contents with an import from that package.

- [ ] **Step 1: Create `src/lib/placeholderLibrary.ts`**

```ts
// TODO: replace with the real dslLibrary once @openuidev/react-ui-dsl is published
// import { dslLibrary } from "@openuidev/react-ui-dsl";
// export { dslLibrary };

import { openuiLibrary } from "@openuidev/react-ui";
import type { Library } from "@openuidev/react-lang";

export const dslLibrary: Library = openuiLibrary as unknown as Library;
```

- [ ] **Step 2: Commit**

```bash
git add examples/react-ui-dsl-demo/src/lib/
git commit -m "feat(react-ui-dsl-demo): add placeholder dslLibrary"
```

---

## Task 4: useGenerate Hook

**Files:**
- Create: `examples/react-ui-dsl-demo/src/useGenerate.ts`

Manages streaming fetch state. Returns `{ response, isStreaming, error, generate, reset }`.

- [ ] **Step 1: Create `src/useGenerate.ts`**

```ts
import { useCallback, useState } from "react";

export interface UseGenerateResult {
  response: string;
  isStreaming: boolean;
  error: string | null;
  generate: (prompt: string) => Promise<void>;
  reset: () => void;
}

export function useGenerate(): UseGenerateResult {
  const [response, setResponse] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = useCallback(() => {
    setResponse("");
    setIsStreaming(false);
    setError(null);
  }, []);

  const generate = useCallback(async (prompt: string) => {
    setResponse("");
    setError(null);
    setIsStreaming(true);

    try {
      const res = await fetch("http://localhost:3001/api/generate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ prompt }),
      });

      if (!res.ok || !res.body) {
        const text = await res.text();
        throw new Error(text || `HTTP ${res.status}`);
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        const chunk = decoder.decode(value, { stream: true });
        setResponse((prev) => prev + chunk);
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Request failed";
      setError(msg);
    } finally {
      setIsStreaming(false);
    }
  }, []);

  return { response, isStreaming, error, generate, reset };
}
```

- [ ] **Step 2: Commit**

```bash
git add examples/react-ui-dsl-demo/src/useGenerate.ts
git commit -m "feat(react-ui-dsl-demo): add useGenerate streaming hook"
```

---

## Task 5: App UI

**Files:**
- Create: `examples/react-ui-dsl-demo/src/main.tsx`
- Create: `examples/react-ui-dsl-demo/src/App.tsx`

Two-column layout. Left column: DSL code viewer (top half) + Renderer preview (bottom half). Right column: prompt textarea, dataModel JSON textarea, Generate button.

- [ ] **Step 1: Create `src/main.tsx`**

```tsx
import "@openuidev/react-ui/components.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
```

- [ ] **Step 2: Create `src/App.tsx`**

```tsx
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
```

- [ ] **Step 3: Start the full dev setup and verify**

```bash
cd examples/react-ui-dsl-demo
pnpm dev
```

Expected:
- Terminal shows `[server] listening on http://localhost:3001` AND Vite's local URL (e.g. `http://localhost:5173`)
- Open `http://localhost:5173` in a browser — the two-column layout renders
- Type a prompt, click Generate
- DSL streams into the left-top code viewer in real time
- Renderer preview updates live in the left-bottom panel

- [ ] **Step 4: Commit**

```bash
git add examples/react-ui-dsl-demo/src/
git commit -m "feat(react-ui-dsl-demo): add App UI and main entry"
```

---

## Swapping in the Real Library (Future Step)

When `@openuidev/react-ui-dsl` is built:

1. Add it to `package.json` dependencies: `"@openuidev/react-ui-dsl": "workspace:*"`
2. Run `pnpm install` from the monorepo root
3. Replace `src/lib/placeholderLibrary.ts` with:

```ts
import { dslLibrary } from "@openuidev/react-ui-dsl";
export { dslLibrary };
```

4. Remove `"@openuidev/react-ui"` from `package.json` if no longer needed
5. Remove the `@openuidev/react-ui/components.css` import in `main.tsx`
