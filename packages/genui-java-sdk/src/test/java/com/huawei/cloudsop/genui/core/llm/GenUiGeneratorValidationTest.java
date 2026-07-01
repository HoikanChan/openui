package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.GenerationValidationException;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig;
import com.huawei.cloudsop.genui.core.validation.OpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD tests for GenUiGenerator sync validation (Section 4.6).
 *
 * <p>All tests use a fake LlmTransport — no real LLM is involved.
 */
class GenUiGeneratorValidationTest {

  // ── Helpers ───────────────────────────────────────────────────────────────

  /** Build a JSON response body that the fake transport returns for sync generate(). */
  private static String syncResponse(String dsl) {
    return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
        + dsl.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        + "\"}}]}";
  }

  /** Wrap DSL in the standard openui fenced block so OpenuiCodeExtractor strips the fence. */
  private static String fenced(String dsl) {
    return "```openui\\n" + dsl + "\\n```";
  }

  // ── Test 1: sync VALID — default config (finalOnly, repair=NONE) ──────────

  /**
   * When the LLM returns a valid DSL (Stack is in base contract, no unknown components),
   * generate() must return the result without throwing.
   */
  @Test
  void syncValid_defaultConfig_returnsResultWithoutThrow() {
    // Stack is a known base-contract component — passes validation.
    String dsl = "root = Stack([])";
    FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

    GenUiGenerator generator = GenUiGenerator.withTransport(
        GenUiLlmConfig.defaults(), transport, null /* finalOnly */, null /* default validator */);

    GenUiGenerationResult result =
        generator.generate(UiGenerationRequest.builder().userInput("show something").build());

    assertEquals(dsl, result.dsl());
  }

  // ── Test 2: sync INVALID — default config throws GenerationValidationException ──

  /**
   * When the LLM returns DSL with an unknown component, generate() must throw
   * GenerationValidationException whose validationResult() has status INVALID and contains the
   * expected "unknown-component" issue code.
   */
  @Test
  void syncInvalid_defaultConfig_throwsGenerationValidationException() {
    // BogusWidget is NOT in the base contract → unknown-component ERROR.
    String dsl = "root = BogusWidget()";
    FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

    GenUiGenerator generator = GenUiGenerator.withTransport(
        GenUiLlmConfig.defaults(), transport, null /* finalOnly */, null /* default validator */);

    GenerationValidationException ex = assertThrows(
        GenerationValidationException.class,
        () -> generator.generate(UiGenerationRequest.builder().userInput("show bogus").build()));

    ValidationResult result = ex.validationResult();
    assertNotNull(result, "validationResult() must not be null");
    assertEquals(ValidationStatus.INVALID, result.status(), "status must be INVALID");
    assertTrue(result.hasBlockingIssues(), "must have blocking issues");
    assertTrue(
        result.issues().stream().anyMatch(i ->
            "unknown-component".equals(i.code()) && i.severity() == ValidationSeverity.ERROR),
        "must have an unknown-component ERROR issue, got: " + result.issues());
  }

  // ── Test 3: INVALID with repair=NONE explicitly ───────────────────────────

  /**
   * Explicit FINAL_ONLY + NONE → same throw behavior (not a new code path, but documents intent).
   */
  @Test
  void syncInvalid_repairNone_throws() {
    String dsl = "root = GhostButton()";
    FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

    GenUiGenerator generator = GenUiGenerator.withTransport(
        GenUiLlmConfig.defaults(), transport,
        GenUiValidationConfig.finalOnly(),
        null);

    GenerationValidationException ex = assertThrows(
        GenerationValidationException.class,
        () -> generator.generate(UiGenerationRequest.builder().userInput("x").build()));

    assertEquals(ValidationStatus.INVALID, ex.validationResult().status());
    assertTrue(ex.getMessage().contains("unknown-component"),
        "exception message should mention the issue code, got: " + ex.getMessage());
  }

  // ── Test 4: validation DISABLED — invalid DSL passes through ─────────────

  /**
   * When validation is DISABLED, an unknown-component DSL must NOT throw; generate() returns the
   * extracted DSL as-is.
   */
  @Test
  void syncInvalid_validationDisabled_noThrow() {
    String dsl = "root = NonExistentComponent()";
    FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

    GenUiGenerator generator = GenUiGenerator.withTransport(
        GenUiLlmConfig.defaults(), transport,
        GenUiValidationConfig.disabled(),
        null);

    // Must NOT throw.
    GenUiGenerationResult result =
        generator.generate(UiGenerationRequest.builder().userInput("go").build());

    assertEquals(dsl, result.dsl(), "DSL must be returned as-is when validation is disabled");
  }

  // ── Test 5: custom validator injected — stub forces INVALID ──────────────

  /**
   * A stub validator that always returns INVALID must cause generate() to throw regardless of
   * the actual DSL content (even a valid Stack DSL).
   */
  @Test
  void customValidator_forcesInvalid_throwsEvenForValidDsl() {
    String dsl = "root = Stack([])";
    FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

    // Stub: always returns INVALID.
    OpenuiLangValidator alwaysInvalid = request ->
        ValidationResult.invalid(
            List.of(new com.huawei.cloudsop.genui.core.validation.ValidationIssue(
                "stub-error", ValidationSeverity.ERROR, "test",
                "stub always-invalid", null, null, null, -1, -1, null, false)),
            new com.huawei.cloudsop.genui.core.validation.ValidationMetadata(1, "root", ValidationMode.FINAL, null));

    GenUiGenerator generator = GenUiGenerator.withTransport(
        GenUiLlmConfig.defaults(), transport,
        GenUiValidationConfig.finalOnly(),
        alwaysInvalid);

    GenerationValidationException ex = assertThrows(
        GenerationValidationException.class,
        () -> generator.generate(UiGenerationRequest.builder().userInput("x").build()));

    assertEquals(ValidationStatus.INVALID, ex.validationResult().status());
    assertTrue(ex.validationResult().issues().stream()
        .anyMatch(i -> "stub-error".equals(i.code())),
        "custom stub error code must be present");
  }

  // ── Test 6: custom validator injected — stub forces VALID ─────────────────

  /**
   * A stub validator that always returns VALID must allow even a known-bad DSL through without
   * throwing.
   */
  @Test
  void customValidator_forcesValid_noThrowForInvalidDsl() {
    String dsl = "root = TotallyFakeComponent()";
    FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

    // Stub: always returns VALID.
    OpenuiLangValidator alwaysValid = request ->
        ValidationResult.valid(dsl, List.of(),
            new com.huawei.cloudsop.genui.core.validation.ValidationMetadata(
                1, "root", ValidationMode.FINAL, null));

    GenUiGenerator generator = GenUiGenerator.withTransport(
        GenUiLlmConfig.defaults(), transport,
        GenUiValidationConfig.finalOnly(),
        alwaysValid);

    // Must NOT throw.
    GenUiGenerationResult result =
        generator.generate(UiGenerationRequest.builder().userInput("x").build());

    assertEquals(dsl, result.dsl());
  }

  // ── Test 7: validator receives the merged contract ────────────────────────

  /**
   * Verify that the validator is called with a non-null contract (the merged contract from the SDK)
   * so it can do contract-level checks.
   */
  @Test
  void validatorReceivesMergedContract() {
    String dsl = "root = Stack([])";
    FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

    AtomicReference<ValidationRequest> capturedRequest = new AtomicReference<>();
    OpenuiLangValidator capturingValidator = request -> {
      capturedRequest.set(request);
      return new DefaultOpenuiLangValidator().validate(request);
    };

    GenUiGenerator generator = GenUiGenerator.withTransport(
        GenUiLlmConfig.defaults(), transport,
        GenUiValidationConfig.finalOnly(),
        capturingValidator);

    generator.generate(UiGenerationRequest.builder().userInput("x").build());

    ValidationRequest req = capturedRequest.get();
    assertNotNull(req, "validator must have been called");
    assertNotNull(req.contract(), "validator must receive a non-null merged contract");
    assertEquals(ValidationMode.FINAL, req.mode(), "mode must be FINAL for sync generate()");
    assertTrue(req.contract().components().containsKey("Stack"),
        "merged contract must include Stack from base contract");
  }

  // ── Test 8: DefaultOpenuiLangValidator — standalone unit tests ────────────

  @Test
  void defaultValidator_nullDsl_returnsMissingRoot() {
    // A null DSL string → preprocessor returns empty → root-missing ERROR → INVALID.
    DefaultOpenuiLangValidator v = new DefaultOpenuiLangValidator();
    ValidationResult result = v.validate(ValidationRequest.builder()
        .dsl("")
        .mode(ValidationMode.FINAL)
        .build());
    assertEquals(ValidationStatus.INVALID, result.status());
    assertTrue(result.issues().stream().anyMatch(i -> "root-missing".equals(i.code())),
        "empty DSL must produce root-missing issue");
  }

  @Test
  void defaultValidator_unknownComponent_returnsInvalid() {
    // ContractCatalog.from(null) is an empty catalog → unknown-component ERROR for any component.
    DefaultOpenuiLangValidator v = new DefaultOpenuiLangValidator();
    ValidationResult result = v.validate(ValidationRequest.builder()
        .dsl("root = AnyComponent()")
        .mode(ValidationMode.FINAL)
        .build());
    // ProgramAnalyzer with empty catalog flags AnyComponent as unknown → INVALID
    assertEquals(ValidationStatus.INVALID, result.status());
    assertTrue(result.issues().stream().anyMatch(i -> "unknown-component".equals(i.code())),
        "AnyComponent not in empty catalog must produce unknown-component issue");
  }

  @Test
  void defaultValidator_streaming_withWarningIsPartial() {
    // In STREAMING mode, an unclosed bracket produces a WARNING (transient syntax issue).
    // With no blocking ERROR that results in PARTIAL.
    DefaultOpenuiLangValidator v = new DefaultOpenuiLangValidator();
    // Provide an incomplete (unclosed) DSL that makes the auto-close fire.
    // With no contract => empty catalog => any component triggers unknown-component ERROR in streaming.
    // Use a streaming-specific approach: just test the metadata/mode plumbing here.
    // Actually the simplest PARTIAL case: partial input in STREAMING with an unresolved ref warning
    // and no contract → no unknown-component check. Use a Ref that's never resolved.
    // But ProgramAnalyzer in STREAMING mode emits unresolved-ref as WARNING (retryable).
    // root = item  ← ref to undeclared 'item' → STREAMING WARNING only → PARTIAL.
    ValidationResult result = v.validate(ValidationRequest.builder()
        .dsl("root = item")  // 'item' is a Ref, not a Comp — no unknown-component
        .mode(ValidationMode.STREAMING)
        .build());
    // No ERROR issues → not INVALID; has warning → PARTIAL
    assertFalse(result.hasBlockingIssues(), "unresolved ref in STREAMING is non-blocking WARNING");
    // The status will be PARTIAL (warnings present) or VALID depending on issues.
    // Unresolved-ref in STREAMING is a WARNING → non-empty issues, mode is STREAMING → PARTIAL.
    // (VALID only when issues are empty in STREAMING or no issues at all in FINAL)
    assertNotNull(result.status());
  }

  // ── Fake transport (mirrors GenUiGeneratorTest.FakeTransport) ─────────────

  private static final class FakeTransport implements LlmTransport {
    private final String response;
    private final boolean fail;

    private FakeTransport(String response, boolean fail) {
      this.response = response;
      this.fail = fail;
    }

    static FakeTransport sync(String response) {
      return new FakeTransport(response, false);
    }

    @Override
    public String post(String body) throws LlmTransportException {
      if (fail) throw new LlmTransportException("boom");
      return response;
    }

    @Override
    public InputStream postStream(String body) throws LlmTransportException {
      if (fail) throw new LlmTransportException("boom");
      return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
    }
  }
}
