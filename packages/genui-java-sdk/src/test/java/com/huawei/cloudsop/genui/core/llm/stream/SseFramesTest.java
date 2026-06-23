package com.huawei.cloudsop.genui.core.llm.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SseFramesTest {
  @Test
  void wrapsContentAsSseFrame() {
    assertEquals("data: hello\n\n", SseFrames.of("hello"));
  }

  @Test
  void wrapsEachLineOfMultilineContentAsSseData() {
    assertEquals("data: first\ndata: second\n\n", SseFrames.of("first\nsecond"));
  }

  @Test
  void wrapsDoneAsSseFrame() {
    assertEquals("data: [DONE]\n\n", SseFrames.done());
  }
}
