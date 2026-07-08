/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

/**
 * 预处理器/解析器的解析模式。
 *
 * <p>
 * 对应 TS 侧一次性 {@code parse()}（完整输入）与流式解析器之间的区别：{@link #STREAMING} 会自动闭合未闭合的括号/字符串，
 * 使部分输入也能解析；而 {@link #FINAL} 保持文本原样（未闭合结构会作为诊断信息暴露）。
 *
 * @since 2026
 */
public enum ParseMode {
    /** 完整输入。不做自动闭合；未闭合的括号/字符串会成为诊断信息。 */
    FINAL,
    /** 部分/流式输入。未闭合的括号/字符串会被自动闭合以实现容错解析。 */
    STREAMING
}
