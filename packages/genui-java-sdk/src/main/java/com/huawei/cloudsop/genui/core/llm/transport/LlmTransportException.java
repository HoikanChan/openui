/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.transport;

/**
 * LLM 传输层失败：连接、HTTP 状态、读取响应等错误统一包装为该受检异常。
 *
 * @since 2026
 */
public class LlmTransportException extends Exception {
    /**
     * 用错误消息创建异常。
     *
     * @param message 错误描述
     */
    public LlmTransportException(String message) {
        super(message);
    }

    /**
     * 用错误消息与根因创建异常。
     *
     * @param message 错误描述
     * @param cause 触发原因
     */
    public LlmTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
