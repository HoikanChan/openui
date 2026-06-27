package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.stream.SseFrames;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.prompt.characterize.CharacterizationConfig;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the core invariant from spec 5.3: characterization touches ONLY the prompt copy of the
 * host data. The seq=0 {@code dataModel} stream envelope and {@code GenUiGenerationResult.dataModel()}
 * must always carry the full, untouched {@code response()} — regardless of whether the prompt's
 * embedded JSON sample was reduced by the characterization gate.
 */
class CharacterizationIntegrationTest {
  private static final int ROW_COUNT = 200;

  @Test
  void generateKeepsFullDataModelButCharacterizesPromptCopy() {
    FakeTransport transport =
        FakeTransport.sync(
            "{\"choices\":[{\"message\":{\"content\":\"```openui\\nroot = Stack([])\\n```\"}}]}");
    Map<String, Object> response = largeHostData();
    GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport);

    GenUiGenerationResult result =
        generator.generate(
            UiGenerationRequest.builder().userInput("show rows").response(response).build());

    // Render Data Model stays full — untouched by characterization.
    assertEquals(response, result.dataModel());
    assertRowCount(result.dataModel(), ROW_COUNT);

    // The PROMPT copy was characterized: sidecar present, full enum domain, sampled JSON rows.
    String prompt = systemPrompt(transport.lastBody);
    assertTrue(prompt.contains("Data shape (full dataset):"));
    assertTrue(prompt.contains("\"closed\" | \"open\" | \"pending\""));
    assertTrue(promptJsonRowCount(prompt) < ROW_COUNT);
  }

  @Test
  void generateStreamKeepsFullDataModelInSeqZeroEnvelopeAndResult() {
    FakeTransport transport =
        FakeTransport.stream(frame("```openui\n"), frame("root = Stack([])\n```"), SseFrames.done());
    ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
    Map<String, Object> response = largeHostData();

    GenUiGenerationResult result =
        GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
            .generateStream(
                UiGenerationRequest.builder().userInput("show rows").response(response).build(),
                envelopes::add);

    RenderStreamEnvelope first = envelopes.get(0);
    assertEquals(RenderStreamEnvelope.TYPE_DATA_MODEL, first.type());
    assertEquals(0, first.seq());
    assertEquals(response, first.content());
    assertRowCount(asDataModel(first.content()), ROW_COUNT);

    assertEquals(response, result.dataModel());
    assertRowCount(result.dataModel(), ROW_COUNT);
  }

  @Test
  void disabledCharacterizationFallsBackToFullPromptCopy() {
    FakeTransport transport =
        FakeTransport.sync(
            "{\"choices\":[{\"message\":{\"content\":\"```openui\\nroot = Stack([])\\n```\"}}]}");
    Map<String, Object> response = largeHostData();
    CharacterizationConfig disabled = CharacterizationConfig.builder().enabled(false).build();
    GenUiGenerator generator =
        GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), disabled, transport);

    GenUiGenerationResult result =
        generator.generate(
            UiGenerationRequest.builder().userInput("show rows").response(response).build());

    assertEquals(response, result.dataModel());

    String prompt = systemPrompt(transport.lastBody);
    assertFalse(prompt.contains("Data shape (full dataset):"));
    assertEquals(ROW_COUNT, promptJsonRowCount(prompt));
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  /**
   * 200 rows of {@code {id, status}} with status cycling open/closed/pending so the full enum
   * domain is NOT entirely present within the first {@code sampleRows} (default 3) rows, but IS
   * present across the full 200-row scan.
   */
  private static Map<String, Object> largeHostData() {
    String[] statuses = {"open", "closed", "pending"};
    List<Object> rows = new ArrayList<>(ROW_COUNT);
    for (int i = 0; i < ROW_COUNT; i++) {
      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("id", (long) i);
      row.put("status", statuses[i % statuses.length]);
      rows.add(row);
    }
    LinkedHashMap<String, Object> response = new LinkedHashMap<>();
    response.put("tickets", rows);
    return response;
  }

  @SuppressWarnings("unchecked")
  private static void assertRowCount(Map<String, Object> dataModel, int expected) {
    List<Object> rows = (List<Object>) dataModel.get("tickets");
    assertEquals(expected, rows.size());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asDataModel(Object content) {
    return (Map<String, Object>) content;
  }

  private static String systemPrompt(String body) {
    Map<String, Object> parsed = Json.asObject(Json.parse(body), "request");
    List<Object> messages = Json.asList(parsed.get("messages"), "messages");
    Map<String, Object> system = Json.asObject(messages.get(0), "system");
    return String.valueOf(system.get("content"));
  }

  /** Counts occurrences of {@code "id":} within the embedded ```json block to approximate row count. */
  private static int promptJsonRowCount(String prompt) {
    int start = prompt.indexOf("```json");
    int end = prompt.indexOf("```", start + 7);
    String jsonBlock = prompt.substring(start + 7, end);
    int count = 0;
    int index = 0;
    while ((index = jsonBlock.indexOf("\"id\":", index)) != -1) {
      count++;
      index += 5;
    }
    return count;
  }

  private static String frame(String content) {
    return "data: {\"choices\":[{\"delta\":{\"content\":" + Json.stringify(content) + "}}]}\n\n";
  }

  private static final class FakeTransport implements LlmTransport {
    private final String response;
    private final String stream;
    private String lastBody;

    private FakeTransport(String response, String stream) {
      this.response = response;
      this.stream = stream;
    }

    static FakeTransport sync(String response) {
      return new FakeTransport(response, null);
    }

    static FakeTransport stream(String... frames) {
      return new FakeTransport(null, String.join("", frames));
    }

    @Override
    public String post(String body) throws LlmTransportException {
      lastBody = body;
      return response;
    }

    @Override
    public InputStream postStream(String body) throws LlmTransportException {
      lastBody = body;
      return new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8));
    }
  }
}
