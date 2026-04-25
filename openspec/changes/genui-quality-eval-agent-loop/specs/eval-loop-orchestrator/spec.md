## ADDED Requirements

### Requirement: Run-scoped orchestration for the full eval loop
The orchestrator SHALL manage the full eval loop through run-scoped artifacts rather than by directly invoking a specific agent runtime.

#### Scenario: New run created with baseline artifacts
- **WHEN** a developer starts a new eval run
- **THEN** the orchestrator creates a run workspace with run metadata, `report-data.json`, and `task-bundle/`
- **THEN** the run state becomes `waiting_for_agent`

### Requirement: Developer-selected manual handoff
The orchestrator SHALL support a developer choosing Codex, Claude Code, or opencode manually after a task bundle is generated.

#### Scenario: Developer chooses an agent after task generation
- **WHEN** the orchestrator has finished generating a task bundle
- **THEN** it does not automatically start an agent
- **THEN** it presents run status and handoff instructions for supported agents

### Requirement: Explicit verification stage
The orchestrator SHALL provide an explicit verification stage that consumes a completed `result-bundle/` and updates run state.

#### Scenario: Verification completes after agent work
- **WHEN** a developer requests verification for a run with a completed result bundle
- **THEN** the orchestrator executes the verifier
- **THEN** it records delta results, regression status, and the next recommended action in run metadata and history

### Requirement: Iteration history persisted across runs
The orchestrator SHALL persist iteration history in run-scoped metadata so that stall detection and auditability work across separate CLI invocations.

#### Scenario: History written after each verified iteration
- **WHEN** a verification cycle completes
- **THEN** the orchestrator appends an iteration record including timestamp, score before/after, verification outcome, and stall counter

#### Scenario: History read on status or follow-up commands
- **WHEN** the orchestrator is invoked for status, verify, or calibration on an existing run
- **THEN** it reads the persisted run metadata and history before proceeding

### Requirement: CLI stages are explicit and developer-friendly
The orchestrator SHALL expose explicit CLI stages instead of a blocking black-box loop that waits for agent output.

#### Scenario: Start stage prepares a run
- **WHEN** the developer runs a start command for the eval loop
- **THEN** the orchestrator performs baseline evaluation and prepares the task bundle

#### Scenario: Status stage shows current run state
- **WHEN** the developer runs a status command for an existing run
- **THEN** the orchestrator prints the current phase, verification outcome, and any pending next step

#### Scenario: Verify stage validates agent output
- **WHEN** the developer runs a verify command for an existing run
- **THEN** the orchestrator consumes the result bundle and performs verification

#### Scenario: Calibrate stage processes pending judge corrections
- **WHEN** the developer runs a calibrate command for an existing run
- **THEN** the orchestrator processes pending judge-targeted corrections before the next optimization handoff
