/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

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
    private SseDeltaParser() {
    }

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
        } catch (StreamEnd end) {
            return accumulated.toString();
        }
        if (!pending.isEmpty()) {
            try {
                handleFrame(pending.toString(), accumulated, sink);
            } catch (StreamEnd end) {
                return accumulated.toString();
            }
        }
        throw new IOException("SSE stream ended without stop or [DONE]");
    }

    private static void drainCompleteFrames(StringBuilder pending, StringBuilder accumulated, Consumer<String> sink)
            throws IOException {
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
        if (lf < 0)
            return crlf;
        if (crlf < 0)
            return lf;
        return Math.min(lf, crlf);
    }

    private static int separatorLength(StringBuilder text, int boundary) {
        return text.substring(boundary).startsWith("\r\n\r\n") ? 4 : 2;
    }

    private static void handleFrame(String frame, StringBuilder accumulated, Consumer<String> sink) throws IOException {
        String payload = dataPayload(frame);
        if (payload != null && "[DONE]".equals(payload.trim()))
            throw new StreamEnd();
        if (payload == null || payload.isBlank()) {
            return;
        }

        String content;
        String finishReason;
        try {
            Map<String, Object> object = Json.asObject(Json.parse(payload), "SSE frame");
            if (object.get("error") != null)
                throw new IOException("SSE upstream error frame");
            List<Object> choices = Json.asList(object.get("choices"), "choices");
            if (choices.isEmpty())
                return;
            Map<String, Object> first = Json.asObject(choices.getFirst(), "choice");
            Object reason = first.get("finish_reason");
            finishReason = reason instanceof String value ? value : null;
            Object delta = first.get("delta");
            Object value = delta instanceof Map<?, ?> fields ? fields.get("content") : null;
            content = value instanceof String text ? text : null;
        } catch (RuntimeException ignored) {
            // Bad frames should not interrupt the rest of the stream.
            return;
        }
        if (content != null && !content.isEmpty()) {
            sink.accept(content);
            accumulated.append(content);
        }
        if (finishReason != null) {
            if (!"stop".equals(finishReason))
                throw new IOException("incomplete, finish_reason=" + finishReason);
            throw new StreamEnd();
        }
    }

    private static final class StreamEnd extends RuntimeException {
        StreamEnd() {
            super(null, null, false, false);
        }
    }

    private static String dataPayload(String frame) {
        StringBuilder payload = new StringBuilder();
        for (String line : frame.split("\\R")) {
            if (!line.startsWith("data:"))
                continue;
            String data = line.substring("data:".length());
            if (data.startsWith(" "))
                data = data.substring(1);
            if (!payload.isEmpty())
                payload.append('\n');
            payload.append(data);
        }
        return payload.isEmpty() ? null : payload.toString();
    }
}
