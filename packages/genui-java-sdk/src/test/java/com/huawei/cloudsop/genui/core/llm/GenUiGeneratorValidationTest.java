/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.GenerationValidationException;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig;
import com.huawei.cloudsop.genui.core.validation.OpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TDD tests for GenUiGenerator sync validation (Section 4.6).
 *
 * <p>
 * All tests use a fake LlmTransport — no real LLM is involved.
 */
class GenUiGeneratorValidationTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Build a JSON response body that the fake transport returns for sync generate(). */
    private static String syncResponse(String dsl) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + dsl.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"}}]}";
    }

    /** Wrap DSL in the standard openui fenced block so OpenuiCodeExtractor strips the fence. */
    private static String fenced(String dsl) {
        return "```openui\\n" + dsl + "\\n```";
    }

    // ── Test 1: sync VALID — default config (finalOnly, repair=NONE) ──────────

    /**
     * When the LLM returns a valid DSL (Stack is in base contract, no unknown components), generate() must return the
     * result without throwing.
     */
    @Test
    void syncValidDefaultConfigReturnsResultWithoutThrow() {
        // Stack is a known base-contract component — passes validation.
        String dsl = "root = Stack([])";
        FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport,
                null /* finalOnly */, null /* default validator */);

        GenUiGenerationResult result = generator
                .generate(UiGenerationRequest.builder().userInput("show something").build());

        assertEquals(dsl, result.dsl());
    }

    // ── Test 1b: sync VALID result carries validationStatus + validationResult ────

    /**
     * The VALID-return path of generate() must populate the result's validationStatus and validationResult, not just
     * return the DSL — service caching/logging (Section 9) depends on this being non-null on success.
     */
    @Test
    void syncValidDefaultConfigResultCarriesValidationStatusAndResult() {
        String dsl = "root = Stack([])";
        FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport,
                null /* finalOnly */, null /* default validator */);

        GenUiGenerationResult result = generator
                .generate(UiGenerationRequest.builder().userInput("show something").build());

        assertEquals(ValidationStatus.VALID, result.validationStatus(),
                "VALID generation must carry validationStatus=VALID on the result");
        assertNotNull(result.validationResult(), "VALID generation must carry a non-null validationResult");
        assertEquals(ValidationStatus.VALID, result.validationResult().status());
        assertFalse(result.validationResult().hasBlockingIssues());
    }

    // ── Test 2: sync INVALID — default config throws GenerationValidationException ──

    /**
     * When the LLM returns DSL with an unknown component, generate() must throw GenerationValidationException whose
     * validationResult() has status INVALID and contains the expected "unknown-component" issue code.
     */
    @Test
    void syncInvalidDefaultConfigThrowsGenerationValidationException() {
        // BogusWidget is NOT in the base contract → unknown-component ERROR.
        String dsl = "root = BogusWidget()";
        FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport,
                null /* finalOnly */, null /* default validator */);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> generator.generate(UiGenerationRequest.builder().userInput("show bogus").build()));

        ValidationResult result = ex.validationResult();
        assertNotNull(result, "validationResult() must not be null");
        assertEquals(ValidationStatus.INVALID, result.status(), "status must be INVALID");
        assertTrue(result.hasBlockingIssues(), "must have blocking issues");
        assertTrue(
                result.issues().stream().anyMatch(
                        i -> "unknown-component".equals(i.code()) && i.severity() == ValidationSeverity.ERROR),
                "must have an unknown-component ERROR issue, got: " + result.issues());
    }

    // ── Test 3: INVALID with repair=NONE explicitly ───────────────────────────

    /**
     * Explicit FINAL_ONLY + NONE → same throw behavior (not a new code path, but documents intent).
     */
    @Test
    void syncInvalidRepairNoneThrows() {
        String dsl = "root = GhostButton()";
        FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport,
                GenUiValidationConfig.finalOnly(), null);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> generator.generate(UiGenerationRequest.builder().userInput("x").build()));

        assertEquals(ValidationStatus.INVALID, ex.validationResult().status());
        assertTrue(ex.getMessage().contains("unknown-component"),
                "exception message should mention the issue code, got: " + ex.getMessage());
    }

    // ── Test 3b: FAIL_FAST_REASK config — sync generate() must still throw on INVALID ──

    /**
     * FAIL_FAST_REASK is a streaming-only repair strategy and cannot execute inside sync generate(). Regression guard:
     * previously only NONE/FINAL_REPAIR threw on INVALID, so FAIL_FAST_REASK fell through the inner repair-policy
     * filter and returned invalid DSL silently. Any INVALID result must now throw regardless of repair policy.
     */
    @Test
    void syncInvalidFailFastReaskConfigStillThrows() {
        // BogusWidget is NOT in the base contract → unknown-component ERROR.
        String dsl = "root = BogusWidget()";
        FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport,
                GenUiValidationConfig.streamingGateWithReask(), null /* default validator */);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> generator.generate(UiGenerationRequest.builder().userInput("show bogus").build()));

        ValidationResult result = ex.validationResult();
        assertNotNull(result, "validationResult() must not be null");
        assertEquals(ValidationStatus.INVALID, result.status(), "status must be INVALID");
        assertTrue(
                result.issues().stream().anyMatch(
                        i -> "unknown-component".equals(i.code()) && i.severity() == ValidationSeverity.ERROR),
                "must have an unknown-component ERROR issue, got: " + result.issues());
    }

    // ── Test 4: validation DISABLED — invalid DSL passes through ─────────────

    /**
     * When validation is DISABLED, an unknown-component DSL must NOT throw; generate() returns the extracted DSL as-is.
     */
    @Test
    void syncInvalidValidationDisabledNoThrow() {
        String dsl = "root = NonExistentComponent()";
        FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport,
                GenUiValidationConfig.disabled(), null);

        // Must NOT throw.
        GenUiGenerationResult result = generator.generate(UiGenerationRequest.builder().userInput("go").build());

        assertEquals(dsl, result.dsl(), "DSL must be returned as-is when validation is disabled");
        assertNull(result.validationStatus(), "validationStatus must be null when validation is DISABLED");
        assertNull(result.validationResult(), "validationResult must be null when validation is DISABLED");
    }

    // ── Test 5: custom validator injected — stub forces INVALID ──────────────

    /**
     * A stub validator that always returns INVALID must cause generate() to throw regardless of the actual DSL content
     * (even a valid Stack DSL).
     */
    @Test
    void customValidatorForcesInvalidThrowsEvenForValidDsl() {
        String dsl = "root = Stack([])";
        FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

        // Stub: always returns INVALID.
        OpenuiLangValidator alwaysInvalid = request -> ValidationResult.invalid(
                List.of(new com.huawei.cloudsop.genui.core.validation.ValidationIssue("stub-error",
                        ValidationSeverity.ERROR, "test", "stub always-invalid", null, null, null, -1, -1, null,
                        false)),
                new com.huawei.cloudsop.genui.core.validation.ValidationMetadata(1, "root", ValidationMode.FINAL,
                        null));

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport,
                GenUiValidationConfig.finalOnly(), alwaysInvalid);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> generator.generate(UiGenerationRequest.builder().userInput("x").build()));

        assertEquals(ValidationStatus.INVALID, ex.validationResult().status());
        assertTrue(ex.validationResult().issues().stream().anyMatch(i -> "stub-error".equals(i.code())),
                "custom stub error code must be present");
    }

    // ── Test 6: custom validator injected — stub forces VALID ─────────────────

    /**
     * A stub validator that always returns VALID must allow even a known-bad DSL through without throwing.
     */
    @Test
    void customValidatorForcesValidNoThrowForInvalidDsl() {
        String dsl = "root = TotallyFakeComponent()";
        FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

        // Stub: always returns VALID.
        OpenuiLangValidator alwaysValid = request -> ValidationResult.valid(dsl, List.of(),
                new com.huawei.cloudsop.genui.core.validation.ValidationMetadata(1, "root", ValidationMode.FINAL,
                        null));

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport,
                GenUiValidationConfig.finalOnly(), alwaysValid);

        // Must NOT throw.
        GenUiGenerationResult result = generator.generate(UiGenerationRequest.builder().userInput("x").build());

        assertEquals(dsl, result.dsl());
    }

    // ── Test 7: validator receives the merged contract ────────────────────────

    /**
     * Verify that the validator is called with a non-null contract (the merged contract from the SDK) so it can do
     * contract-level checks.
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

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport,
                GenUiValidationConfig.finalOnly(), capturingValidator);

        generator.generate(UiGenerationRequest.builder().userInput("x").build());

        ValidationRequest req = capturedRequest.get();
        assertNotNull(req, "validator must have been called");
        assertNotNull(req.contract(), "validator must receive a non-null merged contract");
        assertEquals(ValidationMode.FINAL, req.mode(), "mode must be FINAL for sync generate()");
        assertTrue(req.contract().components().containsKey("Stack"),
                "merged contract must include Stack from base contract");
    }

    // ── Test 7b: externalRefs injection — `data` host binding ─────────────────

    /**
     * When the request carries a dataModel (non-empty response map), the validator must receive
     * {@code externalRefs=["data"]} so host-data references like {@code data.name} are not flagged unresolved.
     */
    @Test
    void validatorReceivesDataExternalRefWhenDataModelPresent() {
        String dsl = "root = Stack([TextContent(data.name)])";
        FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

        AtomicReference<ValidationRequest> capturedRequest = new AtomicReference<>();
        OpenuiLangValidator capturingValidator = request -> {
            capturedRequest.set(request);
            return new DefaultOpenuiLangValidator().validate(request);
        };

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport,
                GenUiValidationConfig.finalOnly(), capturingValidator);

        generator.generate(UiGenerationRequest.builder().userInput("show device")
                .response(java.util.Map.of("name", "CloudEngine")).build());

        ValidationRequest req = capturedRequest.get();
        assertNotNull(req, "validator must have been called");
        assertTrue(req.externalRefs().contains("data"),
                "externalRefs must contain \"data\" when a dataModel is present, got: " + req.externalRefs());
    }

    /** End-to-end: a DSL referencing {@code data.*} must pass FINAL validation when the dataModel is present. */
    @Test
    void syncDataRefValidWhenDataModelPresent() {
        String dsl = "root = Stack([TextContent(data.name)])";
        FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport,
                GenUiValidationConfig.finalOnly(), null);

        GenUiGenerationResult result = generator.generate(UiGenerationRequest.builder().userInput("show device")
                .response(java.util.Map.of("name", "CloudEngine")).build());

        assertEquals(ValidationStatus.VALID, result.validationStatus(),
                "data.* reference must be valid when the request has a dataModel");
    }

    /** Without a dataModel, {@code data} must NOT be injected — the reference is genuinely unresolved. */
    @Test
    void syncDataRefStillInvalidWithoutDataModel() {
        String dsl = "root = Stack([TextContent(data.name)])";
        FakeTransport transport = FakeTransport.sync(syncResponse(dsl));

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport,
                GenUiValidationConfig.finalOnly(), null);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> generator.generate(UiGenerationRequest.builder().userInput("show device").build()));

        assertTrue(ex.validationResult().issues().stream().anyMatch(i -> "unresolved-ref".equals(i.code())),
                "data must stay unresolved without a dataModel, got: " + ex.validationResult().issues());
    }

    // ── Test 8: DefaultOpenuiLangValidator — standalone unit tests ────────────

    @Test
    void defaultValidatorNullDslReturnsMissingRoot() {
        // A null DSL string → preprocessor returns empty → root-missing ERROR → INVALID.
        DefaultOpenuiLangValidator v = new DefaultOpenuiLangValidator();
        ValidationResult result = v.validate(ValidationRequest.builder().dsl("").mode(ValidationMode.FINAL).build());
        assertEquals(ValidationStatus.INVALID, result.status());
        assertTrue(result.issues().stream().anyMatch(i -> "root-missing".equals(i.code())),
                "empty DSL must produce root-missing issue");
    }

    @Test
    void nullContractTreatsAllComponentsAsUnknown() {
        // A null contract yields an EMPTY ContractCatalog, so every component is reported as
        // unknown-component — there is no contract-less "syntax-only" validation mode.
        DefaultOpenuiLangValidator v = new DefaultOpenuiLangValidator();
        ValidationResult result = v
                .validate(ValidationRequest.builder().dsl("root = AnyComponent()").mode(ValidationMode.FINAL).build());
        // ProgramAnalyzer with empty catalog flags AnyComponent as unknown → INVALID
        assertEquals(ValidationStatus.INVALID, result.status());
        assertTrue(result.issues().stream().anyMatch(i -> "unknown-component".equals(i.code())),
                "AnyComponent not in empty catalog must produce unknown-component issue");
    }

    @Test
    void defaultValidatorStreamingWithWarningIsPartial() {
        // In STREAMING mode, ProgramAnalyzer downgrades unresolved-ref from ERROR (FINAL) to WARNING
        // (retryable, since the ref may resolve once more of the stream arrives). A WARNING-only,
        // non-empty issue list in STREAMING mode maps to ValidationStatus.PARTIAL (see
        // DefaultOpenuiLangValidator#validate). generate() always validates in FINAL mode, so PARTIAL
        // is unreachable through generate() — call the validator directly in STREAMING mode instead.
        // 'item' is a Ref, not a Comp, so this exercises unresolved-ref only (no unknown-component).
        DefaultOpenuiLangValidator v = new DefaultOpenuiLangValidator();
        ValidationResult result = v
                .validate(ValidationRequest.builder().dsl("root = item").mode(ValidationMode.STREAMING).build());

        assertFalse(result.hasBlockingIssues(), "unresolved ref in STREAMING is non-blocking WARNING");
        assertEquals(ValidationStatus.PARTIAL, result.status(),
                "non-blocking warning in STREAMING mode must yield PARTIAL, got: " + result.issues());
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
            if (fail)
                throw new LlmTransportException("boom");
            return response;
        }

        @Override
        public InputStream postStream(String body) throws LlmTransportException {
            if (fail)
                throw new LlmTransportException("boom");
            return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
        }
    }
}
