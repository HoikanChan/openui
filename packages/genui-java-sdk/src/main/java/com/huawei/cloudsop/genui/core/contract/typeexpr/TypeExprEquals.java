/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract.typeexpr;

import java.util.List;

/**
 * {@link TypeExpr} 的语义等值比较（忽略空白与字段序等表面差异仅由解析器归一——本比较器做纯结构对照）。
 *
 * <p>供通用求值器在"实参类型 vs 契约参数类型"对照时使用；两棵树按构造逐一对照，对象字面量按字段名配对（忽略声明顺序）。
 *
 * @since 2026
 */
public final class TypeExprEquals {

    private TypeExprEquals() {}

    /**
     * 按结构比较两个类型表达式是否等值；{@code null} 与任何值（包括 {@code null}）都不等值。
     *
     * <p>对象字面量按字段名配对，忽略字段声明顺序；union 分支按声明顺序逐项比较。
     *
     * @param a 左侧类型
     * @param b 右侧类型
     * @return 结构等值时为 {@code true}
     */
    public static boolean equals(TypeExpr a, TypeExpr b) {
        if (a instanceof TypeExpr.Base base) {
            return b instanceof TypeExpr.Base other && base.name().equals(other.name());
        }
        if (a instanceof TypeExpr.ArrayOf arrayOf) {
            return b instanceof TypeExpr.ArrayOf other
                    && equals(arrayOf.element(), other.element());
        }
        if (a instanceof TypeExpr.RecordOf record) {
            return b instanceof TypeExpr.RecordOf other && equals(record.value(), other.value());
        }
        if (a instanceof TypeExpr.ObjectLiteral literal) {
            if (!(b instanceof TypeExpr.ObjectLiteral other)
                    || literal.fields().size() != other.fields().size()) {
                return false;
            }
            for (TypeExpr.ObjectLiteral.Field field : literal.fields()) {
                TypeExpr matched =
                        other.fields().stream()
                                .filter(candidate -> candidate.name().equals(field.name()))
                                .findFirst()
                                .map(TypeExpr.ObjectLiteral.Field::type)
                                .orElse(null);
                if (matched == null || !equals(field.type(), matched)) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof TypeExpr.UnionOf union) {
            if (!(b instanceof TypeExpr.UnionOf other)
                    || union.alternatives().size() != other.alternatives().size()) {
                return false;
            }
            List<TypeExpr> left = union.alternatives();
            List<TypeExpr> right = other.alternatives();
            for (int i = 0; i < left.size(); i++) {
                if (!equals(left.get(i), right.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof TypeExpr.TypeVar) {
            return b instanceof TypeExpr.TypeVar;
        }
        if (a instanceof TypeExpr.ParamRef ref) {
            return b instanceof TypeExpr.ParamRef other && ref.index() == other.index();
        }
        return false;
    }
}
