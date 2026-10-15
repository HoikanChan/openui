/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr;

import java.util.List;
import java.util.Optional;

/**
 * Runtime-free prompt documentation + structured signature for a single openui-lang builtin.
 *
 * <p>
 * Loaded from the top-level {@code builtins} array of {@code base-contract.json}, which the frontend exports via
 * {@code getBuiltinsManifest()} in lang-core. The Java {@code PromptAssembler} filters on {@link #templateBuiltin()} to
 * reproduce the {@code ## Template Built-ins} and {@code ## Data Built-ins} prompt sections byte-for-byte, without
 * re-deriving them.
 *
 * <p>
 * 校验侧结构字段：{@link #name()}（查找键）、{@link #params()}（含 optional/variadic/binder 修饰）、{@link #returnType()}（类型表达式；Each/Render
 * 无——binder 作用域推断留代码侧兜底）。{@code signature} 降级为纯展示字段，只喂提示词，不承载任何校验规则。
 *
 * @since 2026
 */
public record BuiltinSpec(String name, String signature, String description, boolean templateBuiltin,
        List<ParamSpec> params, TypeExpr returnType) {

    /**
     * builtin 的单个参数声明。
     *
     * @param name
     *            参数名（展示用）
     * @param type
     *            参数类型表达式原文（错误消息按原文渲染）
     * @param typeExpr
     *            解析后的类型表达式
     * @param optional
     *            可省略
     * @param variadic
     *            不定长（必须在末位）
     * @param binder
     *            binder 参数——须为字符串字面量，绑定名引入后续参数的作用域
     */
    public record ParamSpec(String name, String type, TypeExpr typeExpr, boolean optional, boolean variadic,
            boolean binder) {

        /** 最小 arity 贡献；variadic 参数可出现零次。 */
        int minArity() {
            return optional || variadic ? 0 : 1;
        }
    }

    /** arity 上限；variadic 不封顶。 */
    public Optional<Integer> maxArity() {
        return params().stream().anyMatch(ParamSpec::variadic) ? Optional.empty() : Optional.of(params().size());
    }

    /** arity 下限。 */
    public int minArity() {
        return params().stream().mapToInt(ParamSpec::minArity).sum();
    }

    /** binder 参数的下标集合。 */
    public List<Integer> binderIndexes() {
        List<Integer> indexes = new java.util.ArrayList<>();
        for (int i = 0; i < params().size(); i++) {
            if (params().get(i).binder()) {
                indexes.add(i);
            }
        }
        return List.copyOf(indexes);
    }
}
