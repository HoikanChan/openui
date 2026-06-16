package com.huawei.cloudsop.genui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.cloudsop.genui.service.llm.LlmClient;
import com.huawei.cloudsop.genui.service.llm.LlmStream;
import com.huawei.cloudsop.genui.service.llm.LlmUpstreamException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 生成端点行为:流式转发、Prompt Override 旁路、错误尾巴、流前失败 502、空 prompt 400。 */
@SpringBootTest
@AutoConfigureMockMvc
class GenerateApiTest {
  @Autowired MockMvc mvc;
  @MockBean LlmClient llmClient;

  @Test
  void streamsLlmOutputAsPlainText() throws Exception {
    when(llmClient.openStream(anyString(), anyString())).thenReturn(stream(
        "data: {\"choices\":[{\"delta\":{\"content\":\"root = Stack([])\"}}]}\n\n"
            + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
            + "data: [DONE]\n"));

    assertEquals("root = Stack([])", performGenerate("{\"prompt\":\"展示UI\"}"));

    // 未带 promptOverride 时,system prompt 必须来自 SDK 拼装(以契约 preamble 开头)
    ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
    verify(llmClient).openStream(systemPrompt.capture(), anyString());
    assertTrue(systemPrompt.getValue().startsWith("You are an AI assistant"));
  }

  @Test
  void promptOverrideBypassesAssembly() throws Exception {
    when(llmClient.openStream(anyString(), anyString())).thenReturn(stream(
        "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n"));

    performGenerate("{\"prompt\":\"展示UI\",\"promptOverride\":\"CUSTOM SYSTEM PROMPT\"}");

    ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
    verify(llmClient).openStream(systemPrompt.capture(), anyString());
    assertEquals("CUSTOM SYSTEM PROMPT", systemPrompt.getValue());
  }

  @Test
  void appendsErrorTailWhenFinishReasonIsNotStop() throws Exception {
    when(llmClient.openStream(anyString(), anyString())).thenReturn(stream(
        "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"},\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n"));

    assertEquals(
        "partial\n\n[ERROR: incomplete, finish_reason=length]",
        performGenerate("{\"prompt\":\"展示UI\"}"));
  }

  @Test
  void emptyPromptReturns400() throws Exception {
    mvc.perform(post("/v1/generate").contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"  \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void upstreamFailureBeforeFirstTokenReturns502() throws Exception {
    when(llmClient.openStream(anyString(), anyString()))
        .thenThrow(new LlmUpstreamException("LLM HTTP 401: bad key"));
    mvc.perform(post("/v1/generate").contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"展示UI\"}"))
        .andExpect(status().isBadGateway());
  }

  @Test
  void unknownContextIdReturns404BeforeStreaming() throws Exception {
    mvc.perform(
            post("/v1/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"展示UI\",\"contextId\":\"no-such\"}"))
        .andExpect(status().isNotFound());
  }

  private String performGenerate(String body) throws Exception {
    MvcResult started =
        mvc.perform(post("/v1/generate").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(request().asyncStarted())
            .andReturn();
    return mvc.perform(asyncDispatch(started))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  private static LlmStream stream(String sse) {
    return new LlmStream(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));
  }
}
