## ADDED Requirements

### Requirement: Semantic regression reports SHALL reference canonical system prompt evidence
React UI DSL semantic regression reports SHALL expose run-level metadata that identifies the canonical system prompt artifact used to audit the run's prompt contract.

#### Scenario: Report records prompt artifact metadata
- **WHEN** a semantic regression report is produced for a React UI DSL eval run
- **THEN** the report metadata MUST include the canonical system prompt artifact path
- **AND** the report metadata MUST include the canonical system prompt hash

#### Scenario: Report prompt metadata matches run metadata
- **WHEN** the report metadata references a canonical system prompt
- **THEN** its artifact path and hash MUST match the values stored in the run manifest

#### Scenario: Report does not duplicate fixture data in prompt evidence
- **WHEN** prompt evidence is recorded for a semantic regression report
- **THEN** the prompt artifact MUST NOT duplicate each fixture's data model
- **AND** fixture-specific data MUST remain available through the report's existing fixture entries
