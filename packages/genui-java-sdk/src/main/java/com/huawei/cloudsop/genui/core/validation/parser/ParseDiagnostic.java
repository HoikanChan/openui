/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

/**
 * 携带行/列信息的结构化解析诊断。
 *
 * <p>
 * 语法问题以此类记录的形式被<em>收集</em>到 {@link Program} 上而非直接抛出，因此单条格式错误的语句可以优雅降级，
 * 后续语句仍可继续解析（流式容错）。后续章节会将其映射为 {@code ValidationIssue}。
 *
 * <p>
 * {@code line}/{@code column} 从 1 开始计数；{@code startOffset}/{@code endOffset} 是预处理后源文本中从 0 开始的
 * 下标（{@code endOffset} 不含）。{@code -1} 表示“未知”。
 *
 * @param code
 *            错误码
 * @param message
 *            诊断描述
 * @param statementId
 *            所属语句 id，或 {@code null}
 * @param line
 *            起始行号
 * @param column
 *            起始列号
 * @param startOffset
 *            起始偏移量
 * @param endOffset
 *            结束偏移量
 *
 * @since 2026
 */
public record ParseDiagnostic(ParseErrorCode code, String message, String statementId, int line, int column,
        int startOffset, int endOffset) {

    /**
     * 基于 {@link SourceSpan} 构建诊断信息。
     *
     * @param code
     *            错误码
     * @param message
     *            诊断描述
     * @param statementId
     *            所属语句 id，或 {@code null}
     * @param span
     *            源码位置范围，{@code null} 时按 {@link SourceSpan#UNKNOWN} 处理
     * @return 诊断实例
     */
    public static ParseDiagnostic at(ParseErrorCode code, String message, String statementId, SourceSpan span) {
        SourceSpan s = span == null ? SourceSpan.UNKNOWN : span;
        return new ParseDiagnostic(code, message, statementId, s.line(), s.column(), s.startOffset(), s.endOffset());
    }
}
