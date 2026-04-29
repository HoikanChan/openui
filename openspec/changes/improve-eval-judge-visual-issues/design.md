## Context

The eval loop already captures screenshots and sends them to a multimodal judge, but the judge contract is too coarse. The current rubric mostly checks component choice, field coverage, and value formatting. As a result, visible defects such as text overlap, clipped charts, horizontal card stacks that fight reading order, or severe empty-space imbalance often survive with `layout_coherence = 3`.

This weakness appears in two places:

1. The rubric does not explicitly define which screenshot-derived defects are disqualifying for a high layout score.
2. The judge output only exposes one free-form `feedback` sentence, so downstream aggregation cannot reliably distinguish overlap from crowding or weak hierarchy.

The change touches the judge prompt, parse contract, failing-pattern aggregation, and report/task-bundle presentation. That makes it cross-cutting enough to justify an explicit design before implementation.

## Goals / Non-Goals

**Goals:**
- Make visible screenshot defects materially lower `layout_coherence`, and when severe, lower `overall`.
- Add a small, stable, machine-readable diagnostics field so downstream tooling can group visual failures by type.
- Keep the new diagnostics contract additive so existing run data without the field still loads.
- Improve task-bundle and report usefulness by surfacing concrete visual issue categories instead of generic layout comments.

**Non-Goals:**
- Replace the current four score dimensions with a brand-new scoring system.
- Build a full visual QA ontology or pixel-diff engine.
- Change fixture rendering behavior in this change; this proposal targets the judge and eval outputs, not the runtime components themselves.
- Require migration of historical run artifacts.

## Decisions

### Decision: Add `visual_issues: string[]` as a bounded enum-backed diagnostics field

The judge response will gain a new additive field, `visual_issues`, containing zero or more tags from a fixed allowlist:
- `overlap`
- `wrong-direction`
- `crowded`
- `whitespace-imbalance`
- `clipped`
- `weak-hierarchy`

Rationale:
- A single free-form sentence is too weak for aggregation.
- A small allowlist is enough to cover the recurring screenshot failures we already see.
- An array allows more than one defect to be reported for the same fixture without introducing nested structures.

Alternatives considered:
- Free-form textual issue labels: rejected because aggregation becomes noisy and inconsistent.
- Rich nested diagnostics objects with severity per issue: rejected as too heavy for the first iteration and likely to create prompt brittleness.

### Decision: Treat visible render defects as score caps, not optional style suggestions

The rubric will explicitly instruct the judge that certain visible defects cap `layout_coherence`:
- Any obvious overlap, clipping, or unreadable crowding cannot receive `layout_coherence = 3`.
- Severe overlap/clipping/crowding should score `0` or `1`.
- Direction mismatch, whitespace imbalance, or weak hierarchy should reduce layout scores when they materially harm scanability.

Rationale:
- The current judge appears to treat style defects as secondary commentary rather than scoring inputs.
- Score caps are easier for the model to follow than vague “consider layout” wording.

Alternatives considered:
- Leave scores unchanged and only add structured tags: rejected because that would preserve the core false-positive problem.
- Add a fifth score dimension for visual quality: rejected because it would expand all downstream reports and scoring logic when the existing `layout_coherence` dimension already owns this concern.

### Decision: Aggregate failing patterns directly from `visual_issues`, not only from low layout scores

The aggregation layer will continue to produce dimension-based patterns, but it will also derive issue-specific patterns from `visual_issues` when present. For example, repeated `overlap` tags across fixtures should yield a concrete failing pattern even if the accompanying feedback text differs.

Rationale:
- Engineers need to know whether a weak layout score is caused by overlap, clipping, or hierarchy.
- Issue-based aggregation gives the eval loop better guidance for prompt/runtime fixes.

Alternatives considered:
- Only display raw tags per fixture and skip aggregation: rejected because it pushes too much manual synthesis onto the engineer reading the run.

### Decision: Parse and store `visual_issues` defensively for backwards compatibility

`JudgeScore` will gain an optional or defaulted `visual_issues` array, and parsing will normalize absent or malformed values to `[]`. Historical run artifacts and older judges that do not emit the field must continue to load.

Rationale:
- The eval loop already has persisted runs on disk.
- This change should not require rewriting old artifacts or branching report logic on versioned schemas.

Alternatives considered:
- Make `visual_issues` required everywhere immediately: rejected because it would break existing runs and complicate adoption.

## Risks / Trade-offs

- [Prompt strictness could over-penalize acceptable layouts] → Mitigation: keep the enum narrow and define only clearly visible defects as hard caps.
- [Model output may emit invalid or noisy labels] → Mitigation: normalize against an allowlist and drop unknown labels during parsing.
- [Issue-based aggregation may duplicate existing layout patterns] → Mitigation: keep dimension-level patterns for score summaries, but prefer issue-specific patterns when tags are present.
- [Historical report viewers may assume older `JudgeScore` shape] → Mitigation: treat `visual_issues` as additive and render empty-state behavior when absent.

## Migration Plan

1. Extend the judge rubric and parser to support `visual_issues`.
2. Extend in-memory types and persisted task-bundle/report data generation to carry the field through.
3. Update failing-pattern aggregation to synthesize issue-specific patterns.
4. Update report/task-bundle presentation to show per-fixture visual issues and aggregate issue summaries.
5. Run eval tests and targeted fixture checks against known visual-failure cases such as `byte-large-values` and `percentage-as-decimal`.

Rollback strategy:
- Remove the new rubric clauses and stop emitting/reading `visual_issues`.
- Because the field is additive, rollback does not require data migration.

## Open Questions

- Whether `wrong-direction` should remain a pure visual tag or later become a component-selection signal if it consistently reflects generator intent rather than renderer styling.
- Whether future calibration flow should allow human corrections to set `visual_issues` directly, not only numeric score corrections.
