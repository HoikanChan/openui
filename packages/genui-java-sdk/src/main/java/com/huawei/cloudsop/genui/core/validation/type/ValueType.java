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

    record ArrayType(ValueType elementType) implements ValueType {
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
