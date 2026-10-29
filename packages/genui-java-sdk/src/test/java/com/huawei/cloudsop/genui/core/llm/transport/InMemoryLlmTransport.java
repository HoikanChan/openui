/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm.transport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Adapter ONLY for existing nonblocking fixtures. Blocking tests must supply an explicit abortable
 * call.
 */
public interface InMemoryLlmTransport extends LlmTransport {
    @Override
    default LlmCall newCall(String body, boolean stream) {
        return new LlmCall() {
            private boolean cancelled;
            private InputStream input;

            public synchronized InputStream execute() throws LlmTransportException {
                if (cancelled) throw new LlmTransportException("cancelled");
                input =
                        stream
                                ? postStream(body)
                                : new ByteArrayInputStream(
                                        post(body).getBytes(StandardCharsets.UTF_8));
                return input;
            }

            public synchronized void cancel() {
                cancelled = true;
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        };
    }
}
