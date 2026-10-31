/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * #80 子集的类型代数:不可变值类型,构造即归一化。
 *
 * <p>身份语义:#77 第 5.1 节。{@link Union} 构造时展平嵌套联合、去重、删除 Never 分支、按结构键排序; {@link Sequence} 对 Never
 * 元素执行两条归一规则——允许零长度时归一为确知空数组 {@code Sequence(never, 0, 0)}, 长度下界大于零时没有合法值,归一为 {@link Never}; {@link
 * RecordType} 中 {@link Extra#closed()} 以 {@code open(never)} 归一表示。证据与精度损失不进类型身份(它们在 {@link Fact}
 * 上),类型相等可直接用 {@code equals}。
 *
 * @since 2026
 */
public sealed interface Type {

    /**
     * 没有合法值的底类型(空集合)。
     *
     * @since 2026
     */
    enum Never implements Type {
        INSTANCE
    }

    /**
     * 信息不足时的全类型(值宇宙全集);不带 reason —— 原因属于 {@link Fact} 的 losses。
     *
     * @since 2026
     */
    enum Unknown implements Type {
        INSTANCE
    }

    /**
     * 基础标量种类。
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

        String displayName() {
            return displayName;
        }
    }

    /**
     * 基础标量。
     *
     * @param kind 标量种类,决定值域与展示名
     * @since 2026
     */
    record Scalar(ScalarKind kind) implements Type {}

    /**
     * 有限字符串字面值集合(非空;空集合应使用 {@link Never})。
     *
     * @param values 允许出现的字符串值;构造时复制为不可变集合
     * @since 2026
     */
    record StringLiterals(Set<String> values) implements Type {
        /**
         * 复制字面值集合并校验非空。
         *
         * @throws IllegalArgumentException 集合为空时——没有任何合法值的字面值集合应表示为 {@link Never}
         */
        public StringLiterals {
            values = Set.copyOf(values);
            if (values.isEmpty()) {
                throw new IllegalArgumentException("StringLiterals requires a non-empty set");
            }
        }
    }

    /**
     * 数组类型:元素上界 + 数量区间。
     *
     * @param element 元素类型上界;{@link Never} 表示只能为空数组
     * @param minLength 长度下界,非负
     * @param maxLength 长度上界,非负或 {@link Long#MAX_VALUE} 表示无界
     * @since 2026
     */
    record Sequence(Type element, long minLength, long maxLength) implements Type {

        /** 无界长度上界。 */
        public static final long UNBOUNDED = Long.MAX_VALUE;

        /**
         * 校验长度区间合法,并拒绝未归一化的 Never 元素序列。
         *
         * @throws IllegalArgumentException 长度为负、下界大于上界,或元素为 {@link Never} 而长度上界大于 零时——后者只含空数组,须先经
         *     {@code Types.sequence} 归一
         */
        public Sequence {
            if (minLength < 0 || maxLength < 0 || minLength > maxLength) {
                throw new IllegalArgumentException(
                        "invalid length interval [" + minLength + ", " + maxLength + "]");
            }
            if (element instanceof Never && minLength > 0) {
                throw new IllegalArgumentException(
                        "Sequence(never, min>0) has no legal value; normalize to Never via Types.sequence");
            }
            if (element instanceof Never && maxLength > 0) {
                throw new IllegalArgumentException(
                        "Sequence(never, 0, max>0) only contains the empty array; normalize via Types.sequence");
            }
        }
    }

    /**
     * 字段存在性:必有 / 可能有。OPTIONAL 表示可能不存在,不是“存在且值为 null”。
     *
     * @since 2026
     */
    enum Presence {
        REQUIRED,
        OPTIONAL
    }

    /**
     * 对象字段:值类型 + 存在性。
     *
     * @param type 字段出现时的取值上界
     * @param presence 字段是否必须出现;OPTIONAL 不代表值为 null
     * @since 2026
     */
    record Field(Type type, Presence presence) {
        /**
         * 校验字段组件非空。
         *
         * @throws IllegalArgumentException 任一组件为 null 时
         */
        public Field {
            if (type == null || presence == null) {
                throw new IllegalArgumentException("field requires type and presence");
            }
        }

        static Field required(Type type) {
            return new Field(type, Presence.REQUIRED);
        }

        static Field optional(Type type) {
            return new Field(type, Presence.OPTIONAL);
        }
    }

    /**
     * 对象额外字段约束。{@link Extra#closed()} 表示不允许额外字段,等价于额外字段值类型为 {@link Never} 的开放约束; 显式列出的字段不再受 extra
     * 重复限制。
     *
     * @param valueType 未显式列出的字段的取值上界;{@link Never} 表示不允许额外字段
     * @since 2026
     */
    record Extra(Type valueType) {
        /**
         * 校验额外字段值类型存在。
         *
         * @throws IllegalArgumentException {@code valueType} 为 null 时——缺少尾部约束无法解释对象值
         */
        public Extra {
            if (valueType == null) {
                throw new IllegalArgumentException("extra requires a value type");
            }
        }

        /**
         * 关闭的额外字段约束:对象只允许显式列出的字段。
         *
         * @return 值类型为 {@link Never} 的约束
         */
        public static Extra closed() {
            return new Extra(Never.INSTANCE);
        }

        /**
         * 开放的额外字段约束。
         *
         * @param valueType 未列出字段的取值上界
         * @return 包装给定值类型的约束;值类型为 {@link Never} 时等价于关闭
         */
        public static Extra open(Type valueType) {
            return new Extra(valueType);
        }

        /**
         * 判断额外字段是否关闭。
         *
         * @return 值类型为 {@link Never} 时为 {@code true}
         */
        public boolean isClosed() {
            return valueType instanceof Never;
        }
    }

    /**
     * 结构对象:字段(必有/可能有)+ 额外字段约束。
     *
     * <p>未知字段不从对象中删除——字段保留、类型可为 {@link Unknown};确定缺失与可能缺失由 presence 区分。
     *
     * @param fields 字段名到字段类型的映射;构造时按传入顺序复制为不可变 map
     * @param extra 额外字段约束;{@link Never} 值类型表示关闭
     * @since 2026
     */
    record RecordType(Map<String, Field> fields, Extra extra) implements Type {
        /**
         * 复制字段映射并校验额外字段约束非空。
         *
         * @throws IllegalArgumentException {@code extra} 为 null 时——无法推断尾部行为
         */
        public RecordType {
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            if (extra == null) {
                throw new IllegalArgumentException("record requires an extra constraint");
            }
        }
    }

    /**
     * 值层面的联合(非空;构造后至少含一个分支)。
     *
     * @param alternatives 分支列表;构造时复制为不可变列表
     * @since 2026
     */
    record Union(List<Type> alternatives) implements Type {
        /**
         * 复制分支列表并校验联合非空。
         *
         * @throws IllegalArgumentException 分支列表为空时——空联合同等于 {@link Never},应直接使用 Never
         */
        public Union {
            alternatives = List.copyOf(alternatives);
            if (alternatives.isEmpty()) {
                throw new IllegalArgumentException("union requires at least one alternative");
            }
        }
    }

    /**
     * 组件值(名义)。
     *
     * @param name 组件名;按名义参与类型身份,不做格式或非空校验
     * @since 2026
     */
    record Component(String name) implements Type {}

    /**
     * 模板值:只描述形状;body 与环境在分析产物中,不进类型身份。
     *
     * @since 2026
     */
    enum Template implements Type {
        INSTANCE
    }

    /**
     * 诊断与消息用的稳定展示名;不参与类型身份。
     *
     * @return 面向用户展示的类型名;同一类型结构稳定,不同结构不会得到相同展示名
     */
    default String displayName() {
        return switch (this) {
            case Never ignored -> "never";
            case Unknown ignored -> "unknown";
            case Scalar scalar -> scalar.kind().displayName();
            case StringLiterals literals ->
                    literals.values().stream()
                            .sorted()
                            .map(v -> "\"" + v + "\"")
                            .reduce((a, b) -> a + " | " + b)
                            .orElse("string");
            case Sequence sequence -> {
                String cardinality =
                        sequence.minLength() == sequence.maxLength()
                                ? String.valueOf(sequence.minLength())
                                : sequence.minLength()
                                        + ".."
                                        + (sequence.maxLength() == Sequence.UNBOUNDED
                                                ? "*"
                                                : sequence.maxLength());
                yield sequence.element().displayName() + "[" + cardinality + "]";
            }
            case RecordType record ->
                    record.extra().isClosed() && record.fields().isEmpty() ? "object{}" : "object";
            case Union union ->
                    union.alternatives().stream()
                            .map(Type::displayName)
                            .distinct()
                            .reduce((a, b) -> a + " | " + b)
                            .orElse("never");
            case Component component -> "Component<" + component.name() + ">";
            case Template ignored -> "template";
        };
    }
}
