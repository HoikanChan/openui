# OpenUI Lang Core

OpenUI Lang Core defines the framework-independent language model used to parse, validate, and evaluate OpenUI Lang.

## Language

**Open Template**:
A named statement whose expression contains free iterator bindings supplied when it is instantiated by a template builtin such as `@Each`.
_Avoid_: Global component value, pseudo-template

**Template Instance**:
One evaluation of an Open Template with an independent lexical environment.
_Avoid_: Shared template object

**Iterator Binding**:
The local name introduced by `@Each` for the current array item and visible throughout the instantiated template dependency graph.
_Avoid_: Global variable, state variable

**Deferred Render Template**:
An `@Render` expression evaluated later by a render-function prop; it captures visible iterator bindings and receives its declared render bindings at invocation time.
_Avoid_: Each template, component template

**Render Binding**:
A call-time parameter declared by `@Render`, such as `v` or `row`, which shadows an outer binding with the same name.
_Avoid_: Iterator binding

**Unbound Template Reference**:
A free reference required by an Open Template but not provided by any lexical environment at its use site.
_Avoid_: Null value, missing data

