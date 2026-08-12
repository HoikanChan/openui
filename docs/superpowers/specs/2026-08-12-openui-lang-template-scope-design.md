# OpenUI Lang Extracted Template Scope Design

## Problem

OpenUI Lang already demonstrates extracted templates such as:

```openui
modelTabs = Tabs(@Each(data.grouped_by_model, "group", modelTabTpl))
modelTabTpl = TabItem(group.model, group.model, [modelTable])
modelTable = Table([countCol], group.devices)
countCol = Col("Count", "count", {cell: @Render("v", "row", TextContent("" + group.count))})
```

The language contract nevertheless describes `@Each` templates as inline-only. Runtime behavior is also implemented through recursive AST substitution rather than an explicit lexical rule. Immediate expressions such as `group.devices` are evaluated while the `@Each` child context exists, but deferred expressions can lose `group` unless the substitution walk reaches and captures every free reference before that context ends.

The exact example above captures `group` successfully in the current `lang-core` source. A production failure of that example therefore also requires verifying the consumed package/build version. The current repository has an older generated `dist` than the latest evaluator source and the package version was not changed by the latest evaluator commit.

## Decisions

1. Keep ordinary named statements as extracted templates; do not add `Template(...)` or function syntax.
2. `@Each(array, "item", template)` accepts an inline expression or a named template.
3. A named template and all transitive named statements reached from it are instantiated in the `@Each` lexical environment.
4. Every iteration creates an independent environment snapshot for ordinary iterator bindings.
5. A deferred `@Render` captures visible iterator bindings when its containing template is instantiated.
6. `@Render` bindings are supplied at invocation time and shadow captured bindings with the same name.
7. Nested `@Each` bindings follow nearest-scope shadowing. Leaving an inner template restores the outer binding.
8. `$state` is not captured as a snapshot; it continues to resolve from the store at evaluation time.
9. `@Each(..., @Render(...))` has no callable-template special meaning. `@Render` remains a deferred prop renderer.
10. Using an Open Template without supplying all required iterator bindings is a validation error, not a null fallback.

## Runtime model

The implementation may continue lowering captured ordinary values into literal AST nodes, but the operation must be defined as lexical template instantiation rather than unrestricted textual substitution.

```text
global environment
└─ @Each instance environment
   └─ group = current item
      └─ @Render invocation environment
         ├─ v = current prop value
         └─ row = current row
```

Lookup uses the nearest environment first. Template instantiation replaces only free references supplied by the current environment. When the walker enters a nested binder body, that binder name becomes shadowed and must not be captured from an outer environment.

For example, the outer `group` is used to evaluate the inner collection but not the inner body:

```openui
@Each(data.groups, "group",
  @Each(group.devices, "group", TextContent(group.name))
)
```

The first `group.devices` refers to the outer group; `group.name` refers to the inner device.

## Implementation boundaries

### Materialization

Materialization continues to inline the named statement dependency graph while preserving bindings introduced by `@Each` and `@Render`. This makes an extracted template equivalent to the corresponding inline expression.

### Template instantiation

Replace the unrestricted single-name substitution helper with a scope-aware instantiation helper. It receives a binding environment, walks the materialized AST immutably, captures free `Ref` nodes, and tracks binder names that shadow outer bindings.

Both ordinary `@Each` templates and deferred descendants use this single path. Delete the evaluator branch that interprets a direct `@Render` body as an `@Each` callback and binds its second parameter to the array index.

### Validation

Track the free iterator references required by each named template. At every template use site, compare those requirements with the lexical bindings in scope. Report an `unbound-template-reference` error containing the template name, missing binding, use site, and available bindings.

Shadowing is valid. A same-name nested binder may produce a non-blocking diagnostic for generation quality, but it must not fail parsing or trigger repair by itself.

### Contract and prompt

Replace inline-only guidance with one canonical rule:

```text
@Each(array, "item", template) evaluates the template once per item. The
template may be inline or a named statement. A named template and its
transitive statement dependencies may reference item. Every instance has an
independent lexical scope.
```

Generated base contracts must be regenerated from the source contract after this rule changes.

## Error behavior

Given:

```openui
root = Stack([modelTabTpl])
modelTabTpl = TextContent(group.name)
```

validation reports:

```text
Template "modelTabTpl" requires binding "group", but use site "root" does
not provide it. Available bindings: none.
```

The reference must not silently become `null` or an empty string.

## Tests

Tests cover the language runtime rather than any concrete `TabItem` implementation:

- an extracted `@Each` template captures its iterator inside a nested `@Render`;
- the same template can be instantiated by two `@Each` expressions without binding leakage;
- a nested `@Each` with the same binder name shadows the outer binder only in its body;
- an `@Render` binder shadows an outer iterator with the same name;
- `$state` remains live inside a captured deferred template;
- a named template used without its required binding produces a structured validation error;
- direct `@Each(..., @Render(...))` no longer receives special callable behavior;
- one-shot and streaming parsers produce equivalent scoped template ASTs.

## Release verification

In addition to source tests, build the package and verify that the produced artifact contains the new evaluator behavior. Publish or consume a version that uniquely identifies the change; source-only verification is insufficient when the application imports packaged output.

