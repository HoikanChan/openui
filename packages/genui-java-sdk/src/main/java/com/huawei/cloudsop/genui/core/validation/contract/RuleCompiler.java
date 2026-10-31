/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.contract;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.Op;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.Operand;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.Rule;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.TypePattern;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.TypeRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * 结构化类型规则的加载期编译器：把契约 JSON 的 {@code typeRule} 对象编译为封闭语法树 {@link TypeRule}。
 *
 * <p>加载期失败（{@link RuleCompilationException}）：未知 op/tag、非法实参索引、对非可选参数使用 {@code argumentOr}、非末位
 * {@code rest} 参数、非法 arity。未绑定的结果变量不在加载期拒绝：这类规则编译通过，调用时该结果返回 {@code Unknown} 并记 {@code
 * UNBOUND_TYPE_VARIABLE}。 规则文法是封闭集——编译器不认识的一律拒绝，不静默降级。
 *
 * @since 2026
 */
public final class RuleCompiler {

    /** 结构化规则格式版本；契约声明值必须一致。 */
    public static final int TYPE_RULES_VERSION = 1;

    private RuleCompiler() {}

    /**
     * 编译单个 builtin 的 {@code typeRule} 条目。
     *
     * @param builtinName 所属 builtin 名（诊断定位用）
     * @param raw 契约 JSON 对象；{@code null} 表示该 builtin 未声明结构化规则
     * @return 编译产物；raw 为 null 时返回 null
     * @throws RuleCompilationException 版本不匹配、缺少 arity/parameters/result，或出现未知 tag/op、非法实参引用等布局冲突
     */
    public static TypeRule compile(String builtinName, Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        String where = "builtin '" + builtinName + "' typeRule";
        Object version = raw.get("typeRulesVersion");
        if (!(version instanceof Number number) || number.intValue() != TYPE_RULES_VERSION) {
            throw new RuleCompilationException(
                    where
                            + ": typeRulesVersion must be "
                            + TYPE_RULES_VERSION
                            + " but was "
                            + version);
        }
        TypeRule.Arity arity = arity(raw.get("arity"), where);
        List<TypeRule.ParamLayout> parameters = parameters(raw.get("parameters"), where);
        if (raw.get("result") == null) {
            throw new RuleCompilationException(where + ": missing 'result' rule");
        }
        Rule result = rule(raw.get("result"), parameters, where + ".result");
        return new TypeRule(arity, parameters, result);
    }

    private static TypeRule.Arity arity(Object raw, String where) {
        if (raw == null) {
            throw new RuleCompilationException(where + ": missing 'arity'");
        }
        Map<String, Object> map = Json.asObject(raw, where + ".arity");
        Object minRaw = map.get("min");
        if (!(minRaw instanceof Number min)) {
            throw new RuleCompilationException(where + ": arity.min must be a number");
        }
        Object maxRaw = map.get("max");
        OptionalInt max;
        if (maxRaw == null || "unbounded".equals(maxRaw)) {
            max = OptionalInt.empty();
        } else if (maxRaw instanceof Number number) {
            max = OptionalInt.of(number.intValue());
        } else {
            throw new RuleCompilationException(
                    where + ": arity.max must be a number or \"unbounded\"");
        }
        try {
            return new TypeRule.Arity(min.intValue(), max);
        } catch (IllegalArgumentException error) {
            throw new RuleCompilationException(where + ": " + error.getMessage());
        }
    }

    private static List<TypeRule.ParamLayout> parameters(Object raw, String where) {
        if (raw == null) {
            throw new RuleCompilationException(where + ": missing 'parameters'");
        }
        List<TypeRule.ParamLayout> layouts = new ArrayList<>();
        List<Object> entries = Json.asList(raw, where + ".parameters");
        for (int i = 0; i < entries.size(); i++) {
            Map<String, Object> entry =
                    Json.asObject(entries.get(i), where + ".parameters[" + i + "]");
            Object name = entry.get("name");
            if (!(name instanceof String text) || text.isBlank()) {
                throw new RuleCompilationException(
                        where + ": parameters[" + i + "] requires a non-blank 'name'");
            }
            boolean rest = Boolean.TRUE.equals(entry.get("rest"));
            if (rest && i != entries.size() - 1) {
                throw new RuleCompilationException(
                        where + ": rest parameter '" + text + "' must be the last parameter");
            }
            TypePattern accept =
                    entry.get("accept") == null
                            ? null
                            : pattern(entry.get("accept"), where + ".parameters[" + i + "].accept");
            layouts.add(new TypeRule.ParamLayout(text, rest, accept));
        }
        return layouts;
    }

    static TypePattern pattern(Object raw, String where) {
        if (raw instanceof String tag) {
            return switch (tag) {
                case "unknown" -> new TypePattern.Unknown();
                case "any" -> new TypePattern.Any();
                case "number" -> new TypePattern.Scalar(TypePattern.ScalarKind.NUMBER);
                case "string" -> new TypePattern.Scalar(TypePattern.ScalarKind.STRING);
                case "boolean" -> new TypePattern.Scalar(TypePattern.ScalarKind.BOOLEAN);
                case "null" -> new TypePattern.Scalar(TypePattern.ScalarKind.NULL);
                case "object" -> new TypePattern.AnyRecord();
                case "array" -> new TypePattern.AnySequence();
                default ->
                        throw new RuleCompilationException(
                                where + ": unknown type tag '" + tag + "'");
            };
        }
        Map<String, Object> map = Json.asObject(raw, where);
        Object tag = map.get("tag");
        if ("sequence".equals(tag)) {
            return new TypePattern.SequenceOf(pattern(map.get("element"), where + ".element"));
        }
        if ("recordOf".equals(tag)) {
            return new TypePattern.RecordOf(pattern(map.get("value"), where + ".value"));
        }
        if ("variable".equals(tag)) {
            Object name = map.get("name");
            if (!(name instanceof String text) || text.isBlank()) {
                throw new RuleCompilationException(
                        where + ": variable pattern requires a non-blank 'name'");
            }
            return new TypePattern.Variable(text);
        }
        throw new RuleCompilationException(where + ": unknown pattern tag '" + tag + "'");
    }

    private static Rule rule(Object raw, List<TypeRule.ParamLayout> parameters, String where) {
        Map<String, Object> map = Json.asObject(raw, where);
        if (map.containsKey("type")) {
            return new Rule.Constant(pattern(map.get("type"), where + ".type"));
        }
        if (map.containsKey("argument")) {
            int index = argumentIndex(map.get("argument"), parameters, where);
            return new Rule.Argument(index);
        }
        if (map.containsKey("argumentOr")) {
            Map<String, Object> argOr = Json.asObject(map.get("argumentOr"), where + ".argumentOr");
            int index = argumentIndex(argOr.get("index"), parameters, where + ".argumentOr");
            if (index >= parameters.size() || parameters.get(index).rest()) {
                throw new RuleCompilationException(
                        where
                                + ": argumentOr("
                                + index
                                + ") must reference a non-rest parameter inside the call form");
            }
            Rule fallback = rule(argOr.get("fallback"), parameters, where + ".argumentOr.fallback");
            return new Rule.ArgumentOr(index, fallback);
        }
        if (map.containsKey("variable")) {
            Object name = map.get("variable");
            if (!(name instanceof String text) || text.isBlank()) {
                throw new RuleCompilationException(
                        where + ": variable rule requires a non-blank name");
            }
            return new Rule.VariableRef(text);
        }
        if (map.containsKey("union")) {
            List<Rule> alternatives = new ArrayList<>();
            List<Object> raws = Json.asList(map.get("union"), where + ".union");
            for (int i = 0; i < raws.size(); i++) {
                alternatives.add(rule(raws.get(i), parameters, where + ".union[" + i + "]"));
            }
            if (alternatives.isEmpty()) {
                throw new RuleCompilationException(
                        where + ": union requires at least one alternative");
            }
            return new Rule.UnionOfRules(alternatives);
        }
        if (map.containsKey("op")) {
            Object opName = map.get("op");
            if (!(opName instanceof String text)) {
                throw new RuleCompilationException(where + ": 'op' must be a string");
            }
            Op op =
                    Op.byWireName(text)
                            .orElseThrow(
                                    () ->
                                            new RuleCompilationException(
                                                    where + ": unknown op '" + text + "'"));
            List<Operand> operands = new ArrayList<>();
            List<Object> raws = Json.asList(map.get("operands"), where + ".operands");
            for (int i = 0; i < raws.size(); i++) {
                Object operandRaw = raws.get(i);
                if (operandRaw instanceof Map<?, ?> operandMap
                        && operandMap.containsKey("argumentsFrom")) {
                    int index =
                            argumentIndex(
                                    operandMap.get("argumentsFrom"),
                                    parameters,
                                    where + ".operands[" + i + "]");
                    if (index < parameters.size() && !parameters.get(index).rest()) {
                        throw new RuleCompilationException(
                                where
                                        + ": argumentsFrom("
                                        + index
                                        + ") must reference a rest parameter");
                    }
                    operands.add(new Operand.ArgumentsFrom(index));
                } else {
                    operands.add(
                            new Operand.RuleOperand(
                                    rule(operandRaw, parameters, where + ".operands[" + i + "]")));
                }
            }
            switch (op) {
                case RECORD_KEYS, RECORD_VALUES, RECORD_ENTRIES -> {
                    if (operands.size() != 1
                            || !(operands.getFirst() instanceof Operand.RuleOperand)) {
                        throw new RuleCompilationException(
                                where + ": " + text + " requires exactly one value operand");
                    }
                }
                default -> {}
            }
            Map<String, String> options = new LinkedHashMap<>();
            Object optionsRaw = map.get("options");
            if (optionsRaw != null) {
                Map<String, Object> optionMap = Json.asObject(optionsRaw, where + ".options");
                for (Map.Entry<String, Object> option : optionMap.entrySet()) {
                    if (!RulePlan.KNOWN_EVIDENCE_OPTIONS.contains(option.getKey())) {
                        throw new RuleCompilationException(
                                where
                                        + ": unknown option '"
                                        + option.getKey()
                                        + "' for op '"
                                        + text
                                        + "'");
                    }
                    options.put(option.getKey(), String.valueOf(option.getValue()));
                }
            }
            return new Rule.Apply(op, operands, options);
        }
        throw new RuleCompilationException(
                where
                        + ": rule must contain exactly one of type/argument/argumentOr/variable/union/op");
    }

    private static int argumentIndex(
            Object raw, List<TypeRule.ParamLayout> parameters, String where) {
        if (!(raw instanceof Number number)) {
            throw new RuleCompilationException(where + ": argument index must be a number");
        }
        int index = number.intValue();
        if (index < 0) {
            throw new RuleCompilationException(where + ": negative argument index " + index);
        }
        boolean hasRest = !parameters.isEmpty() && parameters.get(parameters.size() - 1).rest();
        if (!hasRest && index >= parameters.size()) {
            throw new RuleCompilationException(
                    where
                            + ": argument("
                            + index
                            + ") is out of range for "
                            + parameters.size()
                            + " parameter(s)");
        }
        return index;
    }
}
