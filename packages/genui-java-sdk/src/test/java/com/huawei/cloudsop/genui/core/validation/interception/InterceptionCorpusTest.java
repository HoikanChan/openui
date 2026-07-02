package com.huawei.cloudsop.genui.core.validation.interception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Pins the measured offline interception corpus used by the generated-DSL practice report. */
class InterceptionCorpusTest {

  private static final String ROOT = "interception-corpus/";
  private static final String MANIFEST = ROOT + "manifest.json";
  private static final Set<String> REQUIRED_BAD_CLASSES =
      Set.of(
          "unknown-component",
          "missing-required",
          "null-required",
          "excess-args",
          "invalid-prop",
          "inline-reserved",
          "syntax",
          "root-missing",
          "unresolved-ref");

  @Test
  void corpusMeetsBriefMinimums() {
    List<Map<String, Object>> cases = cases();
    long validControls = cases.stream().filter(c -> "valid-control".equals(str(c.get("expectedClass")))).count();
    assertTrue(validControls >= 8, "expected at least 8 valid controls");

    Map<String, Long> byClass =
        cases.stream()
            .filter(c -> !"valid-control".equals(str(c.get("expectedClass"))))
            .collect(
                Collectors.groupingBy(
                    c -> str(c.get("expectedClass")), LinkedHashMap::new, Collectors.counting()));
    for (String requiredClass : REQUIRED_BAD_CLASSES) {
      assertTrue(
          byClass.getOrDefault(requiredClass, 0L) >= 3,
          "expected at least 3 bad cases for " + requiredClass + ", got " + byClass);
    }
  }

  @TestFactory
  Stream<DynamicTest> validatorMatchesMeasuredManifest() {
    return cases().stream()
        .map(c -> DynamicTest.dynamicTest(str(c.get("id")), () -> assertCase(c)));
  }

  private static void assertCase(Map<String, Object> testCase) {
    String id = str(testCase.get("id"));
    ValidationResult result = validate(testCase);
    List<String> actualCodes = result.issues().stream().map(ValidationIssue::code).toList();
    List<String> expectedCodes = strings(testCase.get("expectedCodes"));
    String expectedOutcome = str(testCase.get("expectedOutcome"));

    if ("valid".equals(expectedOutcome)) {
      assertEquals(ValidationStatus.VALID, result.status(), "[" + id + "] " + describe(result));
      assertTrue(
          result.issues().stream().noneMatch(i -> i.severity().name().equals("ERROR")),
          "[" + id + "] valid control had blocking issues: " + describe(result));
      return;
    }

    if ("intercepted".equals(expectedOutcome)) {
      assertEquals(ValidationStatus.INVALID, result.status(), "[" + id + "] " + describe(result));
      assertTrue(
          actualCodes.containsAll(expectedCodes),
          "[" + id + "] expected codes " + expectedCodes + ", got " + describe(result));
      return;
    }

    if ("missed".equals(expectedOutcome)) {
      assertFalse(
          actualCodes.containsAll(expectedCodes),
          "[" + id + "] manifest pins a miss, but validator now reports expected codes "
              + expectedCodes
              + ": "
              + describe(result));
      return;
    }

    throw new AssertionError("Unknown expectedOutcome for " + id + ": " + expectedOutcome);
  }

  private static ValidationResult validate(Map<String, Object> testCase) {
    Map<String, Object> manifest = manifest();
    String dsl = read(ROOT + str(testCase.get("dslFile")));
    GenerationContract contract = loadContract(str(testCase.get("contractFile")));
    Set<String> externalRefs = new LinkedHashSet<>(strings(manifest.get("externalRefs")));
    return DefaultOpenuiLangValidator.INSTANCE.validate(
        ValidationRequest.builder()
            .dsl(dsl)
            .contract(contract)
            .rootName(str(manifest.get("rootName")))
            .externalRefs(externalRefs)
            .mode(ValidationMode.FINAL)
            .requestId(str(testCase.get("id")))
            .build());
  }

  private static GenerationContract loadContract(String contractFile) {
    Map<String, Object> raw = Json.asObject(Json.parse(read(ROOT + contractFile)), contractFile);
    Map<String, Object> components = Json.asObject(raw.get("components"), contractFile + ".components");
    LinkedHashMap<String, ComponentPromptSpec> specs = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : components.entrySet()) {
      specs.put(
          entry.getKey(),
          new ComponentPromptSpec(
              "interception corpus component",
              Json.asObject(entry.getValue(), "propsSchema for " + entry.getKey())));
    }
    return new GenerationContract(
        str(raw.get("contractVersion")),
        str(raw.get("root")),
        specs,
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static List<Map<String, Object>> cases() {
    return Json.asList(manifest().get("cases"), "cases").stream()
        .map(item -> Json.asObject(item, "case"))
        .toList();
  }

  private static Map<String, Object> manifest() {
    return Json.asObject(Json.parse(read(MANIFEST)), "manifest");
  }

  private static String read(String resource) {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    try (InputStream in = loader.getResourceAsStream(resource)) {
      assertNotNull(in, resource + " not found on test classpath");
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException error) {
      throw new IllegalStateException("failed to read " + resource, error);
    }
  }

  private static List<String> strings(Object value) {
    if (value == null) return List.of();
    return Json.asList(value, "string list").stream().map(InterceptionCorpusTest::str).toList();
  }

  private static String str(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static String describe(ValidationResult result) {
    return result.issues().stream()
        .map(
            issue ->
                issue.code()
                    + "@"
                    + issue.source()
                    + "#"
                    + issue.statementId()
                    + "("
                    + issue.component()
                    + issue.path()
                    + ")")
        .collect(Collectors.toList())
        .toString();
  }
}
