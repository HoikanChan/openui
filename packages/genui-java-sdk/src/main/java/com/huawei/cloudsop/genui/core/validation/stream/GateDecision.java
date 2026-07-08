/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.stream;

import com.huawei.cloudsop.genui.core.validation.ValidationResult;

/**
 * 流式语句门控针对一条已完成的 openui-lang 语句（或一批一起刷出的语句）产出的单个决策。
 *
 * <p>
 * 决策是语句文本离开 {@link StreamingValidationSession} 的唯一途径。生成器把 {@link Kind#EMIT} 决策转换为
 * {@code dsl} 信封，把 {@link Kind#FAIL} / {@link Kind#WITHHOLD} 转入 fail-fast 路径。{@link Kind#BUFFER}
 * 语句会一直留在 session 内部，直到后续某个依赖解析成功（再以 {@link Kind#EMIT} 重新出现）或流结束。
 *
 * @param kind
 *            决策种类
 * @param statementText
 *            语句文本
 * @param validationResult
 *            该语句的校验结果
 *
 * @since 2026
 */
public record GateDecision(Kind kind, String statementText, ValidationResult validationResult) {

    /**
     * 门控决策的种类。
     *
     * @since 2026
     */
    public enum Kind {
        /** 语句校验为渲染安全：作为 {@code dsl} 信封转发。 */
        EMIT,
        /**
         * 语句校验通过，但因存在临时未解析的依赖而被暂留。不会作为独立帧离开 session —— 一旦刷出会重新以
         * {@link #EMIT} 形式出现。
         */
        BUFFER,
        /**
         * 已完成的语句被判定为确定性无效（阻塞级 ERROR）。其文本<em>永远不会</em>被发出。这是 Fail-Fast 的
         * 触发点（后续章节会在此处取消并重新请求）。
         */
        WITHHOLD,
        /**
         * 在 {@code onEnd()} 时产生的终态失败：累积的 DSL 仍存在阻塞级错误（例如缓冲的语句始终未能解析）。
         * 等价于流结束时的 WITHHOLD。
         */
        FAIL
    }

    /**
     * 构建一个携带渲染安全语句文本的 EMIT 决策。
     *
     * @param statementText
     *            语句文本
     * @param result
     *            校验结果
     * @return EMIT 决策
     */
    public static GateDecision emit(String statementText, ValidationResult result) {
        return new GateDecision(Kind.EMIT, statementText, result);
    }

    /**
     * 构建一个 BUFFER 决策（语句留在内部；文本尚未转发）。
     *
     * @param statementText
     *            语句文本
     * @param result
     *            校验结果
     * @return BUFFER 决策
     */
    public static GateDecision buffer(String statementText, ValidationResult result) {
        return new GateDecision(Kind.BUFFER, statementText, result);
    }

    /**
     * 构建一个 WITHHOLD 决策（确定性无效；文本绝不能被转发）。
     *
     * @param statementText
     *            语句文本
     * @param result
     *            校验结果
     * @return WITHHOLD 决策
     */
    public static GateDecision withhold(String statementText, ValidationResult result) {
        return new GateDecision(Kind.WITHHOLD, statementText, result);
    }

    /**
     * 构建一个流结束时的终态 FAIL 决策。
     *
     * @param statementText
     *            语句文本
     * @param result
     *            校验结果
     * @return FAIL 决策
     */
    public static GateDecision fail(String statementText, ValidationResult result) {
        return new GateDecision(Kind.FAIL, statementText, result);
    }
}
