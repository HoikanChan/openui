/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.llm.stream.SseFrames;
import com.huawei.cloudsop.genui.core.llm.transport.InMemoryLlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig;
import com.huawei.cloudsop.genui.core.validation.OpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationMetadata;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * GenUiGenerator validation contract.
 *
 * <p>同步 {@code generate()} 只发一次模型请求并返回提取结果:不校验、不修复、不降级;模型请求或响应失败直接抛 {@link
 * GenerationSdkException}。校验与增量修复只发生在流式入口。
 *
 * <p>All tests use a fake LlmTransport — no real LLM is involved.
 */
class GenUiGeneratorValidationTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Build a JSON response body that the fake transport returns for sync generate(). */
    private static String syncResponse(String dsl) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + escape(dsl)
                + "\"}}]}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String frame(String content) {
        return "data: {\"choices\":[{\"delta\":{\"content\":\"" + escape(content) + "\"}}]}\n\n";
    }

    private static String streamOf(String... contents) {
        StringBuilder stream = new StringBuilder();
        for (String content : contents) {
            stream.append(frame(content));
        }
        return stream.append(SseFrames.done()).toString();
    }

    // ── 同步:不校验、不修复、返回 UNCHECKED ────────────────────────────────

    @Test
    void syncReturnsUncheckedWithoutInvokingValidator() {
        // BogusWidget is NOT in the base contract, but sync generation must not validate it.
        String dsl = "root = BogusWidget()";
        FakeTransport transport = FakeTransport.sync(syncResponse(dsl));
        AtomicInteger validatorCalls = new AtomicInteger();
        OpenuiLangValidator validator =
                request -> {
                    validatorCalls.incrementAndGet();
                    throw new AssertionError("sync generate() must not invoke the validator");
                };

        GenUiGenerator generator =
                GenUiGenerator.withTransport(
                        GenUiLlmConfig.defaults(),
                        transport,
                        GenUiValidationConfig.incremental(),
                        validator);

        GenUiGenerationResult result =
                generator.generate(UiGenerationRequest.builder().userInput("show bogus").build());

        assertEquals(dsl, result.dsl());
        assertEquals(0, validatorCalls.get(), "sync generation must never validate");
        assertNull(result.validationStatus(), "sync result carries no validation verdict");
        assertNull(result.validationResult(), "sync result carries no validation result");
        assertEquals(GenerationEndReason.UNCHECKED, result.endReason());
        assertEquals(1, transport.postCount.get(), "exactly one model request — no repair round");
        assertEquals(0, transport.streamCount.get(), "sync generation never opens a stream");
    }

    @Test
    void syncValidDslIsAlsoUnchecked() {
        String dsl = "root = Stack([])";
        FakeTransport transport = FakeTransport.sync(syncResponse("```openui\n" + dsl + "\n```"));

        GenUiGenerationResult result =
                GenUiGenerator.withTransport(
                                GenUiLlmConfig.defaults(),
                                transport,
                                GenUiValidationConfig.incremental(),
                                request -> {
                                    throw new AssertionError(
                                            "sync generate() must not invoke the validator");
                                })
                        .generate(
                                UiGenerationRequest.builder().userInput("show something").build());

        assertEquals(dsl, result.dsl(), "the openui fenced block is still extracted");
        assertEquals(GenerationEndReason.UNCHECKED, result.endReason());
        assertNull(result.validationStatus());
    }

    @Test
    void syncTransportFailureThrowsWithoutMarkdownFallback() {
        FakeTransport transport = FakeTransport.failing();
        GenUiGenerator generator =
                GenUiGenerator.withTransport(
                        GenUiLlmConfig.defaults(),
                        transport,
                        GenUiValidationConfig.incremental(),
                        null);

        GenerationSdkException error =
                assertThrows(
                        GenerationSdkException.class,
                        () ->
                                generator.generate(
                                        UiGenerationRequest.builder().userInput("base").build()));

        assertEquals("Model request failed: transport down", error.getMessage());
        assertEquals(
                1,
                transport.postCount.get(),
                "failure must not trigger a Markdown fallback request");
        assertEquals(0, transport.streamCount.get());
    }

    @Test
    void syncEmptyContentThrowsWithoutMarkdownFallback() {
        FakeTransport transport =
                FakeTransport.sync(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null}}]}");
        GenUiGenerator generator =
                GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport);

        assertThrows(
                GenerationSdkException.class,
                () -> generator.generate(UiGenerationRequest.builder().userInput("base").build()));

        assertEquals(
                1,
                transport.postCount.get(),
                "an unusable response must not be retried as Markdown");
    }

    // ── 流式:轮末统一校验 ───────────────────────────────────────────────────

    @Test
    void streamInvalidDslWithFinalOnlyReturnsInvalidWithoutRepair() {
        String dsl = "root = BogusWidget()";
        FakeTransport transport = FakeTransport.stream(streamOf(dsl));
        AtomicInteger handoffs = new AtomicInteger();

        GenUiGenerationResult result =
                GenUiGenerator.withTransport(
                                GenUiLlmConfig.defaults(),
                                transport,
                                GenUiValidationConfig.finalOnly(),
                                null)
                        .generateStream(
                                UiGenerationRequest.builder().userInput("show bogus").build(),
                                envelope -> {},
                                (original, reason, sink) -> {
                                    handoffs.incrementAndGet();
                                    return new GenUiGenerationResult(
                                            "fallback", original.response());
                                });

        assertEquals(ValidationStatus.INVALID, result.validationStatus());
        assertEquals(GenerationEndReason.VALIDATION_FAILED, result.endReason());
        assertTrue(result.validationResult().hasBlockingIssues());
        assertTrue(
                result.validationResult().issues().stream()
                        .anyMatch(
                                i ->
                                        "unknown-component".equals(i.code())
                                                && i.severity() == ValidationSeverity.ERROR),
                "must have an unknown-component ERROR issue, got: "
                        + result.validationResult().issues());
        assertEquals(1, transport.streamCount.get(), "finalOnly must not issue a repair request");
        assertEquals(0, handoffs.get());
    }

    @Test
    void streamCustomValidatorForcesInvalidEvenForValidDsl() {
        String dsl = "root = Stack([])";
        FakeTransport transport = FakeTransport.stream(streamOf(dsl));
        OpenuiLangValidator alwaysInvalid =
                request ->
                        ValidationResult.invalid(
                                List.of(
                                        new ValidationIssue(
                                                "stub-error",
                                                ValidationSeverity.ERROR,
                                                "test",
                                                "stub always-invalid",
                                                null,
                                                null,
                                                null,
                                                -1,
                                                -1,
                                                null,
                                                false)),
                                new ValidationMetadata(1, "root", null));

        GenUiGenerationResult result =
                GenUiGenerator.withTransport(
                                GenUiLlmConfig.defaults(),
                                transport,
                                GenUiValidationConfig.finalOnly(),
                                alwaysInvalid)
                        .generateStream(
                                UiGenerationRequest.builder().userInput("x").build(),
                                envelope -> {});

        assertEquals(ValidationStatus.INVALID, result.validationStatus());
        assertTrue(
                result.validationResult().issues().stream()
                        .anyMatch(i -> "stub-error".equals(i.code())),
                "custom stub error code must be present");
    }

    @Test
    void streamCustomValidatorForcesValidEvenForInvalidDsl() {
        String dsl = "root = TotallyFakeComponent()";
        FakeTransport transport = FakeTransport.stream(streamOf(dsl));
        OpenuiLangValidator alwaysValid =
                request ->
                        ValidationResult.valid(
                                dsl, List.of(), new ValidationMetadata(1, "root", null));

        GenUiGenerationResult result =
                GenUiGenerator.withTransport(
                                GenUiLlmConfig.defaults(),
                                transport,
                                GenUiValidationConfig.finalOnly(),
                                alwaysValid)
                        .generateStream(
                                UiGenerationRequest.builder().userInput("x").build(),
                                envelope -> {});

        assertEquals(ValidationStatus.VALID, result.validationStatus());
        assertEquals(dsl, result.dsl());
    }

    @Test
    void streamValidatorReceivesMergedContract() {
        String dsl = "root = Stack([])";
        FakeTransport transport = FakeTransport.stream(streamOf(dsl));
        AtomicReference<ValidationRequest> capturedRequest = new AtomicReference<>();
        OpenuiLangValidator capturingValidator =
                request -> {
                    capturedRequest.set(request);
                    return new DefaultOpenuiLangValidator().validate(request);
                };

        GenUiGenerator generator =
                GenUiGenerator.withTransport(
                        GenUiLlmConfig.defaults(),
                        transport,
                        GenUiValidationConfig.finalOnly(),
                        capturingValidator);

        generator.generateStream(
                UiGenerationRequest.builder().userInput("x").response(Map.of("total", 3)).build(),
                envelope -> {});

        ValidationRequest req = capturedRequest.get();
        assertNotNull(req, "validator must have been called");
        assertNotNull(req.contract(), "validator must receive a non-null merged contract");
        assertEquals(
                Map.of("total", 3),
                req.dataModel(),
                "validator must receive the Render Data Model");
        assertTrue(
                req.contract().components().containsKey("Stack"),
                "merged contract must include Stack from base contract");
    }

    @Test
    void streamValidatorReceivesDataExternalRefWhenDataModelPresent() {
        String dsl = "root = Stack([TextContent(data.name)])";
        FakeTransport transport = FakeTransport.stream(streamOf(dsl));
        AtomicReference<ValidationRequest> capturedRequest = new AtomicReference<>();
        OpenuiLangValidator capturingValidator =
                request -> {
                    capturedRequest.set(request);
                    return new DefaultOpenuiLangValidator().validate(request);
                };

        GenUiGenerator generator =
                GenUiGenerator.withTransport(
                        GenUiLlmConfig.defaults(),
                        transport,
                        GenUiValidationConfig.finalOnly(),
                        capturingValidator);

        generator.generateStream(
                UiGenerationRequest.builder()
                        .userInput("show device")
                        .response(Map.of("name", "CloudEngine"))
                        .build(),
                envelope -> {});

        ValidationRequest req = capturedRequest.get();
        assertNotNull(req, "validator must have been called");
        assertTrue(
                req.externalRefs().contains("data"),
                "externalRefs must contain \"data\" when a dataModel is present, got: "
                        + req.externalRefs());
    }

    @Test
    void streamDataRefValidWhenDataModelPresent() {
        String dsl = "root = Stack([TextContent(data.name)])";
        FakeTransport transport = FakeTransport.stream(streamOf(dsl));

        GenUiGenerationResult result =
                GenUiGenerator.withTransport(
                                GenUiLlmConfig.defaults(),
                                transport,
                                GenUiValidationConfig.finalOnly(),
                                null)
                        .generateStream(
                                UiGenerationRequest.builder()
                                        .userInput("show device")
                                        .response(Map.of("name", "CloudEngine"))
                                        .build(),
                                envelope -> {});

        assertEquals(
                ValidationStatus.VALID,
                result.validationStatus(),
                "data.* reference must be valid when the request has a dataModel");
    }

    @Test
    void streamDataRefStillInvalidWithoutDataModel() {
        String dsl = "root = Stack([TextContent(data.name)])";
        FakeTransport transport = FakeTransport.stream(streamOf(dsl));

        GenUiGenerationResult result =
                GenUiGenerator.withTransport(
                                GenUiLlmConfig.defaults(),
                                transport,
                                GenUiValidationConfig.finalOnly(),
                                null)
                        .generateStream(
                                UiGenerationRequest.builder().userInput("show device").build(),
                                envelope -> {});

        assertEquals(ValidationStatus.INVALID, result.validationStatus());
        assertTrue(
                result.validationResult().issues().stream()
                        .anyMatch(i -> "unresolved-ref".equals(i.code())),
                "data must stay unresolved without a dataModel, got: "
                        + result.validationResult().issues());
    }

    // ── DefaultOpenuiLangValidator — standalone unit tests ────────────────────

    @Test
    void defaultValidatorNullDslReturnsMissingRoot() {
        // A null DSL string → preprocessor returns empty → root-missing ERROR → INVALID.
        DefaultOpenuiLangValidator v = new DefaultOpenuiLangValidator();
        ValidationResult result = v.validate(ValidationRequest.builder().dsl("").build());
        assertEquals(ValidationStatus.INVALID, result.status());
        assertTrue(
                result.issues().stream().anyMatch(i -> "root-missing".equals(i.code())),
                "empty DSL must produce root-missing issue");
    }

    @Test
    void nullContractTreatsAllComponentsAsUnknown() {
        // A null contract yields an EMPTY ContractCatalog, so every component is reported as
        // unknown-component — there is no contract-less "syntax-only" validation mode.
        DefaultOpenuiLangValidator v = new DefaultOpenuiLangValidator();
        ValidationResult result =
                v.validate(ValidationRequest.builder().dsl("root = AnyComponent()").build());
        // ProgramAnalyzer with empty catalog flags AnyComponent as unknown → INVALID
        assertEquals(ValidationStatus.INVALID, result.status());
        assertTrue(
                result.issues().stream().anyMatch(i -> "unknown-component".equals(i.code())),
                "AnyComponent not in empty catalog must produce unknown-component issue");
    }

    // ── Fake transport (mirrors GenUiGeneratorTest.FakeTransport) ─────────────

    private static final class FakeTransport implements InMemoryLlmTransport {
        private final String response;
        private final boolean fail;
        final AtomicInteger postCount = new AtomicInteger();
        final AtomicInteger streamCount = new AtomicInteger();

        private FakeTransport(String response, boolean fail) {
            this.response = response;
            this.fail = fail;
        }

        static FakeTransport sync(String response) {
            return new FakeTransport(response, false);
        }

        static FakeTransport stream(String stream) {
            return new FakeTransport(stream, false);
        }

        static FakeTransport failing() {
            return new FakeTransport(null, true);
        }

        @Override
        public String post(String body) throws LlmTransportException {
            postCount.incrementAndGet();
            if (fail) throw new LlmTransportException("transport down");
            return response;
        }

        @Override
        public InputStream postStream(String body) throws LlmTransportException {
            streamCount.incrementAndGet();
            if (fail) throw new LlmTransportException("transport down");
            return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
        }
    }
}
