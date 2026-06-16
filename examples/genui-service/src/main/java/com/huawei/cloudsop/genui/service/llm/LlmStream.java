package com.huawei.cloudsop.genui.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI 兼容 SSE 响应流:把 delta.content 文本逐段写出,返回 finish_reason。
 *
 * <p>与被替换的 Node 版一致:只透传文本增量,usage 仅打日志;finish_reason 为 null
 * 表示连接被掐断(代理/网络),由调用方追加错误尾巴。
 */
public final class LlmStream implements Closeable {
  private static final Logger log = LoggerFactory.getLogger(LlmStream.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  private final BufferedReader reader;

  public LlmStream(InputStream body) {
    this.reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
  }

  /** 把文本增量写入 out(逐段 flush,保证 chunked 流式),返回 finish_reason(可能为 null)。 */
  public String pipeTo(OutputStream out) throws IOException {
    String finishReason = null;
    String line;
    while ((line = reader.readLine()) != null) {
      if (!line.startsWith("data:")) {
        continue;
      }
      String data = line.substring("data:".length()).trim();
      if (data.isEmpty()) {
        continue;
      }
      if ("[DONE]".equals(data)) {
        break;
      }
      JsonNode chunk = JSON.readTree(data);
      JsonNode choice = chunk.path("choices").path(0);
      String text = choice.path("delta").path("content").asText("");
      if (!text.isEmpty()) {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
      }
      JsonNode reason = choice.path("finish_reason");
      if (reason.isTextual()) {
        finishReason = reason.asText();
      }
      JsonNode usage = chunk.path("usage");
      if (usage.isObject()) {
        log.info("[llm] usage: {}", usage);
      }
    }
    return finishReason;
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }
}
