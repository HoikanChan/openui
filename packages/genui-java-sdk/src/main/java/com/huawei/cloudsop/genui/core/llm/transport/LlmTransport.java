/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.transport;

import java.io.InputStream;

public interface LlmTransport {
    /** Allocate a cancellable request without performing network I/O. */
    default LlmCall newCall(String body, boolean stream) {
        throw new UnsupportedOperationException("Transport must implement cancellable newCall before generation");
    }

    String post(String body) throws LlmTransportException;

    InputStream postStream(String body) throws LlmTransportException;
}
