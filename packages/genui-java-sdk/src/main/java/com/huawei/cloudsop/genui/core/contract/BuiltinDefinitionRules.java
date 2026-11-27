/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr;

import java.util.List;

/**
 * builtin 定义期规则:特殊函数的固定布局前提与返回声明覆盖校验。
 *
 * <p>本类只依赖 {@link BuiltinSpec} 与 {@link TypeExpr},不持有分析会话状态,供契约加载器与 builtin
 * 分析模块共用。规则按函数名固定,契约只声明名称、params 与 returnType;声明与 Java 实现冲突时明确失败, 不静默裁剪结果掩盖冲突。
 *
 * @since 2026
 */
public final class BuiltinDefinitionRules {

    /** 逐元素模板构造的名称;布局由调用方固定。 */
    private static final String EACH = "Each";

    /** 延迟模板构造的名称;模板在使用位置才检查。 */
    private static final String RENDER = "Render";

    /** 分支结果构造的名称;cases 在当前环境求值。 */
    private static final String SWITCH = "Switch";

    /** 保留数量与元素存在证据的排序。 */
    private static final String SORT = "Sort";

    /** 下界归零并删除行保留证据的过滤。 */
    private static final String FILTER = "Filter";

    /** 选择首元素的数组函数。 */
    private static final String FIRST = "First";

    /** 选择末元素的数组函数。 */
    private static final String LAST = "Last";

    /** 单层展平。 */
    private static final String FLATTEN = "Flatten";

    /** 有序对象覆盖。 */
    private static final String MERGE_OBJECT = "MergeObject";

    /** 对象键枚举。 */
    private static final String OBJECT_KEYS = "ObjectKeys";

    /** 对象值枚举。 */
    private static final String OBJECT_VALUES = "ObjectValues";

    /** 对象键值对枚举。 */
    private static final String OBJECT_ENTRIES = "ObjectEntries";

    /** 按输入形状细化标量/数组结果的日期格式化。 */
    private static final String FORMAT_DATE = "FormatDate";

    /** Each/Render 的最长声明参数个数(Each 固定三参;Render 以三参形式声明,中位 row 可选)。 */
    private static final int TEMPLATE_PARAM_COUNT = 3;

    private BuiltinDefinitionRules() {}

    /**
     * 该名称是否由调用方拥有调用布局(Each/Render)。
     *
     * <p>加载器据此跳过普通可选/变长位置规则,不在自身维护函数名单。
     *
     * @param name builtin 名称
     * @return 布局由调用方固定时为 {@code true}
     */
    public static boolean ownsCallLayout(String name) {
        return EACH.equals(name) || RENDER.equals(name);
    }

    /**
     * 定义期结构校验:特殊函数的最低布局前提与返回声明覆盖。
     *
     * <p>Each/Render/Switch 的参数角色必须与 Java 实现的固定布局一致;有 Java 额外结果语义的普通函数, 其 returnType
     * 必须覆盖特殊语义可证明的结果上界。
     *
     * @param spec 已解析的 builtin 声明
     * @throws GenerationSdkException 布局前提不满足或返回声明可证明冲突时
     */
    public static void validateDefinition(BuiltinSpec spec) {
        switch (spec.name()) {
            case EACH -> validateEach(spec);
            case RENDER -> validateRender(spec);
            case SWITCH -> validateSwitch(spec);
            case SORT,
                            FILTER,
                            FIRST,
                            LAST,
                            FLATTEN,
                            MERGE_OBJECT,
                            OBJECT_KEYS,
                            OBJECT_VALUES,
                            OBJECT_ENTRIES,
                            FORMAT_DATE ->
                    validateSpecialReturn(spec);
            default -> {
                // 普通函数只需通用参数/返回校验。
            }
        }
    }

    private static void validateEach(BuiltinSpec spec) {
        requireParamCount(spec, TEMPLATE_PARAM_COUNT);
        TypeExpr source = paramType(spec, 0);
        if (!(source instanceof TypeExpr.ArrayOf)
                && !(source instanceof TypeExpr.Base base && "array".equals(base.name()))) {
            throw invalid(
                    spec,
                    "Each parameter 0 must be an array of elements, got "
                            + spec.params().get(0).type());
        }
        requireBase(spec, 1, "string", "a binder name");
        requireNoOptionalOrVariadic(spec);
    }

    private static void validateRender(BuiltinSpec spec) {
        requireParamCount(spec, TEMPLATE_PARAM_COUNT);
        requireBase(spec, 0, "string", "a binder name");
        requireBase(spec, 1, "string", "an optional row binder name");
        if (!spec.params().get(1).optional()) {
            throw invalid(spec, "Render parameter 'row' must be optional (2- or 3-argument calls)");
        }
        if (spec.params().get(2).optional() || spec.params().get(0).optional()) {
            throw invalid(spec, "Render parameter 'v' and 'template' must be required");
        }
        if (spec.params().stream().anyMatch(BuiltinSpec.ParamSpec::variadic)) {
            throw invalid(spec, "Render must not declare a variadic parameter");
        }
    }

    private static void validateSwitch(BuiltinSpec spec) {
        requireParamCount(spec, TEMPLATE_PARAM_COUNT);
        if (!(spec.params().get(1).typeExpr() instanceof TypeExpr.RecordOf cases)) {
            throw invalid(spec, "Switch parameter 'cases' must be Record<string, T>");
        }
        if (!spec.params().get(2).optional()) {
            throw invalid(spec, "Switch parameter 'default' must be optional");
        }
        TypeExpr required =
                new TypeExpr.UnionOf(List.of(cases.value(), spec.params().get(2).typeExpr()));
        requireReturnCovers(spec, required);
    }

    /**
     * 普通特殊函数:返回声明必须覆盖 Java 特殊语义可证明的结果上界。
     *
     * @param spec 已解析的 builtin 声明
     */
    private static void validateSpecialReturn(BuiltinSpec spec) {
        TypeExpr required =
                switch (spec.name()) {
                    case SORT, FILTER -> paramType(spec, 0);
                    case FIRST, LAST -> union(elementOf(paramType(spec, 0)), base("null"));
                    case FLATTEN -> elementOf(paramType(spec, 0));
                    case MERGE_OBJECT -> base("object");
                    case OBJECT_KEYS -> new TypeExpr.ArrayOf(base("string"));
                    case OBJECT_VALUES -> {
                        TypeExpr value = recordValueOf(paramType(spec, 0));
                        yield value == null ? null : new TypeExpr.ArrayOf(value);
                    }
                    case OBJECT_ENTRIES -> {
                        TypeExpr value = recordValueOf(paramType(spec, 0));
                        yield value == null
                                ? null
                                : new TypeExpr.ArrayOf(
                                        new TypeExpr.ObjectLiteral(
                                                List.of(
                                                        new TypeExpr.ObjectLiteral.Field(
                                                                "key", base("string")),
                                                        new TypeExpr.ObjectLiteral.Field(
                                                                "value", value))));
                    }
                    case FORMAT_DATE ->
                            new TypeExpr.UnionOf(
                                    List.of(base("string"), new TypeExpr.ArrayOf(base("string"))));
                    default -> null;
                };
        if (required != null) {
            requireReturnCovers(spec, required);
        }
    }

    private static void requireParamCount(BuiltinSpec spec, int expected) {
        if (spec.params().size() != expected) {
            throw invalid(
                    spec,
                    spec.name()
                            + " requires exactly "
                            + expected
                            + " parameters for its fixed Java layout, got "
                            + spec.params().size());
        }
    }

    private static void requireBase(BuiltinSpec spec, int index, String expected, String role) {
        TypeExpr type = paramType(spec, index);
        if (!(type instanceof TypeExpr.Base base) || !expected.equals(base.name())) {
            throw invalid(
                    spec,
                    spec.name()
                            + " parameter "
                            + index
                            + " must be "
                            + expected
                            + " ("
                            + role
                            + "), got "
                            + spec.params().get(index).type());
        }
    }

    private static void requireNoOptionalOrVariadic(BuiltinSpec spec) {
        for (BuiltinSpec.ParamSpec param : spec.params()) {
            if (param.optional() || param.variadic()) {
                throw invalid(
                        spec,
                        spec.name()
                                + " parameter '"
                                + param.name()
                                + "' must not be optional or variadic");
            }
        }
    }

    private static TypeExpr paramType(BuiltinSpec spec, int index) {
        return spec.params().get(index).typeExpr();
    }

    private static TypeExpr elementOf(TypeExpr type) {
        return type instanceof TypeExpr.ArrayOf array ? array.element() : null;
    }

    private static TypeExpr recordValueOf(TypeExpr type) {
        return type instanceof TypeExpr.RecordOf record ? record.value() : null;
    }

    private static TypeExpr base(String name) {
        return new TypeExpr.Base(name);
    }

    private static TypeExpr union(TypeExpr first, TypeExpr second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return new TypeExpr.UnionOf(List.of(first, second));
    }

    private static void requireReturnCovers(BuiltinSpec spec, TypeExpr required) {
        if (spec.returnType() == null) {
            throw invalid(
                    spec,
                    spec.name()
                            + " must declare a returnType covering its Java result ("
                            + required
                            + ")");
        }
        if (!covers(spec.returnType(), required)) {
            throw invalid(
                    spec,
                    spec.name()
                            + " returnType does not cover the Java result shape required by"
                            + " its special semantics");
        }
    }

    /**
     * 声明类型是否覆盖要求类型:union 全分支覆盖、{@code any} 覆盖一切、变量按名匹配。
     *
     * @param declared 声明类型
     * @param required 要求类型
     * @return 声明覆盖要求类型时为 {@code true}
     */
    private static boolean covers(TypeExpr declared, TypeExpr required) {
        if (declared == null || required == null) {
            return false;
        }
        if (required instanceof TypeExpr.UnionOf unionOf) {
            return unionOf.alternatives().stream().allMatch(alt -> covers(declared, alt));
        }
        if (declared instanceof TypeExpr.UnionOf unionOf) {
            return unionOf.alternatives().stream().anyMatch(alt -> covers(alt, required));
        }
        if (declared instanceof TypeExpr.Base base && "any".equals(base.name())) {
            return true;
        }
        return coversSameKind(declared, required);
    }

    private static boolean coversSameKind(TypeExpr declared, TypeExpr required) {
        if (declared instanceof TypeExpr.TypeVar declaredVar
                && required instanceof TypeExpr.TypeVar requiredVar) {
            return declaredVar.name().equals(requiredVar.name());
        }
        if (declared instanceof TypeExpr.ArrayOf declaredArray
                && required instanceof TypeExpr.ArrayOf requiredArray) {
            return covers(declaredArray.element(), requiredArray.element());
        }
        if (declared instanceof TypeExpr.RecordOf declaredRecord
                && required instanceof TypeExpr.RecordOf requiredRecord) {
            return covers(declaredRecord.value(), requiredRecord.value());
        }
        if (declared instanceof TypeExpr.ObjectLiteral declaredObject
                && required instanceof TypeExpr.ObjectLiteral requiredObject) {
            return coversObjectLiteral(declaredObject, requiredObject);
        }
        if (declared instanceof TypeExpr.Base declaredBase
                && required instanceof TypeExpr.Base requiredBase) {
            return declaredBase.name().equals(requiredBase.name());
        }
        return false;
    }

    /**
     * 要求对象的每个字段都必须在声明对象里同名字段且类型被覆盖。
     *
     * @param declared 声明对象类型
     * @param required 要求对象类型
     * @return 所有要求字段都被覆盖时为 {@code true}
     */
    private static boolean coversObjectLiteral(
            TypeExpr.ObjectLiteral declared, TypeExpr.ObjectLiteral required) {
        for (TypeExpr.ObjectLiteral.Field field : required.fields()) {
            boolean covered =
                    declared.fields().stream()
                            .anyMatch(
                                    candidate ->
                                            candidate.name().equals(field.name())
                                                    && covers(candidate.type(), field.type()));
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    private static GenerationSdkException invalid(BuiltinSpec spec, String message) {
        return new GenerationSdkException("builtin '" + spec.name() + "': " + message);
    }
}
