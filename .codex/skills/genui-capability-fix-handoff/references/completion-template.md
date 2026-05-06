# Completion Template

Use this in the Linear workpad or completion summary after implementing a GenUI capability fix issue.

````md
## Generalization Evidence

### Reusable Rule Changed
<The prompt rule, example, helper behavior, component fallback, schema guidance, or mixed rule that changed.>

### Changed Layer
Prompt rule | Prompt example | Runtime helper | Component fallback | Schema guidance | Mixed

### Why This Generalizes
<Explain the field/data/component shape this applies to, independent of fixture ids.>

### Anti-Overfit Check
- [ ] No fixture ids added to source
- [ ] No snapshot files modified
- [ ] No sample-specific constants added
- [ ] No branch targets only the listed fixtures
- [ ] The rule is expressed in terms of field/data shape or component semantics

### Validation
- `<command>`: <result>
- `<command>`: <result>

### Eval Evidence
<Score delta, screenshot change summary, or relevant eval verification notes.>

### Residual Risk
<Similar shapes not covered by this change, or cases that still need follow-up.>
````

If an item in the anti-overfit checklist cannot be checked, explain why before asking for review.
