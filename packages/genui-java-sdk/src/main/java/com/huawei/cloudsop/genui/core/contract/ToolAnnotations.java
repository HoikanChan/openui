/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

/**
 * 工具注解提示，描述工具是否只读、是否可能产生破坏性影响。
 *
 * @param readOnlyHint 是否为只读工具；{@code null} 表示契约未声明
 * @param destructiveHint 是否可能造成破坏性影响；{@code null} 表示契约未声明
 * @since 2026
 */
public record ToolAnnotations(Boolean readOnlyHint, Boolean destructiveHint) {}
