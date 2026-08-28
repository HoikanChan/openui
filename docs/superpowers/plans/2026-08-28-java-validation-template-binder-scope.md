# Java Validation Template Binder Scope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Java Validation accept separately declared templates that use an `@Each` or `@Render` binder through their reference path, while still rejecting genuinely unbound template variables.

**Architecture:** Preserve the current AST and type checker. Change only the semantic materialization walk so its `scopedRefs` set is forwarded when a `Ref` resolves another statement; the existing recursion-stack cycle guard continues to prevent loops. Lock the behavior through full-validator tests using the default component contract.

**Tech Stack:** Java 21, JUnit 5, Maven, existing OpenUI parser and `DefaultOpenuiLangValidator`.

---

### Task 1: Propagate binder scope through referenced template statements

**Files:**
- Modify: `packages/genui-java-sdk/src/test/java/com/huawei/cloudsop/genui/core/validation/type/StaticTypeValidationTest.java`
- Modify: `packages/genui-java-sdk/src/main/java/com/huawei/cloudsop/genui/core/validation/semantic/ProgramAnalyzer.java`
- Regenerate: `validation-test/validation-results.md`

- [x] **Step 1: Add a failing valid-template regression test**

Add a test using the real default contract. The template and its Table are separate statements, and the Table row field exists so binder propagation is the only behavior under test:

```java
@Test
void acceptsEachBinderAcrossReferencedTemplateStatements() {
    String dsl = "root = Tabs(@Each(data.groups, \"group\", groupTemplate))\n"
            + "groupTemplate = TabItem(group.model, group.model, [groupTable])\n"
            + "groupTable = Table([nameCol], group.devices)\n"
            + "nameCol = Col(\"Device Name\", \"name\")";
    Map<String, Object> dataModel = Map.of("groups",
            List.of(Map.of("model", "CE12804S", "devices", List.of(Map.of("name", "DC1-spine-01")))));

    ValidationResult result = validate(dsl, dataModel);

    assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
}
```

- [x] **Step 2: Run the valid-template test and verify RED**

Run:

```bash
mvn -q -f packages/genui-java-sdk/pom.xml \
  -Dtest=StaticTypeValidationTest#acceptsEachBinderAcrossReferencedTemplateStatements test
```

Expected: FAIL because `ProgramAnalyzer.resolveRef` restarts materialization with an empty scoped-binder set, producing `unresolved-ref` for `group`.

- [x] **Step 3: Add the unbound-template counterexample**

Add a test proving binder names are not globals:

```java
@Test
void rejectsReferencedTemplateWhenEachBinderIsNotInScope() {
    String dsl = "root = groupTemplate\n"
            + "groupTemplate = TabItem(group.model, group.model, [])";

    ValidationResult result = validate(dsl, Map.of("groups", List.of()));

    issue(result, "unresolved-ref");
}
```

Run:

```bash
mvn -q -f packages/genui-java-sdk/pom.xml \
  -Dtest=StaticTypeValidationTest#rejectsReferencedTemplateWhenEachBinderIsNotInScope test
```

Expected: PASS before and after the fix.

- [x] **Step 4: Forward the active binder scope while resolving references**

Change `ProgramAnalyzer` so both materialization and expression walking pass `scopedRefs` into reference resolution:

```java
if (node instanceof AstNode.Ref ref) {
  return scopedRefs.contains(ref.n()) ? DYNAMIC : resolveRef(ref.n(), ctx, scopedRefs);
}
```

```java
private Object resolveRef(String name, WalkCtx ctx, Set<String> scopedRefs) {
  if (ctx.visited.contains(name)) {
    ctx.unresolved.add(new UnresolvedRef(name, ctx.currentStatementId));
    return null;
  }
  if (!ctx.syms.containsKey(name)) {
    if (ctx.externalRefs.contains(name)) return DYNAMIC;
    ctx.unresolved.add(new UnresolvedRef(name, ctx.currentStatementId));
    return null;
  }
  AstNode target = ctx.syms.get(name);
  if (ctx.unreached != null) ctx.unreached.remove(name);
  if (target instanceof AstNode.Comp comp && Builtins.isReservedCall(comp.name())) {
    return DYNAMIC;
  }
  ctx.visited.add(name);
  String prev = ctx.currentStatementId;
  ctx.currentStatementId = name;
  try {
    return materializeValue(target, ctx, scopedRefs);
  } finally {
    ctx.currentStatementId = prev;
    ctx.visited.remove(name);
  }
}
```

```java
if (node instanceof AstNode.Ref ref) {
  if (!scopedRefs.contains(ref.n())) resolveRef(ref.n(), ctx, scopedRefs);
  return;
}
```

Update every `resolveRef` caller to provide the active scope; use `Set.of()` only at an actual top-level entry.

- [x] **Step 5: Run focused tests and verify GREEN**

Run:

```bash
mvn -q -f packages/genui-java-sdk/pom.xml \
  -Dtest=StaticTypeValidationTest,SemanticValidationTest test
```

Expected: PASS with no `unresolved-ref` in the valid example and an `unresolved-ref` in the invalid counterexample.

- [x] **Step 6: Run the relevant Java SDK regression suite**

Run:

```bash
mvn -q -f packages/genui-java-sdk/pom.xml -Dtest='*,!PromptGoldenTest' test
mvn -q -f packages/genui-java-sdk/pom.xml -DskipTests package
git diff --check
```

Expected: all commands exit 0. `PromptGoldenTest` stays excluded because the workspace already contains unrelated base-contract snapshot drift.

- [x] **Step 7: Replay the validation corpus**

Compile and run the existing temporary corpus runner against all four files, then inspect `rw-003-grouped`:

```bash
javac -cp packages/genui-java-sdk/target/classes /tmp/ValidationReplay.java
java -cp /tmp:packages/genui-java-sdk/target/classes:/Users/chenxie/.m2/repository/com/alibaba/fastjson2/fastjson2/2.0.61/fastjson2-2.0.61.jar \
  ValidationReplay validation-test/validation-results.md validation-test/error_detail.txt \
  validation-test/1.json validation-test/2.json validation-test/3.json validation-test/4.json
```

Expected: `rw-003-grouped` has no `unresolved-ref "group"`. Its independent `type-table-column-missing` issues for `model` and `count` remain because those fields do not exist in `group.devices` rows.

- [x] **Step 8: Preserve the user's Git state**

Do not stage or commit. Report only the files changed for this behavior and the verification evidence.
