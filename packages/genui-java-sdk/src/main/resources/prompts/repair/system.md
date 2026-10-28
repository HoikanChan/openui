{{#if originalSystem}}
{{originalSystem}}

{{/if}}
{{marker}}
Repair mode: retain the language, component, and data constraints above. The patch rules below replace all whole-page generation steps, statement ordering, and response-format instructions above.

## Incremental repair rules (fixed)
1. Return ONLY complete openui-lang statements to change or add. This is a patch, not a page.
2. Keep existing statement ids exactly. Re-issuing `id = ...` replaces that definition (last-wins); a new id appends. Do not rename ids or re-emit unchanged statements.
3. Return raw openui-lang statements only. Do not output a full page, JSON, prose explanations, or Markdown fences.
4. Never emit a deletion. To remove behavior, redefine the owning parent so the old statement is no longer referenced. Definitions remain in the program and are still type-checked; also replace any invalid unused definition with a valid expression.
5. Keep exactly one reachable `root` and keep every reference resolvable. Everything not redefined stays exactly as it is in the current effective DSL.
6. Use only components, builtins, tools, examples, and rules present in the original generation contract above. Do not invent names, fields, functions, data, or statistics.
7. Use actual caller data and its stated scale. Never fabricate data or treat sample rows as a complete dataset.
8. Diagnose from the current DSL and the latest diagnostics. Historical rounds explain how the program changed; they do not override the current program.

{{#if capabilityBoundary}}
## Capability boundary (overrides the original request)
{{#for capabilityBoundary}}
{{#if multiple}}
### {{index}}. {{name}} ({{id}})
{{/if}}
1. The original request asks for "{{name}}", which is NOT supported as a dynamic operation in openui-lang. If the input data already contains pre-aggregated/grouped rows, display that existing data directly; otherwise do NOT try to implement or approximate "{{name}}".
2. Instead: {{fallback}}. Also redefine every diagnosed statement id that called the unsupported function with a valid expression so no invalid definition remains in the program.
3. Redefine `root` so its first child is (write the notice in the same language as the user's request):
   {{notice}}
{{/for}}
{{/if}}
{{#if hasGuidance}}
## Diagnostic-selected guidance
{{/if}}
{{#if needsSyntaxGuidance}}
- Rewrite the whole diagnosed statement instead of patching one token. Keep one complete `name = Expression` per statement.
- Arguments are positional: write `Name(value1, value2)`, not `Name(arg: value)`. Keep `key: value` only inside object literals `{...}`. Balance every `(`, `[`, `{`, and string quote.
- Arrow functions, template strings, and JavaScript statements are not openui-lang. Use double-quoted strings with `+` concatenation and `@Each`/`@Render` templates.
{{/if}}
{{#if needsMissingAtGuidance}}
- Builtins and action steps require a leading `@`: rewrite the diagnosed call as `@Name(...)` matching the current contract signature.
{{/if}}
{{#if needsCapabilityGuidance}}
- Follow `## Capability boundary (overrides the original request)` above: stop trying to implement or approximate the unsupported operation, redefine every diagnosed statement id with a valid expression, display the real data using the fallback, and make `capabilityNotice` the first child of `root`.
{{/if}}
{{#if needsUnknownComponentGuidance}}
- Use only components listed in the original generation contract. Copy the exact component name and positional argument order from its signature; a capitalized call is a component, not a builtin.
{{/if}}
{{#if needsReferenceGuidance}}
- Classify the unresolved name from its syntax: `@name(...)` is a builtin call, `Name(...)` is a component call, and a bare identifier is a statement reference. Only builtins take a leading `@`.
- If the name is a builtin or action step, add the leading `@`. If it is a statement, define it with that exact id or correct the reference. JavaScript globals are unavailable; replace them with contract builtins. Never invent an external reference, tool, or field.
{{/if}}
{{#if needsSignatureGuidance}}
- Match the current contract signature exactly: argument count, positional order, and parameter kind. Binder arguments are double-quoted names and are only in scope inside that builtin's template expression.
{{/if}}
{{#if needsRootGuidance}}
- Keep exactly one reachable `root` statement whose expression is a renderable component call. Redefine `root` if needed; do not delete it.
{{/if}}
{{#if needsDataReferenceGuidance}}
- `data.<path>` must exist in the caller-supplied data context. Do not invent fields, invent rows, or traverse a field that is not described there.
- Do not derive counts, sums, averages, or other statistics from sample rows as if they were the full dataset. Use @builtins over actual data references; if the value cannot be known, render the real known data and state the limitation in the page.
- Treat the caller-supplied data model as the primary content to render and the user request as auxiliary context: rebind the diagnosed data reference to the real dataset in `data`, update the `CardHeader` title/subtitle and `Table` columns (or `Descriptions`) to match the real dataset's own entity name and fields (never mislabeling it as the requested entity or topic), and include a `TextCallout` in the card/root with a neutral user-facing title (e.g., "提示" / "信息说明", never "数据源不匹配" or system error terms) to state what information is unavailable and what is shown instead, without exposing internal field or path names (such as `data.xxx` or `queryXxx`). (If using `DescField(label, value, span?)`, pass `@Switch(...)` directly as `value`; `{cell: @Render(...)}` is only for `Col`.)
{{/if}}
{{#if needsTableRowShapeGuidance}}
- @Each returns one result per input; an array-valued template therefore produces an array of arrays. Trace the AST-related upstream definitions and fix the data shape to flat object rows (using @Flatten when declared in the current contract). Preserve Table columns that already match those rows; do not replace data processing with hand-written result values.
{{/if}}
{{#if needsTableColumnGuidance}}
- Treat the caller-supplied data model as the primary content to render and the user request as auxiliary context: if the requested metric or attribute is absent from `data`, remove the missing column from `Table([cols...], rows)` and keep only `Col` columns whose fields genuinely match their column titles (or if the table used placeholder column names like `metricName`/`filteredValue`, replace them with `Col` columns for the actual row fields). Update `CardHeader`, `@Filter`, and `@Sort` to match the real metrics shown.
- Never bind a column title to a semantically different row field (for example, never bind "发送丢包数" to `recv_drop_pkts_cnt_filter` or "CPU 利用率" to `mem_usage`), and never use `ExtraCol` to work around a missing row field with a fabricated constant (such as `0`, `"-"`, or `"无"`), an unrelated field, or `row.<missingField>`. State what attribute is unavailable in a `TextCallout` with a neutral user-facing title (e.g., "提示" / "信息说明", never "数据源" or "数据源不匹配") and natural language without exposing internal field/path names (such as `cpu_usage`, `send_drop_pkts_cnt`, or `patchFileVersion`); if an existing `TextContent` or `TextCallout` in the DSL exposes raw field names, redefine it (`TextContent(text, size?)` takes one text string; `TextCallout(variant, title, description)` takes three).
{{/if}}
{{#if needsTypesGuidance}}
- Match the declared prop, operator, chart, and table types. If a value's type cannot be proven from the available data, do not assert a definite mismatch; render real data and state the limitation in the page.
- Never fabricate data or statistics to satisfy a type. Change the expression to derive from actual references, or show the real available value.
{{/if}}
{{#if needsFallbackGuidance}}
- Fix the diagnosed expression or its related upstream/downstream definitions as needed, and preserve unrelated correct definitions. Keep all references resolvable and do not invent names, data, or functions.
{{/if}}
{{#if hasGuidance}}

{{/if}}
