# GenUI Java SDK

Java 21 SDK that assembles GenUI system prompts and drives an LLM to generate openui-lang UI from host data. This context covers the prompt-side data handling (how large host data is characterized before being embedded in a prompt) and the repair-side vocabulary (how validation failures are explained back to the model).

## Language

**Render Data Model**:
The full host data the SDK forwards to the runtime as the seq=0 `dataModel` envelope; the rendered UI binds against this, so it is never sampled or reduced.
_Avoid_: response, payload, raw data

**Prompt Data Model**:
A lossy, characterized view of the same host data embedded in the system prompt purely so the model understands shape and characteristics; never rendered, safe to sample aggressively.
_Avoid_: sampled data, compressed data

**Characterization**:
The deterministic, LLM-free transform that turns the Render Data Model into the Prompt Data Model.
_Avoid_: compression (implies recoverability, which is not a goal), summarization

**Enum Domain**:
The complete set of distinct values of a low-cardinality string field, surfaced so the model can generate exhaustive `@Switch` cases without dropping any.
_Avoid_: categories, sample values

**Sample Rows**:
A few deterministic, representative elements of a large array kept verbatim so the model sees realistic field shapes.
_Avoid_: examples, preview

**Sidecar**:
Characterization metadata (counts, inferred schema, Enum Domains) presented out-of-band alongside the data tree rather than inside it, so that `data.<field>` reference paths in the tree stay valid.
_Avoid_: annotations, metadata block

**Reask Prompt**:
The deterministic repair message sent back to the model after a failed generation, presenting the validation issues it must fix.
_Avoid_: retry prompt, error prompt

**Derived Issue**:
A validation issue that exists only because another failure on the same statement corrupted parsing or materialization; it describes a symptom, not a cause.
_Avoid_: cascade error, secondary error

**Cascade Suppression**:
Dropping Derived Issues from a Reask Prompt so the repair model sees causes instead of symptoms. Applies only to the prompt view — never to the validation verdict itself.
_Avoid_: dedup (different concern), filtering

**Root-cause Hint**:
A hint that names the underlying language-subset violation behind an error (e.g. a JS global that does not exist in openui-lang, or a builtin missing its `@`). Exempt from Cascade Suppression because it *is* the explanation.
_Avoid_: suggestion, tip

## Relationships

- A **Render Data Model** is reduced by **Characterization** into a **Prompt Data Model**
- A **Prompt Data Model** is a same-shape data tree (**Sample Rows** in place of full arrays) plus a **Sidecar**
- A **Sidecar** carries the **Enum Domain** and counts that **Sample Rows** alone cannot guarantee
- A **Reask Prompt** presents validation issues after **Cascade Suppression**; a **Root-cause Hint** always survives suppression
- A **Derived Issue** is what **Cascade Suppression** removes

## Flagged ambiguities

- "data model" was used to mean both the full runtime data and the prompt copy — resolved: **Render Data Model** (full, rendered) vs **Prompt Data Model** (characterized, never rendered). The same `request.response()` feeds both paths but only the prompt path is characterized.
