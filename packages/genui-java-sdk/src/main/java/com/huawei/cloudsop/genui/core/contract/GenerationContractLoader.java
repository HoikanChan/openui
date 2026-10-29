/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExprParser;
import com.huawei.cloudsop.genui.core.validation.contract.RuleCompiler;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.TypeRule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GenerationContractLoader {
    private static final String DEFAULT_RESOURCE = "openui/base-contract.json";
    private static final Set<String> ALLOWED_GAP_KINDS = Set.of("data", "component");

    private GenerationContractLoader() {}

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
     * Fail-fast validation for capabilityGaps: checks required fields, kind ("data" | "component"),
     * duplicate ids, and alias collisions against components, builtins, or other capability gaps.
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

    /** 解析单个 builtin 条目并做结构校验（name 重复、optional 排必填前、variadic 不在末位、类型串解析）。 */
    private static BuiltinSpec builtin(Map<String, Object> entry, java.util.Set<String> seenNames) {
        String name = string(entry.get("name"));
        if (name == null || name.isBlank()) {
            throw new GenerationSdkException("builtin entry is missing 'name': " + entry);
        }
        if (!seenNames.add(name)) {
            throw new GenerationSdkException("duplicate builtin name in contract: " + name);
        }
        List<BuiltinSpec.ParamSpec> params = builtinParams(entry.get("params"), name);
        boolean seenPlainOptional = false;
        for (int i = 0; i < params.size(); i++) {
            BuiltinSpec.ParamSpec param = params.get(i);
            if (param.optional()) {
                if (!param.binder()) {
                    seenPlainOptional = true;
                }
            } else if (seenPlainOptional) {
                // binder 参数豁免：其 optional 语义是"该作用域绑定可省略"（Render 的 2/3 参双形式），
                // 后随的 template 仍是必填。
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
        TypeExpr returnType = null;
        Object returnTypeRaw = entry.get("returnType");
        if (returnTypeRaw != null) {
            returnType = TypeExprParser.parse(string(returnTypeRaw), name + ".returnType");
        }
        requireBoundTypeVars(params, returnType, name);
        // 结构化类型规则与词表同一次解析：未知 op/非法引用在此加载期失败（RuleCompilationException）。
        TypeRule typeRule = null;
        Object typeRuleRaw = entry.get("typeRule");
        if (typeRuleRaw != null) {
            typeRule =
                    RuleCompiler.compile(
                            name, Json.asObject(typeRuleRaw, "typeRule of builtin " + name));
        }
        // 元素输入来源必须显式声明: binder builtin 缺省 input 视为漏声明并在加载期失败;
        // 显式 null 才表示"没有元素来源"(如 Render)。否则缺字段会静默退化成 MISSING_CONTEXT,
        // 把本该确定报出的元素级错误降级为"未证明"。
        boolean hasBinders = params.stream().anyMatch(BuiltinSpec.ParamSpec::binder);
        boolean inputDeclared = entry.containsKey("input");
        Object inputRaw = entry.get("input");
        if (hasBinders && !inputDeclared) {
            throw new GenerationSdkException(
                    "builtin '"
                            + name
                            + "': binder builtin must declare 'input' (use null only when it has no element source)");
        }
        int inputIndex = -1;
        if (inputRaw != null) {
            int raw = exactIndex(inputRaw, name, "input");
            if (raw < 0 || raw >= params.size()) {
                throw new GenerationSdkException(
                        "builtin '"
                                + name
                                + "': 'input' index "
                                + raw
                                + " is out of range for "
                                + params.size()
                                + " parameter(s)");
            }
            inputIndex = (int) raw;
            if (params.get(inputIndex).binder()) {
                throw new GenerationSdkException(
                        "builtin '"
                                + name
                                + "': 'input' must not point to binder parameter '"
                                + name
                                + "."
                                + params.get(inputIndex).name()
                                + "'");
            }
            if (params.stream().noneMatch(BuiltinSpec.ParamSpec::binder)) {
                throw new GenerationSdkException(
                        "builtin '" + name + "': 'input' is only meaningful for a binder builtin");
            }
        } else if (hasBinders) {
            // 显式 null 只允许用于确实没有元素来源的 binder builtin(Render);声明了元素数组参数
            // (含具体元素类型的数组)却拒绝声明来源,等于用 null 绕过元素级检查,加载期直接拒绝。
            for (BuiltinSpec.ParamSpec param : params) {
                if (param.typeExpr() instanceof TypeExpr.ArrayOf) {
                    throw new GenerationSdkException(
                            "builtin '"
                                    + name
                                    + "': parameter '"
                                    + param.name()
                                    + "' declares an element array, so 'input' must name its source index (not null)");
                }
            }
        }
        return new BuiltinSpec(
                name,
                string(entry.get("signature")),
                string(entry.get("description")),
                Boolean.TRUE.equals(entry.get("templateBuiltin")),
                params,
                returnType,
                typeRule,
                inputIndex,
                callForms(entry.get("callForms"), params, inputIndex, name));
    }

    private static List<BuiltinSpec.CallForm> callForms(
            Object raw, List<BuiltinSpec.ParamSpec> params, int input, String name) {
        boolean hasBinders = params.stream().anyMatch(BuiltinSpec.ParamSpec::binder);
        if (!hasBinders && raw == null) {
            return List.of();
        }
        if (!hasBinders || raw == null) {
            throw new GenerationSdkException(
                    "builtin '" + name + "': binder callForms must be declared");
        }
        List<BuiltinSpec.CallForm> forms = new ArrayList<>();
        Set<Integer> arities = new LinkedHashSet<>();
        int min = (int) params.stream().filter(p -> !p.optional() && !p.variadic()).count();
        for (Object item : Json.asList(raw, "callForms")) {
            Map<String, Object> form = Json.asObject(item, "callForm");
            int arity = exactIndex(form.get("arity"), name, "arity");
            int body = exactIndex(form.get("body"), name, "body");
            if (arity < min
                    || arity > params.size()
                    || !arities.add(arity)
                    || body < 0
                    || body >= arity) {
                throw new GenerationSdkException(
                        "builtin '" + name + "': invalid or ambiguous callForms body/arity");
            }
            List<Integer> binders = new ArrayList<>();
            for (Object binder : Json.asList(form.get("binders"), "callForm.binders")) {
                int index = exactIndex(binder, name, "binder");
                if (index < 0
                        || index >= arity
                        || index == body
                        || !params.get(index).binder()
                        || binders.contains(index)) {
                    throw new GenerationSdkException(
                            "builtin '" + name + "': invalid callForms binder index");
                }
                binders.add(index);
            }
            for (int index = 0; index < params.size(); index++) {
                BuiltinSpec.ParamSpec param = params.get(index);
                if (param.binder() && !param.optional() && !binders.contains(index)) {
                    throw new GenerationSdkException(
                            "builtin '" + name + "': callForms omits required binder");
                }
            }
            if (binders.isEmpty() || input >= arity || input == body) {
                throw new GenerationSdkException(
                        "builtin '" + name + "': input must not point to body or absent argument");
            }
            forms.add(new BuiltinSpec.CallForm(arity, body, binders));
        }
        for (int arity = min; arity <= params.size(); arity++) {
            if (!arities.contains(arity)) {
                throw new GenerationSdkException(
                        "builtin '" + name + "': callForms missing arity " + arity);
            }
        }
        return List.copyOf(forms);
    }

    private static int exactIndex(Object value, String name, String field) {
        try {
            if (value instanceof Number) {
                return new java.math.BigDecimal(value.toString()).intValueExact();
            }
        } catch (ArithmeticException | NumberFormatException ignored) {
            // Reject fractions and overflow without a floating-point conversion.
        }
        throw new GenerationSdkException(
                "builtin '"
                        + name
                        + "': '"
                        + field
                        + "' must be an integral parameter index, got "
                        + value);
    }

    private static List<BuiltinSpec.ParamSpec> builtinParams(Object value, String builtinName) {
        if (value == null) {
            throw new GenerationSdkException("builtin '" + builtinName + "' is missing 'params'");
        }
        ArrayList<BuiltinSpec.ParamSpec> result = new ArrayList<>();
        for (Object item : Json.asList(value, "params of builtin " + builtinName)) {
            Map<String, Object> param = Json.asObject(item, "param of builtin " + builtinName);
            String name = string(param.get("name"));
            String type = string(param.get("type"));
            if (name == null || type == null) {
                throw new GenerationSdkException(
                        "builtin '" + builtinName + "': param entry requires 'name' and 'type'");
            }
            result.add(
                    new BuiltinSpec.ParamSpec(
                            name,
                            type,
                            TypeExprParser.parse(type, builtinName + "." + name),
                            Boolean.TRUE.equals(param.get("optional")),
                            Boolean.TRUE.equals(param.get("variadic")),
                            Boolean.TRUE.equals(param.get("binder")),
                            nameSyntax(param, builtinName, name)));
        }
        return result;
    }

    /** binder 名语法:仅 binder 参数可声明;声明值经枚举校验(未知值加载期失败);缺省按双形态(与既有行为一致)。 */
    private static String nameSyntax(
            Map<String, Object> param, String builtinName, String paramName) {
        Object raw = param.get("nameSyntax");
        boolean binder = Boolean.TRUE.equals(param.get("binder"));
        if (raw == null) {
            return binder ? "string-or-identifier" : null;
        }
        if (!binder) {
            throw new GenerationSdkException(
                    "builtin '"
                            + builtinName
                            + "': non-binder parameter '"
                            + paramName
                            + "' must not declare nameSyntax");
        }
        String value = string(raw);
        BuiltinSpec.NameSyntax.fromContract(value);
        return value;
    }

    /** returnType 里的 {@code T} 必须能在某个参数类型中解出绑定；param(n) 引用必须在参数表范围内。 */
    private static void requireBoundTypeVars(
            List<BuiltinSpec.ParamSpec> params, TypeExpr returnType, String builtinName) {
        boolean paramsBindT = params.stream().anyMatch(param -> containsTypeVar(param.typeExpr()));
        if (returnType != null && !paramsBindT && containsTypeVar(returnType)) {
            throw new GenerationSdkException(
                    "builtin '"
                            + builtinName
                            + "': returnType references T but no parameter type binds it");
        }
        int paramCount = params.size();
        visit(
                returnType,
                typeExpr -> {
                    if (typeExpr instanceof TypeExpr.ParamRef ref
                            && (ref.index() < 0 || ref.index() >= paramCount)) {
                        throw new GenerationSdkException(
                                "builtin '"
                                        + builtinName
                                        + "': param("
                                        + ref.index()
                                        + ") is out of range for "
                                        + paramCount
                                        + " parameter(s)");
                    }
                });
    }

    private static boolean containsTypeVar(TypeExpr type) {
        return type != null
                && (type instanceof TypeExpr.TypeVar
                        || (type instanceof TypeExpr.ArrayOf arrayOf
                                && containsTypeVar(arrayOf.element()))
                        || (type instanceof TypeExpr.RecordOf record
                                && containsTypeVar(record.value()))
                        || (type instanceof TypeExpr.ObjectLiteral literal
                                && literal.fields().stream()
                                        .anyMatch(field -> containsTypeVar(field.type())))
                        || (type instanceof TypeExpr.UnionOf union
                                && union.alternatives().stream()
                                        .anyMatch(GenerationContractLoader::containsTypeVar))
                        || (type instanceof TypeExpr.Conditional conditional
                                && (containsTypeVar(conditional.whenArray())
                                        || containsTypeVar(conditional.otherwise()))));
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
        } else if (type instanceof TypeExpr.Conditional conditional) {
            visit(conditional.whenArray(), visitor);
            visit(conditional.otherwise(), visitor);
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
