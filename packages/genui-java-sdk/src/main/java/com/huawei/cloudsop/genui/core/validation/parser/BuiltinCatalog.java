/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 校验链的 builtin 函数词表——恒从 SDK 默认 base-contract.json 资源派生（唯一真实来源），与请求契约无关。
 *
 * <p>
 * 解析器/分析器通过构造注入本词表识别 builtin 调用；契约里没有的函数名按幻觉（unknown）处理。Action 步骤名与 Query/Mutation 是 DSL 语法保留字，留在 {@link Builtins}。
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
