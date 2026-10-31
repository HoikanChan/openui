/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import java.util.List;
import java.util.Set;

/**
 * 检查要求(#77 第 5.3 节):组件 schema 与 builtin 参数编译出的期望,不是第二套值类型。
 *
 * <p>类型包含只是检查要求的一部分;required、数量关系、anyOf、oneOf 等谓词保留自己的逻辑。 {@link Unsupported} 表示没有完成的要求,不能当作 {@code
 * Accept(Unknown)}。
 *
 * @since 2026
 */
public sealed interface Expectation
        permits Expectation.Accept,
                Expectation.RequireField,
                Expectation.ConditionalField,
                Expectation.AllOf,
                Expectation.AnyOf,
                Expectation.ExactlyOne,
                Expectation.EnumValues,
                Expectation.SequenceOf,
                Expectation.Unsupported,
                SchemaCompiler.ExtraClosed,
                SchemaCompiler.ExtraConstraint {

    /**
     * 数组要求:每个元素满足嵌套要求。空数组满足任何逐元素要求;元素检查不得忽略空数组可能性 (#77 第 6.4 节)。
     *
     * @param elementExpectation 每个元素的嵌套要求
     * @since 2026
     */
    record SequenceOf(Expectation elementExpectation) implements Expectation {

        /**
         * 紧凑构造方法:拒绝 null 元素要求。
         *
         * @throws IllegalArgumentException 元素要求为 null 时
         */
        public SequenceOf {
            if (elementExpectation == null) {
                throw new IllegalArgumentException("sequenceOf requires an element expectation");
            }
        }
    }

    /**
     * 值类型接受要求。{@code Accept(Unknown)} 表示契约明确接受任意值,可以被证明满足。
     *
     * @param type 期望的值类型上界
     * @since 2026
     */
    record Accept(Type type) implements Expectation {

        /**
         * 紧凑构造方法:拒绝 null 类型。
         *
         * @throws IllegalArgumentException 类型为 null 时
         */
        public Accept {
            if (type == null) {
                throw new IllegalArgumentException("accept requires a type");
            }
        }
    }

    /**
     * 对象字段要求:字段必须存在且值满足嵌套要求。
     *
     * @param name 字段名
     * @param expectation 字段值的嵌套要求
     * @since 2026
     */
    record RequireField(String name, Expectation expectation) implements Expectation {

        /**
         * 紧凑构造方法:要求字段名与嵌套要求均非 null。
         *
         * @throws IllegalArgumentException 字段名或嵌套要求为 null 时
         */
        public RequireField {
            if (name == null || expectation == null) {
                throw new IllegalArgumentException("requireField requires name and expectation");
            }
        }
    }

    /**
     * 对象字段要求:字段若出现则值须满足嵌套要求(properties 非 required 字段)。
     *
     * @param name 字段名
     * @param expectation 字段值的嵌套要求
     * @since 2026
     */
    record ConditionalField(String name, Expectation expectation) implements Expectation {

        /**
         * 紧凑构造方法:要求字段名与嵌套要求均非 null。
         *
         * @throws IllegalArgumentException 字段名或嵌套要求为 null 时
         */
        public ConditionalField {
            if (name == null || expectation == null) {
                throw new IllegalArgumentException(
                        "conditionalField requires name and expectation");
            }
        }
    }

    /**
     * 全部子要求须同时满足。
     *
     * @param expectations 子要求列表,按顺序逐一检查
     * @since 2026
     */
    record AllOf(List<Expectation> expectations) implements Expectation {

        /** 紧凑构造方法:复制为不可变列表。 */
        public AllOf {
            expectations = List.copyOf(expectations);
        }
    }

    /**
     * 任一子要求满足即可(同一个值上组合,见 #77 第 6.4 节)。
     *
     * @param expectations 子要求列表,至少一项
     * @since 2026
     */
    record AnyOf(List<Expectation> expectations) implements Expectation {

        /**
         * 紧凑构造方法:复制为不可变列表并拒绝空列表。
         *
         * @throws IllegalArgumentException 子要求列表为空时
         */
        public AnyOf {
            expectations = List.copyOf(expectations);
            if (expectations.isEmpty()) {
                throw new IllegalArgumentException("anyOf requires at least one expectation");
            }
        }
    }

    /**
     * 恰好一个子要求满足;不得当成普通联合。
     *
     * @param expectations 子要求列表,至少两项
     * @since 2026
     */
    record ExactlyOne(List<Expectation> expectations) implements Expectation {

        /**
         * 紧凑构造方法:复制为不可变列表并拒绝少于两项。
         *
         * @throws IllegalArgumentException 子要求少于两项时
         */
        public ExactlyOne {
            expectations = List.copyOf(expectations);
            if (expectations.size() < 2) {
                throw new IllegalArgumentException("exactlyOne requires at least two expectations");
            }
        }
    }

    /**
     * 字符串枚举域要求。
     *
     * @param values 允许的字符串字面值,至少一项
     * @since 2026
     */
    record EnumValues(Set<String> values) implements Expectation {

        /**
         * 紧凑构造方法:复制为不可变集合并拒绝空集合。
         *
         * @throws IllegalArgumentException 字面值集合为空时
         */
        public EnumValues {
            values = Set.copyOf(values);
            if (values.isEmpty()) {
                throw new IllegalArgumentException("enum requires at least one value");
            }
        }
    }

    /**
     * 当前子集无法编译的 schema 要求:保留结构位置,不编译成“不限制”。
     *
     * @param schemaPath 契约 schema 内的位置
     * @param keyword 未支持的关键词(如 oneOf、min、pattern)
     * @since 2026
     */
    record Unsupported(String schemaPath, String keyword) implements Expectation {}
}
