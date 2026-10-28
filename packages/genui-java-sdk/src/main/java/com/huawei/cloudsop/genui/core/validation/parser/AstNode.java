/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.List;

/**
 * openui-lang 表达式中可能出现的每一种值。
 *
 * <p>
 * 密封接口，对应 {@code packages/lang-core/src/parser/ast.ts} 中 TS 的可辨识联合类型 {@code ASTNode}。 每个变体都是一个
 * record。解析器负责产出这些节点；求值/物化不在本层范围内（后续章节消费该语法树）。
 *
 * @since 2026
 */
public sealed interface AstNode permits AstNode.Comp, AstNode.Str, AstNode.Num, AstNode.Bool, AstNode.Null, AstNode.Arr,
        AstNode.Obj, AstNode.Ref, AstNode.StateRef, AstNode.BinOp, AstNode.UnaryOp, AstNode.Ternary, AstNode.Member,
        AstNode.Index, AstNode.Assign {

    /**
     * 组件调用：{@code Header("Hello", "Subtitle")}。
     *
     * @param name
     *            组件名
     * @param args
     *            位置参数列表
     *
     * @since 2026
     */
    record Comp(String name, List<AstNode> args) implements AstNode {

        /**
         * 紧凑构造方法，将参数列表归一化为不可变列表。
         */
        public Comp {
            args = args == null ? List.of() : List.copyOf(args);
        }
    }

    /**
     * 字符串字面量：{@code "hello"}。
     *
     * @param v
     *            字符串值
     *
     * @since 2026
     */
    record Str(String v) implements AstNode {
    }

    /**
     * 数字字面量：{@code 42} 或 {@code 3.14}。
     *
     * @param v
     *            数值
     *
     * @since 2026
     */
    record Num(double v) implements AstNode {
    }

    /**
     * 布尔字面量：{@code true} / {@code false}。
     *
     * @param v
     *            布尔值
     *
     * @since 2026
     */
    record Bool(boolean v) implements AstNode {
    }

    /**
     * {@code null} 字面量。
     *
     * @since 2026
     */
    record Null() implements AstNode {
    }

    /**
     * 数组字面量：{@code [a, b, c]}。
     *
     * @param els
     *            数组元素列表
     *
     * @since 2026
     */
    record Arr(List<AstNode> els) implements AstNode {

        /**
         * 紧凑构造方法，将元素列表归一化为不可变列表。
         */
        public Arr {
            els = els == null ? List.of() : List.copyOf(els);
        }
    }

    /**
     * 对象字面量：{@code &#123; key: value &#125;}。条目保持插入顺序。
     *
     * @param entries
     *            键值对条目列表
     *
     * @since 2026
     */
    record Obj(List<Entry> entries) implements AstNode {

        /**
         * 紧凑构造方法，将条目列表归一化为不可变列表。
         */
        public Obj {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        /**
         * 对象字面量中的单个 {@code key: value} 条目。
         *
         * @param key
         *            键
         * @param value
         *            值
         *
         * @since 2026
         */
        public record Entry(String key, AstNode value) {
        }
    }

    /**
     * 对另一条语句的引用：{@code myTable}。
     *
     * @param n
     *            被引用的语句 id
     *
     * @since 2026
     */
    record Ref(String n) implements AstNode {
    }

    /**
     * 响应式状态变量引用：{@code $count}（{@code n} 包含前导 $）。
     *
     * @param n
     *            状态变量名（含前导 $）
     *
     * @since 2026
     */
    record StateRef(String n) implements AstNode {
    }

    /**
     * 二元运算：{@code a + b}、{@code x == y}。
     *
     * @param op
     *            运算符
     * @param left
     *            左操作数
     * @param right
     *            右操作数
     *
     * @since 2026
     */
    record BinOp(String op, AstNode left, AstNode right) implements AstNode {
    }

    /**
     * 一元运算：{@code !flag}、{@code -x}。
     *
     * @param op
     *            运算符
     * @param operand
     *            操作数
     *
     * @since 2026
     */
    record UnaryOp(String op, AstNode operand) implements AstNode {
    }

    /**
     * 三元条件表达式：{@code cond ? then : otherwise}。
     *
     * @param cond
     *            条件表达式
     * @param then
     *            条件为真时的取值
     * @param otherwise
     *            条件为假时的取值
     *
     * @since 2026
     */
    record Ternary(AstNode cond, AstNode then, AstNode otherwise) implements AstNode {
    }

    /**
     * 点号成员访问：{@code obj.field}。
     *
     * @param obj
     *            被访问对象
     * @param field
     *            字段名
     *
     * @since 2026
     */
    record Member(AstNode obj, String field) implements AstNode {
    }

    /**
     * 方括号索引访问：{@code arr[0]}。
     *
     * @param obj
     *            被访问对象
     * @param index
     *            索引表达式
     *
     * @since 2026
     */
    record Index(AstNode obj, AstNode index) implements AstNode {
    }

    /**
     * 状态赋值：{@code $count = $count + 1}（{@code target} 包含前导 $）。
     *
     * @param target
     *            被赋值的状态变量名（含前导 $）
     * @param value
     *            赋值表达式
     *
     * @since 2026
     */
    record Assign(String target, AstNode value) implements AstNode {
    }
}
