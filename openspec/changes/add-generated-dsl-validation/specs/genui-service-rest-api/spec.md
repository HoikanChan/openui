## ADDED Requirements

### Requirement: Swagger documents SDK-gated generation stream
GenUI Service REST documentation SHALL describe the `POST /v1/generate` streaming response as an SDK-gated SSE stream. Public stream clients SHALL only need to consume `dataModel`, `dsl`, `error`, and `done` envelope variants.

#### Scenario: Swagger lists public stream envelope variants
- **WHEN** a caller reads the Swagger contract for `POST /v1/generate`
- **THEN** the contract describes `dataModel`, `dsl`, `error`, and `done` envelope variants
- **AND** each variant documents the expected `content` shape or absence of content
- **AND** the contract does not require clients to consume `validation`, `repairing`, `replace`, `commit`, or `discard` variants

#### Scenario: Swagger documents accepted DSL semantics
- **WHEN** a caller reads the `dsl` envelope schema
- **THEN** the schema states that `dsl` content is SDK-accepted `openui-lang`
- **AND** the schema states that invalid completed statements are withheld by the SDK and are not streamed as raw LLM deltas

#### Scenario: Swagger documents stream errors
- **WHEN** a caller reads the `error` envelope schema
- **THEN** the schema documents `code`, `message`, and `retryable`
- **AND** the schema states that an `error` envelope means the current generation result is not a successful cacheable DSL

#### Scenario: Swagger documents done as transport end
- **WHEN** a caller reads the `done` envelope schema
- **THEN** the schema states that `done` marks the end of the SSE stream
- **AND** the schema states that service-side caching and final validity are determined by the SDK completion result
