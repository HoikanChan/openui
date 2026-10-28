/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

/**
 * 形如 {@code id = expression} 的顶层类型化语句。
 *
 * <p>
 * 对应 {@code ast.ts} 中 TS 侧的 {@code Statement} 联合类型（种类：value/state/query/mutation）。每个变体 额外携带一个
 * {@link SourceSpan}（行/列/偏移范围）—— 这是 Java 侧为诊断信息新增的扩展，TS 侧未追踪该信息。
 *
 * @since 2026
 */
public sealed interface Statement permits Statement.Value, Statement.State, Statement.Query, Statement.Mutation {

    /**
     * 语句标识符（左侧）。对于状态语句，该值包含前导 {@code $}。
     *
     * @return 语句标识符
     */
    String id();

    /**
     * 整条语句的源码位置。
     *
     * @return 源码位置范围
     */
    SourceSpan span();

    /**
     * 语句的取值表达式：{@link State} 为初始化表达式，其余变体为右值表达式。
     *
     * @return 取值表达式
     */
    AstNode expr();

    /**
     * {@code id = expr} —— 普通取值声明（可能是一个组件）。
     *
     * @param id
     *            语句标识符
     * @param expr
     *            取值表达式
     * @param span
     *            源码位置范围
     *
     * @since 2026
     */
    record Value(String id, AstNode expr, SourceSpan span) implements Statement {
    }

    /**
     * {@code $id = init} —— 响应式状态声明。
     *
     * @param id
     *            语句标识符（含前导 $）
     * @param init
     *            初始值表达式
     * @param span
     *            源码位置范围
     *
     * @since 2026
     */
    record State(String id, AstNode init, SourceSpan span) implements Statement {

        @Override
        public AstNode expr() {
            return init;
        }
    }

    /**
     * {@code id = Query(...)} —— 查询声明。
     *
     * @param id
     *            语句标识符
     * @param expr
     *            整体表达式
     * @param span
     *            源码位置范围
     *
     * @since 2026
     */
    record Query(String id, AstNode expr, SourceSpan span) implements Statement {
    }

    /**
     * {@code id = Mutation(...)} —— 变更声明。
     *
     * @param id
     *            语句标识符
     * @param expr
     *            整体表达式
     * @param span
     *            源码位置范围
     *
     * @since 2026
     */
    record Mutation(String id, AstNode expr, SourceSpan span) implements Statement {
    }
}
