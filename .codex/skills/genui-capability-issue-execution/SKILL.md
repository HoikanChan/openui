name: genui-capability-issue-execution
description: Use when implementing, validating, or closing out an existing GenUI Linear capability issue.
---

# GenUI Capability Issue Execution

Use this skill when implementing an existing GenUI capability issue. The goal is not to make the listed fixtures pass by any means; the goal is to strengthen a reusable data-shape, prompt, helper, or component capability and report evidence that the change is not fixture-specific.

This skill is the single owner for the execution lifecycle of an existing GenUI capability issue.

It must cover:

- issue reading
- implementation
- validation
- workpad updates
- validation evidence closeout
- screenshot upload through `linear-evidence-upload` when local screenshot evidence exists

Do not rely on a separate Linear workflow skill to finish the issue. This skill owns the closeout.

## Workflow

1. Read the Linear issue and identify the capability goal, problem class, primary evidence fixtures, required fix shape, and Generalization Gate.
2. Resolve the issue state, project context, and the single active `## Codex Workpad` comment.
3. Before editing, write a Generalization Plan in the workpad:
   - shared data/field/component shape
   - non-goals and boundaries
   - chosen fix layer: prompt rule, prompt example, runtime helper, component fallback, schema guidance, or mixed
   - primary evidence fixtures to inspect
4. Inspect the issue-provided `dataModel`, generated DSL, screenshots, and local source.
5. Implement the smallest reusable fix that addresses the capability class.
6. Run the normal validation for the touched area and eval verification when practical.
7. If local validation screenshots exist, call `linear-evidence-upload` and embed the result, or record the upload failure precisely.
8. Update Linear with Generalization Evidence using [references/completion-template.md](references/completion-template.md).

## Workpad Shape

Use exactly one active comment starting with:

```markdown
## Codex Workpad
```

Keep these sections current:

- `Plan`
- `Acceptance Criteria`
- `Validation`
- `Notes`

## State Rules

- `Backlog`: do not modify unless explicitly instructed
- `Todo`: move to `In Progress` before active implementation
- `In Progress`: keep the workpad current
- `Human Review`: do not make implementation changes until review context is read
- terminal states: do nothing unless explicitly instructed

Default Symphony Linear project slug: `genui-3513f1483173`.

Required environment:

- `LINEAR_API_KEY`
- `SYMPHONY_WORKSPACE_ROOT`

For screenshot upload, `LINEAR_API_KEY` must be present in the current shell environment. In normal setups this key is usually exported as a global zsh environment variable and inherited by the shell that launches Codex. If the key is missing, stop and record that screenshot upload could not proceed because the key was unavailable.

## Completion Contract

You may finish only in one of these states:

- `Completed`
- `Blocked`
- `Needs Human Decision`

`Completed` requires:

- validation commands recorded
- current `## Codex Workpad`
- validation screenshots uploaded when they exist, or upload failure explicitly recorded
- PR linked when applicable
- state advanced only after the completion bar is satisfied

If upload fails, record:

- local path
- retry result
- failure summary

Do not leave the workpad looking screenshot-complete when upload failed.

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

Before closeout, check the source diff for these red flags:

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
