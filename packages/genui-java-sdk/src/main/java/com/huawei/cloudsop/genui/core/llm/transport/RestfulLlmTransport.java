/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.transport;

import com.huawei.bsp.roa.util.restclient.RestfulFactory;
import com.huawei.bsp.roa.util.restclient.RestfulParametes;
import com.huawei.bsp.roa.util.restclient.RestfulResponse;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 BSP {@code RestfulFactory} 的传输，endpoint 是网关相对路径。流式请求额外附带 {@code
 * SUPPORT_STREAM_CONTENT_FOR_SDK: true} 头；非 200 状态统一抛异常。
 *
 * @since 2026
 */
public final class RestfulLlmTransport implements LlmTransport {
    private static final int HTTP_OK = 200;
    private static final String STREAM_HEADER = "SUPPORT_STREAM_CONTENT_FOR_SDK";

    private final GenUiLlmConfig config;

    /**
     * 创建传输。config 为 null 时使用默认配置。
     *
     * @param config LLM 配置，可为 null
     */
    public RestfulLlmTransport(GenUiLlmConfig config) {
        this.config = config == null ? GenUiLlmConfig.defaults() : config;
    }

    @Override
    public String post(String body) throws LlmTransportException {
        RestfulResponse response = postInternal(body, false);
        return response.getResponseContent();
    }

    @Override
    public InputStream postStream(String body) throws LlmTransportException {
        RestfulResponse response = postInternal(body, true);
        InputStream stream = response.getDataStream();
        if (stream == null) {
            throw new LlmTransportException("LLM stream response body is missing");
        }
        return stream;
    }

    private RestfulResponse postInternal(String body, boolean stream) throws LlmTransportException {
        RestfulParametes params = new RestfulParametes();
        params.setRawData(Objects.toString(body, ""));
        for (Map.Entry<String, String> header : config.extraHeaders().entrySet()) {
            params.putHttpContextHeader(header.getKey(), header.getValue());
        }
        if (stream) {
            params.putHttpContextHeader(STREAM_HEADER, "true");
        }

        try {
            RestfulResponse response =
                    RestfulFactory.getRestInstance().post(config.endpoint(), params);
            int status = response == null ? -1 : response.getStatus();
            if (status != HTTP_OK) {
                throw new LlmTransportException("LLM request failed, status: " + status);
            }
            return response;
        } catch (LlmTransportException error) {
            throw error;
        } catch (Exception error) {
            throw new LlmTransportException("Failed to invoke LLM transport", error);
        }
    }
}
