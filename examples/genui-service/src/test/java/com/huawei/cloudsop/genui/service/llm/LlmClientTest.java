package com.huawei.cloudsop.genui.service.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LlmClientTest {

  @Test
  void bypassesProxyOnExactHostOrDomainSuffix() {
    assertTrue(LlmClient.shouldBypassProxy("https://api.deepseek.com", "api.deepseek.com"));
    assertTrue(LlmClient.shouldBypassProxy("https://api.deepseek.com/v1", "deepseek.com"));
    assertFalse(LlmClient.shouldBypassProxy("https://api.deepseek.com", "openai.com"));
    assertFalse(LlmClient.shouldBypassProxy("https://api.deepseek.com", ""));
    assertFalse(LlmClient.shouldBypassProxy("https://notdeepseek.com", "deepseek.com"));
    assertTrue(LlmClient.shouldBypassProxy("https://api.deepseek.com", "foo.com, deepseek.com"));
  }

  @Test
  void joinsChatCompletionsUrlWithoutDoubleSlash() {
    assertEquals("https://api.deepseek.com/chat/completions", LlmClient.chatCompletionsUrl("https://api.deepseek.com"));
    assertEquals("https://api.deepseek.com/chat/completions", LlmClient.chatCompletionsUrl("https://api.deepseek.com/"));
  }

  @Test
  void disablesThinkingOnlyForDeepseekV4Models() {
    LlmClient v4 = new LlmClient(new LlmProperties("k", "https://api.deepseek.com", "deepseek-v4-lite", "", ""));
    assertTrue(v4.buildRequestBody("sys", "user").contains("\"thinking\":{\"type\":\"disabled\"}"));

    LlmClient chat = new LlmClient(new LlmProperties("k", "https://api.deepseek.com", "deepseek-chat", "", ""));
    assertFalse(chat.buildRequestBody("sys", "user").contains("thinking"));
  }
}
