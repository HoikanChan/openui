# GenUI Capability Issue Design

## Context

The current GenUI eval workflow is:

1. Codex runs an eval.
2. Codex summarizes failures.
3. Codex creates Linear issues.
4. Codex resolves those issues.
5. The human reviews Linear screenshots and score deltas.

This flow exposes quality problems, but it can incentivize fixture-specific fixes. The issue body often lists affected fixtures and a suggested local fix, so the implementation agent optimizes for closing the visible cases instead of improving a reusable GenUI capability.

The workflow should keep Linear as the durable handoff surface, but change the unit of work from "fix these failing fixtures" to "build or strengthen this reusable capability." Fixture data remains required evidence, not the implementation target.

## Goals

- Create Linear issues directly from eval runs as capability fix issues.
- Include enough fixture evidence in each issue for Codex to inspect the problem without rediscovering run artifacts.
- Require completion evidence that explains why the change generalizes beyond the listed fixtures.
- Avoid adding a separate Linear triage issue unless a future workflow needs durable triage history.
- Avoid requiring extra heldout fixture runs as the default review gate.

## Non-Goals

- Do not fully automate the eval loop in this design.
- Do not require humans to review source code for every issue.
- Do not use Linear MCP base64 attachments for screenshots.
- Do not turn every eval pattern into an issue; only create issues for coherent, actionable capability gaps.

## Workflow

```text
Codex runs eval
  -> reads eval artifacts
  -> groups failures into capability classes
  -> creates 2-5 Linear capability fix issues
  -> embeds primary fixture dataModel, generated DSL, and screenshot evidence
  -> Codex fixes one capability issue
  -> Codex updates Linear with generalization evidence and eval evidence
  -> human reviews screenshots, score deltas, and anti-overfit evidence
```

The eval summary step is internal to Codex. Linear should contain the durable capability issues, not a separate triage issue by default.

## Capability Issue Definition

A capability issue is a Linear issue whose title and acceptance criteria describe a reusable GenUI behavior, not a fixture list.

Example title:

```text
Generalize semantic value formatting for date/number/byte/percent fields
```

Bad title:

```text
Poor value formatting affects aggregated-only and object-map-by-id
```

Fixtures are evidence inside the issue. The implementation target is the reusable data-shape or behavior class.

## Issue Body Template

````markdown
## Source Eval Run
<run-id>
Overall: <score>/10

## Capability Goal
<One sentence describing the reusable behavior to build or strengthen.>

## Problem Class
<Describe the data shape, field semantics, or rendering behavior that fails.>

## Evidence Fixtures

### <fixture-id>

Score:
- overall: <score>/10
- component_fit: <score>
- data_completeness: <score>
- format_quality: <score>
- layout_coherence: <score>

Data Model:
```json
<fixture dataModel>
```

Generated DSL:
```openui
<generated DSL snapshot from the eval run>
```

Screenshot:
![<fixture-id>](<Linear assetUrl>)

Observed Issue:
- <specific visible or judged failure>

Expected General Behavior:
- <behavior expressed as a general rule, not a fixture-specific expectation>

## Required Fix Shape

Fix the reusable capability, not the listed fixtures.

Allowed fix layers:
- DSL generation prompt rule
- Prompt example
- Runtime helper behavior
- Component fallback behavior
- Schema or component guidance

Forbidden:
- Editing snapshots
- Hardcoding fixture ids
- Hardcoding sample values
- Adding branches that only match listed fixtures or their business-specific names

## Generalization Gate

Completion must report:
- Reusable rule changed
- Changed layer: prompt rule, prompt example, runtime helper, component fallback, schema guidance, or mixed
- Why the rule applies beyond the listed fixtures
- Anti-overfit checklist result
- Residual cases not covered
````

## Screenshot Upload Contract

Screenshots must be embedded as Markdown images using Linear asset URLs.

The upload path must use Linear's official two-step file upload flow:

1. Codex/plugin reads the local screenshot file.
2. Codex/plugin calls Linear GraphQL `fileUpload(contentType, filename, size)`.
3. Linear returns `uploadUrl`, `assetUrl`, and required `headers`.
4. Codex/plugin performs a server-side or Node-side `PUT` of the raw image bytes to `uploadUrl` with the returned headers.
5. Codex/plugin writes the returned `assetUrl` into the issue body as `![fixture-id](assetUrl)`.

Do not use the MCP base64 attachment path for these screenshots.

## Fixture Evidence Requirements

Each primary evidence fixture must include:

- `dataModel` as JSON.
- Generated DSL from the eval run.
- Screenshot uploaded through the Linear file upload contract.
- Observed issue in plain language.
- Expected behavior expressed as a general rule.

The issue should usually include 2-5 primary fixtures. If a pattern only has one fixture, Codex should either merge it into a broader capability issue or explicitly justify why one fixture is enough evidence for a capability gap.

## Grouping Rules

Codex should group failures by reusable behavior or data shape, not by judge dimension alone.

Examples:

- Semantic value formatting for date, number, byte, percent, ratio, currency, count, and duration fields.
- Dynamic-key object maps and nested collection rendering.
- Anti-fabrication for null-heavy, missing, or unlabeled data.
- Primitive numeric distribution rendering.
- Specialized visualization support for graph, band, topology, or compact trend patterns.

Judge patterns such as "Poor value formatting" or "Missing data fields" are inputs to grouping, not final issue titles.

## Codex Completion Template

When Codex finishes a capability issue, the Linear workpad or completion summary must include:

````markdown
## Generalization Evidence

### Reusable Rule Changed
<The rule, helper behavior, example, or fallback that changed.>

### Changed Layer
Prompt rule | Prompt example | Runtime helper | Component fallback | Schema guidance | Mixed

### Why This Generalizes
<Explain the data/field shape this applies to, independent of fixture ids.>

### Anti-Overfit Check
- [ ] No fixture ids added to source
- [ ] No snapshot files modified
- [ ] No sample-specific constants added
- [ ] No branch targets only the listed fixtures
- [ ] The rule is expressed in terms of field/data shape or component semantics

### Eval Evidence
<Score delta, screenshot change summary, or relevant eval verification notes.>

### Residual Risk
<Similar shapes not covered by this change, or cases that still need follow-up.>
````

This evidence lets the human review the implementation mechanism without reading source code by default.

## Review Model

Human review remains screenshot- and Linear-centered:

- Review the before/after visual evidence.
- Review score or dimension deltas.
- Review the Generalization Evidence.
- Reject or ask for follow-up if the anti-overfit evidence is weak, if the changed rule is fixture-shaped, or if residual risk hides obvious in-scope cases.

This does not prove full generalization, but it raises the cost of fixture-specific fixes and makes overfitting visible in the durable handoff.

## Error Handling

- If screenshot upload fails, the issue should not silently omit screenshots. Codex should retry once and then record the failed local screenshot path plus the upload error.
- If a fixture's `dataModel` or generated DSL is too large for a practical Linear issue, Codex should include a concise excerpt plus a local artifact path or uploaded text artifact. The excerpt must preserve the fields involved in the observed failure.
- If failures do not form a coherent capability class, Codex should avoid creating a fix issue and instead report that the run needs manual triage.

## Testing and Validation

Default validation should rely on the existing eval verification and the completion evidence:

- Run the normal eval verify command when a code change is made.
- Record exact command and outcome in the Linear workpad.
- Include primary fixture score or screenshot deltas when available.
- Do not require extra heldout fixture runs by default.

Extra heldout or related fixture runs remain optional for high-risk or suspicious fixes.

## Success Criteria

- New Linear issues are capability-oriented, not fixture-oriented.
- Every capability issue contains fixture `dataModel`, generated DSL, and screenshot evidence.
- Screenshots use Linear `fileUpload` presigned URLs and Markdown image embedding.
- Completion summaries include Generalization Evidence.
- Human reviewers can identify likely overfit fixes from Linear without reading code in the common case.
