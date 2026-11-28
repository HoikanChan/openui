/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.langsmith;

/**
 * LangSmith REST API 调用异常，携带可选 HTTP 状态码与响应体摘要。
 *
 * @since 2026
 */
public final class LangSmithException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int statusCode;

    /**
     * 构造带 HTTP 状态码的异常。
     *
     * @param message 错误描述
     * @param statusCode HTTP 状态码，网络层错误传 -1
     */
    public LangSmithException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * 构造包装底层异常的调用异常。
     *
     * @param message 错误描述
     * @param cause 原始异常
     */
    public LangSmithException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    /**
     * 返回 HTTP 状态码，非 HTTP 错误返回 -1。
     *
     * @return HTTP 状态码
     */
    public int statusCode() {
        return statusCode;
    }
}
