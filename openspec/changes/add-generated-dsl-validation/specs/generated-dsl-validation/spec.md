## ADDED Requirements

### Requirement: SDK validates completed generated DSL
Java Generation SDK SHALL expose a Generated DSL Validation capability that validates a completed generated `openui-lang` artifact before it is treated as a successful generation result.

#### Scenario: Valid completed DSL succeeds
- **WHEN** the validator receives completed `openui-lang` that parses, has a renderable `root`, satisfies the selected Component Contract, and has no unresolved non-external references
- **THEN** the validator returns `VALID`
- **AND** the returned result includes the normalized DSL text and no blocking issues

#### Scenario: Completed DSL with syntax error fails
- **WHEN** the validator receives completed `openui-lang` with a syntax error that cannot be interpreted as streaming partial input
- **THEN** the validator returns `INVALID`
- **AND** the result includes at least one syntax issue with code, message, line or statement context, and a repair hint when available

#### Scenario: Completed DSL without renderable root fails
- **WHEN** the validator receives completed `openui-lang` that does not produce a renderable root component
- **THEN** the validator returns `INVALID`
- **AND** the result includes a root-related issue that can be used in a reflection repair prompt

### Requirement: Java parser supports the generated openui-lang subset
The SDK SHALL include a Java-native parser for the generated `openui-lang` subset used by OpenUI prompts, without requiring Node, Graal, ANTLR, tree-sitter, or a remote parser service at production runtime.

#### Scenario: Parser accepts common generated statements
- **WHEN** the parser receives statements containing component calls, builtin calls, literals, arrays, objects, references, member access, index access, arithmetic operators, comparison operators, null coalescing, and ternary expressions
- **THEN** it parses them into a program representation without requiring frontend runtime code

#### Scenario: Parser preserves statement identifiers
- **WHEN** the parser receives multiple `statementId = expression` statements
- **THEN** each parsed statement preserves its statement identifier for diagnostics, reference resolution, and repair prompts

#### Scenario: Parser reports unexpected tokens structurally
- **WHEN** the parser cannot consume a token sequence as valid `openui-lang`
- **THEN** it records a structured validation issue instead of returning only a raw exception string

### Requirement: Validator checks Component Contract compatibility
Generated DSL Validation SHALL validate component calls against the merged Component Contract selected for the generation request.

#### Scenario: Unknown component fails validation
- **WHEN** generated DSL calls a component that is not present in the selected Component Contract and is not a known builtin or reserved call
- **THEN** validation returns `INVALID`
- **AND** the issue includes the unknown component name and a hint listing available or relevant component names

#### Scenario: Missing required prop fails validation
- **WHEN** generated DSL calls a known component without a required positional argument defined by its `propsSchema.required`
- **THEN** validation returns `INVALID`
- **AND** the issue includes the component, prop path, statementId, and signature hint

#### Scenario: Invalid nested prop fails validation
- **WHEN** generated DSL supplies an object prop containing a nested property rejected by the component `propsSchema`
- **THEN** validation returns `INVALID`
- **AND** the issue includes the nested prop path such as `/options/format`

#### Scenario: Excess positional args are repairable issues
- **WHEN** generated DSL supplies more positional arguments than the component schema accepts
- **THEN** validation records an `EXCESS_ARGS` issue
- **AND** the issue is marked repairable so reflection can remove or remap the extra arguments

### Requirement: Validation distinguishes final invalidity from streaming partial state
Generated DSL Validation SHALL support at least `FINAL` and `STREAMING` validation modes so incomplete streamed input is not judged by the same rules as completed generated output.

#### Scenario: Streaming unresolved reference is partial
- **WHEN** streaming validation sees a completed statement that references a statement not yet received
- **THEN** validation does not treat that reference as a blocking error
- **AND** the result exposes the unresolved reference as streaming metadata or a non-blocking issue

#### Scenario: Final unresolved reference is invalid
- **WHEN** final validation sees a reference that is not defined in the completed program and is not an allowed external reference such as `data`
- **THEN** validation returns `INVALID`
- **AND** the issue identifies the unresolved reference and the statement that used it

#### Scenario: Incomplete pending statement is partial
- **WHEN** streaming validation sees a pending statement with unclosed brackets, unclosed strings, or an incomplete expression
- **THEN** validation returns `PARTIAL`
- **AND** it does not trigger reflection repair until a completed statement boundary is observed

### Requirement: Streaming gate forwards only SDK-accepted DSL
The SDK SHALL provide a streaming statement gate that buffers raw LLM deltas, detects completed `openui-lang` statement boundaries, validates completed statements in context, and forwards only SDK-accepted DSL to the stream sink.

#### Scenario: Render-safe valid statement is forwarded
- **WHEN** the streaming gate receives a completed statement that keeps the current program render-safe
- **THEN** it forwards the statement as `dsl` stream content
- **AND** it updates the accepted prefix used for later validation or repair

#### Scenario: Temporary unresolved statement may be buffered
- **WHEN** the streaming gate receives a completed statement that is syntactically valid but depends on a future unresolved statement
- **THEN** it may keep the statement in an accepted internal buffer instead of forwarding it immediately
- **AND** it forwards the buffered DSL only after the dependency is resolved or final validation proves the program valid

#### Scenario: Invalid completed statement is withheld
- **WHEN** the streaming gate receives a completed statement with a definitive syntax or contract error
- **THEN** it does not forward that statement as `dsl` stream content
- **AND** it records validation state that identifies the failed statement and issues for repair, logs, or SDK metadata

#### Scenario: Half statement does not trigger repair
- **WHEN** the streaming gate has only received a partial statement fragment
- **THEN** it keeps the fragment in its pending buffer
- **AND** it does not emit a repair request for that fragment

### Requirement: Fail-Fast Reask repairs invalid streamed statements
When enabled, the SDK SHALL handle a definitively invalid completed streamed statement by cancelling the current model stream and starting a repair-and-continue model request instead of merging two concurrent model streams.

#### Scenario: Invalid statement triggers reask
- **WHEN** streaming gate marks a completed statement as definitively invalid and Fail-Fast Reask is enabled
- **THEN** the SDK cancels the current LLM stream
- **AND** it starts a new LLM request containing the accepted DSL prefix, invalid statement, validation issues, and relevant component contract hints

#### Scenario: Reask resumes from repaired statement
- **WHEN** the repair-and-continue model request succeeds
- **THEN** the next accepted stream content begins with the repaired statement or compatible continuation
- **AND** all repaired output is validated before being forwarded

#### Scenario: Reask failure falls back to final failure
- **WHEN** the repair-and-continue request fails, times out, or produces invalid DSL after the configured retry limit
- **THEN** the SDK emits an error or invalid completion status
- **AND** it does not return the buffered or streamed DSL as a successful cacheable result

### Requirement: Final repair is bounded and revalidated
When repair is enabled for completed generation, the SDK SHALL use bounded reflection repair and MUST revalidate repaired output before returning it as a successful result.

#### Scenario: Full repair succeeds after invalid final output
- **WHEN** final validation returns `INVALID` and full repair is enabled
- **THEN** the SDK sends the invalid DSL and structured validation issues to the model
- **AND** it returns the repaired DSL only after the repaired output validates as `VALID`

#### Scenario: Full repair does not loop indefinitely
- **WHEN** repair output remains invalid
- **THEN** the SDK stops after the configured maximum repair attempts
- **AND** it returns or throws a failure containing the latest validation result

### Requirement: Reask prompts present repair-oriented diagnostics
The SDK reask prompt builder SHALL present validation issues in a form optimized for a repair model: each issue line carries statement and position context when known, symptom-only cascade issues are suppressed from the prompt view (never from the validation result), and unresolved references caused by JavaScript-subset violations carry root-cause hints naming usable openui-lang builtins. Hint enrichment MUST NOT alter issue `message` text, which is bound by TypeScript parity.

#### Scenario: JavaScript global reference gets a root-cause hint
- **WHEN** final validation reports an unresolved reference whose name is a known JavaScript global or keyword such as `Math`, `Date`, or `new`
- **THEN** the issue hint states that JavaScript globals and methods are not available in `openui-lang` and names builtin alternatives such as `@Abs`, `@Round`, `@FormatNumber`
- **AND** the hint does not suggest defining a statement of that name or passing it as an external ref

#### Scenario: Builtin name missing '@' gets a did-you-mean hint
- **WHEN** final validation reports an unresolved reference whose name exactly matches a builtin name including case, such as `FormatNumber`
- **THEN** the issue hint suggests calling the builtin with a leading `@`, such as `did you mean "@FormatNumber"`

#### Scenario: Unresolved reference is attributed to the referencing statement
- **WHEN** final validation reports an unresolved reference used inside a statement
- **THEN** the issue carries the statementId of the statement that used the reference and that statement's start position, not the root statement

#### Scenario: Derived issues of a syntax-broken statement are suppressed in the prompt
- **WHEN** a statement carries a blocking syntax issue and the same statement also produced excess-args, null-required, or unresolved-ref issues
- **THEN** the reask prompt omits those derived issues while the validation result still contains them
- **AND** an unresolved-ref issue carrying a root-cause hint is not suppressed

#### Scenario: Root-not-renderable is suppressed when another error explains it
- **WHEN** the issue list sent to the reask prompt contains any other blocking issue besides `root-not-renderable`
- **THEN** the reask prompt omits the `root-not-renderable` issue
- **AND** `root-not-renderable` is presented only when it is the sole blocking issue

#### Scenario: Prompt issue lines carry statement and position context
- **WHEN** the reask prompt renders an issue that has a statementId or a known line and column
- **THEN** the issue line includes the statement id and `line:column` so a multi-statement document can be located

### Requirement: Java validator parity is tested against TypeScript parser fixtures
The repository SHALL include cross-language validation fixtures or golden tests that compare Java validator behavior with the TypeScript `packages/lang-core` parser for the supported validation subset.

#### Scenario: Shared fixture has matching issue class
- **WHEN** a shared fixture contains an unknown component, missing required prop, invalid nested prop, unresolved final reference, or parse failure
- **THEN** the Java validator test asserts the corresponding issue class and context match the TypeScript oracle for the supported subset

#### Scenario: Supported syntax fixture parses in both runtimes
- **WHEN** a shared fixture contains supported generated `openui-lang` syntax
- **THEN** both the Java validator and TypeScript parser accept the fixture without blocking issues
