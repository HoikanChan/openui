/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;

/**
 * 零外部依赖的生成生命周期观测口,供评测、追踪和日志系统接入。所有回调均提供空默认实现;观测器抛出的运行时异常由 SDK 吞掉,不影响生成主链路。
 *
 * @since 2026
 */
public interface GenerationObserver {
    /** 空观测器单例。 */
    GenerationObserver NOOP = new GenerationObserver() {};

    /** 初次生成模型调用类型。 */
    String KIND_INITIAL = "initial";

    /** 增量修复模型调用类型。 */
    String KIND_REPAIR = "repair";

    /** 整页 Markdown 降级模型调用类型。 */
    String KIND_MARKDOWN_FALLBACK = "markdown_fallback";

    /**
     * 一次 LLM 请求开始前触发。
     *
     * @param roundIndex 轮次序号(0 为首轮,1 起为修复轮,-1 为独立 Markdown 降级)
     * @param kind 调用类型({@link #KIND_INITIAL}/{@link #KIND_REPAIR}/{@link #KIND_MARKDOWN_FALLBACK})
     * @param model 本次请求使用的模型名
     * @param requestBodyJson 实际发送的 chat-completion JSON 原文
     * @param startedAtEpochMs 开始时间戳(UTC 毫秒)
     * @since 2026
     */
    record LlmCallStartEvent(
            int roundIndex,
            String kind,
            String model,
            String requestBodyJson,
            long startedAtEpochMs) {}

    /**
     * 一次 LLM 请求结束(成功或异常)后触发。
     *
     * @param roundIndex 轮次序号
     * @param kind 调用类型
     * @param model 模型名
     * @param rawOutput 模型返回的原始文本,失败时可能为部分文本或空串
     * @param usage 模型返回的 token 用量,网关未返回时为 {@code null}
     * @param durationMs 本次模型调用耗时(毫秒)
     * @param endedAtEpochMs 结束时间戳(UTC 毫秒)
     * @param error 异常信息,成功时为 {@code null}
     * @since 2026
     */
    record LlmCallEndEvent(
            int roundIndex,
            String kind,
            String model,
            String rawOutput,
            LlmUsage usage,
            long durationMs,
            long endedAtEpochMs,
            String error) {}

    /**
     * 生成会话开始时触发。
     *
     * @param request 归一化后的生成请求
     * @param model 默认模型名
     * @param validationEnabled 是否启用校验与修复
     * @param startedAtEpochMs 会话开始时间戳(UTC 毫秒)
     */
    default void onSessionStart(
            UiGenerationRequest request,
            String model,
            boolean validationEnabled,
            long startedAtEpochMs) {}

    /**
     * 单次 LLM 调用发出前触发。
     *
     * @param event 调用开始事件
     */
    default void onLlmCallStart(LlmCallStartEvent event) {}

    /**
     * 单次 LLM 调用结束后触发。
     *
     * @param event 调用结束事件
     */
    default void onLlmCallEnd(LlmCallEndEvent event) {}

    /**
     * 单轮轮末校验完成后触发。
     *
     * @param roundIndex 轮次序号(0 为首轮,1 起为修复轮)
     * @param roundResult 该轮校验结果与累积程序快照
     * @param completedAtEpochMs 校验完成时间戳(UTC 毫秒)
     */
    default void onRoundValidated(
            int roundIndex, RoundEndResult roundResult, long completedAtEpochMs) {}

    /**
     * 生成会话结束时触发。
     *
     * @param result 最终生成结果,异常中止时可能为 {@code null}
     * @param durationMs 会话总耗时(毫秒)
     * @param endedAtEpochMs 会话结束时间戳(UTC 毫秒)
     * @param error 顶层异常描述,正常结束时为 {@code null}
     */
    default void onSessionEnd(
            GenUiGenerationResult result, long durationMs, long endedAtEpochMs, String error) {}

    /**
     * 安全执行观测器回调,吞掉观测器自身的 {@link RuntimeException},避免影响业务生成。
     *
     * @param action 回调动作
     */
    static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // Observer failures must never break generation or repair flow.
        }
    }
}
