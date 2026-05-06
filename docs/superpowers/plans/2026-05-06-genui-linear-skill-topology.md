# GenUI Linear Skill Topology Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current three-skill GenUI Linear flow with two primary skills plus one shared upload helper, while preserving the hard requirement that new capability issues include `dataModel`, generated DSL, and a screenshot.

**Architecture:** Repo-local GenUI skills in `.codex/skills/` become the source of truth for both issue creation and issue execution. `genui-eval-to-linear-issue` owns eval-to-issue creation, `genui-capability-issue-execution` owns existing issue implementation and Linear closeout, and `linear-evidence-upload` owns the narrow upload contract. The old `linear-issue-handoff` skill is removed from the GenUI path by copying only its durable rules into the execution skill and upload helper.

**Tech Stack:** Markdown skills, YAML skill metadata, ripgrep, git, existing Linear MCP tools

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `.codex/skills/eval-loop-issue-handoff/SKILL.md` | Replace via move | Rename and rewrite into `genui-eval-to-linear-issue` with hard issue-evidence requirements |
| `.codex/skills/eval-loop-issue-handoff/agents/openai.yaml` | Replace via move | Rename skill metadata for the new issue-creation entry point |
| `.codex/skills/eval-loop-issue-handoff/references/evidence-checklist.md` | Replace via move | Keep eval evidence checklist under the new skill |
| `.codex/skills/eval-loop-issue-handoff/references/issue-template.md` | Replace via move | Preserve the rule that every created issue must include `dataModel`, DSL, and screenshot |
| `.codex/skills/eval-loop-issue-handoff/references/linear-screenshot-upload.md` | Replace via move | Re-home screenshot upload reference or merge it into the helper |
| `.codex/skills/eval-loop-issue-handoff/references/triage-rules.md` | Replace via move | Preserve issue-grouping and triage boundaries |
| `.codex/skills/genui-capability-fix-handoff/SKILL.md` | Replace via move | Rename and rewrite into `genui-capability-issue-execution` with full closure ownership |
| `.codex/skills/genui-capability-fix-handoff/agents/openai.yaml` | Replace via move | Rename skill metadata for the execution entry point |
| `.codex/skills/genui-capability-fix-handoff/references/completion-template.md` | Replace via move | Preserve reusable-rule completion reporting |
| `.codex/skills/linear-evidence-upload/SKILL.md` | Create | Shared upload helper contract used by both primary skills |
| `.codex/skills/linear-evidence-upload/agents/openai.yaml` | Create | Helper metadata so the capability is discoverable by agents |
| `.codex/skills/linear-evidence-upload/references/upload-contract.md` | Create | Official `fileUpload -> PUT -> assetUrl` workflow and failure contract |
| `C:\Users\Administrator\.codex\skills\linear-issue-handoff\SKILL.md` | Retire or narrow | Remove it from the GenUI path and leave only a generic non-GenUI note if needed |
| `C:\Users\Administrator\.codex\skills\linear-issue-handoff/references/workpad.md` | Read-only source, then inline | Copy workpad shape into the execution skill |
| `C:\Users\Administrator\.codex\skills\linear-issue-handoff/references/state-flow.md` | Read-only source, then inline | Copy state-transition rules into the execution skill |
| `C:\Users\Administrator\.codex\skills\linear-issue-handoff/references/project-context.md` | Read-only source, then inline | Copy default project slug/env rules into the execution skill |
| `docs/superpowers/specs/2026-05-06-genui-linear-skill-topology-design.md` | Reference only | Approved design source for coverage review |

---

### Task 1: Rename the eval issue-creation skill

**Files:**
- Create: `.codex/skills/genui-eval-to-linear-issue/SKILL.md`
- Create: `.codex/skills/genui-eval-to-linear-issue/agents/openai.yaml`
- Create: `.codex/skills/genui-eval-to-linear-issue/references/evidence-checklist.md`
- Create: `.codex/skills/genui-eval-to-linear-issue/references/issue-template.md`
- Create: `.codex/skills/genui-eval-to-linear-issue/references/triage-rules.md`
- Modify: `.codex/skills/genui-eval-to-linear-issue/references/linear-screenshot-upload.md`
- Delete: `.codex/skills/eval-loop-issue-handoff/`

- [ ] **Step 1: Move the existing directory into the new skill name**

```powershell
Move-Item -LiteralPath .codex\skills\eval-loop-issue-handoff -Destination .codex\skills\genui-eval-to-linear-issue
```

- [ ] **Step 2: Rewrite `SKILL.md` header and entry rules**

Replace the frontmatter and opening sections with:

```md
---
name: genui-eval-to-linear-issue
description: Use when turning GenUI eval, benchmark, fuzz, or e2e findings into new Linear capability issues with required evidence.
---

# GenUI Eval To Linear Issue

Use this skill only when the starting point is an eval run or eval artifacts and the output is one or more new Linear capability issues.

Do not use this skill for:

- implementing an existing issue
- updating a workpad on an existing issue
- validation closeout for code changes
```

- [ ] **Step 3: Make the issue-evidence rule explicit in the skill body**

Add a hard-gate section near `Required Evidence`:

```md
## Issue Creation Hard Gate

Do not create a GenUI capability issue unless each primary evidence fixture includes:

- `dataModel`
- generated DSL
- screenshot evidence

If any of these are missing, stop and record which artifact is missing. Do not create a screenshot-free or DSL-free issue that appears complete.
```

- [ ] **Step 4: Update the issue template so the rule is impossible to miss**

In `references/issue-template.md`, keep the existing structure but add this line directly above the first fixture block:

```md
Every created issue must include all three artifact types for each primary evidence fixture: `dataModel`, generated DSL, and screenshot.
```

- [ ] **Step 5: Update the agent metadata**

Set `agents/openai.yaml` to:

```yaml
interface:
  display_name: "GenUI Eval To Linear Issue"
  short_description: "Create new Linear capability issues from GenUI eval evidence."
  default_prompt: "Use $genui-eval-to-linear-issue to turn this GenUI eval run into new Linear capability issues with required dataModel, DSL, and screenshot evidence."
```

- [ ] **Step 6: Verify the rename and evidence text**

Run: `rg -n "genui-eval-to-linear-issue|dataModel|generated DSL|screenshot" .codex\skills\genui-eval-to-linear-issue`
Expected: matches in `SKILL.md`, `issue-template.md`, and `openai.yaml`.

- [ ] **Step 7: Commit**

```bash
git add .codex/skills/genui-eval-to-linear-issue
git commit -m "refactor(skills): rename eval issue creation skill"
```

---

### Task 2: Replace the execution skill with a closure-owning skill

**Files:**
- Create: `.codex/skills/genui-capability-issue-execution/SKILL.md`
- Create: `.codex/skills/genui-capability-issue-execution/agents/openai.yaml`
- Create: `.codex/skills/genui-capability-issue-execution/references/completion-template.md`
- Delete: `.codex/skills/genui-capability-fix-handoff/`

- [ ] **Step 1: Move the existing directory into the new skill name**

```powershell
Move-Item -LiteralPath .codex\skills\genui-capability-fix-handoff -Destination .codex\skills\genui-capability-issue-execution
```

- [ ] **Step 2: Rewrite the skill purpose and remove the dependency on handoff**

Replace the header and workflow opening with:

```md
---
name: genui-capability-issue-execution
description: Use when implementing, validating, or closing out an existing GenUI Linear capability issue.
---

# GenUI Capability Issue Execution

This skill is the single owner for the execution lifecycle of an existing GenUI capability issue.

It must cover:

- issue reading
- implementation
- validation
- workpad updates
- validation evidence closeout
- screenshot upload through `linear-evidence-upload` when local screenshot evidence exists

Do not combine this skill with a separate Linear handoff skill to finish the issue. This skill owns the closeout.
```

- [ ] **Step 3: Inline the completion contract**

Add a `Completion Contract` section:

```md
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
```

- [ ] **Step 4: Update the metadata prompt**

Set `agents/openai.yaml` to:

```yaml
interface:
  display_name: "GenUI Capability Issue Execution"
  short_description: "Execute and close out an existing GenUI capability issue."
  default_prompt: "Use $genui-capability-issue-execution to implement this GenUI capability issue, run validation, update the workpad, and close out the Linear evidence."
```

- [ ] **Step 5: Preserve the reusable-rule completion template**

Keep `references/completion-template.md`, but rename the surrounding references to match the new skill path and make sure `Validation` remains part of the required output:

```md
### Validation
- `<command>`: <result>
- `<command>`: <result>
```

- [ ] **Step 6: Verify there is no remaining `combine with $linear-issue-handoff` wording**

Run: `rg -n "linear-issue-handoff|Combine this with" .codex\skills\genui-capability-issue-execution`
Expected: no matches.

- [ ] **Step 7: Commit**

```bash
git add .codex/skills/genui-capability-issue-execution
git commit -m "refactor(skills): add closure-owning capability execution skill"
```

---

### Task 3: Create the shared upload helper

**Files:**
- Create: `.codex/skills/linear-evidence-upload/SKILL.md`
- Create: `.codex/skills/linear-evidence-upload/agents/openai.yaml`
- Create: `.codex/skills/linear-evidence-upload/references/upload-contract.md`

- [ ] **Step 1: Create the skill skeleton**

Create `.codex/skills/linear-evidence-upload/SKILL.md` with:

```md
---
name: linear-evidence-upload
description: Use when a GenUI Linear workflow already decided a local image must be uploaded to Linear and embedded as Markdown.
---

# Linear Evidence Upload

This is a helper skill, not a user-facing workflow entry point.

Use it only when:

- a local image path already exists
- the caller already decided the image is required evidence
- the goal is to obtain a Linear `assetUrl` and Markdown image snippet

This skill does not decide whether an image should be uploaded. It only performs the upload contract and reports success or failure.
```

- [ ] **Step 2: Encode the upload contract**

Create `references/upload-contract.md` with:

```md
# Upload Contract

1. Read the local file.
2. Determine `contentType`, `filename`, and byte `size`.
3. Request Linear `fileUpload(contentType, filename, size)`.
4. `PUT` the raw bytes to the returned `uploadUrl` with the returned headers.
5. Return:
   - `assetUrl`
   - `![alt](assetUrl)` Markdown
   - upload metadata

If upload fails:

- retry once for transient failure
- return local path, retry result, and failure summary
- never silently swallow the failure
```

- [ ] **Step 3: Add helper metadata**

Create `agents/openai.yaml`:

```yaml
interface:
  display_name: "Linear Evidence Upload"
  short_description: "Upload a required local image to Linear and return embeddable Markdown."
  default_prompt: "Use $linear-evidence-upload to upload this local screenshot to Linear and return the assetUrl plus Markdown image snippet."
```

- [ ] **Step 4: Verify the helper is isolated**

Run: `rg -n "not a user-facing workflow entry point|assetUrl|never silently" .codex\skills\linear-evidence-upload`
Expected: all three concepts appear in the helper files.

- [ ] **Step 5: Commit**

```bash
git add .codex/skills/linear-evidence-upload
git commit -m "feat(skills): add shared Linear evidence upload helper"
```

---

### Task 4: Inline the useful handoff rules into the execution skill

**Files:**
- Modify: `.codex/skills/genui-capability-issue-execution/SKILL.md`
- Read: `C:\Users\Administrator\.codex\skills\linear-issue-handoff\references\workpad.md`
- Read: `C:\Users\Administrator\.codex\skills\linear-issue-handoff\references\state-flow.md`
- Read: `C:\Users\Administrator\.codex\skills\linear-issue-handoff\references\project-context.md`

- [ ] **Step 1: Inline the workpad contract**

Add this compact shape to the execution skill:

```md
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
```

- [ ] **Step 2: Inline the state-flow rules**

Add:

```md
## State Rules

- `Backlog`: do not modify unless explicitly instructed
- `Todo`: move to `In Progress` before active implementation
- `In Progress`: keep the workpad current
- `Human Review`: do not make implementation changes until review context is read
- terminal states: do nothing unless explicitly instructed
```

- [ ] **Step 3: Inline the default project context**

Add:

```md
Default Symphony Linear project slug: `genui-3513f1483173`.

Required environment:

- `LINEAR_API_KEY`
- `SYMPHONY_WORKSPACE_ROOT`
```

- [ ] **Step 4: Add the screenshot-closeout rule**

Add:

```md
If local validation screenshots exist, call `linear-evidence-upload`.

If upload fails, record:

- local path
- retry result
- failure summary

Do not leave the workpad looking screenshot-complete when upload failed.
```

- [ ] **Step 5: Verify the execution skill now stands alone**

Run: `rg -n "Codex Workpad|Backlog|In Progress|genui-3513f1483173|linear-evidence-upload" .codex\skills\genui-capability-issue-execution\SKILL.md`
Expected: all five concepts appear in the execution skill.

- [ ] **Step 6: Commit**

```bash
git add .codex/skills/genui-capability-issue-execution/SKILL.md
git commit -m "docs(skills): inline Linear closeout rules into execution skill"
```

---

### Task 5: Retire the old handoff skill from the GenUI path

**Files:**
- Modify: `C:\Users\Administrator\.codex\skills\linear-issue-handoff\SKILL.md`
- Optional delete: `C:\Users\Administrator\.codex\skills\linear-issue-handoff\references\*.md`

- [ ] **Step 1: Narrow the skill so it is clearly non-GenUI**

Replace the opening of `C:\Users\Administrator\.codex\skills\linear-issue-handoff\SKILL.md` with:

```md
# Linear Issue Coordination

This skill is for generic Linear coordination outside the GenUI capability workflow.

Do not use this skill for:

- GenUI capability issue implementation
- GenUI validation closeout
- GenUI screenshot evidence handling
```

- [ ] **Step 2: Remove the old upload section from the handoff skill**

Delete or replace the `## Validation Images` section with:

```md
GenUI screenshot upload now belongs to the repo-local `linear-evidence-upload` helper and the `genui-capability-issue-execution` workflow.
```

- [ ] **Step 3: Verify no repo-local GenUI skill still points at handoff**

Run: `rg -n "linear-issue-handoff" .codex\skills`
Expected: no matches.

- [ ] **Step 4: Commit**

```bash
git add C:\Users\Administrator\.codex\skills\linear-issue-handoff\SKILL.md .codex\skills
git commit -m "refactor(skills): remove handoff from GenUI Linear path"
```

---

### Task 6: Final verification and documentation sweep

**Files:**
- Test: `.codex/skills/genui-eval-to-linear-issue/`
- Test: `.codex/skills/genui-capability-issue-execution/`
- Test: `.codex/skills/linear-evidence-upload/`
- Test: `docs/superpowers/specs/2026-05-06-genui-linear-skill-topology-design.md`

- [ ] **Step 1: Verify the new skill names and old-name removal**

Run: `rg -n "eval-loop-issue-handoff|genui-capability-fix-handoff|linear-issue-handoff" .codex\skills`
Expected:
- no matches for `eval-loop-issue-handoff`
- no matches for `genui-capability-fix-handoff`
- no repo-local matches for `linear-issue-handoff`

- [ ] **Step 2: Verify the issue-creation hard requirement**

Run: `rg -n "dataModel|generated DSL|screenshot" .codex\skills\genui-eval-to-linear-issue`
Expected: the issue creation skill and template explicitly require all three artifacts.

- [ ] **Step 3: Verify the execution closeout requirement**

Run: `rg -n "Completion Contract|Codex Workpad|linear-evidence-upload|upload failed" .codex\skills\genui-capability-issue-execution`
Expected: all four phrases appear in the execution skill.

- [ ] **Step 4: Review the resulting tree**

Run:

```powershell
Get-ChildItem -Recurse .codex\skills\genui-eval-to-linear-issue, .codex\skills\genui-capability-issue-execution, .codex\skills\linear-evidence-upload
```

Expected: each skill has `SKILL.md`, `agents/openai.yaml`, and the planned reference files.

- [ ] **Step 5: Commit the final docs-only sweep if needed**

```bash
git add .codex/skills
git commit -m "docs(skills): finalize GenUI Linear skill topology"
```

---

## Self-Review

- Spec coverage:
  - Two primary skills: covered by Tasks 1 and 2.
  - Shared upload helper: covered by Task 3.
  - Removal of `handoff` from the main path: covered by Tasks 4 and 5.
  - Mandatory `dataModel` + DSL + screenshot for created issues: covered by Task 1 and verified again in Task 6.
- Placeholder scan:
  - No `TBD`, `TODO`, or unnamed files remain.
- Type and naming consistency:
  - The plan uses only `genui-eval-to-linear-issue`, `genui-capability-issue-execution`, and `linear-evidence-upload`.
