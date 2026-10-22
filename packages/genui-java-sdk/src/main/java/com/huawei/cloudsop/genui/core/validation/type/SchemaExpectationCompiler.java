/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import com.huawei.cloudsop.genui.core.validation.type.Expectation.Accept;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.AllOf;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.AnyOf;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.ExactlyOne;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.RequireField;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.Unsupported;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.Primitive;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.PrimitiveKind;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.StringDomainType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles a Generation Contract schema fragment into an {@link Expectation} (#82), per design §8.5.
 *
 * <p>
 * The supported subset is compiled completely: {@code type}, {@code properties}, {@code required}, {@code items},
 * {@code additionalProperties}, string {@code enum}, {@code anyOf} and the {@code component} extension. Anything else
 * ({@code oneOf}→{@link ExactlyOne}, {@code allOf}, {@code integer}, {@code minLength}/{@code maxLength}/{@code pattern},
 * unknown validation keywords) is preserved as an {@link Unsupported} requirement rather than dropped — a schema with
 * both supported and unsupported parts still checks the supported part, but never reports a fabricated full MATCH for
 * the dropped constraint.
 */
final class SchemaExpectationCompiler {

    /** Validation keywords this SDK fully supports; anything else on a schema node is preserved as Unsupported. */
    private static final Set<String> SUPPORTED =
            Set.of("type", "properties", "required", "items", "additionalProperties", "enum", "anyOf", "oneOf",
                    "allOf", "component");

    /** Pure annotations that carry no validation obligation and need no Unsupported marker. */
    private static final Set<String> ANNOTATIONS = Set.of("description", "title", "default", "examples", "$schema");

    Expectation compile(Object rawSchema) {
        return compile(rawSchema, "");
    }

    private Expectation compile(Object rawSchema, String path) {
        if (!(rawSchema instanceof Map<?, ?> schema)) {
            return new Unsupported(path, "schema");
        }
        List<Expectation> requirements = new ArrayList<>();

        // Composite combinators first: they keep their own quantifier logic rather than flattening into a union.
        if (schema.get("anyOf") instanceof List<?> variants) {
            List<Expectation> candidates = new ArrayList<>();
            for (int i = 0; i < variants.size(); i++) {
                candidates.add(compile(variants.get(i), path + "/anyOf/" + i));
            }
            requirements.add(new AnyOf(candidates));
        }
        if (schema.get("oneOf") instanceof List<?> variants) {
            // oneOf is ExactlyOne — never treated as a plain union.
            List<Expectation> candidates = new ArrayList<>();
            for (int i = 0; i < variants.size(); i++) {
                candidates.add(compile(variants.get(i), path + "/oneOf/" + i));
            }
            requirements.add(new ExactlyOne(candidates));
        }
        if (schema.get("allOf") instanceof List<?> parts) {
            for (int i = 0; i < parts.size(); i++) {
                requirements.add(compile(parts.get(i), path + "/allOf/" + i));
            }
        }
        if (schema.get("component") != null) {
            requirements.add(new Accept(new ValueType.ComponentType(String.valueOf(schema.get("component")))));
        }

        String type = schema.get("type") == null ? null : String.valueOf(schema.get("type"));
        compileByType(schema, type, path, requirements);

        // required is honoured even for fields absent from `properties` (an unconstrained but mandatory field).
        if (schema.get("required") instanceof List<?> required) {
            for (Object name : required) {
                String field = String.valueOf(name);
                Expectation fieldExpectation = fieldExpectation(schema, field, path);
                requirements.add(new RequireField(field, fieldExpectation));
            }
        }

        // Preserve every unsupported validation keyword; never drop it into a fake MATCH.
        for (Object keyObj : schema.keySet()) {
            String key = String.valueOf(keyObj);
            if (!SUPPORTED.contains(key) && !ANNOTATIONS.contains(key)) {
                requirements.add(new Unsupported(path, key));
            }
        }
        return combine(requirements);
    }

    private void compileByType(Map<?, ?> schema, String type, String path, List<Expectation> requirements) {
        if (type == null) {
            return;
        }
        switch (type) {
            case "string" -> {
                if (schema.get("enum") instanceof List<?> values) {
                    Set<String> literals = new LinkedHashSet<>();
                    values.forEach(v -> literals.add(String.valueOf(v)));
                    requirements.add(new Accept(new StringDomainType(literals)));
                } else {
                    requirements.add(new Accept(new Primitive(PrimitiveKind.STRING)));
                }
            }
            case "number" -> requirements.add(new Accept(new Primitive(PrimitiveKind.NUMBER)));
            case "integer" -> {
                requirements.add(new Accept(new Primitive(PrimitiveKind.NUMBER)));
                requirements.add(new Expectation.Predicate("integer", List.of()));
            }
            case "boolean" -> requirements.add(new Accept(new Primitive(PrimitiveKind.BOOLEAN)));
            case "null" -> requirements.add(new Accept(new Primitive(PrimitiveKind.NULL)));
            case "array" -> {
                requirements.add(new Accept(new ValueType.ArrayType(ValueType.unknown())));
                Object itemSchema = schema.get("items");
                if (itemSchema != null) {
                    requirements.add(new ElementRequirement(compile(itemSchema, path + "/items")));
                }
            }
            case "object" -> {
                if (schema.get("properties") instanceof Map<?, ?> properties) {
                    for (Map.Entry<?, ?> entry : properties.entrySet()) {
                        String field = String.valueOf(entry.getKey());
                        requirements.add(new RequireField(field, compile(entry.getValue(), path + "/" + field)));
                    }
                }
                compileAdditionalProperties(schema, path, requirements);
            }
            case "any" -> requirements.add(new Accept(ValueType.unknown()));
            default -> requirements.add(new Unsupported(path, "type:" + type));
        }
    }

    private void compileAdditionalProperties(Map<?, ?> schema, String path, List<Expectation> requirements) {
        Object additional = schema.get("additionalProperties");
        if (additional == null) {
            return; // undeclared -> extra fields allowed (open object), no requirement
        }
        if (additional instanceof Boolean allowed) {
            if (!allowed) {
                requirements.add(new Expectation.Predicate("closedObject", List.of()));
            }
            return;
        }
        requirements.add(new ExtraFieldsRequirement(compile(additional, path + "/additionalProperties")));
    }

    private Expectation fieldExpectation(Map<?, ?> schema, String field, String path) {
        if (schema.get("properties") instanceof Map<?, ?> properties && properties.containsKey(field)) {
            return compile(properties.get(field), path + "/" + field);
        }
        return new Accept(ValueType.unknown()); // required but value-unconstrained
    }

    private static Expectation combine(List<Expectation> requirements) {
        if (requirements.isEmpty()) {
            return new Accept(ValueType.unknown());
        }
        if (requirements.size() == 1) {
            return requirements.get(0);
        }
        return new AllOf(requirements);
    }

    /** A requirement on every element of an array value. */
    record ElementRequirement(Expectation element) implements Expectation {
        @Override
        public String displayName() {
            return element.displayName() + "[]";
        }
    }

    /** A requirement on every additional (unlisted) field of an object value. */
    record ExtraFieldsRequirement(Expectation extra) implements Expectation {
        @Override
        public String displayName() {
            return "additionalProperties: " + extra.displayName();
        }
    }
}
