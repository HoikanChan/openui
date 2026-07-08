/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

/**
 * DSL 字符串的整体有效性状态。
 *
 * <ul>
 * <li>{@link #VALID} —— 未发现阻塞问题。
 * <li>{@link #PARTIAL} —— 仅流式模式：存在未完成语句、暂时未解析的引用，或输入可自动补全闭合。
 * <li>{@link #INVALID} —— 存在语法/合约/根节点/最终未解析等阻塞问题。
 * </ul>
 *
 * @since 2026
 */
public enum ValidationStatus {
    /** 有效，无阻塞问题。 */
    VALID,
    /** 部分有效，仅出现于流式模式。 */
    PARTIAL,
    /** 无效，存在阻塞问题。 */
    INVALID
}
