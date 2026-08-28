# Java Validation Actionable Issues Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Centralize issue reduction, expose actionable diagnostics, eliminate type cascades, and regenerate a low-noise validation report.

**Architecture:** Add one pure `ValidationIssueReducer` module at the validation seam. Keep raw diagnostics compatible, route `ValidationResult` and repair prompts through the reducer, and stop operator mismatch inference at its source.

**Tech Stack:** Java 21, JUnit 5, Maven, existing validation corpus runner.

---

### Task 1: Define actionable issue behavior test-first

**Files:**
- Create: `packages/genui-java-sdk/src/test/java/com/huawei/cloudsop/genui/core/validation/ValidationIssueReducerTest.java`
- Create: `packages/genui-java-sdk/src/main/java/com/huawei/cloudsop/genui/core/validation/ValidationIssueReducer.java`
- Modify: `packages/genui-java-sdk/src/main/java/com/huawei/cloudsop/genui/core/validation/ValidationResult.java`
- Modify: `packages/genui-java-sdk/src/test/java/com/huawei/cloudsop/genui/core/validation/ValidationModelTest.java`

- [x] Add failing tests for exact deduplication, syntax-statement dominance, root-cause-hinted references, Table row-shape dominance, root-tail suppression, ordering, and immutability.
- [x] Run `mvn -q -f packages/genui-java-sdk/pom.xml -Dtest=ValidationIssueReducerTest test` and confirm the new interface is missing.
- [x] Implement the pure reducer and `ValidationResult.actionableIssues()`.
- [x] Run `ValidationIssueReducerTest,ValidationModelTest` and confirm all tests pass.

### Task 2: Replace repair-local suppression

**Files:**
- Modify: `packages/genui-java-sdk/src/main/java/com/huawei/cloudsop/genui/core/validation/repair/ReaskPromptBuilder.java`
- Modify: `packages/genui-java-sdk/src/test/java/com/huawei/cloudsop/genui/core/validation/repair/ReaskPromptBuilderTest.java`

- [x] Make `ReaskPromptBuilder` call `ValidationIssueReducer.actionable(issues)` and delete its private cascade rules.
- [x] Preserve the existing prompt behavior tests and add coverage for Table row-shape dominance.
- [x] Run `ReaskPromptBuilderTest` and confirm all tests pass.

### Task 3: Stop operator mismatch cascades

**Files:**
- Modify: `packages/genui-java-sdk/src/test/java/com/huawei/cloudsop/genui/core/validation/type/StaticTypeValidationTest.java`
- Modify: `packages/genui-java-sdk/src/main/java/com/huawei/cloudsop/genui/core/validation/type/ProgramTypeValidator.java`

- [x] Add a failing test proving an array `+` error produces `type-operator-mismatch` without a downstream `Table.rows` mismatch.
- [x] Return an unknown type immediately after an obvious operator mismatch.
- [x] Run `StaticTypeValidationTest` and confirm all tests pass.

### Task 4: Regenerate report and refactor documentation

**Files:**
- Modify: `packages/genui-java-sdk/README.md`
- Modify: `packages/genui-java-sdk/docs/generated-dsl-interception-report.md`
- Regenerate: `validation-test/validation-results.md`

- [x] Document raw versus actionable issues and the reducer invariants.
- [x] Update the corpus runner to print both views and regenerate all 49 cases.
- [x] Check the high-noise cases and record their reduced counts.

### Task 5: Verify, commit, and review

- [x] Run `mvn -q -f packages/genui-java-sdk/pom.xml -Dtest='*,!PromptGoldenTest' test`.
- [x] Run `mvn -q -f packages/genui-java-sdk/pom.xml -DskipTests package` and `git diff --check`.
- [x] Stage only Java SDK Validation work and intentional validation-test artifacts; leave unrelated workspace changes unstaged.
- [x] Commit the implementation.
- [x] Run the requested two-axis code review against pre-change commit `4d0da71d50b8272d63027cbba7eae2d1967fcdcb` and fix material findings before final handoff.
