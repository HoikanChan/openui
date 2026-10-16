/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract.typeexpr;

import java.util.List;

/**
 * 契约 builtins 条目中 TS 风格类型串的解析结果（封闭文法，见 {@link TypeExprParser}）。
 *
 * <p>
 * 语义等值比较基于结构而非字符串：{@code string[]} 与 {@code string[]} 等值，{@code T} 与 {@code T} 在同一绑定环境下等值。文法之外的构造在解析期即被拒绝。
 *
 * @since 2026
 */
public sealed interface TypeExpr permits TypeExpr.Base, TypeExpr.ArrayOf, TypeExpr.RecordOf, TypeExpr.ObjectLiteral,
        TypeExpr.UnionOf, TypeExpr.TypeVar, TypeExpr.ParamRef, TypeExpr.Conditional {

    /** 基础类型 token：any/number/string/boolean/null/object/array。 */
    record Base(String name) implements TypeExpr {
    }

    /** 数组后缀 {@code T[]}。 */
    record ArrayOf(TypeExpr element) implements TypeExpr {
    }

    /** {@code Record<string, T>}。 */
    record RecordOf(TypeExpr value) implements TypeExpr {
    }

    /** 对象字面量 {@code {字段: T, ...}}（字段序保持声明顺序）。 */
    record ObjectLiteral(List<Field> fields) implements TypeExpr {

        /** 对象字面量的单个字段。 */
        public record Field(String name, TypeExpr type) {
        }
    }

    /** union {@code A | B | ...}（分支序保持声明顺序）。 */
    record UnionOf(List<TypeExpr> alternatives) implements TypeExpr {
    }

    /** 泛型变量 {@code T}——绑定于参数位置，returnType 中代入求值。 */
    record TypeVar() implements TypeExpr {
    }

    /** {@code param(n)}——引用第 n 个实参（0 起）的类型；param 越界按 null 类型处理。 */
    record ParamRef(int index) implements TypeExpr {
    }

    /**
     * 条件返回 {@code A when param(n) is array, else B}——按第 n 实参是否为数组分流。仅此一种条件形态（FormatDate 的数组映射语义）；n 越界按非数组分支求值。
     */
    record Conditional(int paramIndex, TypeExpr whenArray, TypeExpr otherwise) implements TypeExpr {
    }
}
