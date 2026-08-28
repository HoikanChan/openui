package com.huawei.cloudsop.genui.core.validation.type;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Supported component-contract constraints, kept separate from proven program value types. */
sealed interface ExpectedType
        permits ExpectedType.PrimitiveConstraint, ExpectedType.ArrayConstraint, ExpectedType.ObjectConstraint,
        ExpectedType.UnionConstraint, ExpectedType.ComponentConstraint, ExpectedType.EnumConstraint {

    String displayName();

    record PrimitiveConstraint(ValueType.PrimitiveKind kind) implements ExpectedType {
        @Override
        public String displayName() {
            return new ValueType.Primitive(kind).displayName();
        }
    }

    /** A null element constraint means the Contract declared an unconstrained {@code items: any}. */
    record ArrayConstraint(ExpectedType elementConstraint) implements ExpectedType {
        @Override
        public String displayName() {
            return elementConstraint == null ? "array" : elementConstraint.displayName() + "[]";
        }
    }

    record ObjectConstraint(Map<String, ExpectedType> fields) implements ExpectedType {
        public ObjectConstraint {
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }

        @Override
        public String displayName() {
            return "object";
        }
    }

    record UnionConstraint(List<ExpectedType> alternatives) implements ExpectedType {
        public UnionConstraint {
            alternatives = List.copyOf(alternatives);
        }

        @Override
        public String displayName() {
            return alternatives.stream().map(ExpectedType::displayName).distinct().reduce((a, b) -> a + " | " + b)
                    .orElse("never");
        }
    }

    record ComponentConstraint(String name) implements ExpectedType {
        @Override
        public String displayName() {
            return "Component<" + name + ">";
        }
    }

    record EnumConstraint(Set<String> values) implements ExpectedType {
        public EnumConstraint {
            values = Set.copyOf(values);
        }

        @Override
        public String displayName() {
            return values.stream().sorted().map(value -> "\"" + value + "\"").reduce((a, b) -> a + " | " + b)
                    .orElse("string");
        }
    }
}
