/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.contract;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * 结构化类型规则的封闭语法——契约 JSON 经 {@link RuleCompiler} 编译后的产物。
 *
 * <p>语法对应 #77 设计第 8.2 节的有限规则语言，按本票（#81）所需的普通 builtin 运算收敛：
 *
 * <pre>
 * TypePattern := unknown | number | string | boolean | null | object | any
 *              | array | sequence(TypePattern) | recordOf(TypePattern) | variable(name)
 * Rule        := type(TypePattern) | argument(index) | argumentOr(index, Rule) | variable(name)
 *              | union(Rule...) | apply(op, operands)
 * Operand     := Rule | argumentsFrom(index)
 * </pre>
 *
 * 参数引用必须落在调用形式的参数布局内（编译期校验）；可选参数只能经 {@code argumentOr} 读取。 {@code op} 属于封闭枚举——未知 op
 * 在加载期失败，不允许契约运行任意脚本。
 *
 * @since 2026
 */
public sealed interface RulePlan {

    /**
     * 顶层结构化规则：调用形式（arity + 参数布局）与结果规则。
     *
     * @param arity 实参数量约束
     * @param parameters 参数布局
     * @param result 结果规则
     * @since 2026
     */
    record TypeRule(Arity arity, List<ParamLayout> parameters, Rule result) implements RulePlan {
        /** 复制参数布局为不可变列表。 */
        public TypeRule {
            parameters = List.copyOf(parameters);
        }

        /**
         * 调用形式数量约束；{@code max} 为空表示 variadic 不封顶。
         *
         * @param min 最少实参数
         * @param max 最多实参数；空表示不封顶
         * @since 2026
         */
        public record Arity(int min, OptionalInt max) {
            /**
             * 校验数量约束自洽。
             *
             * @throws IllegalArgumentException {@code min} 为负，或 {@code max} 存在且小于 {@code min}
             */
            public Arity {
                if (min < 0 || (max.isPresent() && max.getAsInt() < min)) {
                    throw new IllegalArgumentException(
                            "illegal arity: min=" + min + ", max=" + max);
                }
            }
        }

        /**
         * 参数布局：展示名、{@code rest}（接收该下标起的全部剩余实参）与可选的 accept 模式。 accept 为 null 表示该参数不做结构化模式检查（沿用既有契约
         * params 检查）。
         *
         * @param name 参数展示名
         * @param rest 是否为接收剩余实参的 rest 参数
         * @param accept 实参模式；{@code null} 表示不做结构化模式检查
         * @since 2026
         */
        public record ParamLayout(String name, boolean rest, TypePattern accept) {}
    }

    /**
     * 实参检查的有限类型模式；变量在单次调用内求解。
     *
     * @since 2026
     */
    sealed interface TypePattern extends RulePlan {
        /**
         * 明确声明的粗类型输出：合法降精度（结果 Unknown 记 loss，影响对覆盖可见）。
         *
         * @since 2026
         */
        record Unknown() implements TypePattern {}

        /**
         * 标量模式。
         *
         * @param kind 标量种类
         * @since 2026
         */
        record Scalar(ScalarKind kind) implements TypePattern {}

        /**
         * 标量种类及其规则文本展示名。
         *
         * @since 2026
         */
        enum ScalarKind {
            NUMBER("number"),
            STRING("string"),
            BOOLEAN("boolean"),
            NULL("null");

            private final String displayName;

            ScalarKind(String displayName) {
                this.displayName = displayName;
            }

            /**
             * 该种类在规则文本与诊断中的展示名。
             *
             * @return 展示名，如 {@code number}
             */
            public String displayName() {
                return displayName;
            }
        }

        /**
         * 任意对象（字段不受限）。
         *
         * @since 2026
         */
        record AnyRecord() implements TypePattern {}

        /**
         * 任意数组（元素与数量不受限）。
         *
         * @since 2026
         */
        record AnySequence() implements TypePattern {}

        /**
         * 元素满足给定模式的数组。
         *
         * @param element 元素模式
         * @since 2026
         */
        record SequenceOf(TypePattern element) implements TypePattern {}

        /**
         * 值为给定模式的有序对象。
         *
         * @param value 值模式
         * @since 2026
         */
        record RecordOf(TypePattern value) implements TypePattern {}

        /**
         * 单次调用内求解的类型变量。
         *
         * @param name 变量名
         * @since 2026
         */
        record Variable(String name) implements TypePattern {}

        /**
         * 明确接受任意值——可被证明满足（与"无要求"不同）。
         *
         * @since 2026
         */
        record Any() implements TypePattern {}
    }

    /**
     * 返回/中间求值规则。
     *
     * @since 2026
     */
    sealed interface Rule extends RulePlan {
        /**
         * 固定类型常量规则。
         *
         * @param pattern 结果模式
         * @since 2026
         */
        record Constant(TypePattern pattern) implements Rule {}

        /**
         * 读取指定下标实参的规则。
         *
         * @param index 实参下标
         * @since 2026
         */
        record Argument(int index) implements Rule {}

        /**
         * 实参存在时读取该下标，否则回退到 {@code fallback}。
         *
         * @param index 实参下标
         * @param fallback 该下标越界时使用的规则
         * @since 2026
         */
        record ArgumentOr(int index, Rule fallback) implements Rule {}

        /**
         * 读取已求解类型变量的规则。
         *
         * @param name 变量名
         * @since 2026
         */
        record VariableRef(String name) implements Rule {}

        /**
         * 多个候选规则的并集。
         *
         * @param alternatives 候选规则；构造时复制为不可变列表
         * @since 2026
         */
        record UnionOfRules(List<Rule> alternatives) implements Rule {
            /** 复制候选规则列表为不可变视图。 */
            public UnionOfRules {
                alternatives = List.copyOf(alternatives);
            }
        }

        /**
         * 有限类型运算。
         *
         * @param op 运算种类
         * @param operands 操作数列表
         * @param options 证据声明选项；{@code null} 视为空 map
         * @since 2026
         */
        record Apply(Op op, List<Operand> operands, Map<String, String> options) implements Rule {
            /** 复制操作数与选项，{@code null} 选项归一为空 map。 */
            public Apply {
                operands = List.copyOf(operands);
                options = options == null ? Map.of() : Map.copyOf(options);
            }
        }
    }

    /**
     * 运算操作数：单条规则，或把某下标起的实参序列整体传给运算。
     *
     * @since 2026
     */
    sealed interface Operand extends RulePlan {
        /**
         * 以单条规则作为运算操作数。
         *
         * @param rule 操作数规则
         * @since 2026
         */
        record RuleOperand(Rule rule) implements Operand {}

        /**
         * 把从指定下标起的全部实参作为操作数序列。
         *
         * @param index 起始实参下标
         * @since 2026
         */
        record ArgumentsFrom(int index) implements Operand {}
    }

    /**
     * 有限类型运算的封闭枚举；编译期按名字解析，未知名字加载期失败。每个 op 直接对应 {@code validation.type.analysis.TypeOps}
     * 的一个语义入口——Java 只按运算种类分派。
     *
     * @since 2026
     */
    enum Op {
        /** 保留元素、数量与元素存在证据（Sort → TypeOps.preserve）。 */
        SEQUENCE_PRESERVE("sequence.preserve"),
        /** 保留元素上界与数量上界，下界降 0，删除元素存在证据（Filter → TypeOps.subset）。 */
        SEQUENCE_SUBSET("sequence.subset"),
        /** 取数组元素事实，观察证据下钻一层（Sum/Join 等按元素聚合 → TypeOps.element）。 */
        SEQUENCE_ELEMENT("sequence.element"),
        /** 去掉一层数组层级（Flatten → TypeOps.flattenOne）；元素与数量按已确认规则组合。 */
        SEQUENCE_FLATTEN_ONE("sequence.flattenOne"),
        /** First/Last 选择（→ TypeOps.select）；空输入结果未定，返回 Unknown + 未证明原因。 */
        SEQUENCE_SELECT("sequence.select"),
        /** 有序对象覆盖（MergeObject → TypeOps.overlay）；后写覆盖前值，字段证据被覆盖时替换。 */
        RECORD_OVERLAY("record.overlay"),
        /** 对象键、值与关联键值行的枚举。 */
        RECORD_KEYS("record.keys"),
        RECORD_VALUES("record.values"),
        RECORD_ENTRIES("record.entries"),
        /** 标量转换与数组映射（FormatDate → TypeOps.mapScalarOrArray）；数组分支得到 string[]，其余得到 string。 */
        SHAPE_MAP_SCALAR_OR_ARRAY("shape.mapScalarOrArray");

        private final String wireName;

        Op(String wireName) {
            this.wireName = wireName;
        }

        /**
         * 按规则文本中的运算名解析运算种类。
         *
         * @param name wire 名（如 {@code sequence.preserve}）
         * @return 对应运算；未知名字返回空 Optional
         */
        public static Optional<Op> byWireName(String name) {
            for (Op op : values()) {
                if (op.wireName.equals(name)) {
                    return Optional.of(op);
                }
            }
            return Optional.empty();
        }
    }

    /** 编译期保留的可声明证据名（当前只有元素存在证据一族）。 */
    Set<String> KNOWN_EVIDENCE_OPTIONS = Set.of("keepsElementPresence");
}
