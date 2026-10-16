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
 * <p>
 * <b>类型语义(#56:首次生成实时展示,轮末统一校验)</b>:
 *
 * <ul>
 * <li>{@code dataModel} —— 首帧,回显请求的数据模型。
 * <li>{@code dsl} ——
 * 模型本轮输出的<b>原始增量文本,实时转发</b>:不因校验扣留、不做逐语句门控、不保证多语句原子展示。校验在模型本轮输出结束后统一进行,只影响返回结果的状态,不改变已发出的帧。前端按既有同名覆盖能力消费增量。
 * <li>{@code error} —— SDK 判定自己无法产出可信的最终 DSL(例如底层传输失败且不可恢复);不代表"轮末校验发现问题" ——那属于返回结果的状态,不通过 error 帧暴露。
 * <li>{@code done} —— 仅表示 SSE 流结束,不携带业务成功与否的语义;业务层面的最终校验状态由返回的 {@link GenUiGenerationResult#validationStatus()} 承载,而非
 * done 帧。
 * </ul>
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

    /** 首帧:content 为请求 response 或空 map。 */
    public static RenderStreamEnvelope dataModel(Map<String, Object> dataModel) {
        return new RenderStreamEnvelope(TYPE_DATA_MODEL, 0, dataModel == null ? Map.of() : dataModel);
    }

    /**
     * DSL 帧:content 为模型本轮输出的原始增量文本(#56 实时转发)。不因校验扣留;轮末统一校验不影响已发出的帧。
     *
     * @param seq
     *            帧序号,必须 {@code >= 1}(0 保留给首帧 {@code dataModel})。
     * @param deltaText
     *            模型输出的原始增量文本。
     */
    public static RenderStreamEnvelope dsl(int seq, String deltaText) {
        return new RenderStreamEnvelope(TYPE_DSL, seq, deltaText);
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
