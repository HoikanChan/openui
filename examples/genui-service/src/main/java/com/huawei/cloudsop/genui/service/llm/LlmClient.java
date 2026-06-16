package com.huawei.cloudsop.genui.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** OpenAI 兼容接口客户端:JDK HttpClient,零额外依赖;行为对齐被替换的 Node 版。 */
@Component
public class LlmClient {
  private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  private final LlmProperties props;
  private final HttpClient httpClient;

  public LlmClient(LlmProperties props) {
    this.props = props;
    HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15));
    ProxySelector proxy = resolveProxy(props);
    if (proxy != null) {
      builder.proxy(proxy);
    }
    this.httpClient = builder.build();
    if (props.apiKey() == null || props.apiKey().isBlank()) {
      log.warn("[llm] LLM_API_KEY is not set — /generate will fail with 502; registration/assembly endpoints still work");
    }
  }

  /** 建立流式补全;在产生首个 token 前的任何失败都抛 {@link LlmUpstreamException}(映射 502)。 */
  public LlmStream openStream(String systemPrompt, String userPrompt) {
    if (props.apiKey() == null || props.apiKey().isBlank()) {
      throw new LlmUpstreamException("LLM_API_KEY is not set");
    }
    if (props.baseUrl() == null || props.baseUrl().isBlank()) {
      throw new LlmUpstreamException("LLM_BASE_URL is not set");
    }
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(chatCompletionsUrl(props.baseUrl())))
            .header("Authorization", "Bearer " + props.apiKey())
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(systemPrompt, userPrompt), StandardCharsets.UTF_8))
            .build();
    try {
      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() / 100 != 2) {
        String detail = readErrorBody(response.body());
        throw new LlmUpstreamException("LLM HTTP " + response.statusCode() + ": " + detail);
      }
      return new LlmStream(response.body());
    } catch (IOException e) {
      throw new LlmUpstreamException("LLM request failed: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LlmUpstreamException("LLM request interrupted", e);
    }
  }

  String buildRequestBody(String systemPrompt, String userPrompt) {
    ObjectNode body = JSON.createObjectNode();
    body.put("model", props.model());
    ArrayNode messages = body.putArray("messages");
    ObjectNode system = messages.addObject();
    system.put("role", "system");
    system.put("content", systemPrompt);
    ObjectNode user = messages.addObject();
    user.put("role", "user");
    user.put("content", userPrompt);
    body.put("max_tokens", 8192);
    body.put("stream", true);
    body.putObject("stream_options").put("include_usage", true);
    // deepseek-v4-* 默认 thinking 模式;DSL 生成不需要,且会拖慢首 token 数秒(对齐 Node 版)
    if (props.model().startsWith("deepseek-v4")) {
      body.putObject("thinking").put("type", "disabled");
    }
    return body.toString();
  }

  static String chatCompletionsUrl(String baseUrl) {
    String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    return trimmed + "/chat/completions";
  }

  /** NO_PROXY 命中(全等或域名后缀)时直连;语义对齐 Node 版 shouldBypassProxy。 */
  static boolean shouldBypassProxy(String baseUrl, String noProxy) {
    if (baseUrl == null || baseUrl.isBlank() || noProxy == null || noProxy.isBlank()) {
      return false;
    }
    String host = URI.create(baseUrl).getHost();
    if (host == null) {
      return false;
    }
    for (String entry : noProxy.split(",")) {
      String trimmed = entry.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (host.equals(trimmed) || host.endsWith("." + trimmed)) {
        return true;
      }
    }
    return false;
  }

  private static ProxySelector resolveProxy(LlmProperties props) {
    if (props.httpsProxy() == null || props.httpsProxy().isBlank()) {
      return null;
    }
    if (shouldBypassProxy(props.baseUrl(), props.noProxy())) {
      return null;
    }
    URI proxyUri = URI.create(props.httpsProxy());
    int port = proxyUri.getPort() > 0 ? proxyUri.getPort() : 80;
    return ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), port));
  }

  private static String readErrorBody(InputStream body) {
    try (body) {
      byte[] bytes = body.readNBytes(4096);
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "(unreadable error body)";
    }
  }
}
