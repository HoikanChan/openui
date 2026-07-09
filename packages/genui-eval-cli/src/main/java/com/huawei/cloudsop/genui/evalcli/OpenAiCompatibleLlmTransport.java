/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.evalcli;

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
 * OpenAI 兼容端点的 {@link LlmTransport}:JDK HttpClient,自持完整 URL(LLM_BASE_URL + /chat/completions), 忽略
 * GenUiLlmConfig.endpoint(那是 BSP 网关的相对路径)。写法对齐 genui-service 的 LlmClient。
 */
public final class OpenAiCompatibleLlmTransport implements LlmTransport {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);

    private final String chatCompletionsUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    public OpenAiCompatibleLlmTransport(String baseUrl, String apiKey, String httpsProxy) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("LLM_BASE_URL is not set");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("LLM_API_KEY is not set");
        }
        this.chatCompletionsUrl = joinUrl(baseUrl);
        this.apiKey = apiKey;
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT);
        ProxySelector proxy = resolveProxy(httpsProxy);
        if (proxy != null) {
            builder.proxy(proxy);
        }
        this.httpClient = builder.build();
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

    private HttpResponse<InputStream> send(String body, String accept) throws LlmTransportException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(chatCompletionsUrl)).timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .header("Accept", accept)
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8)).build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                throw new LlmTransportException(
                        "LLM HTTP " + response.statusCode() + ": " + readErrorBody(response.body()));
            }
            return response;
        } catch (IOException error) {
            throw new LlmTransportException("LLM request failed: " + error.getMessage(), error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new LlmTransportException("LLM request interrupted", error);
        }
    }

    private static String joinUrl(String baseUrl) {
        String trimmed = baseUrl.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "/chat/completions";
    }

    private static ProxySelector resolveProxy(String httpsProxy) {
        if (httpsProxy == null || httpsProxy.isBlank()) {
            return null;
        }
        URI uri = URI.create(httpsProxy);
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("Invalid HTTPS_PROXY: " + httpsProxy);
        }
        int port = uri.getPort() > 0 ? uri.getPort() : 80;
        return ProxySelector.of(new InetSocketAddress(uri.getHost(), port));
    }

    private static String readErrorBody(InputStream body) {
        if (body == null) {
            return "(empty body)";
        }
        try (InputStream stream = body) {
            byte[] bytes = stream.readNBytes(4096);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException error) {
            return "(unreadable body: " + error.getMessage() + ")";
        }
    }
}
