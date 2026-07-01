package com.huawei.cloudsop.genui.core.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ValidationModelTest {

  // -----------------------------------------------------------------------
  // ValidationIssue
  // -----------------------------------------------------------------------

  @Test
  void issueRecordHoldsRequiredFields() {
    ValidationIssue issue =
        new ValidationIssue(
            "SYNTAX_001",
            ValidationSeverity.ERROR,
            "syntax",
            "Unexpected token",
            null,
            null,
            null,
            -1,
            -1,
            null,
            false);

    assertEquals("SYNTAX_001", issue.code());
    assertEquals(ValidationSeverity.ERROR, issue.severity());
    assertEquals("syntax", issue.source());
    assertEquals("Unexpected token", issue.message());
    assertNull(issue.statementId());
    assertNull(issue.component());
    assertNull(issue.path());
    assertEquals(-1, issue.line());
    assertEquals(-1, issue.column());
    assertNull(issue.hint());
    assertFalse(issue.retryable());
  }

  @Test
  void issueRecordAcceptsOptionalFields() {
    ValidationIssue issue =
        new ValidationIssue(
            "CONTRACT_002",
            ValidationSeverity.WARNING,
            "contract",
            "Unknown component",
            "stmt-1",
            "MyButton",
            "root.children[0]",
            10,
            5,
            "Did you mean 'Button'?",
            true);

    assertEquals("stmt-1", issue.statementId());
    assertEquals("MyButton", issue.component());
    assertEquals("root.children[0]", issue.path());
    assertEquals(10, issue.line());
    assertEquals(5, issue.column());
    assertEquals("Did you mean 'Button'?", issue.hint());
    assertTrue(issue.retryable());
  }

  // -----------------------------------------------------------------------
  // ValidationMetadata
  // -----------------------------------------------------------------------

  @Test
  void metadataExtraMapIsNeverNull() {
    ValidationMetadata meta = new ValidationMetadata(3, "MyRoot", ValidationMode.FINAL, null);

    assertNotNull(meta.extra());
    assertTrue(meta.extra().isEmpty());
  }

  @Test
  void metadataExtraMapIsImmutable() {
    ValidationMetadata meta =
        new ValidationMetadata(1, null, ValidationMode.STREAMING, Map.of("k", "v"));

    assertThrows(UnsupportedOperationException.class, () -> meta.extra().put("x", "y"));
  }

  @Test
  void metadataExtraIsDefensivelyCopied() {
    Map<String, String> source = new java.util.LinkedHashMap<>();
    source.put("a", "1");
    ValidationMetadata meta = new ValidationMetadata(0, null, ValidationMode.FINAL, source);

    source.put("b", "2");

    assertEquals(Map.of("a", "1"), meta.extra());
  }

  // -----------------------------------------------------------------------
  // ValidationResult
  // -----------------------------------------------------------------------

  @Test
  void resultIssuesListIsNeverNull() {
    ValidationMetadata meta = new ValidationMetadata(0, null, ValidationMode.FINAL, null);
    ValidationResult result =
        new ValidationResult(ValidationStatus.VALID, null, null, meta);

    assertNotNull(result.issues());
    assertTrue(result.issues().isEmpty());
  }

  @Test
  void resultIssuesListIsImmutable() {
    ValidationMetadata meta = new ValidationMetadata(0, null, ValidationMode.FINAL, null);
    ValidationResult result =
        new ValidationResult(ValidationStatus.INVALID, null, new ArrayList<>(), meta);

    assertThrows(UnsupportedOperationException.class, () -> result.issues().add(null));
  }

  @Test
  void resultIssuesIsDefensivelyCopied() {
    ValidationIssue issue =
        new ValidationIssue(
            "C1", ValidationSeverity.ERROR, "syntax", "msg", null, null, null, -1, -1, null, false);
    List<ValidationIssue> mutable = new ArrayList<>();
    mutable.add(issue);

    ValidationMetadata meta = new ValidationMetadata(1, null, ValidationMode.FINAL, null);
    ValidationResult result =
        new ValidationResult(ValidationStatus.INVALID, null, mutable, meta);

    mutable.add(issue);

    assertEquals(1, result.issues().size());
  }

  @Test
  void resultValidFactorySetsStatusAndConvenienceMethods() {
    ValidationMetadata meta = new ValidationMetadata(2, "Root", ValidationMode.FINAL, null);
    ValidationResult result = ValidationResult.valid("dsl-text", List.of(), meta);

    assertEquals(ValidationStatus.VALID, result.status());
    assertEquals("dsl-text", result.normalizedDsl());
    assertTrue(result.isValid());
    assertFalse(result.hasBlockingIssues());
  }

  @Test
  void resultInvalidFactorySetsStatusAndConvenienceMethods() {
    ValidationIssue blockingIssue =
        new ValidationIssue(
            "S1", ValidationSeverity.ERROR, "syntax", "Bad", null, null, null, -1, -1, null, false);
    ValidationMetadata meta = new ValidationMetadata(0, null, ValidationMode.FINAL, null);
    ValidationResult result = ValidationResult.invalid(List.of(blockingIssue), meta);

    assertEquals(ValidationStatus.INVALID, result.status());
    assertNull(result.normalizedDsl());
    assertFalse(result.isValid());
    assertTrue(result.hasBlockingIssues());
  }

  @Test
  void resultPartialFactorySetStatusCorrectly() {
    ValidationMetadata meta = new ValidationMetadata(1, null, ValidationMode.STREAMING, null);
    ValidationResult result = ValidationResult.partial("partial-dsl", List.of(), meta);

    assertEquals(ValidationStatus.PARTIAL, result.status());
    assertEquals("partial-dsl", result.normalizedDsl());
    assertFalse(result.isValid());
    assertFalse(result.hasBlockingIssues());
  }

  @Test
  void hasBlockingIssuesReturnsTrueOnlyForErrorSeverity() {
    ValidationIssue warnIssue =
        new ValidationIssue(
            "W1", ValidationSeverity.WARNING, "contract", "warn", null, null, null, -1, -1, null, false);
    ValidationMetadata meta = new ValidationMetadata(0, null, ValidationMode.FINAL, null);
    ValidationResult warnResult =
        new ValidationResult(ValidationStatus.VALID, null, List.of(warnIssue), meta);

    assertFalse(warnResult.hasBlockingIssues());
  }

  // -----------------------------------------------------------------------
  // ValidationRequest builder
  // -----------------------------------------------------------------------

  @Test
  void requestBuilderNormalizesNullExternalRefsToEmpty() {
    ValidationRequest request =
        ValidationRequest.builder()
            .dsl("root = Box([])")
            .mode(ValidationMode.FINAL)
            .build();

    assertNotNull(request.externalRefs());
    assertTrue(request.externalRefs().isEmpty());
  }

  @Test
  void requestBuilderPreservesExternalRefs() {
    ValidationRequest request =
        ValidationRequest.builder()
            .dsl("root = Box([])")
            .mode(ValidationMode.STREAMING)
            .externalRefs(Set.of("TypeA", "TypeB"))
            .build();

    assertEquals(Set.of("TypeA", "TypeB"), request.externalRefs());
  }

  @Test
  void requestExternalRefsIsImmutable() {
    ValidationRequest request =
        ValidationRequest.builder()
            .dsl("root = Box([])")
            .mode(ValidationMode.FINAL)
            .externalRefs(Set.of("T"))
            .build();

    assertThrows(UnsupportedOperationException.class, () -> request.externalRefs().add("X"));
  }

  @Test
  void requestBuilderAcceptsOptionalFields() {
    ValidationRequest request =
        ValidationRequest.builder()
            .dsl("root = Stack([])")
            .mode(ValidationMode.FINAL)
            .rootName("MyRoot")
            .requestId("req-123")
            .build();

    assertEquals("MyRoot", request.rootName());
    assertEquals("req-123", request.requestId());
    assertNull(request.contract());
  }

  // -----------------------------------------------------------------------
  // GenUiValidationConfig presets
  // -----------------------------------------------------------------------

  @Test
  void finalOnlyPresetHasExpectedModeAndPolicy() {
    GenUiValidationConfig config = GenUiValidationConfig.finalOnly();

    assertEquals(ValidationConfigMode.FINAL_ONLY, config.validationMode());
    assertEquals(RepairPolicyKind.NONE, config.repairPolicy());
  }

  @Test
  void streamingGatePresetHasExpectedModeAndPolicy() {
    GenUiValidationConfig config = GenUiValidationConfig.streamingGate();

    assertEquals(ValidationConfigMode.STREAMING_GATE, config.validationMode());
    assertEquals(RepairPolicyKind.NONE, config.repairPolicy());
  }

  @Test
  void streamingGateWithReaskPresetHasExpectedModeAndPolicy() {
    GenUiValidationConfig config = GenUiValidationConfig.streamingGateWithReask();

    assertEquals(ValidationConfigMode.STREAMING_GATE, config.validationMode());
    assertEquals(RepairPolicyKind.FAIL_FAST_REASK, config.repairPolicy());
  }

  @Test
  void disabledPresetHasExpectedModeAndPolicy() {
    GenUiValidationConfig config = GenUiValidationConfig.disabled();

    assertEquals(ValidationConfigMode.DISABLED, config.validationMode());
    assertEquals(RepairPolicyKind.NONE, config.repairPolicy());
  }

  // -----------------------------------------------------------------------
  // JSON serialization round-trip
  // -----------------------------------------------------------------------

  @Test
  void validationIssueSerializesAndDeserializesViaFastjson2() {
    ValidationIssue original =
        new ValidationIssue(
            "REF_001",
            ValidationSeverity.INFO,
            "reference",
            "Ref resolved",
            "s-1",
            "Box",
            "root",
            3,
            7,
            "hint text",
            false);

    // Serialize to a plain Map so Json.stringify can handle it
    String json = com.alibaba.fastjson2.JSON.toJSONString(original);
    ValidationIssue deserialized = com.alibaba.fastjson2.JSON.parseObject(json, ValidationIssue.class);

    assertEquals(original.code(), deserialized.code());
    assertEquals(original.severity(), deserialized.severity());
    assertEquals(original.source(), deserialized.source());
    assertEquals(original.message(), deserialized.message());
    assertEquals(original.statementId(), deserialized.statementId());
    assertEquals(original.component(), deserialized.component());
    assertEquals(original.path(), deserialized.path());
    assertEquals(original.line(), deserialized.line());
    assertEquals(original.column(), deserialized.column());
    assertEquals(original.hint(), deserialized.hint());
    assertEquals(original.retryable(), deserialized.retryable());
  }

  @Test
  void validationResultSerializesAndDeserializesViaFastjson2() {
    ValidationIssue issue =
        new ValidationIssue(
            "S1", ValidationSeverity.ERROR, "syntax", "Bad token", null, null, null, 1, 1, null, true);
    ValidationMetadata meta =
        new ValidationMetadata(2, "Root", ValidationMode.FINAL, Map.of("traceId", "abc"));
    ValidationResult original = ValidationResult.invalid(List.of(issue), meta);

    String json = com.alibaba.fastjson2.JSON.toJSONString(original);
    ValidationResult deserialized = com.alibaba.fastjson2.JSON.parseObject(json, ValidationResult.class);

    assertEquals(original.status(), deserialized.status());
    assertNull(deserialized.normalizedDsl());
    assertEquals(1, deserialized.issues().size());
    assertEquals("S1", deserialized.issues().get(0).code());
    assertEquals("Root", deserialized.metadata().rootName());
    assertEquals(ValidationMode.FINAL, deserialized.metadata().mode());
  }
}
