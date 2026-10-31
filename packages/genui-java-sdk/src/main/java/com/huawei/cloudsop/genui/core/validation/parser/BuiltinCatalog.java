/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 校验链的 builtin 函数词表——从当前生效契约的 {@code builtins} 派生（参见 ADR 0003 D1）；当请求未携带契约或契约 {@code builtins}
 * 为空（如仅构造组件目录的轻量测试契约）时回退到 SDK 默认 {@code base-contract.json} 派生集。
 *
 * <p>解析器/分析器/类型求值器通过构造或参数注入本词表识别 builtin 调用；契约里没有的函数名按幻觉（unknown）处理。Action 步骤名与 Query/Mutation 是
 * DSL 语法保留字，不来自契约； 它们与 builtin 词表同处本类但判断入口各自独立（{@link #isActionName} / {@link #isReservedCall} 不并入
 * {@link #isBuiltin}）。
 *
 * <p>惰性/模板类（lazy）判定由契约 params 的 {@code binder} 标记派生——带 binder 参数的 builtin 即
 * lazy，其绑定名会为模板参数引入局部作用域（与提示词分节标记 templateBuiltin 是两个概念）。
 *
 * <p>无状态、线程安全；实例可自由共享。
 *
 * @since 2026
 */
public final class BuiltinCatalog {

    /** 保留的语句级调用名之一：{@code Query}。 */
    public static final String QUERY = "Query";

    /** 保留的语句级调用名之一：{@code Mutation}。 */
    public static final String MUTATION = "Mutation";

    /** 全部 action 表达式名（步骤与容器）。 */
    private static final Set<String> ACTION_NAMES =
            Set.of("Run", "ToAssistant", "OpenUrl", "Set", "Reset", "Action");

    /** 保留的语句级调用名集合。 */
    private static final Set<String> RESERVED_CALLS = Set.of(QUERY, MUTATION);

    private static final BuiltinCatalog DEFAULT =
            new BuiltinCatalog(GenerationContractLoader.loadDefault().builtins());

    private final Set<String> names;
    private final Set<String> lazyNames;
    private final Map<String, BuiltinSpec> specs;

    private BuiltinCatalog(Iterable<BuiltinSpec> builtins) {
        Set<String> collectedNames = new LinkedHashSet<>();
        Set<String> collectedLazy = new LinkedHashSet<>();
        Map<String, BuiltinSpec> collectedSpecs = new LinkedHashMap<>();
        for (BuiltinSpec spec : builtins) {
            collectedNames.add(spec.name());
            collectedSpecs.put(spec.name(), spec);
            if (!spec.binderIndexes().isEmpty()) {
                collectedLazy.add(spec.name());
            }
        }
        this.names = Set.copyOf(collectedNames);
        this.lazyNames = Set.copyOf(collectedLazy);
        this.specs = Map.copyOf(collectedSpecs);
    }

    /**
     * 从契约 builtins 构造词表。
     *
     * @param builtins 契约声明的 builtin 条目
     * @return 词表实例
     */
    public static BuiltinCatalog of(Iterable<BuiltinSpec> builtins) {
        return new BuiltinCatalog(builtins);
    }

    /**
     * 从当前生效契约派生词表；当契约为 {@code null} 或未声明 {@code builtins} 时回退到默认词表。
     *
     * @param contract 当前生效契约，可为 {@code null}
     * @return 契约声明的词表，或默认词表
     */
    public static BuiltinCatalog fromContract(GenerationContract contract) {
        if (contract == null || contract.builtins() == null || contract.builtins().isEmpty()) {
            return DEFAULT;
        }
        return of(contract.builtins());
    }

    /**
     * 默认词表：SDK 默认契约的 builtins 全集。
     *
     * @return 默认词表实例
     */
    public static BuiltinCatalog getDefault() {
        return DEFAULT;
    }

    /**
     * 是否为契约内置函数（只认契约 builtins，不含语法保留字）。
     *
     * @param name 待判断的函数名
     * @return 命中契约 builtins 时为 {@code true}
     */
    public boolean isBuiltin(String name) {
        return names.contains(name);
    }

    /**
     * 判断 {@code name} 是否为 action 表达式（步骤或容器）——DSL 语法保留字，与契约 builtin 无关。
     *
     * @param name 待判断的调用名
     * @return 属于 action 表达式名时为 {@code true}
     */
    public static boolean isActionName(String name) {
        return ACTION_NAMES.contains(name);
    }

    /**
     * 判断 {@code name} 是否为保留的语句级调用（{@code Query}/{@code Mutation}）——DSL 语法保留字，与契约 builtin 无关。
     *
     * @param name 待判断的调用名
     * @return 属于保留调用时为 {@code true}
     */
    public static boolean isReservedCall(String name) {
        return RESERVED_CALLS.contains(name);
    }

    /**
     * 惰性/模板类内置函数——契约 params 带 binder 标记（当前为 Each/Render）。
     *
     * @param name 待判断的函数名
     * @return 该函数引入 binder 作用域时为 {@code true}
     */
    public boolean isLazyBuiltin(String name) {
        return lazyNames.contains(name);
    }

    /**
     * 按名取契约条目（无则 null）。
     *
     * @param name 函数名
     * @return 契约条目；未知函数为 {@code null}
     */
    public BuiltinSpec spec(String name) {
        return specs.get(name);
    }

    /**
     * 契约条目全集（供类型求值器构造）。
     *
     * @return 全部 builtin 条目
     */
    public Iterable<BuiltinSpec> specsForTypeValidation() {
        return specs.values();
    }
}
