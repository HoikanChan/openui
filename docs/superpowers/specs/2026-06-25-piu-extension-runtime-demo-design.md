# Piu Extension Runtime Demo Design

## Goal

Build a compact demo that reuses `examples/react-ui-dsl-demo` and exercises the extension path in a realistic model-generation scenario:

1. A business frontend defines a domain component and tool.
2. The model-visible extension contract is registered with GenUI Service.
3. The frontend runtime implementation is registered through the existing `mock/febs/prel-mock.mjs` Piu mechanism.
4. The generated openui-lang is rendered by `DSLRenderer`, which picks up the Piu-registered component library and tool provider.

The demo should not be a process visualization. It should feel like the current generation demo, but narrower and wired through Piu.

## User Flow

Open:

```text
http://localhost:5173/?demo=piu-extension
```

The page shows a prompt/data panel and a preview panel. On startup it:

- installs `prel-mock` as `window.Prel`;
- starts the DSLEngine Piu listener through the package runtime init path;
- registers/auto-loads one business Piu through `Prel.define(...)` and `Prel.autoLoad(...)`;
- has the business Piu emit `smart-canvas:extend` with its runtime extension.

The user clicks Generate. The demo sends the prompt and extension id to GenUI Service. The service assembles the prompt with the registered extension contract, calls the model, and streams openui-lang back. The preview renders through `DSLRenderer`, not raw `Renderer`, so component/tool resolution comes from the Piu runtime store.

## Demo Domain

Use a small alarm scenario:

- Component: `AlarmSummaryCard(title, total, critical, major, minor)`
- Tool: `queryAlarmSummary({ region, window })`

The component renders a concise operational summary card. The tool returns deterministic demo data so the generated DSL can call it without relying on a real backend system.

The default prompt asks the model to generate an alarm overview UI, query alarm summary data first, and render the result with `AlarmSummaryCard`.

## Runtime Registration

The demo must use the existing Piu mock instead of directly calling `canvasStore.addExtension`.

Expected runtime sequence:

```text
PiuExtensionDemo
  -> import Prel from mock/febs/prel-mock.mjs
  -> window.Prel = Prel
  -> init DSL engine listener
       Prel.start("dsl-engine", "1.0.0", ["session", "locale"], ...)
       dsl-engine attaches "smart-canvas:extend"
  -> host Piu sets required dependency state
       session, locale
  -> Prel.define({ "alarm-business-piu": { js: [...] } })
  -> Prel.autoLoad("alarm-business-piu")
       business Piu module runs Prel.start(...)
       business Piu emits "smart-canvas:extend"
  -> dsl-engine handler stores extension in canvasStore
  -> DSLRenderer useLibrary/useToolProvider sees the extension
```

The business Piu entry should be a real ESM module, following the pattern in `mock/febs/__fixtures__/hello-piu.mjs`.

## Generation Registration

The model-visible contract and frontend runtime implementation should come from the same source file where possible.

Create:

- `runtimeExtension`: includes React component implementation and frontend `toolProvider`.
- `generationExtension`: includes `extensionId`, `version`, `components` generated from `generateComponentSpecs([AlarmSummaryCard])`, `tools`, `componentGroups`, and `additionalRules`.

The demo should register `generationExtension` with GenUI Service before generation, using the current service shape:

```text
PUT /v1/generations/{extensionId}
POST /v1/generate
```

If service registration fails because the service is not running, show a compact error in the demo. Do not silently fall back to replay for the primary Generate action.

## Files

Expected changes:

- `examples/react-ui-dsl-demo/src/piu-extension-demo/AlarmExtension.tsx`
  - defines `AlarmSummaryCard`;
  - exports `runtimeExtension`;
  - exports `generationExtension`;
  - exports the default prompt/data model.

- `examples/react-ui-dsl-demo/src/piu-extension-demo/alarm-business-piu.mjs`
  - starts the business Piu through `Prel.start`;
  - emits `smart-canvas:extend` with `runtimeExtension`.

- `examples/react-ui-dsl-demo/src/PiuExtensionDemo.tsx`
  - compact prompt/data/generation UI;
  - initializes `prel-mock` and DSLEngine runtime listener;
  - registers extension contract with GenUI Service;
  - renders generated DSL with `DSLRenderer`.

- `examples/react-ui-dsl-demo/src/genuiService.ts`
  - add `registerGeneration(extensionId, extension)` for `PUT /v1/generations/{extensionId}`.

- `examples/react-ui-dsl-demo/src/main.tsx`
  - route `?demo=piu-extension` to `PiuExtensionDemo`;
  - keep the existing demo as the default.

- `packages/react-ui-dsl/src/index.ts`
  - export `DSLRenderer` and the runtime init API if they are not already exported.

## Boundaries

Do not redesign the existing full demo UI. Do not add test files as part of this demo task. Do not replace the existing `libraryForContext` path in the current demo.

The demo may include a tiny fallback/replay button only as a developer convenience, but the main Generate button must exercise GenUI Service and `DSLRenderer`.

## Risks

- `StreamDSLContext.init()` currently references context props from outside its scope. If this blocks reuse, add a minimal exported initializer dedicated to the Piu extension listener rather than broadening the demo scope.
- The current runtime hook exists in both `context/useDslRuntime.ts` and `canvas/useDslRuntime.ts`. The demo should use the same hook path as `DSLRenderer` so it validates the actual renderer path.
- `prel-mock` waits for dependency states. The demo must set `session` and `locale` before expecting `dsl-engine` to start.

## Acceptance

The demo is acceptable when:

- opening `?demo=piu-extension` shows a compact generation UI;
- the business Piu is loaded via `prel-mock` and emits `smart-canvas:extend`;
- generated DSL renders through `DSLRenderer`;
- the rendered UI uses `AlarmSummaryCard`;
- `queryAlarmSummary` is executed from the Piu-registered frontend `toolProvider`;
- the original `examples/react-ui-dsl-demo` default route still works as before.
