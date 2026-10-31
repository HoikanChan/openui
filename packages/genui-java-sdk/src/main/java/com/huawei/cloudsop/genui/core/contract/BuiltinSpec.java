/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr;

import java.util.List;
import java.util.Optional;

/**
 * Runtime-free prompt documentation + concise signature for a single openui-lang builtin.
 *
 * <p>Loaded from the top-level {@code builtins} array of {@code base-contract.json}, which the
 * frontend exports via {@code getBuiltinsManifest()} in lang-core. The Java {@code PromptAssembler}
 * filters on {@link #templateBuiltin()} to reproduce the {@code ## Template Built-ins} and {@code
 * ## Data Built-ins} prompt sections byte-for-byte, without re-deriving them.
 *
 * <p>一个条目只携带名称、展示文案、参数声明与返回类型(#117 第 2 节)。排序、过滤、对象合并、模板作用域等 特殊语义由 Java builtin module 按名称维护,契约不再声明
 * {@code typeRule}/{@code input}/ {@code callForms},参数也不再声明 {@code binder}/{@code nameSyntax}。
 *
 * <p>{@link #returnType()} 对普通函数是结果类型上界,由签名推导实例化;对 Each/Render 允许省略。 {@code signature}/{@code
 * description} 只喂提示词,不承载校验规则。
 *
 * @param name builtin 名称，契约内唯一
 * @param signature 提示词展示用的调用签名原文
 * @param description 面向模型的功能说明
 * @param templateBuiltin 是否为语言模板构造的提示词分类标记;不决定是否启用 Java 特殊处理
 * @param params 按顺序声明的参数
 * @param returnType 返回类型;Each/Render 允许为 {@code null}
 * @since 2026
 */
public record BuiltinSpec(
        String name,
        String signature,
        String description,
        boolean templateBuiltin,
        List<ParamSpec> params,
        TypeExpr returnType) {

    /** 规范化构造：把参数列表复制为不可变列表。 */
    public BuiltinSpec {
        params = List.copyOf(params);
    }

    /**
     * builtin 的单个参数声明。
     *
     * @param name 参数名（展示与诊断用）
     * @param type 参数类型表达式原文（错误消息按原文渲染）
     * @param typeExpr 解析后的类型表达式
     * @param optional 可省略
     * @param variadic 不定长（必须在末位）
     * @since 2026
     */
    public record ParamSpec(
            String name, String type, TypeExpr typeExpr, boolean optional, boolean variadic) {

        /** 最小 arity 贡献；variadic 参数可出现零次。 */
        int minArity() {
            return optional || variadic ? 0 : 1;
        }
    }

    /**
     * 返回参数个数上限；存在 variadic 参数时无上限。
     *
     * @return 上限；存在 variadic 参数时为空
     */
    public Optional<Integer> maxArity() {
        return params().stream().anyMatch(ParamSpec::variadic)
                ? Optional.empty()
                : Optional.of(params().size());
    }

    /**
     * 返回参数个数下限；optional 与 variadic 参数贡献 0。
     *
     * @return 最小实参个数
     */
    public int minArity() {
        return params().stream().mapToInt(ParamSpec::minArity).sum();
    }
}
