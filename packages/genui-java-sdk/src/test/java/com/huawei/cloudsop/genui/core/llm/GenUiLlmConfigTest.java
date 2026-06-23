package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GenUiLlmConfigTest {
  @Test
  void defaultsUseBuiltInEndpointModelAndGenerationParameters() {
    GenUiLlmConfig config = GenUiLlmConfig.defaults();

    assertEquals(GenUiLlmConfig.DEFAULT_ENDPOINT, config.endpoint());
    assertEquals(GenUiLlmConfig.DEFAULT_MODEL, config.defaultModel());
    assertEquals(0.0, config.temperature());
    assertFalse(config.enableThinking());
    assertTrue(config.jsonObjectResponse());
    assertTrue(config.extraHeaders().isEmpty());
  }

  @Test
  void builderOverridesEndpointModelAndTemperature() {
    GenUiLlmConfig config =
        GenUiLlmConfig.builder()
            .endpoint("/custom/chat")
            .defaultModel("custom-model")
            .temperature(0.7)
            .enableThinking(true)
            .jsonObjectResponse(false)
            .build();

    assertEquals("/custom/chat", config.endpoint());
    assertEquals("custom-model", config.defaultModel());
    assertEquals(0.7, config.temperature());
    assertTrue(config.enableThinking());
    assertFalse(config.jsonObjectResponse());
  }

  @Test
  void builderFallsBackToDefaultsForBlankEndpointAndModel() {
    GenUiLlmConfig config =
        GenUiLlmConfig.builder().endpoint(" ").defaultModel("").build();

    assertEquals(GenUiLlmConfig.DEFAULT_ENDPOINT, config.endpoint());
    assertEquals(GenUiLlmConfig.DEFAULT_MODEL, config.defaultModel());
  }

  @Test
  void extraHeadersAreImmutableAndDefensivelyCopied() {
    Map<String, String> headers = new java.util.LinkedHashMap<>();
    headers.put("X-One", "1");
    GenUiLlmConfig config = GenUiLlmConfig.builder().extraHeaders(headers).putHeader("X-Two", "2").build();

    headers.put("X-Three", "3");

    assertEquals(Map.of("X-One", "1", "X-Two", "2"), config.extraHeaders());
    assertThrows(UnsupportedOperationException.class, () -> config.extraHeaders().put("X-Four", "4"));
  }
}
