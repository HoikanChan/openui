/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

/**
 * DSL 校验过程中发现的单条诊断问题。
 *
 * <p>
 * 所有位置字段（{@code line}、{@code column}）用 {@code -1} 表示“未知”。可空的字符串字段在不适用时为 {@code null}。
 *
 * @param code
 *            问题编码
 * @param severity
 *            严重程度
 * @param source
 *            触发该问题的检查类别（如 "syntax"、"contract"、"reference"）
 * @param message
 *            问题描述
 * @param statementId
 *            所属 DSL 语句 id，若无法归属到具体语句则为 {@code null}
 * @param component
 *            涉及的组件名，或 {@code null}
 * @param path
 *            DSL AST 内的 JSON-pointer 风格路径，或 {@code null}
 * @param line
 *            从 1 开始的行号，未知时为 {@code -1}
 * @param column
 *            从 1 开始的列号，未知时为 {@code -1}
 * @param hint
 *            可选的可读建议，或 {@code null}
 * @param retryable
 *            修复后重新发送提示词是否可能解决该问题
 *
 * @since 2026
 */
public record ValidationIssue(String code, ValidationSeverity severity, String source, String message,
        String statementId, String component, String path, int line, int column, String hint, boolean retryable) {
}
