{{#if originalRequest}}
{{#if capabilityBoundaryActive}}
## Original request (for context only)
{{else}}
## Original request
{{/if}}
{{originalRequest}}

{{/if}}
{{#if currentProgram}}
## Current effective DSL (the whole program; last-wins by statement id)
```openui
{{currentProgram}}
```

Every definition not re-emitted stays in force. Only override or append statements.

{{/if}}
{{#if rawOutput}}
### Current round raw model output (verbatim)
```text
{{rawOutput}}
```

{{/if}}
{{#if diagnostics}}
## Latest round-end diagnostics (authoritative for this round)
{{#for diagnostics}}
{{#if code}}
### {{code}}
{{/if}}
{{message}}
{{#if component}}
- Component: {{component}}
{{/if}}
{{#if path}}
- Path: {{path}}
{{/if}}
{{#if location}}
- Location: {{location}}
{{/if}}
{{#if hint}}
- Hint: {{hint}}
{{/if}}
{{/for}}

{{/if}}
{{#if relatedStatements}}
## Related statements for diagnosed ids (derived from the current AST/scope)
{{#for relatedStatements}}
- `{{id}}`: upstream/downstream related statements: {{ids}}
{{/for}}

{{/if}}
{{#if history}}
## Earlier round history (compact)
Historical diagnostics describe past states. They never override the current DSL or the latest diagnostics above.

{{#for history}}
### Earlier round {{index}}
{{#if valid}}
- Check: VALID
{{/if}}
{{#if invalid}}
- Check: INVALID
{{/if}}
{{#if codes}}
- Issue codes: {{codes}}
{{/if}}
{{#if raw}}
- Raw output:
```text
{{raw}}
```
{{/if}}

{{#if overLimit}}
(the first statement of this earlier output exceeds the history excerpt limit; read the current effective DSL instead)
{{/if}}
{{#if truncated}}
[history excerpt truncated at a complete statement boundary; the current effective DSL above is the authoritative state — do not patch from this excerpt alone]
{{/if}}
{{/for}}
{{/if}}
