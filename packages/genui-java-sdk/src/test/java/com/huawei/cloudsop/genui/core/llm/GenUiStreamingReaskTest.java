package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.stream.SseFrames;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Section 7.5 — STREAMING Fail-Fast Reask. A scripted transport returns the ORIGINAL stream on the
 * first {@code postStream} and a corrected CONTINUATION on the reask. Asserts: bad statement text
 * never appears in any {@code dsl} frame, the corrected statement is emitted as ordinary {@code
 * dsl}, the stream ends {@code done} with VALID, and content following the withheld statement in the
 * original stream is never emitted (fail-fast cancels the original, reask takes over).
 */
class GenUiStreamingReaskTest {

  private static String frame(String content) {
    return "data: {\"choices\":[{\"delta\":{\"content\":" + Json.stringify(content) + "}}]}\n\n";
  }

  private static GenUiGenerator reaskGenerator(ScriptedStream t) {
    return GenUiGenerator.withTransport(
        GenUiLlmConfig.defaults(), t, GenUiValidationConfig.streamingGateWithReask(), null);
  }

  private static UiGenerationRequest req() {
    return UiGenerationRequest.builder().userInput("build").build();
  }

  private static List<String> dslContents(List<RenderStreamEnvelope> envelopes) {
    List<String> out = new ArrayList<>();
    for (RenderStreamEnvelope e : envelopes) {
      if (e.type().equals(RenderStreamEnvelope.TYPE_DSL)) out.add(String.valueOf(e.content()));
    }
    return out;
  }

  private static List<String> types(List<RenderStreamEnvelope> envelopes) {
    List<String> out = new ArrayList<>();
    for (RenderStreamEnvelope e : envelopes) out.add(e.type());
    return out;
  }

  private static void assertSeqStrictlyIncreasing(List<RenderStreamEnvelope> envelopes) {
    for (int i = 1; i < envelopes.size(); i++) {
      assertTrue(
          envelopes.get(i).seq() > envelopes.get(i - 1).seq(),
          "seq must strictly increase across the takeover at index " + i);
    }
  }

  // ── reask success ─────────────────────────────────────────────────────────

  @Test
  void reaskSuccess_badTextNeverEmitted_correctedEmitted_endsValid() {
    // Original: root = Bogus(...) is definitively invalid → withheld. Reask continues from the
    // (empty) accepted prefix with a valid root. Final DSL validates VALID.
    ScriptedStream t =
        ScriptedStream.forStreams(
            frame("root = Bogus(\"x\")\n") + SseFrames.done(),
            frame("root = Stack([CardHeader(\"A\")])\n") + SseFrames.done());

    ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
    GenUiGenerationResult result = reaskGenerator(t).generateStream(req(), envelopes::add);

    // Bad statement text never appears in any dsl frame.
    for (String dsl : dslContents(envelopes)) {
      assertFalse(dsl.contains("Bogus"), "withheld/bad text must never appear in dsl: " + dsl);
    }
    // Corrected statement emitted as ordinary dsl.
    List<String> dsl = dslContents(envelopes);
    assertTrue(dsl.stream().anyMatch(s -> s.contains("Stack")), "corrected continuation emitted");
    assertTrue(dsl.stream().anyMatch(s -> s.contains("CardHeader")), "corrected children emitted");
    // No error frame; ends done + VALID.
    assertFalse(types(envelopes).contains(RenderStreamEnvelope.TYPE_ERROR), "no error frame on success");
    assertEquals(RenderStreamEnvelope.TYPE_DONE, envelopes.get(envelopes.size() - 1).type());
    assertEquals(ValidationStatus.VALID, result.validationStatus());
    assertTrue(result.dsl().contains("Stack"));
    assertFalse(result.dsl().contains("Bogus"), "final DSL must not contain the bad statement");
    assertSeqStrictlyIncreasing(envelopes);
    assertEquals(2, t.streamCount(), "one original + one reask continuation");
  }

  // ── reask preserves an already-accepted prefix + emits corrected tail ─────

  @Test
  void reaskContinuesFromAcceptedPrefix_emittingCorrectedTail() {
    // 'a' emits (valid). Then root references 'a' but the model wrote a definitively-invalid root.
    // Reask continues from accepted prefix (a) with a valid root that keeps referencing 'a'.
    ScriptedStream t =
        ScriptedStream.forStreams(
            frame("a = CardHeader(\"A\")\n") + frame("root = Bogus([a])\n") + SseFrames.done(),
            frame("root = Stack([a])\n") + SseFrames.done());

    ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
    GenUiGenerationResult result = reaskGenerator(t).generateStream(req(), envelopes::add);

    List<String> dsl = dslContents(envelopes);
    for (String frag : dsl) assertFalse(frag.contains("Bogus"), "bad text never in dsl: " + frag);
    assertTrue(dsl.stream().anyMatch(s -> s.contains("CardHeader")), "pre-withhold valid emitted");
    assertTrue(dsl.stream().anyMatch(s -> s.contains("Stack")), "corrected continuation emitted");
    assertEquals(ValidationStatus.VALID, result.validationStatus());
    assertTrue(result.dsl().contains("CardHeader") && result.dsl().contains("Stack"));
    assertSeqStrictlyIncreasing(envelopes);
    assertEquals(2, t.streamCount());
  }

  // ── reask still invalid → error -> done, INVALID ──────────────────────────

  @Test
  void reaskStillInvalid_errorThenDone_invalidStatus() {
    ScriptedStream t =
        ScriptedStream.forStreams(
            frame("root = Bogus(\"x\")\n") + SseFrames.done(),
            frame("root = AlsoBogus()\n") + SseFrames.done());

    ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
    GenUiGenerationResult result = reaskGenerator(t).generateStream(req(), envelopes::add);

    for (String dsl : dslContents(envelopes)) {
      assertFalse(dsl.contains("Bogus"), "bad text never in dsl: " + dsl);
    }
    RenderStreamEnvelope error = envelopes.get(envelopes.size() - 2);
    RenderStreamEnvelope done = envelopes.get(envelopes.size() - 1);
    assertEquals(RenderStreamEnvelope.TYPE_ERROR, error.type());
    assertEquals("VALIDATION_FAILED", Json.asObject(error.content(), "error").get("code"));
    assertEquals(RenderStreamEnvelope.TYPE_DONE, done.type());
    assertEquals(ValidationStatus.INVALID, result.validationStatus());
    assertSeqStrictlyIncreasing(envelopes);
    assertEquals(2, t.streamCount(), "original + one reask attempt (maxAttempts=1)");
  }

  // ── original stream content past the withhold is never emitted ────────────

  @Test
  void originalStreamContentAfterWithholdNeverEmitted() {
    // After the invalid root the original stream has MORE content that must NEVER surface as dsl —
    // the reask (not the abandoned original tail) supplies the real continuation.
    ScriptedStream t =
        ScriptedStream.forStreams(
            frame("root = Bogus(\"x\")\n")
                + frame("leaked = TextContent(\"LEAKED\")\n")
                + SseFrames.done(),
            frame("root = Stack([CardHeader(\"A\")])\n") + SseFrames.done());

    ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
    GenUiGenerationResult result = reaskGenerator(t).generateStream(req(), envelopes::add);

    for (String dsl : dslContents(envelopes)) {
      assertFalse(dsl.contains("LEAKED"), "content after the withhold must not be emitted: " + dsl);
      assertFalse(dsl.contains("Bogus"), "bad text never in dsl: " + dsl);
    }
    assertFalse(result.dsl().contains("LEAKED"), "abandoned original tail must not reach final DSL");
    assertEquals(ValidationStatus.VALID, result.validationStatus());
    assertEquals(2, t.streamCount(), "reask took over from a NEW stream, not the original tail");
  }

  // ── scripted streaming transport ──────────────────────────────────────────

  private static final class ScriptedStream implements LlmTransport {
    private final Deque<String> streams;
    private int streamCount = 0;

    private ScriptedStream(List<String> streams) {
      this.streams = new ArrayDeque<>(streams);
    }

    static ScriptedStream forStreams(String... bodies) {
      return new ScriptedStream(List.of(bodies));
    }

    int streamCount() {
      return streamCount;
    }

    @Override
    public String post(String body) {
      throw new UnsupportedOperationException("sync not used");
    }

    @Override
    public InputStream postStream(String body) throws LlmTransportException {
      if (streams.isEmpty()) {
        throw new LlmTransportException("no scripted stream");
      }
      streamCount++;
      return new ByteArrayInputStream(streams.poll().getBytes(StandardCharsets.UTF_8));
    }
  }
}
