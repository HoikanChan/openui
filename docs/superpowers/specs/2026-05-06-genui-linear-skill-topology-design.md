# GenUI Linear Skill Topology Design

**Date:** 2026-05-06
**Status:** Approved

## Context

The current Linear-related workflow is split across:

- `eval-loop-issue-handoff`
- `genui-capability-fix-handoff`
- `linear-issue-handoff`
- the bundled Linear plugin skill and tools

This separation is understandable at the naming level, but it creates two operational problems:

1. Triggering is unstable. Agents have to decide between multiple partially-overlapping skills for GenUI capability work.
2. Execution is not closed. `genui-capability-fix-handoff` describes implementation work, but it delegates important completion steps such as workpad updates, validation evidence, and screenshot handling to another skill.

The result is that code work often finishes before Linear is fully updated. Validation screenshots are a visible symptom, but the underlying problem is broader: the implementation path has no single owner for the full issue lifecycle.

## Goals

- Reduce GenUI capability work to two clear user-facing entry points.
- Make skill selection obvious from the starting artifact: eval findings versus an existing Linear issue.
- Give one skill end-to-end ownership of existing GenUI capability issues, including implementation, validation, and Linear closeout.
- Reuse one shared image upload capability for both eval evidence and validation evidence.
- Remove `handoff` as a required user-facing concept in the GenUI capability workflow.

## Non-Goals

- Do not redesign the generic Linear plugin skill.
- Do not optimize for all non-GenUI Linear workflows in this pass.
- Do not introduce a third user-facing skill just for image uploads.
- Do not require screenshot upload when no local screenshot evidence exists.

## Target Topology

The GenUI Linear workflow should expose exactly two primary skills and one internal shared capability.

### 1. `genui-eval-to-linear-issue`

This skill is the only entry point for turning eval, benchmark, fuzz, or e2e findings into new Linear capability issues.

It owns:

- reading eval artifacts
- grouping failures into reusable capability classes
- preparing evidence-first issue bodies
- creating new Linear issues
- uploading eval screenshots through the shared upload capability when screenshots exist

It does not own:

- code changes
- ongoing work on an existing issue
- validation closeout for an implementation task

### 2. `genui-capability-issue-execution`

This skill is the only entry point for existing GenUI capability issues that are being implemented, validated, or closed out.

It owns:

- reading the issue and extracting the capability goal
- implementing the smallest reusable fix
- running validation
- updating the single `## Codex Workpad`
- attaching validation evidence
- uploading local validation screenshots through the shared upload capability when screenshots exist
- recording blockers, residual risk, and next-state readiness

This skill is the closure owner for the GenUI capability workflow. It must not rely on a second user-facing skill to finish Linear synchronization.

### 3. `linear-evidence-upload`

This is a shared sub-skill or helper capability, not a user-facing entry point.

It owns one narrow job:

- turn a local image file into a Linear `assetUrl` and a Markdown image reference, or return a structured failure result

It should be callable by both primary skills, but it should never become a third workflow decision for the user or the agent.

## Entry Rules

### Use `genui-eval-to-linear-issue` when:

- the starting point is an eval run or eval artifacts
- the task is to create new Linear capability issues
- the task is to summarize findings into backlog items

### Do not use `genui-eval-to-linear-issue` when:

- an existing Linear issue already defines the work target
- the task is to implement, validate, or update a current issue

### Use `genui-capability-issue-execution` when:

- a Linear capability issue already exists
- the task is to fix, validate, continue, close out, or unblock that issue
- the task includes updating workpad content, attaching screenshots, or writing validation notes for that issue

### Do not use `genui-capability-issue-execution` when:

- the task is issue creation from a raw eval run
- the task is generic Linear administration unrelated to the GenUI capability flow

### Routing rule

If the prompt mentions an existing GenUI capability issue together with implementation, validation, progress update, closeout, or screenshot evidence, route to `genui-capability-issue-execution` directly. Do not route through a separate handoff skill.

## Shared Upload Capability

`linear-evidence-upload` should encapsulate the official Linear two-step upload flow:

1. read local file metadata and bytes
2. request `fileUpload(contentType, filename, size)` from Linear
3. `PUT` the raw bytes to `uploadUrl` with the required headers
4. return:
   - `assetUrl`
   - Markdown image snippet
   - upload metadata

If upload fails, it must return a structured error that includes:

- local path
- attempted content type
- retry result
- failure summary

The caller owns the decision to retry, log, or surface the failure. The upload helper owns accurate execution and error reporting.

## Completion Contract

`genui-capability-issue-execution` may finish only in one of these states.

### Completed

All of the following are true:

- implementation work for the current scope is done
- validation has run and exact results are recorded
- the single `## Codex Workpad` is current
- if local validation screenshots exist, they were either uploaded and embedded, or upload failure was explicitly recorded in Linear
- if a PR exists, it is linked to the issue
- issue state is advanced only when its documented completion bar is satisfied

### Blocked

This state is allowed only when Linear is updated with:

- the exact blocker
- impact on the issue
- work already completed
- the explicit unblock condition

### Needs Human Decision

This state is allowed when implementation cannot safely continue because of ambiguity or conflicting constraints. The unresolved decision must be written back to Linear, not kept only in local notes.

## Screenshot Rule

Screenshot handling is mandatory when evidence exists, but it is not a separate workflow.

If validation or eval work produces a local screenshot with evidence value:

- the primary skill must call `linear-evidence-upload`
- upload failure must never be silent
- the resulting issue or workpad update must contain either the embedded image or a precise failure note

This keeps screenshot handling inside the main workflow owner instead of turning it into a separate handoff step.

## Remove `linear-issue-handoff` From The Main Path

`linear-issue-handoff` should not remain a required entry point for GenUI capability work.

Its useful content should be redistributed:

- workpad format and update rules move into `genui-capability-issue-execution`
- screenshot upload contract moves into `linear-evidence-upload`
- state-flow and PR-linking rules move into `genui-capability-issue-execution`

The skill itself can be retired from the GenUI capability path. If a future need appears for non-GenUI generic Linear coordination, that can be reintroduced as a separate, explicitly generic workflow.

## Migration Plan

1. Rename `eval-loop-issue-handoff` to `genui-eval-to-linear-issue`.
2. Replace `genui-capability-fix-handoff` with `genui-capability-issue-execution`.
3. Remove the instruction that capability execution must combine with `linear-issue-handoff`.
4. Extract the upload contract into `linear-evidence-upload`.
5. Inline workpad, validation, state, and screenshot-closeout rules into `genui-capability-issue-execution`.
6. Remove `linear-issue-handoff` from the primary skill inventory for this workflow.

## Success Criteria

- A user or agent chooses between exactly two GenUI Linear entry points.
- Existing GenUI capability issues are handled by one closure-owning skill.
- Validation screenshots stop depending on a second visible skill being triggered.
- Linear workpad, validation notes, screenshots, PR linkage, and state updates are completed as part of the same execution flow.
- The term `handoff` is removed from the main GenUI capability implementation path.
