# OpenUI System Glossary

**DSLEngine** - Front-end rendering runtime. It receives `openui-lang` and renders UI by packaging `react-ui-dsl`, `lang-core`, and `react-lang` together.

**SmartCanvasService** - Back-end UI generation service. It receives generation requests, calls the large model, and streams generated `openui-lang` to DSLEngine.

**GenUI Service** - Reference REST implementation (in this repo) of the SmartCanvasService contract: it exposes Java Generation SDK registration and prompt assembly as service APIs and streams generated `openui-lang`.

**Java Generation SDK** - Back-end SDK that stores registered Generation Contexts and assembles model prompts for UI generation.

**Generation Context** - Registered generation contract used to guide UI generation, including component capability descriptions, tool metadata, examples, business rules, prompt rules, and data model information.

**Component Contract** - Model-visible component metadata supplied by DSLEngine or downstream extensions so SmartCanvasService can generate valid `openui-lang`.

**Extension Registration** - Downstream-provided model-visible component or tool contract added to the Generation Context before UI generation.

**Contract Name Collision** - A registration attempt that reuses an existing component or tool name in the same Generation Context.

**Context ID** - Identifier that selects one isolated Generation Context for registration and prompt assembly.

**Request Overlay** - Per-generation prompt assembly input that can add dynamic tools or extra rules without changing the registered Generation Context.

**Prompt Override** - Debug-only generation input that replaces the entire assembled prompt, bypassing the Generation Context; not for production callers.

**Contract Version** - Version identifier for the base or extension contract used to assemble a prompt.

## Relationships

- **DSLEngine** owns the base **Component Contract** for the front-end SDK components.
- **Extension Registration** extends the **Generation Context** with downstream components or tools.
- A **Context ID** isolates one business registration from another; generation for one **Context ID** does not read another context's contracts.
- Re-registering the same **Context ID** replaces that context's extension contract.
- A **Contract Name Collision** inside one **Generation Context** is rejected instead of overriding an existing contract.
- An **Extension Registration** contains only downstream extension contracts; the base DSLEngine contract is supplied separately by the Java Generation SDK.
- A **Request Overlay** applies only to one generation request and is not persisted into the selected **Generation Context**.
- A **Prompt Override** is distinct from a **Request Overlay**: an overlay augments prompt assembly, an override discards it entirely.
- A tool in a **Request Overlay** must not reuse a tool name already present in the selected **Generation Context**.
- **Java Generation SDK** supplies the base DSLEngine **Component Contract** by default; callers register only extensions.
- **Contract Version** identifies which component and tool contracts contributed to a generated prompt.
- A front-end library extension derives a new **Component Contract** instead of mutating DSLEngine's base contract.
- **Java Generation SDK** provides registration and prompt assembly capabilities that **SmartCanvasService** can expose as service APIs.
- **SmartCanvasService** assembles prompts from the **Generation Context** but does not own component implementations.
- **GenUI Service** does not persist an **Extension Registration**; callers re-register after a service restart.
- **GenUI Service** seeds preset **Generation Context**s at startup; a consumer selects one by **Context ID**.

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
