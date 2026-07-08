/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.GenerationValidationException;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig;
import com.huawei.cloudsop.genui.core.validation.RepairPolicyKind;
import com.huawei.cloudsop.genui.core.validation.ValidationConfigMode;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Section 7.4 — SYNC full repair (FINAL_REPAIR). Fake transport scripts successive post() responses so the FIRST is
 * invalid and later ones are the repaired output. No real LLM.
 */
class GenUiGeneratorSyncRepairTest {

    private static String syncResponse(String dsl) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + dsl.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"}}]}";
    }

    private static GenUiValidationConfig finalRepair() {
        // FINAL_ONLY + FINAL_REPAIR; maxAttempts is applied internally via RepairPolicy.from default (1),
        // so exercise attempts through separate configs where needed.
        return new GenUiValidationConfig(ValidationConfigMode.FINAL_ONLY, RepairPolicyKind.FINAL_REPAIR);
    }

    private static UiGenerationRequest req() {
        return UiGenerationRequest.builder().userInput("show something").build();
    }

    // ── repair success → VALID returned ───────────────────────────────────────

    @Test
    void finalRepairFirstInvalidThenRepairedReturnsValid() {
        // First post: invalid (unknown component). Repair post: valid Stack.
        ScriptedTransport t = ScriptedTransport.forPosts(syncResponse("root = Bogus(\"x\")"),
                syncResponse("root = Stack([])"));

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), t, finalRepair(), null);

        GenUiGenerationResult result = generator.generate(req());

        assertEquals("root = Stack([])", result.dsl(), "repaired DSL must be returned");
        assertEquals(ValidationStatus.VALID, result.validationStatus());
        assertNotNull(result.validationResult());
        assertEquals(ValidationStatus.VALID, result.validationResult().status());
        assertEquals("true", result.validationResult().metadata().extra().get("repaired"),
                "repaired result must be marked repaired=true for logging");
        assertEquals(2, t.postCount(), "one original + one repair call");
    }

    // ── repair still invalid, maxAttempts=1 → throws ──────────────────────────

    @Test
    void finalRepairRepairStillInvalidThrows() {
        ScriptedTransport t = ScriptedTransport.forPosts(syncResponse("root = Bogus(\"x\")"), // original invalid
                syncResponse("root = StillBogus()")); // repair still invalid

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), t, finalRepair(), null);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> generator.generate(req()));

        assertEquals(ValidationStatus.INVALID, ex.validationResult().status());
        assertTrue(ex.validationResult().issues().stream().anyMatch(i -> "unknown-component".equals(i.code())),
                "last (still-invalid) result surfaced: " + ex.validationResult().issues());
        assertEquals(2, t.postCount(), "original + one repair attempt (maxAttempts=1)");
    }

    // ── repair transport failure → throws with last result ────────────────────

    @Test
    void finalRepairRepairTransportFailsThrows() {
        ScriptedTransport t = ScriptedTransport.forPostsThenFail(syncResponse("root = Bogus(\"x\")"));

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), t, finalRepair(), null);

        GenerationValidationException ex = assertThrows(GenerationValidationException.class,
                () -> generator.generate(req()));
        assertEquals(ValidationStatus.INVALID, ex.validationResult().status());
    }

    // ── repair revalidation must inject `data` external ref ───────────────────

    /**
     * When the request carries a dataModel, the FINAL_REPAIR revalidation (RepairCoordinator#validateFinal) must treat
     * {@code data} as an external ref — otherwise a correct repaired DSL referencing {@code data.*} is rejected.
     */
    @Test
    void finalRepairRevalidationTreatsDataAsExternalRef() {
        ScriptedTransport t = ScriptedTransport.forPosts(syncResponse("root = Bogus(\"x\")"), // original invalid
                syncResponse("root = Stack([TextContent(data.name)])")); // repaired, uses host data

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), t, finalRepair(), null);

        GenUiGenerationResult result = generator.generate(UiGenerationRequest.builder().userInput("show device")
                .response(java.util.Map.of("name", "CloudEngine")).build());

        assertEquals("root = Stack([TextContent(data.name)])", result.dsl());
        assertEquals(ValidationStatus.VALID, result.validationStatus(),
                "repaired DSL referencing data.* must revalidate as VALID when the dataModel is present");
    }

    // ── NONE policy still throws (no repair attempted) ────────────────────────

    @Test
    void repairNoneInvalidThrowsWithoutRepairCall() {
        ScriptedTransport t = ScriptedTransport.forPosts(syncResponse("root = Bogus(\"x\")"));

        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), t,
                GenUiValidationConfig.finalOnly(), null);

        assertThrows(GenerationValidationException.class, () -> generator.generate(req()));
        assertEquals(1, t.postCount(), "NONE must not issue a repair call");
    }

    // ── scripted fake transport ───────────────────────────────────────────────

    private static final class ScriptedTransport implements LlmTransport {
        private final Deque<String> posts;
        private final boolean failAfterScript;
        private int postCount = 0;

        private ScriptedTransport(List<String> posts, boolean failAfterScript) {
            this.posts = new ArrayDeque<>(posts);
            this.failAfterScript = failAfterScript;
        }

        static ScriptedTransport forPosts(String... responses) {
            return new ScriptedTransport(List.of(responses), false);
        }

        static ScriptedTransport forPostsThenFail(String... responses) {
            return new ScriptedTransport(List.of(responses), true);
        }

        int postCount() {
            return postCount;
        }

        @Override
        public String post(String body) throws LlmTransportException {
            postCount++;
            if (posts.isEmpty()) {
                if (failAfterScript)
                    throw new LlmTransportException("repair transport boom");
                throw new LlmTransportException("no scripted response");
            }
            return posts.poll();
        }

        @Override
        public InputStream postStream(String body) {
            return new ByteArrayInputStream(new byte[0]);
        }
    }
}
