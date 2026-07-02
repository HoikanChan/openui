package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Section 6 streaming statement gate. Uses {@link GenUiValidationConfig#streamingGate()} (and its
 * reask variant) with a fake transport feeding deltas that split across statement boundaries.
 *
 * <p>Assertions are on the SEQUENCE of emitted envelopes: which statements surface as {@code dsl}
 * frames, that a withheld invalid statement's text NEVER appears in any {@code dsl} frame, and that
 * fail-fast produces {@code error("VALIDATION_FAILED")} then {@code done}.
 */
class GenUiStreamingGateTest {

  // Tests use the SDK's base contract directly (root=Stack, TextContent(text), CardHeader(title?)),
  // so no extension is registered — this avoids component-name collisions with the base contract.

  private static GenUiGenerator gatedGenerator(FakeStream transport) {
    return GenUiGenerator.withTransport(
        GenUiLlmConfig.defaults(), transport, GenUiValidationConfig.streamingGate(), null);
  }

  private static GenUiGenerator reaskGenerator(FakeStream transport) {
    return GenUiGenerator.withTransport(
        GenUiLlmConfig.defaults(), transport, GenUiValidationConfig.streamingGateWithReask(), null);
  }

  private static UiGenerationRequest req() {
    return UiGenerationRequest.builder().userInput("build").build();
  }

  private static List<RenderStreamEnvelope> run(GenUiGenerator generator) {
    ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
    generator.generateStream(req(), envelopes::add);
    return envelopes;
  }

  private static List<String> dslContents(List<RenderStreamEnvelope> envelopes) {
    List<String> out = new ArrayList<>();
    for (RenderStreamEnvelope e : envelopes) {
      if (e.type().equals(RenderStreamEnvelope.TYPE_DSL)) {
        out.add(String.valueOf(e.content()));
      }
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
          "seq must strictly increase at index " + i);
    }
  }

  // ── 6.5 cases ────────────────────────────────────────────────────────────────

  @Test
  void firstFrameIsDataModelSeqZero() {
    FakeStream t = FakeStream.of(frame("root = Stack([CardHeader(\"A\")])\n"), SseFrames.done());
    List<RenderStreamEnvelope> envelopes = run(gatedGenerator(t));
    assertEquals(RenderStreamEnvelope.TYPE_DATA_MODEL, envelopes.get(0).type());
    assertEquals(0, envelopes.get(0).seq());
    assertSeqStrictlyIncreasing(envelopes);
  }

  @Test
  void multiLineComponentSpanningDeltasEmitsAsOneStatement() {
    // A single statement split across many deltas + an array spanning lines → ONE dsl frame.
    FakeStream t =
        FakeStream.of(
            frame("root = Stack([\n"),
            frame("  CardHeader(\"Hello\"),\n"),
            frame("  TextContent(\"C\")\n"),
            frame("])\n"),
            SseFrames.done());
    List<RenderStreamEnvelope> envelopes = run(gatedGenerator(t));
    List<String> dsl = dslContents(envelopes);
    assertEquals(1, dsl.size(), "multi-line component must surface as exactly one dsl statement");
    assertTrue(dsl.get(0).contains("CardHeader"));
    assertTrue(dsl.get(0).contains("TextContent"));
    assertEquals(RenderStreamEnvelope.TYPE_DONE, envelopes.get(envelopes.size() - 1).type());
    assertSeqStrictlyIncreasing(envelopes);
  }

  @Test
  void stringWithNewlineDoesNotFalselySplit() {
    // Header title contains a literal newline; the statement must not split there.
    FakeStream t =
        FakeStream.of(
            frame("root = CardHeader(\"line1\\nline2\")\n"),
            SseFrames.done());
    List<RenderStreamEnvelope> envelopes = run(gatedGenerator(t));
    List<String> dsl = dslContents(envelopes);
    assertEquals(1, dsl.size());
    assertTrue(dsl.get(0).contains("line1"));
  }

  @Test
  void ternaryAcrossLinesStaysOneStatement() {
    // Ternary spans three deltas/lines but is one statement; wrapped in Stack so the root is
    // statically renderable (the ternary is a child expression).
    FakeStream t =
        FakeStream.of(
            frame("root = Stack([true\n"),
            frame("  ? CardHeader(\"yes\")\n"),
            frame("  : CardHeader(\"no\")])\n"),
            SseFrames.done());
    List<RenderStreamEnvelope> envelopes = run(gatedGenerator(t));
    List<String> dsl = dslContents(envelopes);
    assertEquals(1, dsl.size(), "ternary spanning lines must be one statement");
    assertTrue(dsl.get(0).contains("yes"));
    assertTrue(dsl.get(0).contains("no"));
  }

  @Test
  void halfStatementNotEmittedUntilComplete() {
    // Only a partial statement arrives before done at a non-boundary — but here we send an
    // incomplete tail then complete it, verifying nothing is emitted early.
    FakeStream t =
        FakeStream.of(
            frame("root = Stack([CardHeader(\"A\")"),
            // no newline yet → still pending
            SseFrames.done());
    List<RenderStreamEnvelope> envelopes = run(gatedGenerator(t));
    // At end the pending tail is drained; auto-close repairs the bracket so it is a valid statement.
    List<String> dsl = dslContents(envelopes);
    assertEquals(1, dsl.size());
    assertTrue(dsl.get(0).contains("Header"));
  }

  @Test
  void twoStatementsEmitInOrderFirstBeforeSecondCompletes() {
    FakeStream t =
        FakeStream.of(
            frame("a = CardHeader(\"A\")\n"),
            frame("root = Stack([a])\n"),
            SseFrames.done());
    List<RenderStreamEnvelope> envelopes = run(gatedGenerator(t));
    List<String> dsl = dslContents(envelopes);
    assertEquals(2, dsl.size());
    assertTrue(dsl.get(0).contains("Header"));
    assertTrue(dsl.get(1).contains("Stack"));
    assertSeqStrictlyIncreasing(envelopes);
  }

  @Test
  void definitivelyInvalidStatementIsWithheldAndYieldsErrorThenDone() {
    // Bogus is an unknown component → definitively invalid → WITHHELD, never in any dsl frame.
    String badText = "Bogus";
    FakeStream t =
        FakeStream.of(
            frame("root = Bogus(\"x\")\n"),
            frame("extra = CardHeader(\"never\")\n"),
            SseFrames.done());
    List<RenderStreamEnvelope> envelopes = run(gatedGenerator(t));

    // No dsl frame contains the bad statement text.
    for (String dsl : dslContents(envelopes)) {
      assertFalse(dsl.contains(badText), "withheld statement text must never appear in a dsl frame: " + dsl);
    }
    // Ends with error(VALIDATION_FAILED) then done.
    RenderStreamEnvelope error = envelopes.get(envelopes.size() - 2);
    RenderStreamEnvelope done = envelopes.get(envelopes.size() - 1);
    assertEquals(RenderStreamEnvelope.TYPE_ERROR, error.type());
    Map<String, Object> payload = Json.asObject(error.content(), "error");
    assertEquals("VALIDATION_FAILED", payload.get("code"));
    assertEquals(false, payload.get("retryable"));
    assertEquals(RenderStreamEnvelope.TYPE_DONE, done.type());
    assertNull(done.content());
    assertSeqStrictlyIncreasing(envelopes);
  }

  @Test
  void reaskPolicyBehavesLikeFailPathForNow() {
    FakeStream t = FakeStream.of(frame("root = Bogus(\"x\")\n"), SseFrames.done());
    List<RenderStreamEnvelope> envelopes = run(reaskGenerator(t));
    RenderStreamEnvelope error = envelopes.get(envelopes.size() - 2);
    assertEquals(RenderStreamEnvelope.TYPE_ERROR, error.type());
    assertEquals("VALIDATION_FAILED", Json.asObject(error.content(), "error").get("code"));
    assertEquals(RenderStreamEnvelope.TYPE_DONE, envelopes.get(envelopes.size() - 1).type());
  }

  @Test
  void temporaryUnresolvedBufferedThenFlushedWhenDependencyArrives() {
    // root references `a` before it is defined → root buffered; when `a` arrives root flushes.
    FakeStream t =
        FakeStream.of(
            frame("root = Stack([a])\n"),
            frame("a = CardHeader(\"A\")\n"),
            SseFrames.done());
    List<RenderStreamEnvelope> envelopes = run(gatedGenerator(t));
    List<String> dsl = dslContents(envelopes);
    // Both statements eventually emit; no error.
    assertFalse(types(envelopes).contains(RenderStreamEnvelope.TYPE_ERROR));
    assertEquals(2, dsl.size(), "buffered root + its dependency both flush");
    assertTrue(dsl.stream().anyMatch(s -> s.contains("Stack")));
    assertTrue(dsl.stream().anyMatch(s -> s.contains("Header")));
    assertEquals(RenderStreamEnvelope.TYPE_DONE, envelopes.get(envelopes.size() - 1).type());
    assertSeqStrictlyIncreasing(envelopes);
  }

  @Test
  void cleanEndReturnsValidStatus() {
    FakeStream t = FakeStream.of(frame("root = Stack([CardHeader(\"A\")])\n"), SseFrames.done());
    ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
    GenUiGenerationResult result = gatedGenerator(t).generateStream(req(), envelopes::add);
    assertEquals(ValidationStatus.VALID, result.validationStatus());
    assertTrue(result.dsl().contains("Stack"));
  }

  @Test
  void withheldStatusIsInvalid() {
    FakeStream t = FakeStream.of(frame("root = Bogus(\"x\")\n"), SseFrames.done());
    ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
    GenUiGenerationResult result = gatedGenerator(t).generateStream(req(), envelopes::add);
    assertEquals(ValidationStatus.INVALID, result.validationStatus());
  }

  @Test
  void fenceWrappedDslIsGatedNormally() {
    FakeStream t =
        FakeStream.of(
            frame("```openui\n"),
            frame("root = Stack([CardHeader(\"A\")])\n"),
            frame("```"),
            SseFrames.done());
    List<RenderStreamEnvelope> envelopes = run(gatedGenerator(t));
    List<String> dsl = dslContents(envelopes);
    assertEquals(1, dsl.size());
    assertFalse(dsl.get(0).contains("```"), "fence markers must be stripped from accepted dsl");
    assertTrue(dsl.get(0).contains("Stack"));
  }

  // ── fake transport ──────────────────────────────────────────────────────────

  private static String frame(String content) {
    return "data: {\"choices\":[{\"delta\":{\"content\":" + Json.stringify(content) + "}}]}\n\n";
  }

  private static final class FakeStream implements LlmTransport {
    private final String stream;

    private FakeStream(String stream) {
      this.stream = stream;
    }

    static FakeStream of(String... frames) {
      return new FakeStream(String.join("", frames));
    }

    @Override
    public String post(String body) {
      throw new UnsupportedOperationException("sync not used");
    }

    @Override
    public InputStream postStream(String body) throws LlmTransportException {
      return new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8));
    }
  }
}
