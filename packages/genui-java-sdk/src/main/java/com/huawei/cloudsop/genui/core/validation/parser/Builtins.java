/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.Set;

/**
 * 解析器使用的内置/保留调用分类。
 *
 * <p>
 * 对应表达式解析器所查询的 {@code packages/lang-core/src/parser/builtins.ts} 中的名称集合（{@code isBuiltin}、 action 名称、保留调用）。仅移植与解析器相关的子集
 * —— 运行时 {@code fn} 实现与提示词文档不在范围内。
 *
 * @since 2026
 */
public final class Builtins {

    /** 保留的语句级调用名之一：{@code Query}。 */
    public static final String QUERY = "Query";

    /** 保留的语句级调用名之一：{@code Mutation}。 */
    public static final String MUTATION = "Mutation";

    /** 数据类内置函数（对应 builtins.ts 中 {@code BUILTINS} 的 key）。 */
    private static final Set<String> DATA_BUILTINS = Set.of("Count", "First", "Last", "Sum", "Avg", "Min", "Max",
            "Sort", "Filter", "ObjectEntries", "ObjectKeys", "Round", "Abs", "Floor", "Ceil", "Switch", "FormatDate",
            "FormatBytes", "FormatNumber", "FormatPercent", "FormatDuration");

    /** 惰性/模板类内置函数（{@code LAZY_BUILTINS}）。 */
    private static final Set<String> LAZY_BUILTINS = Set.of("Each", "Render");

    /** Action 步骤名（{@code ACTION_STEPS} 的 key）。 */
    private static final Set<String> ACTION_STEPS = Set.of("Run", "ToAssistant", "OpenUrl", "Set", "Reset");

    /** 全部 action 表达式名 —— 各步骤加 {@code Action} 容器本身。 */
    private static final Set<String> ACTION_NAMES = union(ACTION_STEPS, Set.of("Action"));

    /** 完整内置函数名集合（{@code BUILTIN_NAMES}）。 */
    private static final Set<String> BUILTIN_NAMES = union(union(DATA_BUILTINS, LAZY_BUILTINS), ACTION_NAMES);

    /** 保留的语句级调用名集合（{@code RESERVED_CALLS}）。 */
    private static final Set<String> RESERVED_CALLS = Set.of(QUERY, MUTATION);

    private Builtins() {
    }

    /**
     * 判断 {@code name} 是否为内置函数（而非组件）。
     *
     * @param name
     *            待判断的名称
     * @return 是内置函数时返回 {@code true}
     */
    public static boolean isBuiltin(String name) {
        return BUILTIN_NAMES.contains(name);
    }

    /**
     * 判断 {@code name} 是否为保留的语句级调用（{@code Query}/{@code Mutation}）。
     *
     * @param name
     *            待判断的名称
     * @return 是保留调用时返回 {@code true}
     */
    public static boolean isReservedCall(String name) {
        return RESERVED_CALLS.contains(name);
    }

    /**
     * 判断 {@code name} 是否为惰性/模板类内置函数（{@code Each}/{@code Render}），其 binder 参数会为模板参数 引入局部作用域。
     *
     * @param name
     *            待判断的名称
     * @return 是惰性内置函数时返回 {@code true}
     */
    public static boolean isLazyBuiltin(String name) {
        return LAZY_BUILTINS.contains(name);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        var s = new java.util.HashSet<>(a);
        s.addAll(b);
        return Set.copyOf(s);
    }
}
