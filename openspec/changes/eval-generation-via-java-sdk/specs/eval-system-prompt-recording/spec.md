## MODIFIED Requirements

### Requirement: Eval run SHALL record a canonical system prompt
React UI DSL eval runs SHALL write one canonical system prompt artifact for the run, generated from the same Java Generation SDK prompt assembly path used by regen（经 Eval Generation CLI 的 `--print-prompt`，注入与 regen 相同的 base contract）。

#### Scenario: Start run records canonical prompt
- **WHEN** a user starts an eval run
- **THEN** the run directory MUST contain a `system-prompt.txt` artifact
- **AND** the artifact MUST contain the fully assembled system prompt as produced by the Java Generation SDK for the run's base contract

#### Scenario: Canonical prompt uses placeholder data model
- **WHEN** the canonical system prompt is generated
- **THEN** the data model section MUST use an explicit placeholder instead of any fixture-specific data
- **AND** the placeholder MUST state that fixture data is recorded in the eval report data

#### Scenario: Regen still uses fixture data
- **WHEN** eval regen calls the LLM for a fixture
- **THEN** the actual system prompt sent to the LLM MUST still include that fixture's real data model
- **AND** writing the canonical prompt MUST NOT change generated DSL behavior

### Requirement: Eval run SHALL identify the recorded prompt by hash
React UI DSL eval runs SHALL compute and persist a stable hash for the canonical system prompt artifact.

#### Scenario: Prompt hash is stored with run metadata
- **WHEN** the canonical system prompt artifact is written
- **THEN** the run metadata MUST include the artifact's run-relative path
- **AND** the run metadata MUST include a hash computed from the full artifact content

#### Scenario: Prompt content changes alter hash
- **WHEN** the assembled canonical prompt content changes because the base contract (component signatures, builtins, rules, examples) or SDK prompt assembly changes
- **THEN** the computed prompt hash MUST change
