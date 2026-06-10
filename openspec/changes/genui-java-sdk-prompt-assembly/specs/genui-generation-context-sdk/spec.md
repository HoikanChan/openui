## ADDED Requirements

### Requirement: Java SDK shall register GenUI context extensions
Java Generation SDK SHALL provide `register(GenUIContextExtension)` to store one extension contract for the Generation Context identified by `GenUIContextExtension.contextId`.

#### Scenario: Register extension for context
- **WHEN** a caller registers a `GenUIContextExtension` whose `contextId = "ctxA"`
- **THEN** the SDK stores the extension under `ctxA`
- **AND** future prompt assembly for `ctxA` can use the registered extension

#### Scenario: Re-register replaces same context extension
- **WHEN** a caller registers extension version `v1` for `ctxA`
- **AND** later registers extension version `v2` for `ctxA`
- **THEN** the SDK replaces the `ctxA` extension with version `v2`
- **AND** version `v1` no longer contributes to prompt assembly for `ctxA`

#### Scenario: Contexts are isolated
- **WHEN** extensions are registered for `ctxA` and `ctxB`
- **AND** a caller assembles a prompt for `ctxA`
- **THEN** the assembled prompt MUST NOT include components, tools, examples, or rules registered only under `ctxB`

### Requirement: GenUIContextExtension shall contain only extension contracts
`GenUIContextExtension` SHALL describe only downstream extensions and SHALL NOT include the DSLEngine base component contract.

#### Scenario: Extension registers additional components
- **WHEN** an extension includes component prompt specs for `BizCard` and `BizMetric`
- **THEN** those components are treated as extensions to the base DSLEngine contract
- **AND** the extension payload is not required to include base components such as `Stack`

#### Scenario: Extension carries model-visible generation data
- **WHEN** an extension includes `components`, `componentGroups`, `tools`, `examples`, and `additionalRules`
- **THEN** the SDK can add those values to the selected Generation Context
- **AND** the SDK does not require renderer implementations in the extension payload

### Requirement: Java SDK shall assemble prompts from base context, registered extension, and request overlay
Java Generation SDK SHALL provide `assemblePrompt(GenUIPromptRequest)` that builds a system prompt from the built-in DSLEngine base contract, the registered extension selected by `GenUIPromptRequest.contextId`, and per-request overlay fields.

#### Scenario: Assemble prompt with registered extension
- **WHEN** `ctxA` has a registered extension with component `BizCard`
- **AND** a caller assembles a prompt for `ctxA`
- **THEN** the prompt includes base DSLEngine component signatures
- **AND** the prompt includes the `BizCard` component signature

#### Scenario: Assemble prompt with no registered extension
- **WHEN** no extension is registered for `ctxA`
- **AND** a caller assembles a prompt for `ctxA`
- **THEN** the prompt is assembled from the built-in DSLEngine base contract and the request overlay only

#### Scenario: Data model is request-scoped
- **WHEN** `GenUIPromptRequest` contains a raw data model
- **THEN** the assembled prompt includes a `## Data Model` section for that request
- **AND** the data model is not persisted into the registered Generation Context

### Requirement: Request overlay shall support dynamic tools and extra rules
`GenUIPromptRequest` SHALL support request-scoped `tools` and `extraRules`, and these fields SHALL apply only to one prompt assembly call.

#### Scenario: Dynamic tool appears only in current prompt
- **WHEN** a request for `ctxA` includes dynamic tool `searchTickets`
- **THEN** the assembled prompt includes `searchTickets`
- **AND** a later request for `ctxA` without `searchTickets` does not include that tool unless it is registered in the context

#### Scenario: Extra rules append only to current prompt
- **WHEN** a request contains extra rule `Prefer tables over charts for this request`
- **THEN** the assembled prompt includes that rule
- **AND** the rule is not persisted into `ctxA`

#### Scenario: Request overlay does not accept extra prompt text
- **WHEN** a caller builds `GenUIPromptRequest`
- **THEN** the request schema exposes `extraRules`
- **AND** the request schema does not expose `extraPrompt`

### Requirement: Java SDK shall reject name collisions within the final Generation Context
The SDK SHALL reject component or tool names that collide within the final Generation Context assembled from base contract, registered extension, and request overlay.

#### Scenario: Extension component collides with base component
- **WHEN** DSLEngine base contract contains component `Stack`
- **AND** a caller registers an extension for `ctxA` that also defines component `Stack`
- **THEN** registration fails with a name collision error

#### Scenario: Request tool collides with registered tool
- **WHEN** `ctxA` has registered tool `loadOrders`
- **AND** a request overlay also includes tool `loadOrders`
- **THEN** prompt assembly fails with a name collision error

#### Scenario: Independent contexts may use the same extension names
- **WHEN** `ctxA` registers component `BizCard`
- **AND** `ctxB` registers component `BizCard`
- **THEN** both registrations can succeed because each context is isolated

### Requirement: Java SDK shall expose contract version metadata
The SDK SHALL preserve contract version information for the base DSLEngine contract and registered GenUIContextExtension used for prompt assembly.

#### Scenario: Assembled prompt result includes versions
- **WHEN** a prompt is assembled for `ctxA`
- **THEN** the result metadata includes the base contract version
- **AND** the result metadata includes the registered extension version when one is present

#### Scenario: Re-register updates extension version metadata
- **WHEN** `ctxA` is re-registered from extension version `v1` to `v2`
- **AND** a prompt is assembled for `ctxA`
- **THEN** the result metadata reports extension version `v2`

### Requirement: Java prompt assembly shall match the front-end prompt byte-for-byte
The Java SDK prompt assembler SHALL produce output identical to the front-end `generatePrompt` for an equivalent merged generation spec, so back-end and front-end prompts do not diverge.

#### Scenario: Assembled prompt matches front-end golden output
- **WHEN** the same merged generation spec is assembled by the front-end and by the Java SDK
- **THEN** the Java SDK output equals the front-end output byte-for-byte

#### Scenario: Parity is covered across prompt branches
- **WHEN** the parity fixtures are assembled
- **THEN** the fixtures exercise prompts with and without tools, with bindings, with a data model, with edit mode, with inline mode, with grouped and ungrouped components, with examples, and with additional rules

#### Scenario: Builtin documentation comes from the base contract manifest
- **WHEN** the Java SDK renders the Template Built-ins and Data Built-ins sections
- **THEN** the builtin lines are taken from the base contract builtins manifest
- **AND** the SDK does not hardcode builtin signatures
