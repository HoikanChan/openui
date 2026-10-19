/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.RepairPolicyKind;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.repair.RepairCoordinator.FullRepairOutcome;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Attempt-budget and timeout behavior of {@link RepairCoordinator#repairFull}, plus the {@link FullRepairOutcome}
 * outcome-kind / per-attempt-log visibility that repair-bench consumes.
 *
 * @since 2026
 */
class RepairCoordinatorTest {

    private static String syncResponse(String dsl) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + dsl.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"}}]}";
    }

    private static GenerationContract baseContract() {
        // Empty contract → any component is unknown. Good enough to force INVALID unless Stack is used;
        // but with an empty contract even Stack is unknown, so the "success" path here uses a stub
        // validator via DefaultOpenuiLangValidator with a real base contract is overkill — instead we
        // assert the still-invalid/timeout branches, which don't need a passing DSL.
        return new GenerationContract("1.0", "Stack", java.util.Map.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static RepairCoordinator coordinator(LlmTransport t, RepairPolicy policy) {
        return new RepairCoordinator(t, new DefaultOpenuiLangValidator(), policy)
                .withBodyBuilder((messages, stream) -> "{}");
    }

    private static ValidationResult invalidSeed() {
        // A cheap seed INVALID result; issues drive the first repair prompt only.
        return new DefaultOpenuiLangValidator().validate(com.huawei.cloudsop.genui.core.validation.ValidationRequest
                .builder().dsl("root = Bogus()").contract(baseContract()).rootName("Stack")
                .mode(com.huawei.cloudsop.genui.core.validation.ValidationMode.FINAL).build());
    }

    @Test
    void retryExhaustedReportsStillInvalidWithAttemptLog() {
        // maxAttempts=2, both repairs still invalid (empty contract → Stack is unknown too).
        ScriptedPosts t = ScriptedPosts.of(syncResponse("root = StillBad1()"), syncResponse("root = StillBad2()"));
        RepairCoordinator c = coordinator(t, RepairPolicy.of(RepairPolicyKind.FINAL_REPAIR, 2));

        FullRepairOutcome outcome = c.repairFull("intent", "root = Bogus()", invalidSeed(), baseContract(), "Stack");

        assertEquals(FullRepairOutcome.OutcomeKind.STILL_INVALID, outcome.kind());
        assertFalse(outcome.repaired());
        assertFalse(outcome.timedOut());
        assertEquals(2, outcome.attempts(), "both attempts consumed");
        assertEquals(2, outcome.attemptLog().size());
        assertEquals(1, outcome.attemptLog().get(0).attempt());
        assertFalse(outcome.attemptLog().get(0).valid());
        assertTrue(
                outcome.attemptLog().get(0).issues().stream()
                        .anyMatch(issue -> "unknown-component".equals(issue.code())),
                "first attempt revalidation keeps its issues");
        assertEquals("root = StillBad2()", outcome.dsl(), "last repaired DSL text surfaced");
        assertEquals(2, t.count());
    }

    @Test
    void timeoutStopsBeforeConsumingAllAttempts() {
        // A tiny already-elapsed timeout: the loop should bail on the first deadline check and never
        // post at all (attemptLog empty, kind=TIMED_OUT).
        ScriptedPosts t = ScriptedPosts.of(syncResponse("root = StillBad()"));
        RepairPolicy policy = new RepairPolicy(RepairPolicyKind.FINAL_REPAIR, 3, Duration.ofNanos(1));
        RepairCoordinator c = coordinator(t, policy);

        FullRepairOutcome outcome = c.repairFull("intent", "root = Bogus()", invalidSeed(), baseContract(), "Stack");

        assertEquals(FullRepairOutcome.OutcomeKind.TIMED_OUT, outcome.kind());
        assertTrue(outcome.timedOut(), "should report timeout");
        assertFalse(outcome.repaired());
        assertEquals(0, outcome.attempts(), "no completed attempts once the deadline is already past");
        assertTrue(outcome.attemptLog().isEmpty());
        assertEquals(0, t.count(), "no repair posts issued once the deadline is already past");
    }

    @Test
    void transportFailureReportsTransportErrorKind() {
        ScriptedPosts t = ScriptedPosts.failing();
        RepairCoordinator c = coordinator(t, RepairPolicy.of(RepairPolicyKind.FINAL_REPAIR, 2));

        FullRepairOutcome outcome = c.repairFull("intent", "root = Bogus()", invalidSeed(), baseContract(), "Stack");

        assertEquals(FullRepairOutcome.OutcomeKind.TRANSPORT_ERROR, outcome.kind(),
                "transport failure is distinguishable from STILL_INVALID (bench 重跑 vs 改提示词的分流依据)");
        assertFalse(outcome.repaired());
        assertEquals(1, outcome.attempts(), "stops at the failing attempt");
        assertEquals(1, outcome.attemptLog().size());
        FullRepairOutcome.AttemptRecord record = outcome.attemptLog().get(0);
        assertEquals(1, record.attempt());
        assertFalse(record.valid());
        assertNull(record.issues(), "transport-failed attempt carries null issues");
    }

    @Test
    void repairedCarriesAttemptLogAndValidRecord() {
        ScriptedPosts t = ScriptedPosts.of(syncResponse("root = Stack([])"));
        com.huawei.cloudsop.genui.core.validation.OpenuiLangValidator alwaysValid = request -> com.huawei.cloudsop.genui.core.validation.ValidationResult
                .valid(request.dsl(), List.of(), new com.huawei.cloudsop.genui.core.validation.ValidationMetadata(1,
                        "root", com.huawei.cloudsop.genui.core.validation.ValidationMode.FINAL, null));
        RepairCoordinator c = new RepairCoordinator(t, alwaysValid, RepairPolicy.of(RepairPolicyKind.FINAL_REPAIR, 3))
                .withBodyBuilder((messages, stream) -> "{}");

        FullRepairOutcome outcome = c.repairFull("intent", "root = Bogus()", invalidSeed(), baseContract(), "Stack");

        assertEquals(FullRepairOutcome.OutcomeKind.REPAIRED, outcome.kind());
        assertTrue(outcome.repaired());
        assertEquals(1, outcome.attempts());
        FullRepairOutcome.AttemptRecord record = outcome.attemptLog().get(0);
        assertTrue(record.valid());
        assertEquals(List.of(), record.issues());
    }

    @Test
    void fullRepairRevalidationPreservesRenderDataModel() {
        ScriptedPosts transport = ScriptedPosts.of(syncResponse("root = Stack([])"));
        AtomicReference<com.huawei.cloudsop.genui.core.validation.ValidationRequest> captured = new AtomicReference<>();
        com.huawei.cloudsop.genui.core.validation.OpenuiLangValidator validator = request -> {
            captured.set(request);
            return com.huawei.cloudsop.genui.core.validation.ValidationResult.valid(request.dsl(), List.of(),
                    new com.huawei.cloudsop.genui.core.validation.ValidationMetadata(1, "root",
                            com.huawei.cloudsop.genui.core.validation.ValidationMode.FINAL, null));
        };
        RepairCoordinator coordinator = new RepairCoordinator(transport, validator,
                RepairPolicy.of(RepairPolicyKind.FINAL_REPAIR, 1)).withBodyBuilder((messages, stream) -> "{}")
                .withDataModel(Map.of("total", 3));

        FullRepairOutcome outcome = coordinator.repairFull("intent", "root = Bogus()", invalidSeed(), baseContract(),
                "Stack");

        assertTrue(outcome.repaired());
        assertEquals(Map.of("total", 3), captured.get().dataModel(),
                "re-validation must receive the Render Data Model");
    }

    private static final class ScriptedPosts
            implements
                com.huawei.cloudsop.genui.core.llm.transport.InMemoryLlmTransport {
        private final Deque<String> responses;
        private final boolean fail;
        private int count = 0;

        private ScriptedPosts(List<String> responses, boolean fail) {
            this.responses = new ArrayDeque<>(responses);
            this.fail = fail;
        }

        static ScriptedPosts of(String... responses) {
            return new ScriptedPosts(List.of(responses), false);
        }

        static ScriptedPosts failing() {
            return new ScriptedPosts(List.of(), true);
        }

        int count() {
            return count;
        }

        @Override
        public String post(String body) throws LlmTransportException {
            count++;
            if (fail || responses.isEmpty()) {
                throw new LlmTransportException("boom");
            }
            return responses.poll();
        }

        @Override
        public InputStream postStream(String body) {
            return new ByteArrayInputStream(new byte[0]);
        }
    }
}
