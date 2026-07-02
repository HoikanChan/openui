package com.huawei.cloudsop.genui.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.service.llm.LlmClient;
import com.huawei.cloudsop.genui.service.llm.LlmStream;
import com.huawei.cloudsop.genui.service.llm.LlmUpstreamException;

/**
 * 生成端点行为:SDK 校验门产生的 dataModel/dsl/error/done envelope 序列化为 SSE JSON。
 *
 * <p>协议(design Decision #7):首帧 dataModel(seq=0)→ 若干 dsl(仅门放行的完整语句)→ done;
 * withhold 的非法语句触发 error(VALIDATION_FAILED)→ done,其文本从不出现在 dsl 帧;流前失败 502 不变;
 * finish_reason 非 stop / 流中途失败 → error(LLM_STREAM_FAILED)→ done(取代旧文本尾巴);
 * 空 prompt 400 / 未知 extensionId 404 / promptOverride 不变。
 */
@SpringBootTest
@AutoConfigureMockMvc
class GenerateApiTest {
  @Autowired MockMvc mvc;
  @MockBean LlmClient llmClient;

  @Test
  void streamsAcceptedDslAsEnvelopeFrames() throws Exception {
    when(llmClient.openStream(anyString(), anyString())).thenReturn(stream(
        "data: {\"choices\":[{\"delta\":{\"content\":\"root = Stack([])\\n\"}}]}\n\n"
            + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
            + "data: [DONE]\n"));

    List<Frame> frames = performGenerate("{\"prompt\":\"展示UI\"}");

    assertEquals("dataModel", frames.get(0).type);
    assertEquals(0, frames.get(0).seq);
    // 接受的 DSL 出现在某个 dsl 帧的 content 里
    assertTrue(
        frames.stream()
            .anyMatch(f -> "dsl".equals(f.type) && String.valueOf(f.content).contains("root = Stack([])")),
        "accepted DSL must appear in a dsl frame: " + frames);
    assertEquals("done", frames.get(frames.size() - 1).type);
    assertFalse(hasType(frames, "error"), "clean stream must not emit an error frame: " + frames);
    assertSeqMonotonic(frames);

    // 未带 promptOverride 时,system prompt 必须来自 SDK 拼装(以契约 preamble 开头)
    ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
    verify(llmClient).openStream(systemPrompt.capture(), anyString());
    assertTrue(systemPrompt.getValue().startsWith("You are an AI assistant"));
  }

  @Test
  void withholdsDefinitivelyInvalidStatementThenErrorThenDone() throws Exception {
    // NoSuchComponent 是未知组件 → 完整语句被校验门判定为 definitively invalid → WITHHOLD。
    when(llmClient.openStream(anyString(), anyString())).thenReturn(stream(
        "data: {\"choices\":[{\"delta\":{\"content\":\"root = NoSuchComponent([])\\n\"}}]}\n\n"
            + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
            + "data: [DONE]\n"));

    List<Frame> frames = performGenerate("{\"prompt\":\"展示UI\"}");

    assertEquals("dataModel", frames.get(0).type);
    // 被 withhold 的语句文本从不进 dsl 帧
    assertFalse(
        frames.stream()
            .anyMatch(f -> "dsl".equals(f.type) && String.valueOf(f.content).contains("NoSuchComponent")),
        "withheld statement text must never appear in a dsl frame: " + frames);
    Frame error = lastOfType(frames, "error");
    assertEquals("VALIDATION_FAILED", errorCode(error));
    assertEquals("done", frames.get(frames.size() - 1).type);
    assertSeqMonotonic(frames);
  }

  @Test
  void promptOverrideBypassesAssembly() throws Exception {
    when(llmClient.openStream(anyString(), anyString())).thenReturn(stream(
        "data: {\"choices\":[{\"delta\":{\"content\":\"root = Stack([])\\n\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n"));

    performGenerate("{\"prompt\":\"展示UI\",\"promptOverride\":\"CUSTOM SYSTEM PROMPT\"}");

    ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
    verify(llmClient).openStream(systemPrompt.capture(), anyString());
    assertEquals("CUSTOM SYSTEM PROMPT", systemPrompt.getValue());
  }

  @Test
  void emitsErrorFrameWhenFinishReasonIsNotStop() throws Exception {
    when(llmClient.openStream(anyString(), anyString())).thenReturn(stream(
        "data: {\"choices\":[{\"delta\":{\"content\":\"root = Stack([])\\n\"},\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n"));

    List<Frame> frames = performGenerate("{\"prompt\":\"展示UI\"}");

    Frame error = lastOfType(frames, "error");
    assertEquals("LLM_STREAM_FAILED", errorCode(error));
    assertTrue(String.valueOf(errorMessage(error)).contains("finish_reason=length"), frames.toString());
    assertEquals("done", frames.get(frames.size() - 1).type);
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
  void unknownextensionIdReturns404BeforeStreaming() throws Exception {
    mvc.perform(
            post("/v1/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"展示UI\",\"extensionId\":\"no-such\"}"))
        .andExpect(status().isNotFound());
  }

  // ── SSE frame parsing helpers ─────────────────────────────────────────────

  private static final class Frame {
    final String type;
    final int seq;
    final Object content;

    Frame(String type, int seq, Object content) {
      this.type = type;
      this.seq = seq;
      this.content = content;
    }

    @Override
    public String toString() {
      return "Frame{" + type + ", seq=" + seq + ", content=" + content + "}";
    }
  }

  private List<Frame> performGenerate(String body) throws Exception {
    MvcResult started =
        mvc.perform(post("/v1/generate").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(request().asyncStarted())
            .andReturn();
    String raw =
        mvc.perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return parseFrames(raw);
  }

  @SuppressWarnings("unchecked")
  private static List<Frame> parseFrames(String raw) {
    List<Frame> frames = new ArrayList<>();
    for (String block : raw.split("\n\n")) {
      String line = block.trim();
      if (!line.startsWith("data:")) {
        continue;
      }
      String json = line.substring("data:".length()).trim();
      if (json.isEmpty()) {
        continue;
      }
      Map<String, Object> obj = (Map<String, Object>) Json.parse(json);
      frames.add(
          new Frame(
              String.valueOf(obj.get("type")),
              ((Number) obj.get("seq")).intValue(),
              obj.get("content")));
    }
    return frames;
  }

  private static boolean hasType(List<Frame> frames, String type) {
    return frames.stream().anyMatch(f -> type.equals(f.type));
  }

  private static Frame lastOfType(List<Frame> frames, String type) {
    Frame found = null;
    for (Frame f : frames) {
      if (type.equals(f.type)) {
        found = f;
      }
    }
    if (found == null) {
      throw new AssertionError("no frame of type " + type + " in " + frames);
    }
    return found;
  }

  @SuppressWarnings("unchecked")
  private static Object errorCode(Frame f) {
    return ((Map<String, Object>) f.content).get("code");
  }

  @SuppressWarnings("unchecked")
  private static Object errorMessage(Frame f) {
    return ((Map<String, Object>) f.content).get("message");
  }

  private static void assertSeqMonotonic(List<Frame> frames) {
    for (int i = 1; i < frames.size(); i++) {
      assertTrue(
          frames.get(i).seq > frames.get(i - 1).seq,
          "seq must strictly increase: " + frames);
    }
  }

  private static LlmStream stream(String sse) {
    return new LlmStream(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));
  }
}
