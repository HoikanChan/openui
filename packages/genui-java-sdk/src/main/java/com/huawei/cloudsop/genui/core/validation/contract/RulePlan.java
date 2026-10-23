/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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
 * <p>
 * 语法对应 #77 设计第 8.2 节的有限规则语言，按本票（#81）所需的普通 builtin 运算收敛：
 *
 * <pre>
 * TypePattern := unknown | number | string | boolean | null | object | any
 *              | array | sequence(TypePattern) | recordOf(TypePattern) | variable(name)
 * Rule        := type(TypePattern) | argument(index) | argumentOr(index, Rule) | variable(name)
 *              | union(Rule...) | apply(op, operands)
 * Operand     := Rule | argumentsFrom(index)
 * </pre>
 *
 * 参数引用必须落在调用形式的参数布局内（编译期校验）；可选参数只能经 {@code argumentOr} 读取。 {@code op} 属于封闭枚举——未知 op 在加载期失败，不允许契约运行任意脚本。
 *
 * @since 2026
 */
public sealed interface RulePlan {

    /** 顶层结构化规则：调用形式（arity + 参数布局）与结果规则。 */
    record TypeRule(Arity arity, List<ParamLayout> parameters, Rule result) implements RulePlan {
        public TypeRule {
            parameters = List.copyOf(parameters);
        }

        /** 调用形式数量约束；{@code max} 为空表示 variadic 不封顶。 */
        public record Arity(int min, OptionalInt max) {
            public Arity {
                if (min < 0 || (max.isPresent() && max.getAsInt() < min)) {
                    throw new IllegalArgumentException("illegal arity: min=" + min + ", max=" + max);
                }
            }
        }

        /**
         * 参数布局：展示名、{@code rest}（接收该下标起的全部剩余实参）与可选的 accept 模式。 accept 为 null 表示该参数不做结构化模式检查（沿用既有契约 params 检查）。
         */
        public record ParamLayout(String name, boolean rest, TypePattern accept) {
            public ParamLayout(String name, boolean rest) {
                this(name, rest, null);
            }
        }
    }

    /** 实参检查的有限类型模式；变量在单次调用内求解。 */
    sealed interface TypePattern extends RulePlan {
        /** 明确声明的粗类型输出：合法降精度（结果 Unknown 记 loss，影响对覆盖可见）。 */
        record Unknown() implements TypePattern {
        }

        record Scalar(ScalarKind kind) implements TypePattern {
        }

        enum ScalarKind {
            NUMBER("number"), STRING("string"), BOOLEAN("boolean"), NULL("null");

            private final String displayName;

            ScalarKind(String displayName) {
                this.displayName = displayName;
            }

            public String displayName() {
                return displayName;
            }
        }

        /** 任意对象（字段不受限）。 */
        record AnyRecord() implements TypePattern {
        }

        /** 任意数组（元素与数量不受限）。 */
        record AnySequence() implements TypePattern {
        }

        record SequenceOf(TypePattern element) implements TypePattern {
        }

        record RecordOf(TypePattern value) implements TypePattern {
        }

        record Variable(String name) implements TypePattern {
        }

        /** 明确接受任意值——可被证明满足（与"无要求"不同）。 */
        record Any() implements TypePattern {
        }
    }

    /** 返回/中间求值规则。 */
    sealed interface Rule extends RulePlan {
        record Constant(TypePattern pattern) implements Rule {
        }

        record Argument(int index) implements Rule {
        }

        record ArgumentOr(int index, Rule fallback) implements Rule {
        }

        record VariableRef(String name) implements Rule {
        }

        record UnionOfRules(List<Rule> alternatives) implements Rule {
            public UnionOfRules {
                alternatives = List.copyOf(alternatives);
            }
        }

        record Apply(Op op, List<Operand> operands, Map<String, String> options) implements Rule {
            public Apply {
                operands = List.copyOf(operands);
                options = options == null ? Map.of() : Map.copyOf(options);
            }
        }
    }

    /** 运算操作数：单条规则，或把某下标起的实参序列整体传给运算。 */
    sealed interface Operand extends RulePlan {
        record RuleOperand(Rule rule) implements Operand {
        }

        record ArgumentsFrom(int index) implements Operand {
        }
    }

    /**
     * 有限类型运算的封闭枚举；编译期按名字解析，未知名字加载期失败。每个 op 直接对应 {@code validation.type.analysis.TypeOps} 的一个语义入口——Java 只按运算种类分派。
     */
    enum Op {
        /** 保留元素、数量与元素存在证据（Sort → TypeOps.preserve）。 */
        SEQUENCE_PRESERVE("sequence.preserve"),
        /** 保留元素上界与数量上界，下界降 0，删除元素存在证据（Filter → TypeOps.subset）。 */
        SEQUENCE_SUBSET("sequence.subset"),
        /** 取数组元素事实，观察证据下钻一层（Sum/Join 等按元素聚合 → TypeOps.element）。 */
        SEQUENCE_ELEMENT("sequence.element"),
        /** 有序对象覆盖（MergeObject → TypeOps.overlay）；后写覆盖前值，字段证据被覆盖时替换。 */
        RECORD_OVERLAY("record.overlay");

        private final String wireName;

        Op(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

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
