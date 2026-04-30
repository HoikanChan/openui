# Eval-Loop Issue Template

Use this template for Linear, GitHub, Jira, or markdown handoff.

If one issue covers multiple fixtures, include 1-3 representative fixture evidence blocks inline and list the remaining affected fixtures in the summary or scope section.

```md
## Summary
<1-2 paragraphs. State the failure mechanism, not just the symptom.>

## Why This Matters
- <Why fixing this improves overall eval quality or eval reliability>
- <Why this is broader than one snapshot>

## Reproduction
1. Run:
   - `pnpm eval start --suite <suite>`
2. Inspect run:
   - `<run-id>`
3. Focus fixtures:
   - `<fixture-a>`
   - `<fixture-b>`

## Current Behavior
<Short description of what currently happens.>

## Expected Behavior
<Short description of what should happen instead.>

## Evidence

### Fixture: `<fixture-id>`
- Status: `<passed|failed>`
- Score: `<overall>/10`
- Breakdown: `cf=<n> dc=<n> fq=<n> lc=<n>`
- Failure reason:
  - `<failureReason or "none">`
- Judge feedback:
  - `<judge feedback>`
- Screenshot path:
  - `<path-to-task-bundle-screenshot>`
- Snapshot path:
  - `<path-to-suite-snapshot>`
- Report path:
  - `<path-to-report-data.json>`
- Prompt:
  - `<prompt>`
- Eval hints:
  - `<evalHints if useful>`

#### dataModel
```json
<full dataModel>
```

#### DSL Excerpt
```openui-lang
<minimal DSL snippet showing the bug>
```

### Fixture: `<fixture-id-2>`
<repeat as needed>

## Proposed Scope
- <Concrete work item 1>
- <Concrete work item 2>
- <Concrete work item 3>

## Acceptance Criteria
- [ ] <Measurable benchmark improvement or correctness condition>
- [ ] <Affected fixture no longer fails in the current way>
- [ ] <Regression coverage added where appropriate>

## References
- Eval run: `<run-id>`
- Suite: `<suite>`
- Local analysis: `<issues-map.md or other notes>`
```
