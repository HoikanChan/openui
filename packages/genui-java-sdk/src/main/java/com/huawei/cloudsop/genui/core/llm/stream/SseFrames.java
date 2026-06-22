package com.huawei.cloudsop.genui.core.llm.stream;

public final class SseFrames {
  private SseFrames() {}

  public static String of(String content) {
    StringBuilder frame = new StringBuilder();
    for (String line : String.valueOf(content).split("\\R", -1)) {
      frame.append("data: ").append(line).append('\n');
    }
    frame.append('\n');
    return frame.toString();
  }

  public static String done() {
    return "data: [DONE]\n\n";
  }
}
