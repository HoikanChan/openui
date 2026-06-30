/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流式生成统一帧。服务层把每个 envelope 原样序列化为 SSE {@code data:} 行。
 *
 * <p>
 * 帧顺序固定:首帧 {@code dataModel}(seq=0),随后若干 {@code dsl}(seq 递增), 失败时 {@code error},结束 {@code done}(content 为
 * null)。{@code content} 不做额外包装, 由 {@code type} 决定其 JSON 类型。
 *
 * @since 2026
 */
public record RenderStreamEnvelope(String type, int seq, Object content) {
    public static final String TYPE_DATA_MODEL = "dataModel";
    public static final String TYPE_DSL = "dsl";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_DONE = "done";

    /** 首帧:content 为请求 response 或空 map。 */
    public static RenderStreamEnvelope dataModel(Map<String, Object> dataModel) {
        return new RenderStreamEnvelope(TYPE_DATA_MODEL, 0, dataModel == null ? Map.of() : dataModel);
    }

    /** DSL 增量帧:content 为模型 delta 文本。 */
    public static RenderStreamEnvelope dsl(int seq, String delta) {
        return new RenderStreamEnvelope(TYPE_DSL, seq, delta);
    }

    /** 错误帧:content 为 {@code {code, message, retryable}}。 */
    public static RenderStreamEnvelope error(int seq, String code, String message, boolean retryable) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        payload.put("retryable", retryable);
        return new RenderStreamEnvelope(TYPE_ERROR, seq, payload);
    }

    /** 结束帧:content 为 null,序列化时不应输出 content 字段。 */
    public static RenderStreamEnvelope done(int seq) {
        return new RenderStreamEnvelope(TYPE_DONE, seq, null);
    }
}
