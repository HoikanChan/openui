/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.langsmith;

import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;
import com.huawei.cloudsop.genui.core.llm.transport.HttpClientLlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmCall;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;

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

/**
 * 共享的 OpenAI 兼容端点传输层，同时支持本地模型（Ollama/vLLM/LM Studio，允许空 API key 且自动绕过代理） 与云端/内网兼容网关。
 *
 * @since 2026
 */
public final class SharedOpenAiTransport implements LlmTransport {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15L);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10L);
    private static final int ERROR_BODY_LIMIT = 4096;
    private static final int HTTP_SUCCESS_CLASS = 2;
    private static final int HTTP_CLASS_DIVISOR = 100;
    private static final int DEFAULT_PROXY_PORT = 80;

    private final String chatCompletionsUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    /**
     * 创建 OpenAI 兼容传输实例。
     *
     * @param baseUrl 基础 URL（自动补全 {@code /chat/completions}，已包含则不重复拼接）
     * @param apiKey 可选 API key，本地模型网关允许为空
     * @param httpsProxy 可选代理地址；当目标主机为 localhost 或 127.0.0.1 时自动跳过代理直连
     * @throws IllegalArgumentException baseUrl 为空或代理地址无效时抛出
     */
    public SharedOpenAiTransport(String baseUrl, String apiKey, String httpsProxy) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("LLM_BASE_URL is not set");
        }
        this.chatCompletionsUrl = resolveCompletionsUrl(baseUrl);
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.strip();
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT);
        URI targetUri = URI.create(this.chatCompletionsUrl);
        if (!isLoopbackHost(targetUri.getHost())) {
            ProxySelector proxy = resolveProxy(httpsProxy);
            if (proxy != null) {
                builder.proxy(proxy);
            }
        }
        this.httpClient = builder.build();
    }

    @Override
    public LlmCall newCall(String body, boolean stream) {
        GenUiLlmConfig.Builder cfg = GenUiLlmConfig.builder().endpoint(chatCompletionsUrl);
        if (apiKey != null) {
            cfg.putHeader("Authorization", "Bearer " + apiKey);
        }
        return new HttpClientLlmTransport(cfg.build(), httpClient).newCall(body, stream);
    }

    @Override
    public String post(String body) throws LlmTransportException {
        HttpResponse<InputStream> response = send(body, "application/json");
        try (InputStream stream = response.body()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new LlmTransportException("Failed to read LLM response body", error);
        }
    }

    @Override
    public InputStream postStream(String body) throws LlmTransportException {
        return send(body, "text/event-stream").body();
    }

    /**
     * 返回实际请求的 chat completions 完整 URL。
     *
     * @return 完整端点 URL
     */
    public String endpointUrl() {
        return chatCompletionsUrl;
    }

    private HttpResponse<InputStream> send(String body, String accept)
            throws LlmTransportException {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(URI.create(chatCompletionsUrl))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .header("Accept", accept)
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        body == null ? "" : body, StandardCharsets.UTF_8));
        if (apiKey != null) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        try {
            HttpResponse<InputStream> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / HTTP_CLASS_DIVISOR != HTTP_SUCCESS_CLASS) {
                throw new LlmTransportException(
                        "LLM HTTP "
                                + response.statusCode()
                                + ": "
                                + readErrorBody(response.body()));
            }
            return response;
        } catch (IOException error) {
            throw new LlmTransportException("LLM request failed: " + error.getMessage(), error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new LlmTransportException("LLM request interrupted", error);
        }
    }

    static String resolveCompletionsUrl(String baseUrl) {
        String trimmed = baseUrl.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed;
        }
        return trimmed + "/chat/completions";
    }

    static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        return "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host)
                || asciiEquals(host, "localhost");
    }

    private static boolean asciiEquals(String value, String expectedLower) {
        if (value.length() != expectedLower.length()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            char lower = ch >= 'A' && ch <= 'Z' ? (char) (ch + ('a' - 'A')) : ch;
            if (lower != expectedLower.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    static ProxySelector resolveProxy(String httpsProxy) {
        if (httpsProxy == null || httpsProxy.isBlank()) {
            return null;
        }
        URI uri = URI.create(httpsProxy.strip());
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("Invalid HTTPS_PROXY: " + httpsProxy);
        }
        int port = uri.getPort() > 0 ? uri.getPort() : DEFAULT_PROXY_PORT;
        return ProxySelector.of(new InetSocketAddress(uri.getHost(), port));
    }

    private static String readErrorBody(InputStream body) {
        if (body == null) {
            return "(empty body)";
        }
        try (InputStream stream = body) {
            return new String(stream.readNBytes(ERROR_BODY_LIMIT), StandardCharsets.UTF_8);
        } catch (IOException error) {
            return "(unreadable body: " + error.getMessage() + ")";
        }
    }
}
