/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm.transport;

import java.io.InputStream;

/** A single request. Created without I/O, executed once, owned until its body is closed. */
public interface LlmCall extends AutoCloseable {
    InputStream execute() throws LlmTransportException;

    /**
     * Thread-safe, idempotent and prompt. Abort pending headers AND blocked body reads, release
     * resources, and prevent execution if called before execute. Must not wait for the reader.
     */
    void cancel();

    @Override
    default void close() {
        cancel();
    }
}
