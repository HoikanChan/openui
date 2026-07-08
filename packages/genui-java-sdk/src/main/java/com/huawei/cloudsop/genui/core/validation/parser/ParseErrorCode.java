/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

/**
 * 机器可读的解析器语法错误码。
 *
 * <p>
 * 这些编码对应 TS 流水线暴露的诊断<em>类别</em>（参见 {@code enrich-errors.ts}/{@code types.ts}），而非精确的错误
 * 文案。后续章节会将其映射为 {@code ValidationIssue} 编码。
 *
 * @since 2026
 */
public enum ParseErrorCode {
    /** 语句行的标识符后缺少 {@code =}。 */
    MISSING_ASSIGNMENT,
    /** 某行以无法作为语句标识符的 token 开头。 */
    INVALID_STATEMENT,
    /** 开括号 {@code (} / {@code [} / {@code &#123;} 未闭合。 */
    UNCLOSED_BRACKET,
    /** 字符串字面量未闭合。 */
    UNCLOSED_STRING,
    /** 出现了表达式文法无法使用的 token。 */
    UNEXPECTED_TOKEN
}
