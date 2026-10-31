/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExprParser;
import com.huawei.cloudsop.genui.core.validation.type.analysis.BuiltinAnalysis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 契约 JSON 加载器:从 classpath 读取内置 {@code base-contract.json},或把调用方提供的 JSON 文本解析为 {@link
 * GenerationContract},并在加载期完成结构校验。
 *
 * @since 2026
 */
public final class GenerationContractLoader {
    private static final String DEFAULT_RESOURCE = "openui/base-contract.json";
    private static final Set<String> ALLOWED_GAP_KINDS = Set.of("data", "component");

    /** 已删除的 builtin 条目字段;残留即加载失败,不静默忽略。 */
    private static final Set<String> REMOVED_BUILTIN_FIELDS =
            Set.of(
                    "typeRule",
                    "typeRulesVersion",
                    "inference",
                    "resultBehavior",
                    "input",
                    "callForms");

    /** 已删除的参数级字段;残留即加载失败。 */
    private static final Set<String> REMOVED_PARAM_FIELDS = Set.of("binder", "nameSyntax");

    private GenerationContractLoader() {}

    /**
     * 从当前线程上下文类加载器读取内置契约资源,上下文类加载器为空时回退到本类加载器。
     *
     * @return 解析并校验后的内置契约
     * @throws GenerationSdkException 资源不存在、读取失败,或 JSON 解析/结构校验失败
     */
    public static GenerationContract loadDefault() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = GenerationContractLoader.class.getClassLoader();
        }
        try (InputStream input = loader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input == null) {
                throw new GenerationSdkException(
                        "Default base contract resource not found: " + DEFAULT_RESOURCE);
            }
            return fromJson(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new GenerationSdkException("Failed to load default base contract", error);
        }
    }

    /**
     * 把契约 JSON 文本解析为契约对象;浮点数字保留 {@code BigDecimal} 精度,直到加载期校验完成。
     *
     * @param json 契约 JSON 文本
     * @return 解析并校验后的契约
     * @throws GenerationSdkException JSON 非法,或契约结构校验失败
     */
    public static GenerationContract fromJson(String json) {
        // Contract indexes must retain their exact decimal value until load-time validation.
        try {
            return fromMap(
                    Json.asObject(
                            com.alibaba.fastjson2.JSON.parse(
                                    json,
                                    com.alibaba.fastjson2.JSONReader.Feature
                                            .UseBigDecimalForDoubles,
                                    com.alibaba.fastjson2.JSONReader.Feature.UseBigDecimalForFloats,
                                    com.alibaba.fastjson2.JSONReader.Feature.DisableSingleQuote),
                            "GenerationContract"));
        } catch (com.alibaba.fastjson2.JSONException error) {
            throw new GenerationSdkException("Invalid JSON: " + error.getMessage(), error);
        }
    }

    static GenerationContract fromMap(Map<String, Object> map) {
        Map<String, ComponentPromptSpec> components = componentMap(map.get("components"));
        List<BuiltinSpec> builtins = builtins(map.get("builtins"));
        List<CapabilityGapSpec> capabilityGaps = capabilityGaps(map.get("capabilityGaps"));
        validateCapabilityGaps(capabilityGaps, components.keySet(), builtins, "contract");
        return new GenerationContract(
                string(map.get("contractVersion")),
                string(map.get("root")),
                components,
                componentGroups(map.get("componentGroups")),
                tools(map.get("tools")),
                strings(map.get("examples")),
                strings(map.get("additionalRules")),
                builtins,
                capabilityGaps);
    }

    static List<CapabilityGapSpec> capabilityGaps(Object value) {
        ArrayList<CapabilityGapSpec> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (Object item : Json.asList(value, "capabilityGaps")) {
            Map<String, Object> entry = Json.asObject(item, "capabilityGap");
            result.add(
                    new CapabilityGapSpec(
                            string(entry.get("id")),
                            string(entry.get("kind")),
                            string(entry.get("name")),
                            strings(entry.get("aliases")),
                            strings(entry.get("phrases")),
                            string(entry.get("fallback"))));
        }
        return result;
    }

    /**
     * 对 capabilityGaps 做 fail-fast 校验:必填字段、kind({@code data}|{@code component})、id
     * 重复,以及别名与组件、builtins 或其他 gap 的冲突。
     *
     * @param gaps 待校验的能力缺口条目;为空时直接返回
     * @param componentNames 契约中已声明的组件名,用于别名冲突检查
     * @param builtins 契约中已声明的 builtin,用于别名冲突检查
     * @param scope 出错信息中的来源标识(如 contract)
     * @throws GenerationSdkException 条目为空、字段缺失/非法、id 重复或别名冲突
     */
    public static void validateCapabilityGaps(
            List<CapabilityGapSpec> gaps,
            Set<String> componentNames,
            List<BuiltinSpec> builtins,
            String scope) {
        if (gaps == null || gaps.isEmpty()) {
            return;
        }
        Set<String> builtinNames = new LinkedHashSet<>();
        if (builtins != null) {
            for (BuiltinSpec builtin : builtins) {
                if (builtin != null && builtin.name() != null) {
                    builtinNames.add(builtin.name());
                }
            }
        }
        Set<String> seenIds = new LinkedHashSet<>();
        Map<String, String> seenAliases = new LinkedHashMap<>();
        for (CapabilityGapSpec gap : gaps) {
            if (gap == null) {
                throw new GenerationSdkException(
                        "capabilityGap entry must not be null in " + scope);
            }
            if (gap.id() == null || gap.id().isBlank()) {
                throw new GenerationSdkException("capabilityGap entry is missing 'id' in " + scope);
            }
            if (!seenIds.add(gap.id())) {
                throw new GenerationSdkException(
                        "duplicate capabilityGap id in " + scope + ": " + gap.id());
            }
            if (gap.kind() == null || !ALLOWED_GAP_KINDS.contains(gap.kind())) {
                throw new GenerationSdkException(
                        "capabilityGap '"
                                + gap.id()
                                + "' in "
                                + scope
                                + " has invalid kind '"
                                + gap.kind()
                                + "' (expected 'data' or 'component')");
            }
            if (gap.name() == null || gap.name().isBlank()) {
                throw new GenerationSdkException(
                        "capabilityGap '" + gap.id() + "' is missing 'name' in " + scope);
            }
            if (gap.fallback() == null || gap.fallback().isBlank()) {
                throw new GenerationSdkException(
                        "capabilityGap '" + gap.id() + "' is missing 'fallback' in " + scope);
            }
            for (String rawAlias : gap.aliases()) {
                String alias = CapabilityGapSpec.normalizeAlias(rawAlias);
                if (alias.isEmpty()) {
                    throw new GenerationSdkException(
                            "capabilityGap '" + gap.id() + "' contains a blank alias in " + scope);
                }
                if (componentNames != null && componentNames.contains(alias)) {
                    throw new GenerationSdkException(
                            "capabilityGap '"
                                    + gap.id()
                                    + "' alias '"
                                    + alias
                                    + "' collides with existing component in "
                                    + scope);
                }
                if (builtinNames.contains(alias)) {
                    throw new GenerationSdkException(
                            "capabilityGap '"
                                    + gap.id()
                                    + "' alias '"
                                    + alias
                                    + "' collides with existing builtin in "
                                    + scope);
                }
                String previousGap = seenAliases.put(alias, gap.id());
                if (previousGap != null && !previousGap.equals(gap.id())) {
                    throw new GenerationSdkException(
                            "capabilityGap '"
                                    + gap.id()
                                    + "' alias '"
                                    + alias
                                    + "' collides with capabilityGap '"
                                    + previousGap
                                    + "' in "
                                    + scope);
                }
            }
            for (String phrase : gap.phrases()) {
                if (phrase == null || phrase.isBlank()) {
                    throw new GenerationSdkException(
                            "capabilityGap '" + gap.id() + "' contains a blank phrase in " + scope);
                }
            }
        }
    }

    private static List<BuiltinSpec> builtins(Object value) {
        ArrayList<BuiltinSpec> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        java.util.Set<String> seenNames = new java.util.HashSet<>();
        for (Object item : Json.asList(value, "builtins")) {
            Map<String, Object> entry = Json.asObject(item, "builtin");
            result.add(builtin(entry, seenNames));
        }
        return result;
    }

    /**
     * 解析单个 builtin 条目并做结构校验:必填字段、名称唯一、已删除字段、optional 排必填前、variadic 不在末位、 类型串解析。
     *
     * <p>必填字段缺失或类型错误直接失败并指出函数与字段(#117 第 2.2 节);仅 Each/Render 允许省略 returnType。
     */
    private static BuiltinSpec builtin(Map<String, Object> entry, java.util.Set<String> seenNames) {
        Object nameRaw = entry.get("name");
        if (!(nameRaw instanceof String name) || name.isBlank()) {
            throw new GenerationSdkException("builtin entry missing field 'name'");
        }
        if (!seenNames.add(name)) {
            throw new GenerationSdkException("duplicate builtin name in contract: " + name);
        }
        requireNoRemovedFields(name, entry.keySet(), REMOVED_BUILTIN_FIELDS, "builtin");
        String signature = requiredString(entry, "signature", name);
        String description = requiredString(entry, "description", name);
        boolean templateBuiltin = requiredBoolean(entry, "templateBuiltin", name);
        List<BuiltinSpec.ParamSpec> params = builtinParams(entry.get("params"), name);
        // Each/Render 的参数布局由 builtin module 拥有（Render 的中段 row 可省），
        // 加载器不再为它们套普通可选/变长位置规则，也不在这里列出函数名。
        if (!BuiltinAnalysis.ownsCallLayout(name)) {
            boolean seenPlainOptional = false;
            for (int i = 0; i < params.size(); i++) {
                BuiltinSpec.ParamSpec param = params.get(i);
                if (param.optional()) {
                    seenPlainOptional = true;
                } else if (seenPlainOptional) {
                    throw new GenerationSdkException(
                            "builtin '"
                                    + name
                                    + "': required parameter '"
                                    + param.name()
                                    + "' must not follow an optional parameter");
                }
                if (param.variadic() && i != params.size() - 1) {
                    throw new GenerationSdkException(
                            "builtin '"
                                    + name
                                    + "': variadic parameter '"
                                    + param.name()
                                    + "' must be the last parameter");
                }
            }
        }
        TypeExpr returnType = null;
        Object returnTypeRaw = entry.get("returnType");
        if (returnTypeRaw != null) {
            returnType = TypeExprParser.parse(string(returnTypeRaw), name + ".returnType");
        } else if (!BuiltinAnalysis.ownsCallLayout(name)) {
            throw new GenerationSdkException("builtin '" + name + "' missing field 'returnType'");
        }
        requireBoundTypeVars(params, returnType, name);
        BuiltinSpec spec =
                new BuiltinSpec(name, signature, description, templateBuiltin, params, returnType);
        // 特殊函数的最低布局前提与返回声明覆盖由 builtin module 统一校验(#117 第 3.1 节)。
        BuiltinAnalysis.validateDefinition(spec);
        return spec;
    }

    /** 必填字符串字段:缺失或空白都报 {@code builtin 'X' missing field 'Y'}。 */
    private static String requiredString(Map<String, Object> entry, String field, String name) {
        Object raw = entry.get(field);
        if (!(raw instanceof String text) || text.isBlank()) {
            throw new GenerationSdkException(
                    "builtin '" + name + "' missing field '" + field + "'");
        }
        return text;
    }

    /** 必填 JSON boolean 字段:缺失按 missing field 报错,其他类型指出必须是 boolean。 */
    private static boolean requiredBoolean(Map<String, Object> entry, String field, String name) {
        Object raw = entry.get(field);
        if (raw == null) {
            throw new GenerationSdkException(
                    "builtin '" + name + "' missing field '" + field + "'");
        }
        if (!(raw instanceof Boolean value)) {
            throw new GenerationSdkException(
                    "builtin '" + name + "' field '" + field + "' must be a JSON boolean");
        }
        return value;
    }

    private static List<BuiltinSpec.ParamSpec> builtinParams(Object value, String builtinName) {
        if (value == null) {
            throw new GenerationSdkException(
                    "builtin '" + builtinName + "' missing field 'params'");
        }
        if (!(value instanceof List<?>)) {
            throw new GenerationSdkException(
                    "builtin '" + builtinName + "' field 'params' must be a JSON array");
        }
        ArrayList<BuiltinSpec.ParamSpec> result = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (Object item : Json.asList(value, "params of builtin " + builtinName)) {
            Map<String, Object> param = Json.asObject(item, "param of builtin " + builtinName);
            String name = string(param.get("name"));
            String type = string(param.get("type"));
            if (name == null || type == null) {
                throw new GenerationSdkException(
                        "builtin '" + builtinName + "': param entry requires 'name' and 'type'");
            }
            if (name.isBlank()) {
                throw new GenerationSdkException(
                        "builtin '" + builtinName + "': parameter field 'name' must not be blank");
            }
            if (!names.add(name)) {
                throw new GenerationSdkException(
                        "builtin '"
                                + builtinName
                                + "': duplicate parameter field 'name': '"
                                + name
                                + "'");
            }
            requireNoRemovedFields(
                    builtinName + "." + name, param.keySet(), REMOVED_PARAM_FIELDS, "parameter");
            result.add(
                    new BuiltinSpec.ParamSpec(
                            name,
                            type,
                            TypeExprParser.parse(type, builtinName + "." + name),
                            Boolean.TRUE.equals(param.get("optional")),
                            Boolean.TRUE.equals(param.get("variadic"))));
        }
        return result;
    }

    /** returnType 里的每个命名类型变量必须能在某个参数类型中解出绑定。错误信息同时指出函数与变量名,便于契约作者定位 (#119)。 */
    private static void requireBoundTypeVars(
            List<BuiltinSpec.ParamSpec> params, TypeExpr returnType, String builtinName) {
        java.util.Set<String> parameterVars = new java.util.TreeSet<>();
        for (BuiltinSpec.ParamSpec param : params) {
            collectTypeVars(param.typeExpr(), parameterVars);
        }
        java.util.Set<String> returnVars = new java.util.TreeSet<>();
        collectTypeVars(returnType, returnVars);
        for (String variable : returnVars) {
            if (!parameterVars.contains(variable)) {
                throw new GenerationSdkException(
                        "builtin '"
                                + builtinName
                                + "': returnType references type variable '"
                                + variable
                                + "' but no parameter type binds it");
            }
        }
    }

    private static void collectTypeVars(TypeExpr type, java.util.Set<String> names) {
        visit(
                type,
                typeExpr -> {
                    if (typeExpr instanceof TypeExpr.TypeVar variable) {
                        names.add(variable.name());
                    }
                });
    }

    private interface Visitor {
        void visit(TypeExpr type);
    }

    private static void visit(TypeExpr type, Visitor visitor) {
        if (type == null) {
            return;
        }
        visitor.visit(type);
        if (type instanceof TypeExpr.ArrayOf arrayOf) {
            visit(arrayOf.element(), visitor);
        } else if (type instanceof TypeExpr.RecordOf record) {
            visit(record.value(), visitor);
        } else if (type instanceof TypeExpr.ObjectLiteral literal) {
            for (TypeExpr.ObjectLiteral.Field field : literal.fields()) {
                visit(field.type(), visitor);
            }
        } else if (type instanceof TypeExpr.UnionOf union) {
            for (TypeExpr alternative : union.alternatives()) {
                visit(alternative, visitor);
            }
        }
    }

    private static void requireNoRemovedFields(
            String owner, Set<String> keys, Set<String> removed, String kind) {
        for (String field : removed) {
            if (keys.contains(field)) {
                throw new GenerationSdkException(
                        kind
                                + " '"
                                + owner
                                + "' declares removed field '"
                                + field
                                + "'; the concise builtin contract has no rule or scope fields");
            }
        }
    }

    static Map<String, ComponentPromptSpec> componentMap(Object value) {
        LinkedHashMap<String, ComponentPromptSpec> result = new LinkedHashMap<>();
        if (value == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : Json.asObject(value, "components").entrySet()) {
            Map<String, Object> component =
                    Json.asObject(entry.getValue(), "component " + entry.getKey());
            result.put(
                    entry.getKey(),
                    component.containsKey("propsSchema")
                            ? new ComponentPromptSpec(
                                    string(component.get("description")),
                                    Json.asObject(component.get("propsSchema"), "propsSchema"))
                            : new ComponentPromptSpec(
                                    string(component.get("signature")),
                                    string(component.get("description"))));
        }
        return result;
    }

    static List<ComponentGroup> componentGroups(Object value) {
        ArrayList<ComponentGroup> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (Object item : Json.asList(value, "componentGroups")) {
            Map<String, Object> group = Json.asObject(item, "componentGroup");
            result.add(
                    new ComponentGroup(
                            string(group.get("name")),
                            strings(group.get("components")),
                            strings(group.get("notes"))));
        }
        return result;
    }

    private static List<ToolSpec> tools(Object value) {
        ArrayList<ToolSpec> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (Object item : Json.asList(value, "tools")) {
            if (item instanceof String name) {
                result.add(new ToolSpec(name, null, Map.of(), Map.of(), null));
                continue;
            }
            Map<String, Object> tool = Json.asObject(item, "tool");
            result.add(
                    new ToolSpec(
                            string(tool.get("name")),
                            string(tool.get("description")),
                            objectOrEmpty(tool.get("inputSchema")),
                            objectOrEmpty(tool.get("outputSchema")),
                            annotations(tool.get("annotations"))));
        }
        return result;
    }

    private static ToolAnnotations annotations(Object value) {
        if (value == null) {
            return null;
        }
        Map<String, Object> map = Json.asObject(value, "tool annotations");
        return new ToolAnnotations(bool(map.get("readOnlyHint")), bool(map.get("destructiveHint")));
    }

    private static Map<String, Object> objectOrEmpty(Object value) {
        if (value == null) {
            return Map.of();
        }
        return Json.asObject(value, "schema");
    }

    static List<String> strings(Object value) {
        ArrayList<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (Object item : Json.asList(value, "string list")) {
            result.add(string(item));
        }
        return result;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Boolean bool(Object value) {
        return value instanceof Boolean bool ? bool : null;
    }
}
