/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm.transport;

import java.io.InputStream;

/**
 * A single request. Created without I/O, executed once, owned until its body is closed.
 *
 * @since 2026
 */
public interface LlmCall extends AutoCloseable {
    /**
     * 执行请求并返回响应体流。每个调用只能执行一次，重复执行抛 IllegalStateException。
     *
     * @return 响应体流，调用方负责关闭
     * @throws LlmTransportException 请求失败、被取消、被中断，或 HTTP 状态非 2xx 时抛出
     */
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
