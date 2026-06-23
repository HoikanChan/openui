# OpenUI System Glossary

**DSLEngine** - Front-end rendering runtime. It receives `openui-lang` and renders UI by packaging `react-ui-dsl`, `lang-core`, and `react-lang` together.

**SmartCanvasService** - Back-end UI generation service. It receives generation requests, calls the large model, and streams generated `openui-lang` to DSLEngine.

**GenUI Service** - Reference REST implementation (in this repo) of the SmartCanvasService contract: it exposes Java Generation SDK registration and prompt assembly as service APIs and streams generated `openui-lang`.

**Java Generation SDK** - Back-end SDK that stores registered Extension Registrations by Generation ID and assembles model prompts for UI generation.

**Generation Extension** - Registered generation contract used to guide UI generation, including component capability descriptions, tool metadata, examples, business rules, prompt rules, and data model information.

**Component Contract** - Model-visible and service-validated component metadata supplied by DSLEngine or downstream extensions so SmartCanvasService can generate valid `openui-lang`; it is based on component description plus `propsSchema`, not a prompt-only signature.

**Props Schema** - OpenUI-controlled subset of JSON Schema used in a Component Contract for registration-time shape validation and post-generation prop validation.

**Extension Registration** - Downstream-provided model-visible component or tool contract registered under a Generation ID before UI generation.

**Contract Name Collision** - A registration attempt that reuses an existing component or tool name in the same Generation Extension.

**Generation ID** - Identifier that selects one isolated Generation Extension for registration and prompt assembly.

**Request Overlay** - Per-generation prompt assembly input that can add extra rules without changing the registered Generation Extension.

**Prompt Override** - Debug-only generation input that replaces the entire assembled prompt, bypassing the Generation Extension; not for production callers.

**Contract Version** - Version identifier for the base or extension contract used to assemble a prompt.

**Extension Template** - Business-authored reusable `openui-lang` template registered inside a Generation Extension and selected by `templateId`; it is distinct from generated template cache entries.

**Generated Template Cache** - SmartCanvasService-managed cache of generated and validated `openui-lang` templates, keyed from request context such as source, intent, Generation ID, and Contract Version.

**Direct Render Response** - SmartCanvasService response envelope that instructs DSLEngine to render without LLM generation, such as Extension Template, PIU render, or iframe render.

**Render Stream Payload** - JSON payload carried over SmartCanvasService SSE responses; its `type` field tells DSLEngine whether the payload is generated `openui-lang`, an Extension Template, a PIU render instruction, an iframe render instruction, completion metadata, or an error.

**Streaming IR Validation** - SmartCanvasService validation mode where generated `openui-lang` may be forwarded as arbitrary chunks, while the service buffers to line boundaries and validates each completed line against the selected Generation Extension.

## Relationships

- **DSLEngine** owns the base **Component Contract** for the front-end SDK components.
- **Extension Registration** defines one **Generation Extension** with downstream components or tools.
- A **Component Contract** must include `propsSchema` so **SmartCanvasService** can validate generated component props before streaming or caching generated `openui-lang`.
- **Extension Registration** validates basic `propsSchema` shape at registration time; generated `openui-lang` is validated against that schema after model generation.
- **Props Schema** is not full JSON Schema; SmartCanvasService supports only the agreed OpenUI subset needed for component prop validation.
- **Props Schema** property order defines the positional argument order for generated `openui-lang` component calls.
- A **Props Schema** for positional component calls must list all required properties before optional properties; registration rejects schemas with required properties after optional properties.
- A **Generation ID** isolates one business registration from another; generation for one **Generation ID** does not read another extension's contracts.
- Re-registering the same **Generation ID** replaces that extension's contract.
- A **Contract Name Collision** inside one **Generation Extension** is rejected instead of overriding an existing contract.
- An **Extension Registration** contains only downstream extension contracts; the base DSLEngine contract is supplied separately by the Java Generation SDK.
- A **Request Overlay** applies only to one generation request and is not persisted into the selected **Generation Extension**.
- A **Prompt Override** is distinct from a **Request Overlay**: an overlay augments prompt assembly, an override discards it entirely.
- **Java Generation SDK** supplies the base DSLEngine **Component Contract** by default; callers register only extensions.
- **Contract Version** identifies which component and tool contracts contributed to a generated prompt.
- A front-end library extension derives a new **Component Contract** instead of mutating DSLEngine's base contract.
- **Java Generation SDK** provides registration and prompt assembly capabilities that **SmartCanvasService** can expose as service APIs.
- **SmartCanvasService** assembles prompts from the selected **Generation Extension** but does not own component implementations.
- **GenUI Service** does not persist an **Extension Registration**; callers re-register after a service restart.
- **GenUI Service** seeds preset **Generation Extension**s at startup; a consumer selects one by **Generation ID**.
- An **Extension Template** is registered business configuration; a **Generated Template Cache** entry is runtime output and is not part of the Extension Registration contract.
- A **Direct Render Response** is selected by upstream request fields such as `templateId`, `renderPiu`, or `iframeUrl`; DSLEngine owns the final rendering behavior for each direct render mode.
- SmartCanvasService uses one SSE response channel; DSLEngine dispatches each **Render Stream Payload** by its `type` field instead of by distinct SSE event names.
- **Streaming IR Validation** does not require `ir` payload chunks to align with `openui-lang` statements; validation runs when the service observes completed line boundaries.

# Canvas Glossary

## Core Concepts

### LUI (Language UI)
The conversational dialog area where LLM responses are rendered. Default rendering target for DSL components.

### Canvas (Intelligent Canvas)
A multi-tab visualization workspace independent from LUI dialog. Supports dashboard cards, previews, and embedded HTML content.

### DashboardTab
A tab within Canvas that displays DashboardCard components in a 12-column grid layout.

### PreviewTab
A tab within Canvas that renders PreviewCard content in full-page mode. Each PreviewCard occupies its own tab.

### HTMLLoader Tab
A tab within Canvas that embeds external HTML content via iframe with bidirectional postMessage communication. Each HTMLLoader occupies its own tab. iframeId serves as the communication identifier.

## DSL Components

### DashboardCard
A DSL component that renders to DashboardTab in Canvas. Displays as a grid card with optional title and size constraints.

### PreviewCard
A DSL component that renders to a dedicated PreviewTab in Canvas. Title defines the tab name, content fills the entire tab area. HTMLLoader can be used as PreviewCard children for iframe embedding.

### HTMLLoader
A DSL component for iframe embedding with bidirectional communication. Used as PreviewCard children. Args: url (required, iframe source), iframeId (required, communication identifier), data (optional, object sent to iframe after ready signal). Communication protocol: iframe sends `{type: "openui-ready", iframeId}` when loaded → HTMLLoader sends `{type: "openui-data", iframeId, data}` → iframe can send `{type: "openui-close", iframeId}` to request tab removal.

## Rendering Targets

### LUI Target
Default rendering destination. Components without canvas-specific markers render to the LUI dialog area.

### Canvas Target
Rendering destination for DashboardCard, PreviewCard, and HTMLLoader components. These components push data to canvasStore and render in Canvas instead.

## Layout Concepts

### Grid Layout
12-column fixed grid used by DashboardTab. Cards arranged left-to-right, wrapping to next row when space insufficient.

### Full-Page Layout
Layout mode for PreviewTab where content occupies entire tab area without grid constraints.

### Grid Unit
Size measurement unit for DashboardCard. `{w: number}` represents column span (1-12). Default width is 6 columns.

## ElementNode

Parsed representation of a DSL component invocation. Contains `typeName`, `props`, `statementId`, and `partial` flag.

### CanvasItem
Union type of canvas-renderable ElementNodes: `DashboardCardNode | PreviewCardNode | HTMLLoaderNode`. Distinguished by `typeName` field.
