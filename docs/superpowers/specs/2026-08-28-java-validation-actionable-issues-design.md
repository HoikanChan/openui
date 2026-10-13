# Java Validation Actionable Issues Design

## Goal

Expose a low-noise issue view for repair and reporting without removing the complete diagnostics required for debugging and cross-language parity.

## Module and Interface

`ValidationIssueReducer` is the single deep module that owns issue dominance and cascade suppression. Its interface is one pure operation:

```java
List<ValidationIssue> ValidationIssueReducer.actionable(List<ValidationIssue> rawIssues)
```

`ValidationResult.issues()` remains the complete immutable diagnostic list. `ValidationResult.actionableIssues()` delegates to the reducer. Repair prompts and validation reports consume the actionable view; callers that need parser parity continue using the raw view.

## Reduction Rules

Rules preserve input order and never invent diagnostics:

1. Remove exact duplicate records.
2. For one syntax-broken statement, keep its first blocking syntax diagnostic and suppress later blocking syntax diagnostics from that statement.
3. Suppress derived contract, reference, and type diagnostics on a syntax-broken statement. An unresolved reference with a root-cause repair hint remains actionable.
4. `type-table-row-shape-mismatch` dominates `type-prop-mismatch` at the same statement/component/path.
5. When another actionable error exists, suppress the structural tail `root-missing` or `root-not-renderable`.
6. Preserve issues at different statements or paths. Multiple missing columns, component parameters, charts, and series remain independent actionable errors.

The type checker also stops propagating a fabricated result type after an obvious operator mismatch. This prevents an upstream operator error from creating a downstream component-prop error.

## Compatibility

- Validation status continues to use raw issues; reduction cannot turn an invalid program valid.
- Streaming retryability continues to inspect raw issues.
- Existing serialized `ValidationResult` fields do not change.
- `ReaskPromptBuilder` delegates to the reducer instead of maintaining a second private suppression implementation.

## Verification

- Unit-test every dominance rule and ordering invariant.
- Prove the operator cascade with the real validator.
- Keep the existing repair-prompt suppression tests green through the new module seam.
- Replay all 49 validation cases and report raw and actionable counts per case.
- Run the Java SDK validation suite, package build, and formatting checks before commit.
