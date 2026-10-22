package com.huawei.cloudsop.genui.core.validation.type;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Proven value types used by Java preflight validation. */
public sealed interface ValueType
        permits ValueType.Primitive, ValueType.ArrayType, ValueType.ObjectType, ValueType.UnionType,
        ValueType.ComponentType, ValueType.TemplateType, ValueType.EmptyArrayType, ValueType.StringDomainType {

    /** Stable human/model-facing type name. */
    String displayName();

    enum PrimitiveKind {
        STRING("string"), NUMBER("number"), BOOLEAN("boolean"), NULL("null");

        private final String displayName;

        PrimitiveKind(String displayName) {
            this.displayName = displayName;
        }
    }

    record Primitive(PrimitiveKind kind) implements ValueType {
        @Override
        public String displayName() {
            return kind.displayName;
        }
    }

    /**
     * 数组类型。{@code elementPresenceProven} 是元素存在证据：为真表示已观察/已证明至少一个元素存在 （直接数据观察、数组字面量，或经 Sort 等声明保留证据的运算传播）。Filter
     * 等子集运算删除该保证， 但类型上界不变——证据失效不是类型未知。证据属于类型身份（参与 equals/hashCode）， 相同元素类型、不同证据的数组不互换检查结论。
     */
    record ArrayType(ValueType elementType, boolean elementPresenceProven) implements ValueType {
        public ArrayType(ValueType elementType) {
            this(elementType, false);
        }

        /** 返回元素类型不变、证据按给定值调整的类型。 */
        public ArrayType withElementPresence(boolean present) {
            return present == elementPresenceProven ? this : new ArrayType(elementType, present);
        }

        @Override
        public String displayName() {
            return elementType.displayName() + "[]";
        }
    }

    record ObjectType(Map<String, ValueType> fields) implements ValueType {
        public ObjectType {
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }

        @Override
        public String displayName() {
            return "object";
        }
    }

    record UnionType(List<ValueType> alternatives) implements ValueType {
        public UnionType {
            alternatives = List.copyOf(alternatives);
        }

        @Override
        public String displayName() {
            return alternatives.stream().map(ValueType::displayName).distinct().sorted().reduce((a, b) -> a + " | " + b)
                    .orElse("never");
        }
    }

    record ComponentType(String name) implements ValueType {
        @Override
        public String displayName() {
            return "Component<" + name + ">";
        }
    }

    record TemplateType(ValueType bodyType) implements ValueType {
        @Override
        public String displayName() {
            return "Template<" + bodyType.displayName() + ">";
        }
    }

    record EmptyArrayType() implements ValueType {
        @Override
        public String displayName() {
            return "empty[]";
        }
    }

    /** Bounded observed string literals from a concrete Render Data Model or DSL literal. */
    record StringDomainType(java.util.Set<String> values) implements ValueType {
        public StringDomainType {
            values = java.util.Set.copyOf(values);
        }

        @Override
        public String displayName() {
            return "string";
        }
    }
}
