/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm.transport;

import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** JDK transport for a complete HTTP(S) endpoint; injected clients retain host proxy/TLS configuration. */
public final class HttpClientLlmTransport implements LlmTransport {
    private static final HttpClient DEFAULT_CLIENT = HttpClient.newHttpClient();
    private final GenUiLlmConfig config;
    private final HttpClient client;

    public HttpClientLlmTransport(GenUiLlmConfig config) {
        this(config, DEFAULT_CLIENT);
    }

    public HttpClientLlmTransport(GenUiLlmConfig config, HttpClient client) {
        this.config = Objects.requireNonNull(config);
        this.client = Objects.requireNonNull(client);
        URI endpoint = URI.create(config.endpoint());
        if (endpoint.getHost() == null
                || !("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))) {
            throw new IllegalArgumentException(
                    "HttpClient transport requires a complete HTTP(S) endpoint; BSP relative paths require a BSP adapter");
        }
    }

    @Override
    public LlmCall newCall(String body, boolean stream) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(config.endpoint()))
                .header("Content-Type", "application/json")
                .header("Accept", stream ? "text/event-stream" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Objects.toString(body, ""), StandardCharsets.UTF_8));
        config.extraHeaders().forEach(request::setHeader);
        return new Call(request.build());
    }

    @Override
    public String post(String body) throws LlmTransportException {
        try (LlmCall call = newCall(body, false); InputStream input = call.execute()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LlmTransportException("Failed to read LLM response", e);
        }
    }

    @Override
    public InputStream postStream(String body) throws LlmTransportException {
        LlmCall call = newCall(body, true);
        try {
            return new FilterInputStream(call.execute()) {
                @Override
                public void close() {
                    call.close();
                }
            };
        } catch (RuntimeException | LlmTransportException e) {
            call.close();
            throw e;
        }
    }

    private final class Call implements LlmCall {
        private final HttpRequest request;
        private CompletableFuture<HttpResponse<InputStream>> pending;
        private InputStream input;
        private boolean cancelled;

        private Call(HttpRequest request) {
            this.request = request;
        }

        @Override
        public InputStream execute() throws LlmTransportException {
            CompletableFuture<HttpResponse<InputStream>> response;
            synchronized (this) {
                if (cancelled)
                    throw new LlmTransportException("LLM request cancelled");
                if (pending != null)
                    throw new IllegalStateException("Request already executed");
                pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
                response = pending;
                // Even when cancellation wins after headers arrive, the returned body must be closed.
                pending.thenAccept(result -> {
                    synchronized (this) {
                        if (cancelled)
                            closeBody(result.body());
                        else
                            input = result.body();
                    }
                });
            }
            try {
                HttpResponse<InputStream> result = response.get();
                synchronized (this) {
                    if (cancelled) {
                        closeBody(result.body());
                        throw new LlmTransportException("LLM request cancelled");
                    }
                    input = result.body();
                }
                if (result.statusCode() / 100 != 2) {
                    cancel();
                    throw new LlmTransportException("LLM HTTP " + result.statusCode());
                }
                return result.body();
            } catch (InterruptedException e) {
                cancel();
                Thread.currentThread().interrupt();
                throw new LlmTransportException("LLM request interrupted", e);
            } catch (ExecutionException | CancellationException e) {
                cancel();
                throw new LlmTransportException("LLM request failed or cancelled", e);
            }
        }

        @Override
        public void cancel() {
            CompletableFuture<?> future;
            InputStream body;
            synchronized (this) {
                if (cancelled)
                    return;
                cancelled = true;
                future = pending;
                body = input;
            }
            if (future != null)
                future.cancel(true);
            closeBody(body);
        }
    }

    private static void closeBody(InputStream body) {
        if (body == null)
            return;
        try {
            body.close();
        } catch (IOException ignored) {
            // Abort is best effort after the underlying exchange has already failed.
        }
    }
}
