---
name: genui-capability-fix-handoff
description: Use when resolving GenUI Linear capability fix issues from eval runs, especially issues that require reusable fixes, anti-overfit evidence, Linear workpad updates, or Generalization Evidence.
---

# GenUI Capability Fix Handoff

Use this skill when implementing a GenUI capability fix issue. The goal is not to make the listed fixtures pass by any means; the goal is to strengthen a reusable data-shape, prompt, helper, or component capability and report evidence that the change is not fixture-specific.

Combine this with `$linear-issue-handoff` when updating Linear state, comments, or workpads.

## Workflow

1. Read the Linear issue and identify the capability goal, problem class, primary evidence fixtures, required fix shape, and Generalization Gate.
2. Create or update the single `## Codex Workpad` comment if using Linear.
3. Before editing, write a Generalization Plan in the workpad:
   - shared data/field/component shape
   - non-goals and boundaries
   - chosen fix layer: prompt rule, prompt example, runtime helper, component fallback, schema guidance, or mixed
   - primary evidence fixtures to inspect
4. Inspect the issue-provided `dataModel`, generated DSL, screenshots, and local source.
5. Implement the smallest reusable fix that addresses the capability class.
6. Run the normal validation for the touched area and eval verification when practical.
7. Update Linear with Generalization Evidence using [references/completion-template.md](references/completion-template.md).

## Hard Rules

- Do not edit eval snapshots to make the issue look fixed.
- Do not hardcode fixture ids in source.
- Do not hardcode sample values from the issue evidence.
- Do not add branches that only match listed fixtures or their business-specific names.
- Do not claim generalization from score improvement alone.
- Do not move the issue to review until Generalization Evidence is complete.

## Fix Layer Selection

| Symptom | Prefer |
|---|---|
| LLM chooses wrong component family for a data shape | prompt rule or prompt example |
| LLM knows the component but uses the API incorrectly | prompt example or schema guidance |
| Correct DSL renders poorly for many inputs | component fallback or runtime helper |
| Parser/runtime cannot express the needed shape | runtime or language capability |
| Issue evidence shows fabricated labels/values | prompt anti-fabrication rule and examples |

If the issue says "prompt fix" but evidence shows a component/runtime defect, follow the evidence and explain the layer change in the workpad.

## Anti-Overfit Review

Before handoff, check the source diff for these red flags:

- fixture ids
- evidence fixture business names used as condition keys
- literal sample values from `dataModel`
- snapshot edits
- only one affected fixture changed while the stated rule is broader
- completion summary that says "fixed fixture X" but does not name a reusable rule

If any red flag is present, either remove it or explicitly justify why it is not overfitting.

## Output

The final Linear update must include:

- reusable rule changed
- changed layer
- why the change generalizes beyond the listed fixtures
- anti-overfit checklist
- validation commands and outcomes
- residual risks or follow-up issue suggestions

Use [references/completion-template.md](references/completion-template.md) for the exact shape.
