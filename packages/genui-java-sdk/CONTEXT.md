# GenUI Java SDK

Java 21 SDK that assembles GenUI system prompts and drives an LLM to generate openui-lang UI from host data. This context covers the prompt-side data handling, in particular how large host data is characterized before being embedded in a prompt.

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

## Relationships

- A **Render Data Model** is reduced by **Characterization** into a **Prompt Data Model**
- A **Prompt Data Model** is a same-shape data tree (**Sample Rows** in place of full arrays) plus a **Sidecar**
- A **Sidecar** carries the **Enum Domain** and counts that **Sample Rows** alone cannot guarantee

## Flagged ambiguities

- "data model" was used to mean both the full runtime data and the prompt copy — resolved: **Render Data Model** (full, rendered) vs **Prompt Data Model** (characterized, never rendered). The same `request.response()` feeds both paths but only the prompt path is characterized.
