/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.TypeRule;

import java.util.List;
import java.util.Optional;

/**
 * Runtime-free prompt documentation + structured signature for a single openui-lang builtin.
 *
 * <p>Loaded from the top-level {@code builtins} array of {@code base-contract.json}, which the
 * frontend exports via {@code getBuiltinsManifest()} in lang-core. The Java {@code PromptAssembler}
 * filters on {@link #templateBuiltin()} to reproduce the {@code ## Template Built-ins} and {@code
 * ## Data Built-ins} prompt sections byte-for-byte, without re-deriving them.
 *
 * <p>校验侧结构字段：{@link #name()}（查找键）、{@link #params()}（含 optional/variadic/binder 修饰；其 {@link
 * ParamSpec#typeExpr()} 仍用于模板构造的非 binder 参数检查）、{@link #returnType()}（纯展示字段；加载期只解析语法并校验类型变量绑定与
 * {@code param(n)} 下标，不参与结果推断，也不与 {@code typeRule} 交叉核对）。{@code signature}
 * 降级为纯展示字段，只喂提示词，不承载任何校验规则。加载器不比较 {@link #params()} 推导的 arity 与 {@code
 * typeRule.arity}，两者一致靠人工核对与测试。
 *
 * <p>{@link #typeRule()} 是契约驱动的结构化类型规则（#77 第 8 节），也是非模板 builtin 参数与结果的唯一求值规则：调用形式、参数模式与结果运算
 * 在加载期编译为封闭语法树，词表与类型规则来自同一次契约解析。{@code null} 只表示该 builtin 是语言模板构造（Each/Render/Switch），其 binder 布局仍从
 * {@link #callForms()}/{@link #params()} 读取，模板作用域、分支语义与非 binder 参数检查由 Java 侧实现——不存在第二份 builtin
 * 类型词表。
 *
 * <p>非模板 builtin 缺少 {@code typeRule}（例如只声明 params/returnType 的遗留 custom 契约）是未证明状态：结果记为 UNPROVEN /
 * {@code UNSUPPORTED_RULE}，不会按 {@code returnType} 推断。
 *
 * @since 2026
 */
public record BuiltinSpec(
        String name,
        String signature,
        String description,
        boolean templateBuiltin,
        List<ParamSpec> params,
        TypeExpr returnType,
        TypeRule typeRule,
        int inputIndex,
        List<CallForm> callForms) {

    public BuiltinSpec {
        callForms = List.copyOf(callForms);
    }

    public BuiltinSpec(
            String name,
            String signature,
            String description,
            boolean templateBuiltin,
            List<ParamSpec> params,
            TypeExpr returnType,
            TypeRule typeRule,
            int inputIndex) {
        this(
                name,
                signature,
                description,
                templateBuiltin,
                params,
                returnType,
                typeRule,
                inputIndex,
                List.of());
    }

    /** Compiled exact-arity layout; indexes refer to actual call arguments. */
    public record CallForm(int arity, int body, List<Integer> binders) {
        public CallForm {
            binders = List.copyOf(binders);
        }
    }

    /** 兼容构造：无结构化类型规则、无输入来源声明的条目。 */
    public BuiltinSpec(
            String name,
            String signature,
            String description,
            boolean templateBuiltin,
            List<ParamSpec> params,
            TypeExpr returnType) {
        this(name, signature, description, templateBuiltin, params, returnType, null, -1);
    }

    /** 兼容构造：带结构化类型规则、无输入来源声明的条目。 */
    public BuiltinSpec(
            String name,
            String signature,
            String description,
            boolean templateBuiltin,
            List<ParamSpec> params,
            TypeExpr returnType,
            TypeRule typeRule) {
        this(name, signature, description, templateBuiltin, params, returnType, typeRule, -1);
    }

    /**
     * builtin 的单个参数声明。
     *
     * @param name 参数名（展示用）
     * @param type 参数类型表达式原文（错误消息按原文渲染）
     * @param typeExpr 解析后的类型表达式
     * @param optional 可省略
     * @param variadic 不定长（必须在末位）
     * @param binder binder 参数——绑定名引入后续参数的作用域
     * @param nameSyntax binder 名允许的语法形态（仅 binder 参数有意义；契约枚举值见 {@link NameSyntax#fromContract}； 非
     *     binder 参数为 {@code null}）
     */
    public record ParamSpec(
            String name,
            String type,
            TypeExpr typeExpr,
            boolean optional,
            boolean variadic,
            boolean binder,
            String nameSyntax) {

        /** 兼容构造：无 nameSyntax 声明的条目。 */
        public ParamSpec(
                String name,
                String type,
                TypeExpr typeExpr,
                boolean optional,
                boolean variadic,
                boolean binder) {
            this(name, type, typeExpr, optional, variadic, binder, null);
        }

        /** 最小 arity 贡献；variadic 参数可出现零次。 */
        int minArity() {
            return optional || variadic ? 0 : 1;
        }
    }

    /** arity 上限；variadic 不封顶。 */
    public Optional<Integer> maxArity() {
        return params().stream().anyMatch(ParamSpec::variadic)
                ? Optional.empty()
                : Optional.of(params().size());
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

    /** 一次调用的 binder 和 body 位置只取自加载期编译的契约调用形式。 */
    public List<Integer> activeBinderIndexes(int argCount) {
        return callForms.stream()
                .filter(form -> form.arity() == argCount)
                .findFirst()
                .map(CallForm::binders)
                .orElse(List.of());
    }

    public int bodyIndex(int argCount) {
        return callForms.stream()
                .filter(form -> form.arity() == argCount)
                .findFirst()
                .map(CallForm::body)
                .orElse(-1);
    }

    /** 元素绑定关系的输入来源实参下标;未声明(如 Render)时为 -1。 */
    public int inputIndex() {
        return inputIndex;
    }

    /** binder 名语法枚举的契约值（base-contract.json 的 params[].nameSyntax）。 */
    public enum NameSyntax {
        /** 字符串字面量或裸标识符。 */
        STRING_OR_IDENTIFIER,
        /** 仅字符串字面量。 */
        STRING;

        /** 解析契约声明值;未知值即加载失败,避免词表漂移。 */
        public static NameSyntax fromContract(String value) {
            return switch (value) {
                case "string-or-identifier" -> STRING_OR_IDENTIFIER;
                case "string" -> STRING;
                default ->
                        throw new GenerationSdkException(
                                "unknown binder nameSyntax in contract: " + value);
            };
        }

        /** 契约原文值(与 base-contract.json 一致)。 */
        public String contractValue() {
            return switch (this) {
                case STRING_OR_IDENTIFIER -> "string-or-identifier";
                case STRING -> "string";
            };
        }
    }
}
