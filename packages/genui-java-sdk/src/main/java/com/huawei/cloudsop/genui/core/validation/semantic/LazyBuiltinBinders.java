/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;

import java.util.List;

/**
 * 惰性 builtin（{@code @Each}/{@code @Render}）binder 参数的共享解析规则。
 *
 * <p>
 * {@link ProgramAnalyzer} 与 {@link StatementRelations} 都需要判断哪些实参是 binder、binder 名是什么；规则必须保持单一来源，避免两处漂移。
 *
 * @since 2026
 */
final class LazyBuiltinBinders {

    private LazyBuiltinBinders() {
    }

    /**
     * binder 参数位置——从契约 params 的 {@code binder} 标记派生（Each 为 1；Render 为除模板外的全部前缀参数）。 仅对实参充足的调用返回非空（Each 需 3 参、Render 需 2
     * 参）。
     *
     * @param comp
     *            被检查的调用
     * @param builtins
     *            当前 builtin 词表
     * @return binder 实参下标；调用不引入 binder 作用域时为空列表
     */
    static List<Integer> binderIndexes(AstNode.Comp comp, BuiltinCatalog builtins) {
        if (!builtins.isLazyBuiltin(comp.name())) {
            return List.of();
        }
        BuiltinSpec spec = builtins.spec(comp.name());
        if (spec == null) {
            return List.of();
        }
        int argc = comp.args().size();
        int minBindArgs = "Each".equals(comp.name()) ? 3 : 2;
        if (argc < minBindArgs) {
            return List.of();
        }
        return spec.binderIndexes().stream().filter(index -> index < argc - 1 || "Each".equals(comp.name())).toList();
    }

    /**
     * binder 可以是裸标识符（{@link AstNode.Ref}）或字符串字面量（{@link AstNode.Str}）；其他形状无效。
     *
     * @param arg
     *            binder 实参
     * @return binder 名；不是合法 binder 时为 {@code null}
     */
    static String binderName(AstNode arg) {
        if (arg instanceof AstNode.Ref ref) {
            return ref.n();
        }
        if (arg instanceof AstNode.Str str) {
            return str.v();
        }
        return null;
    }
}
