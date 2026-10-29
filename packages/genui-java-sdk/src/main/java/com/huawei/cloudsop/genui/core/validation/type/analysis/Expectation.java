/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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

    /** 数组要求:每个元素满足嵌套要求。空数组满足任何逐元素要求;元素检查不得忽略空数组可能性 (#77 第 6.4 节)。 */
    record SequenceOf(Expectation elementExpectation) implements Expectation {
        public SequenceOf {
            if (elementExpectation == null) {
                throw new IllegalArgumentException("sequenceOf requires an element expectation");
            }
        }
    }

    /** 值类型接受要求。{@code Accept(Unknown)} 表示契约明确接受任意值,可以被证明满足。 */
    record Accept(Type type) implements Expectation {
        public Accept {
            if (type == null) {
                throw new IllegalArgumentException("accept requires a type");
            }
        }
    }

    /** 对象字段要求:字段必须存在且值满足嵌套要求。 */
    record RequireField(String name, Expectation expectation) implements Expectation {
        public RequireField {
            if (name == null || expectation == null) {
                throw new IllegalArgumentException("requireField requires name and expectation");
            }
        }
    }

    /** 对象字段要求:字段若出现则值须满足嵌套要求(properties 非 required 字段)。 */
    record ConditionalField(String name, Expectation expectation) implements Expectation {
        public ConditionalField {
            if (name == null || expectation == null) {
                throw new IllegalArgumentException(
                        "conditionalField requires name and expectation");
            }
        }
    }

    /** 全部子要求须同时满足。 */
    record AllOf(List<Expectation> expectations) implements Expectation {
        public AllOf {
            expectations = List.copyOf(expectations);
        }
    }

    /** 任一子要求满足即可(同一个值上组合,见 #77 第 6.4 节)。 */
    record AnyOf(List<Expectation> expectations) implements Expectation {
        public AnyOf {
            expectations = List.copyOf(expectations);
            if (expectations.isEmpty()) {
                throw new IllegalArgumentException("anyOf requires at least one expectation");
            }
        }
    }

    /** 恰好一个子要求满足;不得当成普通联合。 */
    record ExactlyOne(List<Expectation> expectations) implements Expectation {
        public ExactlyOne {
            expectations = List.copyOf(expectations);
            if (expectations.size() < 2) {
                throw new IllegalArgumentException("exactlyOne requires at least two expectations");
            }
        }
    }

    /** 字符串枚举域要求。 */
    record EnumValues(Set<String> values) implements Expectation {
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
     */
    record Unsupported(String schemaPath, String keyword) implements Expectation {}
}
