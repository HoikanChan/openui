package com.huawei.cloudsop.genui.core.llm.stream;

import com.huawei.cloudsop.genui.core.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class SseDeltaParser {
  private SseDeltaParser() {}

  public static String parse(InputStream sseStream, Consumer<String> sink) throws IOException {
    Objects.requireNonNull(sseStream, "sseStream must not be null");
    Objects.requireNonNull(sink, "sink must not be null");

    StringBuilder pending = new StringBuilder();
    StringBuilder accumulated = new StringBuilder();
    try (InputStreamReader reader = new InputStreamReader(sseStream, StandardCharsets.UTF_8)) {
      char[] buffer = new char[1024];
      int read;
      while ((read = reader.read(buffer)) != -1) {
        pending.append(buffer, 0, read);
        drainCompleteFrames(pending, accumulated, sink);
      }
    }
    if (!pending.isEmpty()) {
      handleFrame(pending.toString(), accumulated, sink);
    }
    return accumulated.toString();
  }

  private static void drainCompleteFrames(
      StringBuilder pending, StringBuilder accumulated, Consumer<String> sink) {
    int boundary;
    while ((boundary = frameBoundary(pending)) >= 0) {
      String frame = pending.substring(0, boundary);
      int separatorLength = separatorLength(pending, boundary);
      pending.delete(0, boundary + separatorLength);
      handleFrame(frame, accumulated, sink);
    }
  }

  private static int frameBoundary(StringBuilder text) {
    int lf = text.indexOf("\n\n");
    int crlf = text.indexOf("\r\n\r\n");
    if (lf < 0) return crlf;
    if (crlf < 0) return lf;
    return Math.min(lf, crlf);
  }

  private static int separatorLength(StringBuilder text, int boundary) {
    return text.substring(boundary).startsWith("\r\n\r\n") ? 4 : 2;
  }

  private static void handleFrame(
      String frame, StringBuilder accumulated, Consumer<String> sink) {
    String payload = dataPayload(frame);
    if (payload == null || payload.isBlank() || "[DONE]".equals(payload.trim())) {
      return;
    }

    try {
      Map<String, Object> object = Json.asObject(Json.parse(payload), "SSE frame");
      List<Object> choices = Json.asList(object.get("choices"), "choices");
      if (choices.isEmpty()) return;
      Map<String, Object> first = Json.asObject(choices.getFirst(), "choice");
      Map<String, Object> delta = Json.asObject(first.get("delta"), "delta");
      Object contentValue = delta.get("content");
      if (!(contentValue instanceof String content) || content.isEmpty()) return;
      sink.accept(content);
      accumulated.append(content);
    } catch (RuntimeException ignored) {
      // Bad frames should not interrupt the rest of the stream.
    }
  }

  private static String dataPayload(String frame) {
    StringBuilder payload = new StringBuilder();
    for (String line : frame.split("\\R")) {
      if (!line.startsWith("data:")) continue;
      String data = line.substring("data:".length());
      if (data.startsWith(" ")) data = data.substring(1);
      if (!payload.isEmpty()) payload.append('\n');
      payload.append(data);
    }
    return payload.isEmpty() ? null : payload.toString();
  }
}
