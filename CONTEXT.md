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
