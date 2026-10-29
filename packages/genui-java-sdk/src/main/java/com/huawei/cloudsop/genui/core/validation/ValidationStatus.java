/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

/**
 * DSL 字符串的整体有效性状态。
 *
 * <ul>
 *   <li>{@link #VALID} —— 未发现阻塞问题。
 *   <li>{@link #INVALID} —— 存在语法/合约/根节点/未解析引用等阻塞问题。
 * </ul>
 *
 * @since 2026
 */
public enum ValidationStatus {
    /** 有效，无阻塞问题。 */
    VALID,
    /** 无效，存在阻塞问题。 */
    INVALID
}
