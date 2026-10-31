# 0006 — Concise builtin signatures replace the contract rule language

## Status

Accepted (2026-10-31). Supersedes ADR 0005 decision item 4 in full and ADR 0003
decision D3's first layer (arity/return typing parsed from `signature`). It does not
change ADR 0003 D1/D2/D4 or the repository-root ADR on lexical template scope.

## Context

#77/#79/#83 moved builtin typing into a versioned `typeRule` object: arity and
per-parameter accept patterns plus a result rule over a finite set of type
operations, compiled by `RuleCompiler` and executed by `RuleEvaluator`. Each and
Render additionally declared their call layout in the contract through `input`,
`callForms`, `binder` and `nameSyntax`.

That design kept the same semantics in at least two places (`params` vs
`typeRule.parameters/arity`; `TypeExpr` vs `TypePattern`; binder layout rebuilt in
the loader, `BuiltinSpec`, a forwarding layer and three consumers) and made the
contract a small programming language that the frontend had to keep exporting.
#117 replaced it with a concise signature: name, display text, ordered parameters
and a return type. #118–#122 implemented that replacement.

## Decision

1. The contract builtin entry is exactly `name`, `signature`, `description`,
   `templateBuiltin`, `params` (`name`, `type`, `optional`, `variadic`) and
   `returnType`. `signature` and `description` are prompt text only.
   `returnType` is required for ordinary builtins; Each and Render may omit it,
   which is the only spec-sanctioned exception (#117 §2.2). "Required fields" in
   item 3 therefore does not mean every entry carries `returnType`.
2. `TypeExpr` is the only type model. It covers base types, arrays, `Record<string,
   T>`, object literals, unions and named type variables. `param(n)` and
   `when`/`else` are removed; `TypeVar` carries its name and binds per call.
3. The loader performs structural validation only: required fields, uniqueness,
   type grammar, named type-variable bindings, ordinary optional/variadic layout.
   It delegates Each/Render/Switch layout preconditions to `BuiltinAnalysis` and
   does not maintain a second per-function signature list.
4. Residual `typeRule`, `typeRulesVersion`, `inference`, `resultBehavior`, `input`,
   `callForms`, parameter `binder`/`nameSyntax`, or removed type syntax fails at
   load with the builtin name and the offending field. Nothing is silently ignored.
5. `SignatureInference` derives entry expectations from `params` and the result
   upper bound from `returnType`; `BuiltinAnalysis` owns Java-only semantics
   (Sort/Filter evidence, Flatten, object merge and enumeration, FormatDate shape
   refinement, Switch/Each/Render template scope and deferred analysis). `TypeOps`
   remains the general array/object kernel.
6. The old rule tree (`RulePlan`, `TypePattern`, `Rule`, `Operand`, `Op`),
   `RuleCompiler`, `RuleEvaluator`, `RuleCompilationException`, its `CallSite`
   protocol, `TypeExprEquals` and `LazyBuiltinBinders` are deleted. There is one
   signature model and one builtin module.

## Consequences

- Frontends must export the concise entry shape; an old contract is rejected rather
  than translated. `contractVersion` alone is not proof of compatibility — the SDK
  commit and the contract digest move together.
- `BuiltinSpec`/`ParamSpec` lost their rule/layout fields and compatible
  constructors. Integrations constructing them by hand must migrate to the six- and
  five-argument records.
- Behaviour stays in the existing diagnostics: First/Last empty input yields null,
  Format* returns remain UNPROVEN for undefined inputs, Filter still lowers the
  element-presence bound, and template scope/shadowing is unchanged.
- #91's requirement to export `typeRule` and template scope configuration is
  superseded by this decision; exporting complete signatures and display text
  remains the goal. #91 is not modified or closed here.
