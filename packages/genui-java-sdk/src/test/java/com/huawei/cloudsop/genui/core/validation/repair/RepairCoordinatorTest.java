package com.huawei.cloudsop.genui.core.validation.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.RepairPolicyKind;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Attempt-budget and timeout behavior of {@link RepairCoordinator#repairFull}. */
class RepairCoordinatorTest {

  private static String syncResponse(String dsl) {
    return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
        + dsl.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        + "\"}}]}";
  }

  private static GenerationContract baseContract() {
    // Empty contract → any component is unknown. Good enough to force INVALID unless Stack is used;
    // but with an empty contract even Stack is unknown, so the "success" path here uses a stub
    // validator via DefaultOpenuiLangValidator with a real base contract is overkill — instead we
    // assert the exhausted/timeout branches, which don't need a passing DSL.
    return new GenerationContract(
        "1.0", "Stack", java.util.Map.of(), List.of(), List.of(), List.of(), List.of());
  }

  private static RepairCoordinator coordinator(LlmTransport t, RepairPolicy policy) {
    return new RepairCoordinator(t, new DefaultOpenuiLangValidator(), policy)
        .withBodyBuilder((messages, stream) -> "{}");
  }

  private static ValidationResult invalidSeed() {
    // A cheap seed INVALID result; issues drive the first repair prompt only.
    return new DefaultOpenuiLangValidator()
        .validate(
            com.huawei.cloudsop.genui.core.validation.ValidationRequest.builder()
                .dsl("root = Bogus()")
                .contract(baseContract())
                .rootName("Stack")
                .mode(com.huawei.cloudsop.genui.core.validation.ValidationMode.FINAL)
                .build());
  }

  @Test
  void retryExhausted_reportsNotRepairedWithAttemptCount() {
    // maxAttempts=2, both repairs still invalid (empty contract → Stack is unknown too).
    ScriptedPosts t =
        ScriptedPosts.of(syncResponse("root = StillBad1()"), syncResponse("root = StillBad2()"));
    RepairCoordinator c = coordinator(t, RepairPolicy.of(RepairPolicyKind.FINAL_REPAIR, 2));

    RepairCoordinator.FullRepairOutcome outcome =
        c.repairFull("intent", "root = Bogus()", invalidSeed(), baseContract(), "Stack");

    assertFalse(outcome.repaired());
    assertEquals(2, outcome.attempts(), "both attempts consumed");
    assertFalse(outcome.timedOut());
    assertEquals(2, t.count());
  }

  @Test
  void timeout_stopsBeforeConsumingAllAttempts() {
    // A tiny already-elapsed timeout: the loop should bail on the first deadline check and never
    // post at all (attempts=0, timedOut=true).
    ScriptedPosts t = ScriptedPosts.of(syncResponse("root = StillBad()"));
    RepairPolicy policy =
        new RepairPolicy(RepairPolicyKind.FINAL_REPAIR, 3, Duration.ofNanos(1), Duration.ZERO);
    RepairCoordinator c = coordinator(t, policy);

    // Sleep a hair so the nano deadline is definitely in the past by the first check.
    RepairCoordinator.FullRepairOutcome outcome =
        c.repairFull("intent", "root = Bogus()", invalidSeed(), baseContract(), "Stack");

    assertTrue(outcome.timedOut(), "should report timeout");
    assertFalse(outcome.repaired());
    assertEquals(0, t.count(), "no repair posts issued once the deadline is already past");
  }

  @Test
  void transportFailure_reportsNotRepaired() {
    ScriptedPosts t = ScriptedPosts.failing();
    RepairCoordinator c = coordinator(t, RepairPolicy.of(RepairPolicyKind.FINAL_REPAIR, 2));

    RepairCoordinator.FullRepairOutcome outcome =
        c.repairFull("intent", "root = Bogus()", invalidSeed(), baseContract(), "Stack");

    assertFalse(outcome.repaired());
    assertEquals(1, outcome.attempts(), "stops at the failing attempt");
  }

  @Test
  void fullRepairRevalidationPreservesRenderDataModel() {
    ScriptedPosts transport = ScriptedPosts.of(syncResponse("root = Stack([])"));
    AtomicReference<com.huawei.cloudsop.genui.core.validation.ValidationRequest> captured =
        new AtomicReference<>();
    var validator =
        (com.huawei.cloudsop.genui.core.validation.OpenuiLangValidator)
            request -> {
              captured.set(request);
              return com.huawei.cloudsop.genui.core.validation.ValidationResult.valid(
                  request.dsl(),
                  List.of(),
                  new com.huawei.cloudsop.genui.core.validation.ValidationMetadata(
                      1,
                      "root",
                      com.huawei.cloudsop.genui.core.validation.ValidationMode.FINAL,
                      null));
            };
    RepairCoordinator coordinator =
        new RepairCoordinator(
                transport, validator, RepairPolicy.of(RepairPolicyKind.FINAL_REPAIR, 1))
            .withBodyBuilder((messages, stream) -> "{}");

    coordinator.repairFull(
        "intent",
        "root = Bogus()",
        invalidSeed(),
        baseContract(),
        "Stack",
        Map.of("total", 3));

    assertEquals(Map.of("total", 3), captured.get().dataModel());
  }

  private static final class ScriptedPosts implements LlmTransport {
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
      if (fail || responses.isEmpty()) throw new LlmTransportException("boom");
      return responses.poll();
    }

    @Override
    public InputStream postStream(String body) {
      return new ByteArrayInputStream(new byte[0]);
    }
  }
}
