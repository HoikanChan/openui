/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract.typeexpr;

import java.util.List;

/**
 * 契约 builtins 条目中 TS 风格类型串的解析结果（封闭文法，见 {@link TypeExprParser}）。
 *
 * <p>文法之外的构造(含已删除的 {@code param(n)} 与 {@code when/else})在解析期即被拒绝。
 *
 * @since 2026
 */
public sealed interface TypeExpr
        permits TypeExpr.Base,
                TypeExpr.ArrayOf,
                TypeExpr.RecordOf,
                TypeExpr.ObjectLiteral,
                TypeExpr.UnionOf,
                TypeExpr.TypeVar {

    /**
     * 基础类型 token：any/number/string/boolean/null/object/array。
     *
     * @param name 基础类型关键字
     * @since 2026
     */
    record Base(String name) implements TypeExpr {}

    /**
     * 数组后缀 {@code T[]}。
     *
     * @param element 元素类型
     * @since 2026
     */
    record ArrayOf(TypeExpr element) implements TypeExpr {}

    /**
     * {@code Record<string, T>}。
     *
     * @param value 值类型
     * @since 2026
     */
    record RecordOf(TypeExpr value) implements TypeExpr {}

    /**
     * 对象字面量 {@code {字段: T, ...}}（字段序保持声明顺序）。
     *
     * @param fields 字段列表
     * @since 2026
     */
    record ObjectLiteral(List<Field> fields) implements TypeExpr {

        /**
         * 紧凑构造方法:字段列表冻结为不可变集合,嵌套定义可安全跨阶段共享。
         *
         * @throws NullPointerException 字段列表或元素为 null 时
         */
        public ObjectLiteral {
            fields = List.copyOf(fields);
        }

        /**
         * 对象字面量的单个字段。
         *
         * @param name 字段名
         * @param type 字段类型
         * @since 2026
         */
        public record Field(String name, TypeExpr type) {}
    }

    /**
     * union {@code A | B | ...}（分支序保持声明顺序）。
     *
     * @param alternatives 分支类型列表
     * @since 2026
     */
    record UnionOf(List<TypeExpr> alternatives) implements TypeExpr {

        /**
         * 紧凑构造方法:分支列表冻结为不可变集合,嵌套定义可安全跨阶段共享。
         *
         * @throws NullPointerException 分支列表或元素为 null 时
         */
        public UnionOf {
            alternatives = List.copyOf(alternatives);
        }
    }

    /**
     * 命名泛型变量 {@code T}、{@code D} 等单个大写字母——按变量名在本次调用中独立绑定,returnType 中代入求值。
     *
     * @param name 变量名(单个大写字母)
     * @since 2026
     */
    record TypeVar(String name) implements TypeExpr {

        /**
         * 紧凑构造方法:变量名必须非空。
         *
         * @throws IllegalArgumentException 变量名为 null 或空白时
         */
        public TypeVar {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("type variable requires a name");
            }
        }
    }
}
