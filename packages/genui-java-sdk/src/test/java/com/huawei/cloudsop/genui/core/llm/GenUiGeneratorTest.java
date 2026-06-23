package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.ComponentGroup;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenUIGeneration;
import com.huawei.cloudsop.genui.core.contract.ToolSpec;
import com.huawei.cloudsop.genui.core.llm.stream.SseFrames;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenUiGeneratorTest {
  @Test
  void generatesWithRegisteredExtensionAndExtractsOpenuiBlock() {
    FakeTransport transport =
        FakeTransport.sync(
            "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"```openui\\nroot = Stack([])\\n```\"}}]}");
    GenUiGenerator generator =
        GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport).register(extension());

    String result =
        generator.generate(
            UiGenerationRequest.builder()
                .extensionId("alarm")
                .userInput("show alarms")
                .response(orderedMap("alarms", List.of(orderedMap("id", 7))))
                .suggestion("Prefer alarm cards")
                .overlayTools(List.of(tool("lookupTicket")))
                .overlayRules(List.of("Overlay rule"))
                .toolCalls(true)
                .build());

    assertEquals("root = Stack([])", result);
    Map<String, Object> body = Json.asObject(Json.parse(transport.lastBody), "request");
    assertEquals(false, body.get("stream"));
    assertEquals(GenUiLlmConfig.DEFAULT_MODEL, body.get("model"));
    List<Object> messages = Json.asList(body.get("messages"), "messages");
    Map<String, Object> system = Json.asObject(messages.get(0), "system");
    Map<String, Object> user = Json.asObject(messages.get(1), "user");
    String prompt = String.valueOf(system.get("content"));
    assertTrue(prompt.contains("AlarmCard(title: string)"));
    assertTrue(prompt.contains("lookupTicket"));
    assertTrue(prompt.contains("\"alarms\""));
    assertTrue(prompt.contains("Prefer alarm cards"));
    assertTrue(prompt.contains("Overlay rule"));
    assertTrue(String.valueOf(user.get("content")).endsWith(" /no_think"));
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
  void streamsDeltasAndExtractsFinalCode() {
    FakeTransport transport =
        FakeTransport.stream(
            frame("```openui\n"),
            frame("root = Stack([])\n```"),
            SseFrames.done());
    ArrayList<String> deltas = new ArrayList<>();

    String result =
        GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
            .generateStream(UiGenerationRequest.builder().userInput("stream").build(), deltas::add);

    assertEquals(List.of("```openui\n", "root = Stack([])\n```"), deltas);
    assertEquals("root = Stack([])", result);
    Map<String, Object> body = Json.asObject(Json.parse(transport.lastBody), "request");
    assertEquals(true, body.get("stream"));
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

  private static GenUIGeneration extension() {
    return new GenUIGeneration(
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
