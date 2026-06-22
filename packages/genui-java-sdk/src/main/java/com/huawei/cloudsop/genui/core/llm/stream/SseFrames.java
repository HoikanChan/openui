package com.huawei.cloudsop.genui.core.llm.stream;

public final class SseFrames {
  private SseFrames() {}

  public static String of(String content) {
    return "data: " + String.valueOf(content) + "\n\n";
  }

  public static String done() {
    return "data: [DONE]\n\n";
  }
}
