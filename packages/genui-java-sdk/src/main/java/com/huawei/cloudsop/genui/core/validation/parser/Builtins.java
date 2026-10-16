/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.Set;

/**
 * DSL 语句语法的保留字（不是函数库）。
 *
 * <p>
 * Action 步骤名（Run/ToAssistant/OpenUrl/Set/Reset 与 Action 容器）与语句级保留调用（Query/Mutation）是语法结构，留在代码侧。builtin
 * <b>函数</b>词表（数据类与惰性/模板类）恒从 SDK 默认 base-contract.json 的 builtins 派生，见 {@link BuiltinCatalog}——契约里没有的函数名即幻觉。
 *
 * @since 2026
 */
public final class Builtins {

    /** 保留的语句级调用名之一：{@code Query}。 */
    public static final String QUERY = "Query";

    /** 保留的语句级调用名之一：{@code Mutation}。 */
    public static final String MUTATION = "Mutation";

    /** Action 步骤名（{@code ACTION_STEPS} 的 key）。 */
    private static final Set<String> ACTION_STEPS = Set.of("Run", "ToAssistant", "OpenUrl", "Set", "Reset");

    /** 全部 action 表达式名 —— 各步骤加 {@code Action} 容器本身。 */
    private static final Set<String> ACTION_NAMES = union(ACTION_STEPS, Set.of("Action"));

    /** 保留的语句级调用名集合（{@code RESERVED_CALLS}）。 */
    private static final Set<String> RESERVED_CALLS = Set.of(QUERY, MUTATION);

    private Builtins() {
    }

    /** 判断 {@code name} 是否为 action 表达式（步骤或容器）。 */
    public static boolean isActionName(String name) {
        return ACTION_NAMES.contains(name);
    }

    /** 判断 {@code name} 是否为保留的语句级调用（{@code Query}/{@code Mutation}）。 */
    public static boolean isReservedCall(String name) {
        return RESERVED_CALLS.contains(name);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        var s = new java.util.HashSet<>(a);
        s.addAll(b);
        return Set.copyOf(s);
    }
}
