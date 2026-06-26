package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.ComponentGroup;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenUIExtension;
import com.huawei.cloudsop.genui.core.contract.ToolSpec;
import com.huawei.cloudsop.genui.core.llm.stream.SseFrames;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenUiGeneratorTest {
  @Test
  void generatesWithRegisteredExtensionAndExtractsOpenuiBlock() {
    FakeTransport transport =
        FakeTransport.sync(
            "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"```openui\\nroot = Stack([])\\n```\"}}]}");
    GenUiGenerator generator =
        GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport).register(extension());

    Map<String, Object> response = orderedMap("alarms", List.of(orderedMap("id", 7)));
    GenUiGenerationResult result =
        generator.generate(
            UiGenerationRequest.builder()
                .extensionId("alarm")
                .userInput("show alarms")
                .response(response)
                .suggestion("Prefer alarm cards")
                .toolCalls(true)
                .build());

    assertEquals("root = Stack([])", result.dsl());
    assertEquals(response, result.dataModel());
    Map<String, Object> body = Json.asObject(Json.parse(transport.lastBody), "request");
    assertEquals(false, body.get("stream"));
    assertEquals(GenUiLlmConfig.DEFAULT_MODEL, body.get("model"));
    assertFalse(body.containsKey("response_format"));
    List<Object> messages = Json.asList(body.get("messages"), "messages");
    Map<String, Object> system = Json.asObject(messages.get(0), "system");
    Map<String, Object> user = Json.asObject(messages.get(1), "user");
    String prompt = String.valueOf(system.get("content"));
    assertTrue(prompt.contains("AlarmCard(title: string)"));
    assertTrue(prompt.contains("lookupAlarm"));
    assertTrue(prompt.contains("\"alarms\""));
    assertTrue(prompt.contains("Prefer alarm cards"));
    assertTrue(prompt.contains("Use AlarmCard for alarms"));
    assertTrue(String.valueOf(user.get("content")).endsWith(" /no_think"));
  }

  @Test
  void syncWithoutResponseReturnsEmptyDataModel() {
    FakeTransport transport =
        FakeTransport.sync("{\"choices\":[{\"message\":{\"content\":\"root = Stack([])\"}}]}");

    GenUiGenerationResult result =
        GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
            .generate(UiGenerationRequest.builder().userInput("base").build());

    assertEquals("root = Stack([])", result.dsl());
    assertTrue(result.dataModel().isEmpty());
  }

  @Test
  void syncThrowsOnTransportError() {
    GenUiGenerator generator =
        GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), FakeTransport.failing());

    assertThrows(
        GenerationSdkException.class,
        () -> generator.generate(UiGenerationRequest.builder().userInput("base").build()));
  }

  @Test
  void unregisteredExtensionFallsBackToBaseContract() {
    FakeTransport transport =
        FakeTransport.sync("{\"choices\":[{\"message\":{\"content\":\"root = Stack([])\"}}]}");
    GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport);

    generator.generate(UiGenerationRequest.builder().extensionId("missing").userInput("base").build());

    Map<String, Object> body = Json.asObject(Json.parse(transport.lastBody), "request");
    List<Object> messages = Json.asList(body.get("messages"), "messages");
    String prompt = String.valueOf(Json.asObject(messages.get(0), "system").get("content"));
    assertTrue(prompt.contains("Stack("));
    assertFalse(prompt.contains("AlarmCard("));
  }

  @Test
  void streamEmitsDataModelThenDslThenDone() {
    FakeTransport transport =
        FakeTransport.stream(
            frame("```openui\n"),
            frame("root = Stack([])\n```"),
            SseFrames.done());
    ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
    Map<String, Object> response = orderedMap("alarms", List.of());

    GenUiGenerationResult result =
        GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
            .generateStream(
                UiGenerationRequest.builder()
                    .userInput("stream")
                    .response(response)
                    .build(),
                envelopes::add);

    // 首帧 dataModel
    RenderStreamEnvelope first = envelopes.get(0);
    assertEquals(RenderStreamEnvelope.TYPE_DATA_MODEL, first.type());
    assertEquals(0, first.seq());
    assertEquals(response, first.content());

    // DSL chunk,seq 从 1 起递增
    assertEquals(RenderStreamEnvelope.TYPE_DSL, envelopes.get(1).type());
    assertEquals(1, envelopes.get(1).seq());
    assertEquals("```openui\n", envelopes.get(1).content());
    assertEquals(RenderStreamEnvelope.TYPE_DSL, envelopes.get(2).type());
    assertEquals(2, envelopes.get(2).seq());
    assertEquals("root = Stack([])\n```", envelopes.get(2).content());

    // done 帧无 content
    RenderStreamEnvelope done = envelopes.get(3);
    assertEquals(RenderStreamEnvelope.TYPE_DONE, done.type());
    assertEquals(3, done.seq());
    assertNull(done.content());

    assertEquals("root = Stack([])", result.dsl());
    assertEquals(response, result.dataModel());
    Map<String, Object> body = Json.asObject(Json.parse(transport.lastBody), "request");
    assertEquals(true, body.get("stream"));
  }

  @Test
  void streamWithoutResponseEmitsEmptyDataModelFrame() {
    FakeTransport transport = FakeTransport.stream(frame("root = Stack([])"), SseFrames.done());
    ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();

    GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
        .generateStream(UiGenerationRequest.builder().userInput("stream").build(), envelopes::add);

    RenderStreamEnvelope first = envelopes.get(0);
    assertEquals(RenderStreamEnvelope.TYPE_DATA_MODEL, first.type());
    assertEquals(Map.of(), first.content());
  }

  @Test
  void streamFailureEmitsErrorThenDoneAndReturnsAccumulated() {
    FakeTransport transport =
        FakeTransport.streamThenFail(frame("```openui\nroot = Stack([])\n```"));
    ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();

    // 不向调用方抛出该流中异常
    GenUiGenerationResult result =
        GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
            .generateStream(
                UiGenerationRequest.builder().userInput("stream").build(), envelopes::add);

    // 末尾是 error 后接 done
    RenderStreamEnvelope error = envelopes.get(envelopes.size() - 2);
    RenderStreamEnvelope done = envelopes.get(envelopes.size() - 1);
    assertEquals(RenderStreamEnvelope.TYPE_ERROR, error.type());
    Map<String, Object> payload = Json.asObject(error.content(), "error");
    assertEquals("LLM_STREAM_FAILED", payload.get("code"));
    assertEquals(true, payload.get("retryable"));
    assertEquals(RenderStreamEnvelope.TYPE_DONE, done.type());

    // 返回结果包含已累计内容的提取结果
    assertEquals("root = Stack([])", result.dsl());
  }

  @Test
  void usesConfiguredModelAndRequestContext() {
    FakeTransport transport =
        FakeTransport.sync("{\"choices\":[{\"message\":{\"content\":\"root = Stack([])\"}}]}");
    GenUiLlmConfig config = GenUiLlmConfig.builder().defaultModel("custom-model").build();

    GenUiGenerator.withTransport(config, transport)
        .generate(
            UiGenerationRequest.builder()
                .userInput("build")
                .request(orderedMap("title", "Alarm overview"))
                .build());

    Map<String, Object> body = Json.asObject(Json.parse(transport.lastBody), "request");
    assertEquals("custom-model", body.get("model"));
    List<Object> messages = Json.asList(body.get("messages"), "messages");
    String user = String.valueOf(Json.asObject(messages.get(1), "user").get("content"));
    assertTrue(user.contains("Alarm overview"));
  }

  @Test
  void createDoesNotExposeSdkOrTransport() {
    assertNotNull(GenUiGenerator.create());
    assertNotNull(GenUiGenerator.create(GenUiLlmConfig.defaults()));
  }

  @Test
  void registerIsChainable() {
    GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), FakeTransport.sync("{}"));
    assertSame(generator, generator.register(extension()));
  }

  private static String frame(String content) {
    return "data: {\"choices\":[{\"delta\":{\"content\":"
        + Json.stringify(content)
        + "}}]}\n\n";
  }

  private static GenUIExtension extension() {
    return new GenUIExtension(
        "alarm",
        "v1",
        Map.of("AlarmCard", new ComponentPromptSpec("AlarmCard(title: string)", "Alarm card")),
        List.of(new ComponentGroup("Alarm", List.of("AlarmCard"), List.of())),
        List.of(tool("lookupAlarm")),
        List.of("root = Stack([AlarmCard(title: \"A\")])"),
        List.of("Use AlarmCard for alarms"));
  }

  private static ToolSpec tool(String name) {
    return new ToolSpec(name, name + " description", Map.of("type", "object"), Map.of(), null);
  }

  private static LinkedHashMap<String, Object> orderedMap(String key, Object value) {
    LinkedHashMap<String, Object> map = new LinkedHashMap<>();
    map.put(key, value);
    return map;
  }

  private static final class FakeTransport implements LlmTransport {
    private final String response;
    private final String stream;
    private final boolean fail;
    private final boolean failMidStream;
    private String lastBody;

    private FakeTransport(String response, String stream, boolean fail, boolean failMidStream) {
      this.response = response;
      this.stream = stream;
      this.fail = fail;
      this.failMidStream = failMidStream;
    }

    static FakeTransport sync(String response) {
      return new FakeTransport(response, null, false, false);
    }

    static FakeTransport stream(String... frames) {
      return new FakeTransport(null, String.join("", frames), false, false);
    }

    static FakeTransport streamThenFail(String... frames) {
      return new FakeTransport(null, String.join("", frames), false, true);
    }

    static FakeTransport failing() {
      return new FakeTransport(null, null, true, false);
    }

    @Override
    public String post(String body) throws LlmTransportException {
      lastBody = body;
      if (fail) throw new LlmTransportException("boom");
      return response;
    }

    @Override
    public InputStream postStream(String body) throws LlmTransportException {
      lastBody = body;
      if (fail) throw new LlmTransportException("boom");
      InputStream frames = new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8));
      return failMidStream ? new FrameThenFailStream(frames) : frames;
    }
  }

  /** 先吐出全部帧字节,再在下一次 read 时抛 IOException,模拟流中途断开。 */
  private static final class FrameThenFailStream extends InputStream {
    private final InputStream delegate;

    private FrameThenFailStream(InputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public int read() throws IOException {
      int value = delegate.read();
      if (value == -1) throw new IOException("connection dropped");
      return value;
    }

    @Override
    public int read(byte[] buffer, int off, int len) throws IOException {
      int read = delegate.read(buffer, off, len);
      if (read == -1) throw new IOException("connection dropped");
      return read;
    }
  }
}
