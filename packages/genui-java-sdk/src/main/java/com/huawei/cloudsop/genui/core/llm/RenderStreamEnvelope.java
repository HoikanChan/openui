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
 * <b>类型语义(design Decision #7)</b>:validation / repair / replace / commit 均不是独立的 envelope 类型 —— 它们是 SDK 内部的流式校验门(Section
 * 6)与修复(Section 7)机制的实现细节,不对外 暴露。本记录只保留固定的四种类型:
 *
 * <ul>
 * <li>{@code dataModel} —— 首帧,回显请求的数据模型。
 * <li>{@code dsl} —— 经 SDK 校验门放行、可安全渲染的 openui-lang 内容(一条完整语句或一批语句), 不是模型的原始增量文本;未通过校验门的内容不会以此类型出现。
 * <li>{@code error} —— SDK 判定自己无法产出可信的最终 DSL(例如底层传输失败且不可恢复);不代表 "本次渲染有个别语句被丢弃" —— 那属于 SDK 内部处理,不通过 error 帧暴露。
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
     * DSL 帧:content 为经 SDK 校验门接受(accepted)的 openui-lang 内容 —— 一条完整语句或一批语句, 已排除会破坏渲染的非法片段。这不是模型的原始流式增量文本(raw
     * delta);未接受的内容不会出现在 此帧中(校验门的具体放行/重试策略见 Section 6)。
     *
     * @param seq
     *            帧序号,必须 {@code >= 1}(0 保留给首帧 {@code dataModel})。
     * @param acceptedDsl
     *            已被 SDK 接受、可安全渲染的 DSL 内容。
     */
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
