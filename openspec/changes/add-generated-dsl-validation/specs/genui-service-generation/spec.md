## MODIFIED Requirements

### Requirement: Streaming generation endpoint
GenUI Service SHALL accept generation requests through `POST /v1/generate`, assemble prompts through the Java Generation SDK, invoke the LLM, and stream render envelopes as `text/event-stream`. The service SHALL rely on the SDK validation gate so public `dsl` envelopes contain only SDK-accepted `openui-lang`, not raw LLM deltas. Empty prompt or missing required generation input SHALL return 400 before any stream envelope is written.

#### Scenario: Streaming generation produces accepted DSL
- **WHEN** a valid generation request is submitted
- **THEN** the response uses `text/event-stream`
- **AND** the service streams `dataModel`, SDK-accepted `dsl`, `error`, and `done` envelopes
- **AND** `dsl` envelopes do not contain a completed statement that the SDK has classified as definitively invalid

#### Scenario: Assembled context affects generation
- **WHEN** the generation request selects a registered context or extension
- **THEN** the system prompt sent to the LLM includes that extension's components, tools, examples, and rules

#### Scenario: Empty prompt is rejected before streaming
- **WHEN** the generation request has an empty or blank prompt
- **THEN** the service returns 400 with an error response before writing any stream envelope

#### Scenario: Invalid statement is withheld and repaired internally
- **WHEN** the SDK streaming gate detects a definitively invalid completed statement
- **THEN** the service does not stream that invalid statement in a `dsl` envelope
- **AND** if configured repair succeeds, the service streams the repaired accepted DSL as ordinary `dsl`
- **AND** clients are not required to consume validation, repairing, replace, commit, or discard envelope types

#### Scenario: Final valid result may be cached by the service
- **WHEN** the SDK completes the stream with a final validation status of `VALID` or `REPAIRED`
- **THEN** the service may cache the final normalized DSL
- **AND** cache eligibility is determined by the SDK completion result, not by a frontend stream command

#### Scenario: Unrecoverable validation failure is not cached
- **WHEN** final validation fails and configured repair attempts are disabled, exhausted, or invalid
- **THEN** the service emits an `error` envelope before ending the stream if streaming has already begun
- **AND** the service does not cache the streamed candidate content as a successful result

### Requirement: LLM invocation behavior parity with retired Node server
GenUI Service's LLM invocation behavior SHALL preserve the configurable OpenAI-compatible behavior of the retired Node server where applicable, including model configuration, proxy support, and pre-stream failure handling. For SDK-gated streaming responses, failures after any stream envelope has been written SHALL be represented as structured stream envelopes rather than appending plain text error tails to the DSL.

#### Scenario: Mid-stream failure emits structured error
- **WHEN** the LLM stream ends unexpectedly, returns a non-success finish reason, or validation repair fails after streaming has begun
- **THEN** the service emits an `error` envelope and then ends the stream with `done`
- **AND** it does not append `[ERROR: ...]` plain text into a `dsl` envelope

#### Scenario: Pre-stream failure returns 502
- **WHEN** the LLM interface fails before the first stream envelope is written
- **THEN** the service returns 502 with a JSON error body instead of an empty stream

#### Scenario: Proxy bypass remains effective
- **WHEN** `HTTPS_PROXY` is configured and the LLM host matches a `NO_PROXY` entry
- **THEN** the LLM request bypasses the proxy according to the existing proxy rules

#### Scenario: Model and endpoint configuration remain honored
- **WHEN** the service is configured with model, endpoint, or transport settings
- **THEN** the SDK LLM invocation uses those settings for both original generation and repair-and-continue requests unless a repair-specific override is configured
