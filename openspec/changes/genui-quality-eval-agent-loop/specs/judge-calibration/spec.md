## ADDED Requirements

### Requirement: corrections.json accepted as optional human input
The system SHALL accept a `corrections.json` file in the eval workspace as optional human feedback for judge calibration or prompt optimization.

#### Scenario: Judge corrections routed to calibration
- **WHEN** a correction entry has `target = "judge"`
- **THEN** the system routes that entry into the judge calibration flow

#### Scenario: Prompt corrections routed to optimizer
- **WHEN** a correction entry has `target = "prompt"`
- **THEN** the system does not send that entry to rubric calibration
- **THEN** the system forwards that entry into the next optimization task bundle

### Requirement: Correction entries use explicit lifecycle states
The system SHALL track correction processing with explicit states instead of a single boolean processed flag.

#### Scenario: Correction state recorded explicitly
- **WHEN** a correction entry is written or updated
- **THEN** it has a lifecycle state such as `pending`, `applied`, `failed`, or `forwarded_to_optimizer`

### Requirement: Rubric updated by a calibration flow
The calibration flow SHALL update the rubric prompt based on pending judge-targeted corrections, then validate the updated rubric against the corrected fixtures.

#### Scenario: Updated rubric validated against corrections
- **WHEN** calibration updates the rubric
- **THEN** the system re-runs judge scoring for fixtures referenced by those corrections
- **THEN** the new scores are compared with the human-corrected scores
- **THEN** calibration succeeds only when the divergence is within the allowed tolerance

#### Scenario: Calibration failure preserves human feedback
- **WHEN** the updated rubric still diverges from human corrections beyond the allowed tolerance
- **THEN** calibration writes a failure summary
- **THEN** the original rubric is restored
- **THEN** the relevant correction entries are marked `failed` rather than silently consumed

### Requirement: Prompt-targeted corrections remain visible to optimizer
The system SHALL ensure that prompt-targeted human feedback is retained until it is included in an optimization handoff.

#### Scenario: Prompt correction forwarded into task bundle
- **WHEN** the next optimization task bundle is generated
- **THEN** any pending prompt-targeted corrections are included as explicit hints in that bundle
- **THEN** those entries are marked `forwarded_to_optimizer`
