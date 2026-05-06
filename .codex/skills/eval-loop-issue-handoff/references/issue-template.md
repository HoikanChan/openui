# GenUI Capability Fix Issue Template

Use this template for Linear issues created from GenUI eval runs. The issue title should name the reusable capability, not the affected fixtures.

Good title:

```text
Generalize semantic value formatting for date/number/byte/percent fields
```

Bad title:

```text
Poor value formatting affects aggregated-only and object-map-by-id
```

````md
## Source Eval Run
<run-id>
Suite: <e2e|fuzz|benchmark>
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

Failure reason:
- <failureReason or "none">

Judge feedback:
- <judge feedback>

Prompt:
- <prompt>

Eval hints:
- <evalHints or "none">

Paths:
- Screenshot source: `<local screenshot path>`
- Snapshot source: `<suite snapshot path>`
- Report data: `<report-data.json path>`

Data Model:
```json
<full dataModel, or relevant excerpt with full source path if too large>
```

Generated DSL:
```openui
<generated DSL from the eval run, or the smallest excerpt preserving the failure>
```

Screenshot:
![<fixture-id>](<Linear assetUrl>)

Observed Issue:
- <specific visible or judged failure>

Expected General Behavior:
- <behavior expressed as a general rule, not a fixture-specific expectation>

### <fixture-id-2>
<repeat as needed>

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
