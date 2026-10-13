# Java Validation Template Binder Scope Design

## Goal

Allow an `@Each` or `@Render` binder to be used by a separately declared template statement, including statements referenced transitively by that template, while still rejecting the same binder when the template is reached outside its declaring call.

## Semantics

- A binder is introduced only by the body position of `@Each` or `@Render`.
- The binder scope follows references resolved from that body. It is not restricted to the physical line containing the builtin call.
- Scope propagation is transitive. If the body references `groupTemplate`, and `groupTemplate` references `groupTable`, both statements may use `group`.
- The binder never becomes a global symbol. A root or another statement that reaches the same template without the binder must receive `unresolved-ref`.
- Validation is contextual: visiting a statement once without a binder must not suppress or poison a later visit with a binder, and vice versa.
- Existing type inference remains responsible for checking the binder's fields and the types passed to components. Table column-versus-row-shape diagnostics remain independent from binder visibility.

## Valid Example

```openui
root = Tabs(@Each(data.groups, "group", groupTemplate))

groupTemplate = TabItem(group.model, group.model, [groupTable])
groupTable = Table([nameCol], group.devices)
nameCol = Col("Device Name", "name")
```

```json
{
  "groups": [
    {
      "model": "CE12804S",
      "devices": [{"name": "DC1-spine-01"}]
    }
  ]
}
```

This program must be `VALID`: `groupTemplate` and `groupTable` are separate statements, but both are reached from the `@Each` body with `group` in scope.

## Invalid Counterexample

```openui
root = groupTemplate
groupTemplate = TabItem(group.model, group.model, [])
```

This program must be `INVALID` with `unresolved-ref` for `group`, because no `@Each` or `@Render` call introduces that binder on the reference path.

## Implementation Boundary

The semantic reference walk will carry the current scoped-binder set when resolving a statement reference. Existing cycle protection remains a recursion-stack guard; it must not cache a statement as globally validated because the same statement can be reached under different binder scopes. The static type pass already passes its nested type scope through referenced statements and should remain unchanged unless the regression test exposes a separate failure.

This change does not execute templates, filters, or renderers and does not make binders global. It only fixes reference visibility during static validation.

## Verification

1. Add a failing semantic validation test for the valid transitive template example.
2. Confirm the test currently fails with `unresolved-ref` for `group`.
3. Add an invalid counterexample proving an unbound standalone template is still rejected.
4. Implement the smallest contextual reference-walk change.
5. Run the focused semantic/type tests and the Java SDK validation suite excluding the known unrelated prompt golden drift.
6. Replay `validation-test` and confirm `rw-003-grouped` no longer reports `unresolved-ref "group"`; any independent Table column-shape issues remain visible.
