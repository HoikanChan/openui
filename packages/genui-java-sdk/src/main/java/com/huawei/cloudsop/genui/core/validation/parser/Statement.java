/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.List;

/**
 * 形如 {@code id = expression} 的顶层类型化语句。
 *
 * <p>
 * 对应 {@code ast.ts} 中 TS 侧的 {@code Statement} 联合类型（种类：value/state/query/mutation）。每个变体
 * 额外携带一个 {@link SourceSpan}（行/列/偏移范围）—— 这是 Java 侧为诊断信息新增的扩展，TS 侧未追踪该信息。
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
     * 从 {@code Comp} 节点提取出的工具/查询调用形状。
     *
     * @param callee
     *            被调用的名称
     * @param args
     *            调用参数列表
     *
     * @since 2026
     */
    record CallNode(String callee, List<AstNode> args) {

        /**
         * 紧凑构造方法，将参数列表归一化为不可变列表。
         */
        public CallNode {
            args = args == null ? List.of() : List.copyOf(args);
        }
    }

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
    }

    /**
     * {@code id = Query(...)} —— 查询声明。
     *
     * @param id
     *            语句标识符
     * @param call
     *            查询调用形状
     * @param expr
     *            整体表达式
     * @param deps
     *            依赖项列表
     * @param span
     *            源码位置范围
     *
     * @since 2026
     */
    record Query(String id, CallNode call, AstNode expr, List<String> deps, SourceSpan span) implements Statement {

        /**
         * 紧凑构造方法，将依赖列表归一化为不可变列表。
         */
        public Query {
            deps = deps == null ? List.of() : List.copyOf(deps);
        }
    }

    /**
     * {@code id = Mutation(...)} —— 变更声明。
     *
     * @param id
     *            语句标识符
     * @param call
     *            变更调用形状
     * @param expr
     *            整体表达式
     * @param span
     *            源码位置范围
     *
     * @since 2026
     */
    record Mutation(String id, CallNode call, AstNode expr, SourceSpan span) implements Statement {
    }
}
