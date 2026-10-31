/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.transport;

import java.io.InputStream;

/**
 * LLM 请求传输抽象：既可一次性同步取回完整响应，也可取得可取消的调用或流式响应体。
 *
 * @since 2026
 */
public interface LlmTransport {
    /**
     * 分配一个可取消的请求，不发起网络 I/O。
     *
     * @param body 请求体 JSON
     * @param stream 是否需要 SSE 流式响应
     * @return 尚未执行的调用
     * @throws UnsupportedOperationException 实现类未覆盖此默认方法时抛出
     */
    default LlmCall newCall(String body, boolean stream) {
        throw new UnsupportedOperationException(
                "Transport must implement cancellable newCall before generation");
    }

    /**
     * 同步提交请求并取回完整响应体。
     *
     * @param body 请求体 JSON
     * @return 响应体文本
     * @throws LlmTransportException 请求失败、HTTP 状态非 2xx 或读取响应失败时抛出
     */
    String post(String body) throws LlmTransportException;

    /**
     * 提交请求并返回流式响应体。
     *
     * @param body 请求体 JSON
     * @return SSE 响应流，调用方负责关闭
     * @throws LlmTransportException 请求失败、HTTP 状态非 2xx 或响应体缺失时抛出
     */
    InputStream postStream(String body) throws LlmTransportException;
}
