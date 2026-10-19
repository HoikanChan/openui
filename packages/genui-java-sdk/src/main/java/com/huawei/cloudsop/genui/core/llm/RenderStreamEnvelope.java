/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Render stream protocol: dataModel, realtime dsl fragments, optional whole-page replace, error, done. Consumers must
 * clear the entire DSL/parser/page state on replace, retaining the original data model. Only the owning generation
 * session emits done; a model turn ending is not a session terminal. Raw fragments are not guaranteed to be valid until
 * final validation completes.
 */
public record RenderStreamEnvelope(String type, int seq, Object content) {
    public RenderStreamEnvelope {
        if (seq < 0) {
            throw new IllegalArgumentException("seq must be >= 0, got: " + seq);
        }
    }

    public static final String TYPE_DATA_MODEL = "dataModel";
    public static final String TYPE_DSL = "dsl";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_DONE = "done";
    public static final String TYPE_REPLACE = "replace";

    /** Discard the entire previous page and parser state before consuming the independent Markdown DSL. */
    public static RenderStreamEnvelope replace(int seq, GenerationEndReason reason) {
        return new RenderStreamEnvelope(TYPE_REPLACE, seq,
                Map.of("scope", "page", "format", "Markdown", "reason", reason.name()));
    }

    /** 首帧:content 为请求 response 或空 map。 */
    public static RenderStreamEnvelope dataModel(Map<String, Object> dataModel) {
        return new RenderStreamEnvelope(TYPE_DATA_MODEL, 0, dataModel == null ? Map.of() : dataModel);
    }

    /** Realtime DSL fragment; may be incomplete until more fragments arrive. */
    public static RenderStreamEnvelope dsl(int seq, String acceptedDsl) {
        return new RenderStreamEnvelope(TYPE_DSL, seq, acceptedDsl);
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
