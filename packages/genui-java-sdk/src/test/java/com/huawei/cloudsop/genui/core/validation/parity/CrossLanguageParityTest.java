package com.huawei.cloudsop.genui.core.validation.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Cross-language parity test (Task 8 / design D10).
 *
 * <p>The TypeScript {@code packages/lang-core} parser is the ORACLE for the Java validator's
 * <b>contract-error taxonomy</b> on the SUPPORTED SUBSET. This test loads the COMMITTED,
 * TS-generated {@code parity/oracle.json} from the classpath (the Java build runs NO Node),
 * rebuilds each case's {@link GenerationContract}, runs {@link DefaultOpenuiLangValidator} in
 * {@link ValidationMode#FINAL}, and asserts parity per the brief.
 *
 * <p>Regenerate the oracle after changing cases or the TS parser:
 * <pre>{@code pnpm --dir packages/lang-core run generate:validation-oracle}</pre>
 *
 * <h2>Parity mapping (TS → Java)</h2>
 * <ul>
 *   <li><b>Contract errors</b> (unknown-component / missing-required / null-required / excess-args /
 *       invalid-prop / inline-reserved): asserted EXACTLY — same {@code code}, {@code component},
 *       {@code path}, {@code statementId}. Java must also not emit EXTRA contract-error codes beyond
 *       the oracle for that case.</li>
 *   <li><b>unresolved</b>: TS {@code meta.unresolved} names ⟷ Java {@code unresolved-ref} issues,
 *       compared as a NAME SET (Java uses its own structural code; the referenced name is carried in
 *       the message).</li>
 *   <li><b>root / syntax</b>: asserted at the STRUCTURAL level only (not exact-string), because the
 *       Java structural codes are Java-authored — see KNOWN DIVERGENCES below.</li>
 * </ul>
 *
 * <h2>KNOWN DIVERGENCES (asserted at issue-CLASS level, per design "不追求完整 AST parity")</h2>
 * <ol>
 *   <li><b>Autoclose in FINAL mode.</b> TS {@code parse()} <i>always</i> auto-closes unbalanced
 *       brackets/strings, so the oracle records the {@code syntax-failure-unclosed} case as
 *       {@code status=valid}. Java FINAL does NOT auto-close (only STREAMING does), so it surfaces an
 *       {@code source="syntax"} diagnostic and is INVALID. For syntax cases this test asserts only
 *       that Java is INVALID and has a syntax-sourced issue — it does NOT demand TS's valid status.</li>
 *   <li><b>Structural codes differ.</b> TS exposes root/unresolved via {@code meta} (hasRoot flag,
 *       {@code meta.unresolved}); Java emits Java-authored structural codes
 *       {@code root-missing}/{@code root-not-renderable}/{@code unresolved-ref}. Compared at the
 *       structural level (root not resolved ⟷ a {@code source="root"} issue; unresolved by name set).</li>
 *   <li><b>Duplicate-id last-wins</b> is applied in {@code ProgramAnalyzer} (mirroring the TS
 *       {@code stmtMap} {@code Map.set}), not in the parser. Not exercised by the current cases.</li>
 * </ol>
 */
class CrossLanguageParityTest {

  /** The exact set of contract-error codes shared by TS materialize.ts and Java. */
  private static final Set<String> CONTRACT_ERROR_CODES =
      Set.of(
          "unknown-component",
          "missing-required",
          "null-required",
          "excess-args",
          "invalid-prop",
          "inline-reserved");

  private static final String ORACLE_RESOURCE = "parity/oracle.json";

  @TestFactory
  Stream<DynamicTest> javaValidatorMatchesTypeScriptOracle() {
    List<Object> cases = loadCases();
    return cases.stream()
        .map(raw -> Json.asObject(raw, "case"))
        .map(
            c -> {
              String name = String.valueOf(c.get("name"));
              return DynamicTest.dynamicTest(name, () -> assertParity(c));
            });
  }

  private void assertParity(Map<String, Object> testCase) {
    String name = String.valueOf(testCase.get("name"));
    String dsl = String.valueOf(testCase.get("dsl"));
    GenerationContract contract = buildContract(Json.asObject(testCase.get("contract"), "contract"));
    Map<String, Object> expected = Json.asObject(testCase.get("expected"), "expected");

    ValidationResult result =
        DefaultOpenuiLangValidator.INSTANCE.validate(
            ValidationRequest.builder()
                .dsl(dsl)
                .contract(contract)
                .rootName("root")
                .mode(ValidationMode.FINAL)
                .build());

    List<ValidationIssue> issues = result.issues();

    // ── KNOWN DIVERGENCE #1: syntax cases. TS auto-closes (records valid); Java FINAL does not. ──
    boolean isSyntaxCase = name.startsWith("syntax-");
    if (isSyntaxCase) {
      assertEquals(
          ValidationStatus.INVALID,
          result.status(),
          "[" + name + "] Java FINAL must reject unclosed input (TS auto-closes; structural divergence)");
      boolean hasSyntaxIssue = issues.stream().anyMatch(i -> "syntax".equals(i.source()));
      assertTrue(
          hasSyntaxIssue,
          "[" + name + "] expected a source=syntax diagnostic; got " + codesOf(issues));
      return; // contract/root parity not meaningful once syntax fails
    }

    // ── Contract errors: EXACT match on the supported subset ────────────────────
    List<ExpectedError> expectedErrors = expectedErrors(expected);
    List<ValidationIssue> javaContractErrors =
        issues.stream().filter(i -> CONTRACT_ERROR_CODES.contains(i.code())).collect(Collectors.toList());

    for (ExpectedError e : expectedErrors) {
      boolean matched =
          javaContractErrors.stream()
              .anyMatch(
                  i ->
                      i.code().equals(e.code)
                          && java.util.Objects.equals(i.component(), e.component)
                          && java.util.Objects.equals(i.path(), e.path)
                          && java.util.Objects.equals(i.statementId(), e.statementId));
      assertTrue(
          matched,
          "["
              + name
              + "] missing contract error "
              + e
              + " — Java contract errors were "
              + describe(javaContractErrors));
    }

    // Java must not emit EXTRA contract-error codes not present in the oracle for this case.
    Set<String> expectedCodes =
        expectedErrors.stream().map(x -> x.code).collect(Collectors.toCollection(LinkedHashSet::new));
    Set<String> javaCodes =
        javaContractErrors.stream().map(ValidationIssue::code).collect(Collectors.toCollection(LinkedHashSet::new));
    assertEquals(
        expectedCodes,
        javaCodes,
        "[" + name + "] contract-error code set mismatch (TS=oracle, Java=actual)");

    // ── unresolved: compare by NAME SET (Java carries the name in the message) ───
    Set<String> expectedUnresolved = new LinkedHashSet<>(strings(expected.get("unresolved")));
    Set<String> javaUnresolved =
        issues.stream()
            .filter(i -> "unresolved-ref".equals(i.code()))
            .map(CrossLanguageParityTest::unresolvedName)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    assertEquals(
        expectedUnresolved,
        javaUnresolved,
        "[" + name + "] unresolved-ref name-set mismatch");

    // ── root: STRUCTURAL. TS hasRoot==false ⟷ Java root-missing / root-not-renderable ──
    boolean expectedHasRoot = Boolean.TRUE.equals(expected.get("hasRoot"));
    if (!expectedHasRoot) {
      boolean hasRootIssue = issues.stream().anyMatch(i -> "root".equals(i.source()));
      assertTrue(
          hasRootIssue,
          "["
              + name
              + "] TS resolved no root; expected a source=root structural issue, got "
              + codesOf(issues));
    }
  }

  // ── contract (component → propsSchema) → GenerationContract ────────────────────

  private static GenerationContract buildContract(Map<String, Object> components) {
    LinkedHashMap<String, ComponentPromptSpec> specs = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : components.entrySet()) {
      Map<String, Object> propsSchema = Json.asObject(entry.getValue(), "propsSchema");
      specs.put(entry.getKey(), new ComponentPromptSpec("parity fixture", propsSchema));
    }
    return new GenerationContract(
        "parity", "root", specs, List.of(), List.of(), List.of(), List.of());
  }

  // ── oracle loading ─────────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private static List<Object> loadCases() {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    try (InputStream in = loader.getResourceAsStream(ORACLE_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(
            ORACLE_RESOURCE + " not found on test classpath — regenerate via "
                + "`pnpm --dir packages/lang-core run generate:validation-oracle`");
      }
      String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      Map<String, Object> root = Json.asObject(Json.parse(json), "oracle");
      return Json.asList(root.get("cases"), "cases");
    } catch (java.io.IOException e) {
      throw new IllegalStateException("failed to read " + ORACLE_RESOURCE, e);
    }
  }

  // ── helpers ─────────────────────────────────────────────────────────────────────

  private record ExpectedError(String code, String component, String path, String statementId) {
    @Override
    public String toString() {
      return "{code=" + code + ", component=" + component + ", path=" + path + ", statementId=" + statementId + "}";
    }
  }

  private static List<ExpectedError> expectedErrors(Map<String, Object> expected) {
    List<ExpectedError> out = new ArrayList<>();
    for (Object item : Json.asList(expected.get("errors"), "errors")) {
      Map<String, Object> e = Json.asObject(item, "error");
      out.add(
          new ExpectedError(
              str(e.get("code")), str(e.get("component")), str(e.get("path")), str(e.get("statementId"))));
    }
    return out;
  }

  /** Extract the referenced name from an unresolved-ref issue message: {@code ...reference "name"...}. */
  private static String unresolvedName(ValidationIssue issue) {
    String message = issue.message();
    if (message == null) return null;
    int first = message.indexOf('"');
    int last = message.lastIndexOf('"');
    if (first < 0 || last <= first) return null;
    return message.substring(first + 1, last);
  }

  private static List<String> strings(Object value) {
    List<String> out = new ArrayList<>();
    if (value == null) return out;
    for (Object item : Json.asList(value, "string list")) out.add(str(item));
    return out;
  }

  private static String str(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static List<String> codesOf(List<ValidationIssue> issues) {
    return issues.stream().map(ValidationIssue::code).collect(Collectors.toList());
  }

  private static String describe(List<ValidationIssue> issues) {
    return issues.stream()
        .map(i -> i.code() + "@" + i.component() + i.path() + "#" + i.statementId())
        .collect(Collectors.toList())
        .toString();
  }
}
