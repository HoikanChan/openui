/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 组件 propsSchema 子集编译为 {@link Expectation}(#77 第 8.5 节)。
 *
 * <p>第一版完整支持 {@code type}、{@code properties}、{@code required}、{@code items}、 {@code
 * additionalProperties}、字符串 {@code enum}、{@code anyOf} 和 {@code component} 扩展。 其他验证要求({@code
 * oneOf}、{@code min/max}、{@code minLength/maxLength}、{@code pattern} 等)编译为 {@link
 * Expectation.Unsupported} 保留结构,不能删去后报告完整 MATCH;同一 schema 中已支持部分仍继续检查。
 *
 * @since 2026
 */
public final class SchemaCompiler {

    /** 当前支持的模式关键词;其余对象键按未支持的验证要求处理(description/title 属注解,跳过)。 */
    private static final java.util.Set<String> ANNOTATION_KEYWORDS =
            java.util.Set.of("description", "title", "default", "examples");

    /** 已支持关键词;未在表内且非注解的关键词一律 Unsupported。 */
    private static final java.util.Set<String> SUPPORTED_KEYWORDS =
            java.util.Set.of(
                    "type",
                    "properties",
                    "required",
                    "items",
                    "additionalProperties",
                    "enum",
                    "anyOf",
                    "component");

    private SchemaCompiler() {}

    /**
     * 编译单参数 schema(propsSchema.properties[paramName] 的 JSON 视图)。
     *
     * @param schema 参数级 schema map(JSON 对象视图)
     * @param schemaPath 该 schema 在契约中的路径(供 Unsupported 定位)
     * @return 编译出的检查要求
     */
    public static Expectation compileParam(Map<String, Object> schema, String schemaPath) {
        return compile(schema, schemaPath);
    }

    @SuppressWarnings("unchecked")
    private static Expectation compile(Map<String, Object> schema, String schemaPath) {
        if (schema == null || schema.isEmpty()) {
            return new Expectation.Accept(Type.Unknown.INSTANCE);
        }
        List<Expectation> requirements = new ArrayList<>();
        // Schema siblings are conjunctive, including component extensions and unsupported keywords.
        Object component = schema.get("component");
        if (component instanceof String componentName) {
            requirements.add(new Expectation.Accept(new Type.Component(componentName)));
        }
        Object anyOf = schema.get("anyOf");
        if (anyOf instanceof List<?> alternatives) {
            List<Expectation> compiled = new ArrayList<>();
            int index = 0;
            for (Object alternative : alternatives) {
                compiled.add(
                        alternative instanceof Map<?, ?> map
                                ? compile((Map<String, Object>) map, schemaPath + "/anyOf/" + index)
                                : new Expectation.Unsupported(
                                        schemaPath + "/anyOf/" + index, "nonObjectSchema"));
                index++;
            }
            requirements.add(
                    compiled.isEmpty()
                            ? new Expectation.Accept(Type.Never.INSTANCE)
                            : compiled.size() == 1
                                    ? compiled.getFirst()
                                    : new Expectation.AnyOf(compiled));
        }
        Object type = schema.get("type");
        if (type instanceof String typeName) {
            requirements.addAll(
                    switch (typeName) {
                        case "string" ->
                                List.of(
                                        new Expectation.Accept(
                                                Types.scalar(Type.ScalarKind.STRING)));
                        case "number" ->
                                List.of(
                                        new Expectation.Accept(
                                                Types.scalar(Type.ScalarKind.NUMBER)));
                        case "boolean" ->
                                List.of(
                                        new Expectation.Accept(
                                                Types.scalar(Type.ScalarKind.BOOLEAN)));
                        case "null" ->
                                List.of(new Expectation.Accept(Types.scalar(Type.ScalarKind.NULL)));
                        case "integer" ->
                                List.of(new Expectation.Unsupported(schemaPath, "integer"));
                        case "any" -> List.of(new Expectation.Accept(Type.Unknown.INSTANCE));
                        case "array" -> List.of(compileArray(schema, schemaPath));
                        case "object" -> compileObject(schema, schemaPath);
                        default ->
                                List.of(
                                        new Expectation.Unsupported(
                                                schemaPath, "type:" + typeName));
                    });
        } else if (schema.containsKey("properties") || schema.containsKey("required")) {
            requirements.addAll(compileObject(schema, schemaPath));
        } else if (schema.containsKey("items")) {
            requirements.add(compileArray(schema, schemaPath));
        }
        Object enumValues = schema.get("enum");
        if (enumValues instanceof List<?> values
                && values.stream().allMatch(String.class::isInstance)
                && !values.isEmpty()) {
            java.util.Set<String> domain = new java.util.LinkedHashSet<>();
            values.forEach(value -> domain.add((String) value));
            requirements.add(new Expectation.EnumValues(domain));
        } else if (enumValues != null) {
            requirements.add(new Expectation.Unsupported(schemaPath, "enum"));
        }
        // 未支持的验证要求保留为 Unsupported,不编译成“不限制”。
        for (String keyword : schema.keySet()) {
            if (SUPPORTED_KEYWORDS.contains(keyword) || ANNOTATION_KEYWORDS.contains(keyword)) {
                continue;
            }
            requirements.add(new Expectation.Unsupported(schemaPath, keyword));
        }
        if (requirements.isEmpty()) {
            return new Expectation.Accept(Type.Unknown.INSTANCE);
        }
        return requirements.size() == 1
                ? requirements.getFirst()
                : new Expectation.AllOf(requirements);
    }

    private static Expectation compileArray(Map<String, Object> schema, String schemaPath) {
        Object items = schema.get("items");
        if (items == null) {
            // 无 items 声明:数组形状 + 任意元素。
            return new Expectation.Accept(Types.unknownSequence());
        }
        if (!(items instanceof Map<?, ?> itemsMap)) {
            return new Expectation.AllOf(
                    List.of(
                            new Expectation.Accept(Types.unknownSequence()),
                            new Expectation.Unsupported(schemaPath + "/items", "nonObjectSchema")));
        }
        @SuppressWarnings("unchecked")
        Expectation element = compile((Map<String, Object>) itemsMap, schemaPath + "/items");
        return new Expectation.SequenceOf(element);
    }

    private static List<Expectation> compileObject(Map<String, Object> schema, String schemaPath) {
        List<Expectation> requirements = new ArrayList<>();
        requirements.add(new Expectation.Accept(Types.openRecord()));
        Object properties = schema.get("properties");
        if (properties instanceof Map<?, ?> propertyMap) {
            for (Map.Entry<?, ?> entry : propertyMap.entrySet()) {
                String name = String.valueOf(entry.getKey());
                if (!(entry.getValue() instanceof Map<?, ?> fieldSchema)) {
                    requirements.add(
                            new Expectation.Unsupported(
                                    schemaPath + "/properties/" + name, "nonObjectSchema"));
                    continue;
                }
                @SuppressWarnings("unchecked")
                Expectation fieldExpectation =
                        compile(
                                (Map<String, Object>) fieldSchema,
                                schemaPath + "/properties/" + name);
                // properties 声明的是“字段若出现则须满足”,不是必有;按字段位置映射,不整体套在对象上。
                requirements.add(new Expectation.ConditionalField(name, fieldExpectation));
            }
        }
        Object required = schema.get("required");
        if (required instanceof List<?> names) {
            for (Object name : names) {
                String fieldName = String.valueOf(name);
                // 值要求已由 properties 的 ConditionalField 覆盖(含属性 schema 非对象时的 Unsupported);
                // required 只补充字段存在性,不重复编译同一属性 schema。
                requirements.add(
                        new Expectation.RequireField(
                                fieldName, new Expectation.Accept(Type.Unknown.INSTANCE)));
            }
        }
        Object additional = schema.get("additionalProperties");
        if (additional instanceof Boolean allowed && !allowed) {
            requirements.add(
                    new ExtraClosed(schemaPath + "/additionalProperties", declaredFields(schema)));
        } else if (additional instanceof Map<?, ?> additionalSchema) {
            @SuppressWarnings("unchecked")
            Expectation compiled =
                    compile(
                            (Map<String, Object>) additionalSchema,
                            schemaPath + "/additionalProperties");
            requirements.add(new ExtraConstraint(compiled, declaredFields(schema)));
        }
        return requirements;
    }

    /** 额外字段关闭要求(内部标记,分析层映射为结构检查)。 */
    public record ExtraClosed(String schemaPath, java.util.Set<String> declaredFields)
            implements Expectation {
        public ExtraClosed {
            declaredFields = java.util.Set.copyOf(declaredFields);
        }
    }

    /** 额外字段受约束要求(内部标记)。 */
    public record ExtraConstraint(Expectation expectation, java.util.Set<String> declaredFields)
            implements Expectation {
        public ExtraConstraint {
            declaredFields = java.util.Set.copyOf(declaredFields);
        }
    }

    private static java.util.Set<String> declaredFields(Map<String, Object> schema) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        if (schema.get("properties") instanceof Map<?, ?> properties) {
            properties.keySet().forEach(key -> names.add(String.valueOf(key)));
        }
        return names;
    }
}
