package com.huawei.cloudsop.genui.core.validation.type;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Proven value types used by Java preflight validation. */
public sealed interface ValueType permits ValueType.Primitive, ValueType.ArrayType, ValueType.ObjectType,
        ValueType.UnionType, ValueType.ComponentType, ValueType.TemplateType, ValueType.EmptyArrayType,
        ValueType.StringDomainType, ValueType.UnknownType, ValueType.NeverType {

    /** Stable human/model-facing type name. */
    String displayName();

    /** The Unknown type: information is insufficient; any value is possible. Distinct from a union and from Never. */
    static UnknownType unknown() {
        return UnknownType.INSTANCE;
    }

    /**
     * The Never type: no legal value exists. Never produces evidence (e.g. a zero-length array is not a Never value).
     */
    static NeverType never() {
        return NeverType.INSTANCE;
    }

    /** Unknown = information insufficient (#82 minimal seam; full evidence identity arrives with #80's Fact layer). */
    enum UnknownType implements ValueType {
        INSTANCE;

        @Override
        public String displayName() {
            return "unknown";
        }
    }

    /** Never = no legal value. Kept distinct from a known-empty array (which is a real, observable value). */
    enum NeverType implements ValueType {
        INSTANCE;

        @Override
        public String displayName() {
            return "never";
        }
    }

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
