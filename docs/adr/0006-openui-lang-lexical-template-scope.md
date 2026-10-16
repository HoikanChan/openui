---
status: accepted
---

# Use lexical scope for extracted OpenUI Lang templates

OpenUI Lang keeps ordinary named-statement syntax for extracted `@Each` templates. A named template and its transitive statement dependencies inherit bindings from the use site; deferred `@Render` expressions capture those bindings, while their declared call-time bindings shadow captured names. This preserves compact, generation-friendly DSL without adding function syntax, and replaces accidental scope behavior with one lexical rule.

Inline-only templates and a new `Template(...)` declaration were rejected: inline-only expressions make generated UI deeply nested and difficult to repair, while a second declaration syntax adds model-visible complexity without adding necessary capability.

