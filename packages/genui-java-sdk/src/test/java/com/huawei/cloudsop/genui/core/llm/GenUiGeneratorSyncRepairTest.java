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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Section 7.4 — SYNC full repair (FINAL_REPAIR). Fake transport scripts successive post() responses
 * so the FIRST is invalid and later ones are the repaired output. No real LLM.
 */
class GenUiGeneratorSyncRepairTest {

  private static String syncResponse(String dsl) {
    return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
        + dsl.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        + "\"}}]}";
  }

  private static GenUiValidationConfig finalRepair(int maxAttempts) {
    // FINAL_ONLY + FINAL_REPAIR; maxAttempts is applied internally via RepairPolicy.from default (1),
    // so exercise attempts through separate configs where needed.
    return new GenUiValidationConfig(ValidationConfigMode.FINAL_ONLY, RepairPolicyKind.FINAL_REPAIR);
  }

  private static UiGenerationRequest req() {
    return UiGenerationRequest.builder().userInput("show something").build();
  }

  // ── repair success → VALID returned ───────────────────────────────────────

  @Test
  void finalRepair_firstInvalidThenRepaired_returnsValid() {
    // First post: invalid (unknown component). Repair post: valid Stack.
    ScriptedTransport t =
        ScriptedTransport.forPosts(
            syncResponse("root = Bogus(\"x\")"), syncResponse("root = Stack([])"));

    GenUiGenerator generator =
        GenUiGenerator.withTransport(
            GenUiLlmConfig.defaults(), t, finalRepair(1), null);

    GenUiGenerationResult result = generator.generate(req());

    assertEquals("root = Stack([])", result.dsl(), "repaired DSL must be returned");
    assertEquals(ValidationStatus.VALID, result.validationStatus());
    assertNotNull(result.validationResult());
    assertEquals(ValidationStatus.VALID, result.validationResult().status());
    assertEquals(
        "true",
        result.validationResult().metadata().extra().get("repaired"),
        "repaired result must be marked repaired=true for logging");
    assertEquals(2, t.postCount(), "one original + one repair call");
  }

  // ── repair still invalid, maxAttempts=1 → throws ──────────────────────────

  @Test
  void finalRepair_repairStillInvalid_throws() {
    ScriptedTransport t =
        ScriptedTransport.forPosts(
            syncResponse("root = Bogus(\"x\")"), // original invalid
            syncResponse("root = StillBogus()")); // repair still invalid

    GenUiGenerator generator =
        GenUiGenerator.withTransport(
            GenUiLlmConfig.defaults(), t, finalRepair(1), null);

    GenerationValidationException ex =
        assertThrows(GenerationValidationException.class, () -> generator.generate(req()));

    assertEquals(ValidationStatus.INVALID, ex.validationResult().status());
    assertTrue(
        ex.validationResult().issues().stream().anyMatch(i -> "unknown-component".equals(i.code())),
        "last (still-invalid) result surfaced: " + ex.validationResult().issues());
    assertEquals(2, t.postCount(), "original + one repair attempt (maxAttempts=1)");
  }

  // ── repair transport failure → throws with last result ────────────────────

  @Test
  void finalRepair_repairTransportFails_throws() {
    ScriptedTransport t =
        ScriptedTransport.forPostsThenFail(syncResponse("root = Bogus(\"x\")"));

    GenUiGenerator generator =
        GenUiGenerator.withTransport(
            GenUiLlmConfig.defaults(), t, finalRepair(1), null);

    GenerationValidationException ex =
        assertThrows(GenerationValidationException.class, () -> generator.generate(req()));
    assertEquals(ValidationStatus.INVALID, ex.validationResult().status());
  }

  // ── NONE policy still throws (no repair attempted) ────────────────────────

  @Test
  void repairNone_invalid_throwsWithoutRepairCall() {
    ScriptedTransport t = ScriptedTransport.forPosts(syncResponse("root = Bogus(\"x\")"));

    GenUiGenerator generator =
        GenUiGenerator.withTransport(
            GenUiLlmConfig.defaults(), t, GenUiValidationConfig.finalOnly(), null);

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
        if (failAfterScript) throw new LlmTransportException("repair transport boom");
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
