/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;

import java.util.List;

/**
 * 惰性 builtin（{@code @Each}/{@code @Render}）binder 布局的共享解析规则（#83）。
 *
 * <p>
 * 布局全部来自契约编译产物 {@link BuiltinSpec}：binder 位（params 的 {@code binder} 标记）、名语法（{@code nameSyntax}）、 body
 * 位置（{@code callForms[].body}）、元素绑定的输入来源（条目的 {@code input} 下标）。语义层（{@link ProgramAnalyzer}、
 * {@link StatementRelations}）与类型层（AnalysisSession）共用本类，不各自重述布局。
 *
 * @since 2026
 */
public final class LazyBuiltinBinders {

    private LazyBuiltinBinders() {
    }

    /**
     * 一次调用实际引入的 binder 下标,来自匹配实参数量的契约调用形式。
     *
     * @param comp
     *            被检查的调用
     * @param builtins
     *            当前 builtin 词表
     * @return binder 实参下标；调用不引入 binder 作用域时为空列表
     */
    public static List<Integer> binderIndexes(AstNode.Comp comp, BuiltinCatalog builtins) {
        if (!builtins.isLazyBuiltin(comp.name())) {
            return List.of();
        }
        BuiltinSpec spec = builtins.spec(comp.name());
        if (spec == null || comp.args().size() < spec.minArity()) {
            return List.of();
        }
        return spec.activeBinderIndexes(comp.args().size());
    }

    /** body/模板实参下标:委托契约编译产物 {@link BuiltinSpec#bodyIndex(int)}。 */
    public static int bodyIndex(BuiltinSpec spec, AstNode.Comp comp) {
        return spec.bodyIndex(comp.args().size());
    }

    /**
     * 按契约 nameSyntax 取 binder 名:{@code string-or-identifier} 允许字符串字面量与裸标识符, {@code string} 仅字符串字面量;其他形状或越界下标返回
     * {@code null}。
     *
     * @param spec
     *            编译后的 builtin 声明
     * @param comp
     *            被检查的调用
     * @param index
     *            binder 实参下标
     * @return binder 名;该实参不是契约允许的 binder 形态时为 {@code null}
     */
    public static String binderName(BuiltinSpec spec, AstNode.Comp comp, int index) {
        if (index < 0 || index >= comp.args().size() || index >= spec.params().size()
                || !spec.params().get(index).binder()) {
            return null;
        }
        AstNode arg = comp.args().get(index);
        if (arg instanceof AstNode.Str str) {
            return str.v();
        }
        String syntax = spec.params().get(index).nameSyntax();
        if (arg instanceof AstNode.Ref ref
                && BuiltinSpec.NameSyntax.STRING_OR_IDENTIFIER == BuiltinSpec.NameSyntax.fromContract(syntax)) {
            return ref.n();
        }
        return null;
    }
}
