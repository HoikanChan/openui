## ADDED Requirements

### Requirement: Agent-neutral task bundle generation
The optimizer SHALL generate a run-scoped `task-bundle/` that provides any supported developer agent with the same optimization context.

#### Scenario: Task bundle includes normalized optimization context
- **WHEN** the optimizer prepares work for an eval run
- **THEN** it writes a `task-bundle/` containing a summary, constraints, failing patterns, target fixture information, and relevant source file pointers
- **THEN** the bundle is sufficient for Codex, Claude Code, and opencode to perform the same optimization task without changing the core contract

#### Scenario: Agent-specific instructions remain adapters, not the core protocol
- **WHEN** the optimizer prepares agent handoff materials
- **THEN** any agent-specific instructions are written under an adapter-specific location such as `task-bundle/adapters/`
- **THEN** the canonical optimization inputs remain the shared task bundle artifacts

### Requirement: Standard result bundle consumption
After a developer chooses an agent and the agent completes its work, the optimizer SHALL read a standard `result-bundle/` rather than relying on a free-form markdown response.

#### Scenario: Result bundle parsed successfully
- **WHEN** the verifier finds a completed `result-bundle/`
- **THEN** it reads normalized machine-readable metadata such as touched files and claimed affected fixtures
- **THEN** it also reads human-readable summaries for developer review

### Requirement: Delta verification after agent changes
After reading a completed `result-bundle/`, the optimizer SHALL re-run evaluation and compute score deltas for the current code state.

#### Scenario: Delta computed for the modified codebase
- **WHEN** a completed result bundle is available
- **THEN** the verifier re-runs evaluation with fresh DSL generation where required
- **THEN** it computes per-fixture and overall score deltas against the baseline run

### Requirement: Full-suite regression gate
The optimizer SHALL use a full fixture re-run as the final regression gate for global prompt or schema changes.

#### Scenario: Verification passes only after full-suite validation
- **WHEN** optimizer verification runs after agent changes
- **THEN** the verifier may use targeted signals for fast diagnosis
- **THEN** final success is granted only after a full fixture evaluation completes without regression beyond the allowed threshold

#### Scenario: Regression produces review-needed output
- **WHEN** any fixture regresses during final verification
- **THEN** the optimizer does NOT mark the iteration as successful
- **THEN** it writes a review-needed summary describing the regressions

### Requirement: Commit remains developer-controlled by default
The optimizer SHALL default to producing a verification result and commit recommendation rather than automatically committing code changes.

#### Scenario: Positive verification yields a commit recommendation
- **WHEN** all verification gates pass
- **THEN** the optimizer writes a summary of the score improvement
- **THEN** it marks the run ready for developer review or an explicit finalize step

### Requirement: Stall detection based on verified history
The optimizer SHALL detect stalls using persisted verified iteration history rather than only the current process memory.

#### Scenario: Stall after N non-improving verified iterations
- **WHEN** the overall score has not improved for 3 consecutive verified iterations
- **THEN** optimizer marks the run as stalled
- **THEN** it writes a stall summary for human review
