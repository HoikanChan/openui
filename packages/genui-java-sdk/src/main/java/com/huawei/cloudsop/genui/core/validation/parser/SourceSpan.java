/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

/**
 * 预处理后 DSL 文本中的一段源码位置范围。
 *
 * <p>
 * {@code line}/{@code column} 从 1 开始计数；{@code startOffset}/{@code endOffset} 是预处理后源文本中从 0 开始的
 * 下标，{@code endOffset} 不包含在范围内。任意字段为 {@code -1} 表示“未知”。
 *
 * @param line
 *            起始行号，从 1 开始
 * @param column
 *            起始列号，从 1 开始
 * @param startOffset
 *            起始偏移量（从 0 开始，含）
 * @param endOffset
 *            结束偏移量（从 0 开始，不含）
 *
 * @since 2026
 */
public record SourceSpan(int line, int column, int startOffset, int endOffset) {

    /** 未知范围（所有字段均为 {@code -1}）。 */
    public static final SourceSpan UNKNOWN = new SourceSpan(-1, -1, -1, -1);

    /**
     * 计算该范围的字符长度。
     *
     * @return 字符长度；若任一偏移量未知则返回 {@code -1}
     */
    public int length() {
        if (startOffset < 0 || endOffset < 0) {
            return -1;
        }
        return endOffset - startOffset;
    }
}
