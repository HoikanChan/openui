/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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
 * 校验链的 builtin 函数词表——从当前生效契约的 {@code builtins} 派生（参见 ADR 0003 D1）；当请求未携带契约或契约 {@code builtins} 为空（如仅构造组件目录的轻量测试契约）时回退到
 * SDK 默认 {@code base-contract.json} 派生集。
 *
 * <p>
 * 解析器/分析器/类型求值器通过构造或参数注入本词表识别 builtin 调用；契约里没有的函数名按幻觉（unknown）处理。Action 步骤名与 Query/Mutation 是 DSL 语法保留字，留在
 * {@link Builtins}。
 *
 * <p>
 * 惰性/模板类（lazy）判定由契约 params 的 {@code binder} 标记派生——带 binder 参数的 builtin 即 lazy，其绑定名会为模板参数引入局部作用域（与提示词分节标记
 * templateBuiltin 是两个概念）。
 *
 * <p>
 * 无状态、线程安全；实例可自由共享。
 *
 * @since 2026
 */
public final class BuiltinCatalog {

    private static final BuiltinCatalog DEFAULT = new BuiltinCatalog(GenerationContractLoader.loadDefault().builtins());

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

    /** 从契约 builtins 构造词表。 */
    public static BuiltinCatalog of(Iterable<BuiltinSpec> builtins) {
        return new BuiltinCatalog(builtins);
    }

    /** 从当前生效契约派生词表；当契约为 {@code null} 或未声明 {@code builtins} 时回退到默认词表。 */
    public static BuiltinCatalog fromContract(GenerationContract contract) {
        if (contract == null || contract.builtins() == null || contract.builtins().isEmpty()) {
            return DEFAULT;
        }
        return of(contract.builtins());
    }

    /** 默认词表：SDK 默认契约的 builtins 全集。 */
    public static BuiltinCatalog getDefault() {
        return DEFAULT;
    }

    /** 是否为契约内置函数。 */
    public boolean isBuiltin(String name) {
        return names.contains(name);
    }

    /** 惰性/模板类内置函数——契约 params 带 binder 标记（当前为 Each/Render）。 */
    public boolean isLazyBuiltin(String name) {
        return lazyNames.contains(name);
    }

    /** 词表全集（只读）。 */
    public Set<String> names() {
        return names;
    }

    /** 按名取契约条目（无则 null）。 */
    public BuiltinSpec spec(String name) {
        return specs.get(name);
    }

    /** 契约条目全集（供类型求值器构造）。 */
    public Iterable<BuiltinSpec> specsForTypeValidation() {
        return specs.values();
    }
}
