# Piu Extension Runtime Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a compact `examples/react-ui-dsl-demo` route that uses `mock/febs/prel-mock.mjs` to register a Piu runtime extension and renders model-generated openui-lang through `DSLRenderer`.

**Architecture:** The demo keeps the existing full demo untouched and adds a `?demo=piu-extension` entry. GenUI Service receives a model-visible `generationExtension`; the browser loads a classic-script business Piu through `Prel.define`/`Prel.autoLoad`; that Piu emits `smart-canvas:extend` to the DSLEngine Piu socket; `DSLRenderer` consumes the registered component and tool through the runtime store.

**Tech Stack:** React 19, Vite, TypeScript, `@openuidev/react-ui-dsl`, `@openuidev/react-lang`, Zod, GenUI Service REST API, `mock/febs/prel-mock.mjs`.

---

## File Structure

- Modify `packages/react-ui-dsl/src/context/piu.ts`
  - Add the `window.Prel` type so runtime initialization is typed.

- Modify `packages/react-ui-dsl/src/context/StreamDSLContext.tsx`
  - Reuse and repair the existing `init()` Piu listener for `smart-canvas:extend`.

- Modify `packages/react-ui-dsl/src/index.ts`
  - Export `DSLRenderer` and the existing `StreamDSLContext.init` as `initDslPiu`.

- Create `examples/react-ui-dsl-demo/src/piu-extension-demo/AlarmExtension.tsx`
  - Define the business component, runtime extension, model-visible generation extension, default prompt, and demo data model.

- Create `examples/react-ui-dsl-demo/public/piu/alarm-business-piu.js`
  - Classic browser script loaded by `prel-mock.autoLoad`; starts the business Piu and emits the extension registration event.

- Modify `examples/react-ui-dsl-demo/src/genuiService.ts`
  - Add `registerGeneration`.

- Create `examples/react-ui-dsl-demo/src/PiuExtensionDemo.tsx`
  - Compact prompt/data/generation UI, Piu setup, service registration, DSL streaming, and `DSLRenderer` preview.

- Modify `examples/react-ui-dsl-demo/src/main.tsx`
  - Route `?demo=piu-extension` to the new demo; default route remains `App`.

- Modify `examples/react-ui-dsl-demo/README.md`
  - Add the new demo route and explain that it uses `prel-mock`.

---

### Task 1: Repair and Export the Existing Piu Runtime Initializer

**Files:**
- Modify: `packages/react-ui-dsl/src/context/piu.ts`
- Modify: `packages/react-ui-dsl/src/context/StreamDSLContext.tsx`
- Modify: `packages/react-ui-dsl/src/index.ts`

- [ ] **Step 1: Add a typed `Prel` shape to `piu.ts`**

In `packages/react-ui-dsl/src/context/piu.ts`, update the top-level types and global declaration to this shape:

```ts
type PiuSocket = {
  attach?: (thisObj: unknown, handlers: Record<string, unknown>) => unknown;
  emit?: (eventName: string, ...params: unknown[]) => unknown;
};

type PrelLike = {
  start: (
    name: string,
    version: string,
    dependencies: string[],
    cb: (socket: PiuSocket) => void,
  ) => PiuSocket;
};

const PIU_NAME = 'dsl-engine';
const PIU_VERSION = '1.0.0';
const PIU_DEPENDENCIES = ['session', 'locale'];

type PiuCallback = (socket: PiuSocket) => void;

declare global {
  interface Window {
    DSL_ENGINE_PIU?: PiuSocket;
    Prel: PrelLike;
  }
}
```

Keep the existing `flushCallbacks`, `initializePiu`, and `createPiu` logic unchanged.

- [ ] **Step 2: Repair `StreamDSLContext.tsx` imports and `init()`**

In `packages/react-ui-dsl/src/context/StreamDSLContext.tsx`, replace the import block with:

```tsx
// @ts-nocheck
import { createContext, useContext, type PropsWithChildren } from "react";
import { createParser } from "@openuidev/react-lang";
import { CanvasStoreProvider } from "../canvas/CanvasStoreContext";
import { canvasStore, type GenUIExtension } from "../canvas/canvasStore";
import { dslLibrary } from "../genui-lib/dslLibrary";
import { createPiu } from "./piu";
```

Then replace the existing `init()` with this idempotent implementation:

```tsx
let initPromise: Promise<void> | null = null;

export async function init() {
  if (initPromise) {
    return initPromise;
  }

  initPromise = new Promise((resolve) => {
    createPiu((piu) => {
      piu.attach(piu, {
        "smart-canvas:extend": (extension: GenUIExtension) => {
          canvasStore.addExtension(extension);
        },
        "smart-canvas:addCards": (list: unknown) => {
          const cards = list as AddCardItem[];
          cards.forEach(({ data, title, id }) => {
            canvasStore.addPreviewCard(
              { title, children: parseDslToChildren(String(data)) },
              id,
            );
          });
        },
        "smart-canvas:conversation": () => {
          // Conversation forwarding is owned by host integrations.
        },
        "smart-canvas:removeCards": (list: unknown) => {
          const ids = list as string[];
          ids.forEach((id) => {
            canvasStore.removePreviewTab(id);
          });
        },
      });
      resolve();
    });
  });

  return initPromise;
}
```

This keeps the existing `init()` API, fixes the stale imports, removes the missing `ExpandPanel` dependency, and avoids referencing `StreamDSLContext` component props from outside the component scope.

- [ ] **Step 3: Export the demo-facing APIs**

Append these exports to `packages/react-ui-dsl/src/index.ts`:

```ts
export { default as DSLRenderer } from "./DSLRenderer";
export { init as initDslPiu } from "./context/StreamDSLContext";
```

- [ ] **Step 4: Run a type-oriented smoke command**

Run:

```bash
pnpm --filter @openuidev/react-ui-dsl typecheck
```

Expected: it may surface pre-existing unrelated errors in the current Piu branch, but it must not report missing exports for `DSLRenderer` or `initDslPiu`.

- [ ] **Step 5: Commit task 1**

```bash
git add packages/react-ui-dsl/src/context/piu.ts packages/react-ui-dsl/src/context/StreamDSLContext.tsx packages/react-ui-dsl/src/index.ts
git commit -m "feat: expose piu runtime extension initializer"
```

---

### Task 2: Define the Alarm Extension and Business Piu Script

**Files:**
- Create: `examples/react-ui-dsl-demo/src/piu-extension-demo/AlarmExtension.tsx`
- Create: `examples/react-ui-dsl-demo/public/piu/alarm-business-piu.js`

- [ ] **Step 1: Create `AlarmExtension.tsx`**

Create `examples/react-ui-dsl-demo/src/piu-extension-demo/AlarmExtension.tsx`:

```tsx
import { defineComponent, generateComponentSpecs } from "@openuidev/react-ui-dsl";
import { z } from "zod";

export const ALARM_EXTENSION_ID = "piu-alarm-runtime-demo";

export const DEFAULT_ALARM_PROMPT =
  "生成一个区域告警概览 UI。先调用 queryAlarmSummary 查询华东一区最近 1 小时的告警汇总，然后用 AlarmSummaryCard 展示总数、Critical、Major、Minor 数量。只输出 openui-lang。";

export const DEFAULT_ALARM_DATA_MODEL = {
  region: "华东一区",
  window: "1h",
};

const AlarmSummaryCardSchema = z.object({
  title: z.string(),
  total: z.number(),
  critical: z.number(),
  major: z.number(),
  minor: z.number(),
});

export const AlarmSummaryCard = defineComponent({
  name: "AlarmSummaryCard",
  description:
    "告警汇总卡片，用于展示一个区域内的总告警数和 critical/major/minor 三类告警数量。",
  props: AlarmSummaryCardSchema,
  component: ({ props }) => {
    const items = [
      { label: "Critical", value: props.critical, color: "#cf1322", bg: "#fff1f0" },
      { label: "Major", value: props.major, color: "#d46b08", bg: "#fff7e6" },
      { label: "Minor", value: props.minor, color: "#ad8b00", bg: "#feffe6" },
    ];

    return (
      <section
        data-testid="alarm-summary-card"
        style={{
          width: "min(560px, 100%)",
          border: "1px solid #d9e2ec",
          borderRadius: 8,
          background: "#ffffff",
          boxShadow: "0 8px 24px rgba(15, 23, 42, 0.08)",
          padding: 18,
          fontFamily: "Inter, system-ui, sans-serif",
        }}
      >
        <div style={{ display: "flex", justifyContent: "space-between", gap: 16 }}>
          <div>
            <div style={{ fontSize: 13, color: "#5b677a", marginBottom: 4 }}>
              Alarm Overview
            </div>
            <h2 style={{ margin: 0, fontSize: 20, color: "#1f2937" }}>{props.title}</h2>
          </div>
          <div style={{ textAlign: "right" }}>
            <div style={{ fontSize: 12, color: "#6b7280" }}>Total</div>
            <strong style={{ fontSize: 34, color: "#111827", lineHeight: 1 }}>
              {props.total}
            </strong>
          </div>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 10, marginTop: 18 }}>
          {items.map((item) => (
            <div
              key={item.label}
              style={{
                borderRadius: 6,
                background: item.bg,
                padding: "10px 12px",
                border: "1px solid rgba(15, 23, 42, 0.08)",
              }}
            >
              <div style={{ color: item.color, fontSize: 12, fontWeight: 700 }}>
                {item.label}
              </div>
              <div style={{ color: item.color, fontSize: 24, fontWeight: 800 }}>
                {item.value}
              </div>
            </div>
          ))}
        </div>
      </section>
    );
  },
});

export async function queryAlarmSummary(args: Record<string, unknown>) {
  const region = typeof args.region === "string" ? args.region : DEFAULT_ALARM_DATA_MODEL.region;
  const window = typeof args.window === "string" ? args.window : DEFAULT_ALARM_DATA_MODEL.window;

  return {
    region,
    window,
    total: 42,
    critical: 7,
    major: 15,
    minor: 20,
  };
}

export const runtimeExtension = {
  extensionId: ALARM_EXTENSION_ID,
  components: [AlarmSummaryCard],
  tools: [
    {
      name: "queryAlarmSummary",
      description: "查询指定区域和时间窗口内的告警汇总数量。",
      inputSchema: {
        type: "object",
        properties: {
          region: { type: "string" },
          window: { type: "string" },
        },
        required: ["region", "window"],
      },
      annotations: { readOnlyHint: true },
    },
  ],
  toolProvider: {
    queryAlarmSummary,
  },
};

export const generationExtension = {
  extensionId: ALARM_EXTENSION_ID,
  version: "1.0.0",
  components: generateComponentSpecs([AlarmSummaryCard]),
  componentGroups: [
    {
      name: "Alarm Operations",
      components: ["AlarmSummaryCard"],
      notes: [
        "告警概览场景优先使用 AlarmSummaryCard。",
        "需要实时告警汇总时先用 Query(\"queryAlarmSummary\", ...) 获取数据。",
      ],
    },
  ],
  tools: runtimeExtension.tools,
  examples: [
    'summary = Query("queryAlarmSummary", {region: "华东一区", window: "1h"}, {total: 0, critical: 0, major: 0, minor: 0})\\nroot = AlarmSummaryCard("华东一区告警概览", summary.total, summary.critical, summary.major, summary.minor)',
  ],
  additionalRules: [
    "如果用户要求告警概览，必须先调用 queryAlarmSummary，再使用 AlarmSummaryCard 渲染结果。",
    "AlarmSummaryCard 的参数顺序是 title, total, critical, major, minor。",
  ],
};

declare global {
  interface Window {
    __OPENUI_ALARM_RUNTIME_EXTENSION__?: typeof runtimeExtension;
  }
}
```

- [ ] **Step 2: Create the browser-loaded business Piu script**

Create `examples/react-ui-dsl-demo/public/piu/alarm-business-piu.js`:

```js
(function registerAlarmBusinessPiu() {
  const Prel = window.Prel;
  if (!Prel) {
    console.error("[alarm-business-piu] window.Prel is not available");
    return;
  }

  Prel.start("alarm-business-piu", "1.0.0", ["session", "locale"], function startAlarmPiu(socket, state) {
    const extension = window.__OPENUI_ALARM_RUNTIME_EXTENSION__;
    const dslEngine = window.DSL_ENGINE_PIU;

    if (!extension) {
      console.error("[alarm-business-piu] runtime extension is not available");
      return;
    }

    if (!dslEngine || typeof dslEngine.emit !== "function") {
      console.error("[alarm-business-piu] DSL engine Piu is not ready");
      return;
    }

    dslEngine.emit("smart-canvas:extend", extension);
  });
})();
```

- [ ] **Step 3: Commit task 2**

```bash
git add examples/react-ui-dsl-demo/src/piu-extension-demo/AlarmExtension.tsx examples/react-ui-dsl-demo/public/piu/alarm-business-piu.js
git commit -m "feat: add alarm piu extension demo assets"
```

---

### Task 3: Add GenUI Service Registration Helper

**Files:**
- Modify: `examples/react-ui-dsl-demo/src/genuiService.ts`

- [ ] **Step 1: Add a registration type and function**

Append to `examples/react-ui-dsl-demo/src/genuiService.ts`:

```ts
export interface GenerationRegistrationSummary {
  extensionId: string;
  version?: string;
  componentCount?: number;
  toolCount?: number;
}

export async function registerGeneration(
  extensionId: string,
  registration: Record<string, unknown>,
): Promise<GenerationRegistrationSummary> {
  const { extensionId: _extensionId, ...body } = registration;
  const res = await fetch(`${API_BASE}/generations/${encodeURIComponent(extensionId)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }

  return (await res.json()) as GenerationRegistrationSummary;
}
```

- [ ] **Step 2: Commit task 3**

```bash
git add examples/react-ui-dsl-demo/src/genuiService.ts
git commit -m "feat: register generation extensions from demo"
```

---

### Task 4: Build the Compact Piu Extension Demo Route

**Files:**
- Create: `examples/react-ui-dsl-demo/src/PiuExtensionDemo.tsx`

- [ ] **Step 1: Create `PiuExtensionDemo.tsx`**

Create `examples/react-ui-dsl-demo/src/PiuExtensionDemo.tsx`:

```tsx
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
        await bootPiuRuntime();
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
          <strong>Runtime:</strong> {status}
          <br />
          <strong>Generation:</strong> {registrationSummary}
        </div>

        {error && (
          <pre style={{ whiteSpace: "pre-wrap", background: "#fff1f0", color: "#b42318", padding: 10, borderRadius: 6 }}>
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
          <pre style={{ margin: 0, padding: 16, whiteSpace: "pre-wrap", fontSize: 12, lineHeight: 1.55 }}>
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
```

- [ ] **Step 2: Add a local type declaration for the mock module**

Create `examples/react-ui-dsl-demo/src/prel-mock.d.ts`:

```ts
declare module "../../../mock/febs/prel-mock.mjs" {
  type Piu = {
    setup(stateMap: Record<string, { value: unknown; publicWritable?: boolean }>): Piu;
  };

  const Prel: {
    __reset(): typeof Prel;
    start(name: string, version: string, deps: string[], cb: (...args: unknown[]) => void): Piu;
    define(defs: Record<string, { version?: string; js?: string[]; css?: string[] }>): typeof Prel;
    autoLoad(piuNames: string | string[], opts?: { fresh?: boolean; baseUrl?: string }): Promise<unknown>;
  };

  export default Prel;
}
```

- [ ] **Step 3: Commit task 4**

```bash
git add examples/react-ui-dsl-demo/src/PiuExtensionDemo.tsx examples/react-ui-dsl-demo/src/prel-mock.d.ts
git commit -m "feat: add compact piu extension demo"
```

---

### Task 5: Route the New Demo and Document It

**Files:**
- Modify: `examples/react-ui-dsl-demo/src/main.tsx`
- Modify: `examples/react-ui-dsl-demo/README.md`

- [ ] **Step 1: Route by query string**

Replace `examples/react-ui-dsl-demo/src/main.tsx` with:

```tsx
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { PiuExtensionDemo } from "./PiuExtensionDemo";

const params = new URLSearchParams(window.location.search);
const Demo = params.get("demo") === "piu-extension" ? PiuExtensionDemo : App;

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Demo />
  </StrictMode>,
);
```

- [ ] **Step 2: Add README instructions**

Add this section to `examples/react-ui-dsl-demo/README.md` after the existing Dev section:

```md
## Piu Extension Runtime Demo

The compact extension demo keeps the main demo untouched and is available at:

```text
http://localhost:5173/?demo=piu-extension
```

It uses `mock/febs/prel-mock.mjs` in the browser:

- DSLEngine starts a `dsl-engine` Piu and listens for `smart-canvas:extend`.
- The demo host sets the required `session` and `locale` states.
- `Prel.autoLoad("alarm-business-piu")` loads `public/piu/alarm-business-piu.js`.
- The business Piu emits `smart-canvas:extend` with the runtime component and tool provider.
- The page registers the matching generation extension with GenUI Service and sends `extensionId` to `/v1/generate`.
- The generated DSL is rendered by `DSLRenderer`, so the custom component and tool are resolved from the Piu runtime store.
```

- [ ] **Step 3: Commit task 5**

```bash
git add examples/react-ui-dsl-demo/src/main.tsx examples/react-ui-dsl-demo/README.md
git commit -m "docs: document piu extension demo route"
```

---

### Task 6: Verify the Demo

**Files:**
- No new files.

- [ ] **Step 1: Install dependencies if needed**

Run from the repo root:

```bash
pnpm install
```

Expected: dependencies are installed without changing source files.

- [ ] **Step 2: Run package checks**

Run:

```bash
pnpm --dir examples/react-ui-dsl-demo test
pnpm --dir examples/react-ui-dsl-demo build
```

Expected: both commands pass. If existing tests mock `@openuidev/react-ui-dsl`, update the mock to include `DSLRenderer` only if the new route import causes module evaluation in tests.

- [ ] **Step 3: Run the service**

Run from the repo root:

```bash
mvn -pl examples/genui-service spring-boot:run
```

Expected: GenUI Service starts on `http://localhost:3001`.

- [ ] **Step 4: Run the Vite app**

Run:

```bash
pnpm --dir examples/react-ui-dsl-demo dev
```

Expected: Vite starts and prints a local URL, usually `http://localhost:5173`.

- [ ] **Step 5: Manual smoke check**

Open:

```text
http://localhost:5173/?demo=piu-extension
```

Expected:

- the status becomes `ready`;
- generation registration summary shows `piu-alarm-runtime-demo`;
- clicking `Generate with Extension` streams DSL;
- the preview renders `AlarmSummaryCard`;
- the card displays `42`, `7`, `15`, and `20`, proving `queryAlarmSummary` ran through the Piu-registered frontend toolProvider.

- [ ] **Step 6: Check the default demo route**

Open:

```text
http://localhost:5173/
```

Expected: the existing full demo appears unchanged.

---

## Self-Review

- Spec coverage: the plan covers the compact demo route, GenUI Service registration, `prel-mock` startup, business Piu auto-load, `smart-canvas:extend`, `DSLRenderer`, and default route preservation.
- Open-ended item scan: no task uses vague implementation instructions; each file-level change includes concrete code.
- Type consistency: the extension id is consistently `piu-alarm-runtime-demo`; the tool name is consistently `queryAlarmSummary`; the component name is consistently `AlarmSummaryCard`; generation requests use `extensionId`, not `contextId`.
