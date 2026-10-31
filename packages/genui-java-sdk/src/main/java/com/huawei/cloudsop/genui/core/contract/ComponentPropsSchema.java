/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import com.huawei.cloudsop.genui.core.GenerationSdkException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组件 propsSchema 的加载期校验与提示词签名格式化。
 *
 * <p>校验只覆盖未携带 {@code x-openui-signature} 的新式模式：{@code type} 必须为 {@code object}，{@code required}
 * 中的每个名字必须已在 {@code properties} 中声明， 且必填字段必须全部排在可选字段之前。
 *
 * @since 2026
 */
public final class ComponentPropsSchema {
    private ComponentPropsSchema() {}

    /**
     * 校验组件 propsSchema 的结构约束。
     *
     * <p>空模式或携带 {@code x-openui-signature} 的旧式模式直接放行。
     *
     * @param componentName 组件名，仅用于错误消息
     * @param component 待校验的组件提示词规格
     * @throws GenerationSdkException 当 {@code type} 非 {@code object}、{@code required}
     *     引用未声明字段，或必填字段出现在可选字段之后时
     */
    public static void validate(String componentName, ComponentPromptSpec component) {
        Map<String, Object> schema = component.propsSchema();
        if (schema.isEmpty() || schema.containsKey("x-openui-signature")) {
            return;
        }

        Object type = schema.get("type");
        if (type != null && !type.equals("object")) {
            throw new GenerationSdkException(
                    "propsSchema for " + componentName + " must have type object");
        }

        Map<String, Object> properties = properties(componentName, schema);
        Set<String> required = required(componentName, schema);
        for (String requiredName : required) {
            if (!properties.containsKey(requiredName)) {
                throw new GenerationSdkException(
                        "propsSchema for "
                                + componentName
                                + " requires unknown prop "
                                + requiredName);
            }
        }

        boolean sawOptional = false;
        for (String propName : properties.keySet()) {
            if (required.contains(propName)) {
                if (sawOptional) {
                    throw new GenerationSdkException(
                            "propsSchema for "
                                    + componentName
                                    + " required props must precede optional props");
                }
            } else {
                sawOptional = true;
            }
        }
    }

    /**
     * 生成组件的提示词签名；优先返回旧版 {@code x-openui-signature}。
     *
     * <p>否则按 propsSchema 的字段顺序拼出 {@code Name(prop: type, optional?: type)}， 可选字段以 {@code ?} 标记。
     *
     * @param componentName 组件名
     * @param component 组件提示词规格
     * @return 该组件的调用签名
     */
    public static String formatSignature(String componentName, ComponentPromptSpec component) {
        String legacy = component.signature();
        if (legacy != null && !legacy.isBlank()) {
            return legacy;
        }

        Map<String, Object> schema = component.propsSchema();
        Map<String, Object> properties = properties(componentName, schema);
        Set<String> required = required(componentName, schema);

        ArrayList<String> args = new ArrayList<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String propName = entry.getKey();
            String optional = required.contains(propName) ? "" : "?";
            args.add(propName + optional + ": " + formatType(entry.getValue()));
        }
        return componentName + "(" + String.join(", ", args) + ")";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(
            String componentName, Map<String, Object> schema) {
        Object value = schema.get("properties");
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new GenerationSdkException(
                    "propsSchema.properties for " + componentName + " must be an object");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static Set<String> required(String componentName, Map<String, Object> schema) {
        Object value = schema.get("required");
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new GenerationSdkException(
                    "propsSchema.required for " + componentName + " must be an array");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object item : list) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private static String formatType(Object schemaValue) {
        if (!(schemaValue instanceof Map<?, ?> schema)) {
            return "any";
        }
        Object enumValue = schema.get("enum");
        if (enumValue instanceof List<?> values && !values.isEmpty()) {
            ArrayList<String> variants = new ArrayList<>();
            for (Object value : values) {
                variants.add(formatLiteral(value));
            }
            return String.join(" | ", variants);
        }
        Object component = schema.get("component");
        if (Boolean.TRUE.equals(component)) {
            return "Component";
        }
        Object type = schema.get("type");
        if ("array".equals(type)) {
            return formatType(schema.get("items")) + "[]";
        }
        if ("object".equals(type)) {
            return formatObjectType(schema);
        }
        if ("string".equals(type) || "number".equals(type) || "boolean".equals(type)) {
            return String.valueOf(type);
        }
        if ("integer".equals(type)) {
            return "number";
        }
        if (type instanceof List<?> list && !list.isEmpty()) {
            ArrayList<String> variants = new ArrayList<>();
            for (Object item : list) {
                variants.add(formatType(Map.of("type", item)));
            }
            return String.join(" | ", variants);
        }
        return "any";
    }

    private static String formatObjectType(Map<?, ?> schema) {
        Object propertiesValue = schema.get("properties");
        if (!(propertiesValue instanceof Map<?, ?> properties) || properties.isEmpty()) {
            return "Record<string, any>";
        }
        Set<String> required = required("nested object", cast(schema));
        ArrayList<String> fields = new ArrayList<>();
        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            String name = String.valueOf(entry.getKey());
            fields.add(
                    name
                            + (required.contains(name) ? "" : "?")
                            + ": "
                            + formatType(entry.getValue()));
        }
        return "{" + String.join(", ", fields) + "}";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static String formatLiteral(Object value) {
        if (value instanceof String text) {
            return "\"" + text + "\"";
        }
        return String.valueOf(value);
    }
}
