package com.huawei.cloudsop.genui.service.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LlmStreamTest {

  @Test
  void pipesDeltaContentAndReturnsFinishReason() throws Exception {
    String sse =
        "data: {\"choices\":[{\"delta\":{\"content\":\"root = \"}}]}\n"
            + "\n"
            + "data: {\"choices\":[{\"delta\":{\"content\":\"Stack([])\"}}]}\n"
            + "\n"
            + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"total_tokens\":42}}\n"
            + "\n"
            + "data: [DONE]\n";
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (LlmStream stream = new LlmStream(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)))) {
      String finishReason = stream.pipeTo(out);
      assertEquals("stop", finishReason);
    }
    assertEquals("root = Stack([])", out.toString(StandardCharsets.UTF_8));
  }

  @Test
  void returnsNullFinishReasonWhenConnectionDropped() throws Exception {
    String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n";
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (LlmStream stream = new LlmStream(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)))) {
      assertNull(stream.pipeTo(out));
    }
    assertEquals("partial", out.toString(StandardCharsets.UTF_8));
  }

  @Test
  void preservesMultibyteUtf8Content() throws Exception {
    String sse =
        "data: {\"choices\":[{\"delta\":{\"content\":\"设备列表 — 全部在线\"}}]}\n"
            + "\n"
            + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n"
            + "\n"
            + "data: [DONE]\n";
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (LlmStream stream = new LlmStream(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)))) {
      stream.pipeTo(out);
    }
    assertEquals("设备列表 — 全部在线", out.toString(StandardCharsets.UTF_8));
  }
}
