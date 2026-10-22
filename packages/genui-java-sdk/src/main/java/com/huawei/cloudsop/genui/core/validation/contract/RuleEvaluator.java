/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.contract;

import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.Operand;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.Rule;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.TypePattern;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.TypeRule;
import com.huawei.cloudsop.genui.core.validation.type.ValueType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ArrayType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.EmptyArrayType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ObjectType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.Primitive;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.PrimitiveKind;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.StringDomainType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.UnionType;
import com.huawei.cloudsop.genui.core.validation.type.ValueTypeOps;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 结构化类型规则的求值器：按契约编译产物完成单次调用内的泛型求解与有限类型运算。
 *
 * <p>
 * 语义要点（#77 第 9、6.5、7.5 节）：
 *
 * <ul>
 * <li>重复出现的类型变量合并下界（取并集），不是最后一个实参覆盖之前绑定；任何实参推断不出时保留 Unknown 下界，未知输入不能被已知输入盖掉。</li>
 * <li>variadic 实参逐个检查、逐个并入下界。</li>
 * <li>Sort（sequence.preserve）保留元素存在证据；Filter（sequence.subset/filter）删除"某行必然保留"的证据但保留元素上界。</li>
 * <li>MergeObject（record.overlay）按实参顺序后写覆盖；未知字段（无类型实参）可能覆盖前字段——保留字段存在性， 但值类型并入
 * Unknown（本类型系统中以"并入全部标量种类"的宽上界表达未知，不创造精确证据）。</li>
 * </ul>
 *
 * 求值器按 op 种类分派，不含任何函数名分支。
 *
 * @since 2026
 */
public final class RuleEvaluator {

    /** 宽上界：表达"该位置值未知"——并入全部标量种类。不引入单独 Unknown 节点，避免两套语义并行。 */
    private static final ValueType WIDE_UNKNOWN = new UnionType(
            List.of(new Primitive(PrimitiveKind.NUMBER), new Primitive(PrimitiveKind.STRING),
                    new Primitive(PrimitiveKind.BOOLEAN), new Primitive(PrimitiveKind.NULL)));

    private RuleEvaluator() {
    }

    /** 宿主能力：取实参类型（已按宿主作用域推断）与上报检查结论。 */
    public interface CallSite {
        /** 第 index 个实参的推断类型；推断不出为 {@code Optional.empty()}（未知，不是错误）。 */
        Optional<ValueType> argumentType(int index);

        /** 实参 index 不满足模式 pattern（pattern 按契约原文渲染）。 */
        void reportArgumentMismatch(int index, String expectedPattern, ValueType actual);

        /** 实参数量不满足调用形式。 */
        void reportArityViolation(int minimum, Optional<Integer> maximum, int actual);
    }

    /**
     * 对一次调用求值：数量检查 → 逐参模式检查（含 variadic 每个实参）→ 变量下界合并 → 代入结果规则。
     *
     * @return 结果类型；信息不足时为宽上界而不是"无类型"（调用本身不因未知而消失）
     */
    public static ValueType evaluate(TypeRule rule, int argumentCount, CallSite callSite) {
        checkArity(rule.arity(), argumentCount, callSite);
        Map<String, ValueType> lowerBounds = new LinkedHashMap<>();
        List<TypePattern> parameterPatterns = parameterPatterns(rule);
        List<TypeRule.ParamLayout> layouts = rule.parameters();
        for (int index = 0; index < argumentCount; index++) {
            TypePattern pattern = patternFor(parameterPatterns, layouts, index);
            if (pattern == null) {
                continue;
            }
            Optional<ValueType> actual = callSite.argumentType(index);
            if (actual.isPresent()) {
                checkPattern(pattern, actual.get(), index, callSite);
                mergeLowerBound(lowerBounds, pattern, actual.get());
            } else {
                // 未知输入保留 Unknown 下界：后续已知实参不能把变量收窄成虚假的具体类型。
                mergeUnknownBound(lowerBounds, pattern);
            }
        }
        return instantiate(rule.result(), lowerBounds, argumentCount, callSite);
    }

    private static void checkArity(TypeRule.Arity arity, int actual, CallSite callSite) {
        Optional<Integer> maximum = arity.max().isPresent() ? Optional.of(arity.max().getAsInt()) : Optional.empty();
        if (actual < arity.min() || (maximum.isPresent() && actual > maximum.get())) {
            callSite.reportArityViolation(arity.min(), maximum, actual);
        }
    }

    /** 各位置的参数模式：accept 为 null 的位置不做结构化模式检查。 */
    private static List<TypePattern> parameterPatterns(TypeRule rule) {
        List<TypePattern> patterns = new ArrayList<>();
        for (TypeRule.ParamLayout layout : rule.parameters()) {
            patterns.add(layout.accept());
        }
        return patterns;
    }

    private static TypePattern patternFor(List<TypePattern> patterns, List<TypeRule.ParamLayout> layouts, int index) {
        if (index < patterns.size()) {
            return patterns.get(index);
        }
        if (!layouts.isEmpty() && layouts.get(layouts.size() - 1).rest() && !patterns.isEmpty()) {
            return patterns.get(patterns.size() - 1);
        }
        return null;
    }

    /** 模式检查：未知（empty）已被调用方拦截；这里对已推断类型做结构判定。 */
    private static void checkPattern(TypePattern pattern, ValueType actual, int index, CallSite callSite) {
        if (patternSatisfied(pattern, actual)) {
            return;
        }
        callSite.reportArgumentMismatch(index, displayPattern(pattern), actual);
    }

    static boolean patternSatisfied(TypePattern pattern, ValueType actual) {
        if (pattern instanceof TypePattern.Any || pattern instanceof TypePattern.Unknown
                || pattern instanceof TypePattern.Variable) {
            return true;
        }
        if (pattern instanceof TypePattern.Scalar scalar) {
            return switch (scalar.kind()) {
                case NUMBER -> isAll(actual, PrimitiveKind.NUMBER);
                case STRING -> isStringLike(actual);
                case BOOLEAN -> isAll(actual, PrimitiveKind.BOOLEAN);
                case NULL -> isAll(actual, PrimitiveKind.NULL);
            };
        }
        if (pattern instanceof TypePattern.AnyRecord) {
            return isRecordLike(actual);
        }
        if (pattern instanceof TypePattern.AnySequence) {
            return isSequenceLike(actual);
        }
        if (pattern instanceof TypePattern.SequenceOf sequence) {
            return sequenceSatisfied(sequence.element(), actual);
        }
        if (pattern instanceof TypePattern.RecordOf record) {
            return recordSatisfied(record.value(), actual);
        }
        return true;
    }

    private static boolean sequenceSatisfied(TypePattern element, ValueType actual) {
        if (actual instanceof EmptyArrayType) {
            // 空数组不提供元素结构；纯"所有元素属于 T"的要求不被空数组违反。
            return true;
        }
        if (actual instanceof ArrayType array) {
            return patternSatisfied(element, array.elementType());
        }
        if (actual instanceof UnionType union) {
            return union.alternatives().stream().allMatch(branch -> sequenceSatisfied(element, branch));
        }
        return false;
    }

    private static boolean recordSatisfied(TypePattern value, ValueType actual) {
        if (actual instanceof ObjectType object) {
            return object.fields().values().stream().allMatch(field -> patternSatisfied(value, field));
        }
        if (actual instanceof UnionType union) {
            return union.alternatives().stream().allMatch(branch -> recordSatisfied(value, branch));
        }
        return false;
    }

    private static boolean isAll(ValueType type, PrimitiveKind kind) {
        if (type instanceof Primitive primitive) {
            return primitive.kind() == kind;
        }
        if (type instanceof UnionType union) {
            return !union.alternatives().isEmpty()
                    && union.alternatives().stream().allMatch(branch -> isAll(branch, kind));
        }
        return false;
    }

    private static boolean isStringLike(ValueType type) {
        if (type instanceof StringDomainType) {
            return true;
        }
        if (type instanceof UnionType union) {
            return !union.alternatives().isEmpty()
                    && union.alternatives().stream().allMatch(RuleEvaluator::isStringLike);
        }
        return isAll(type, PrimitiveKind.STRING);
    }

    private static boolean isRecordLike(ValueType type) {
        if (type instanceof ObjectType) {
            return true;
        }
        if (type instanceof UnionType union) {
            return !union.alternatives().isEmpty()
                    && union.alternatives().stream().allMatch(RuleEvaluator::isRecordLike);
        }
        return false;
    }

    static boolean isSequenceLike(ValueType type) {
        if (type instanceof ArrayType || type instanceof EmptyArrayType) {
            return true;
        }
        if (type instanceof UnionType union) {
            return !union.alternatives().isEmpty()
                    && union.alternatives().stream().allMatch(RuleEvaluator::isSequenceLike);
        }
        return false;
    }

    /** 变量下界合并：同一变量多次出现时取并集（下界合并），后者不覆盖前者。 */
    private static void mergeLowerBound(Map<String, ValueType> bounds, TypePattern pattern, ValueType actual) {
        if (pattern instanceof TypePattern.Variable variable) {
            bounds.merge(variable.name(), actual, (a, b) -> union(List.of(a, b)));
            return;
        }
        if (pattern instanceof TypePattern.SequenceOf sequence && sequence.element() instanceof TypePattern.Variable) {
            elementType(actual).ifPresent(element -> mergeVariableBound(bounds, sequence.element(), element));
            return;
        }
        if (pattern instanceof TypePattern.RecordOf record && record.value() instanceof TypePattern.Variable) {
            if (actual instanceof ObjectType object && !object.fields().isEmpty()) {
                mergeVariableBound(bounds, record.value(), union(List.copyOf(object.fields().values())));
            }
        }
    }

    private static void mergeVariableBound(Map<String, ValueType> bounds, TypePattern variablePattern,
            ValueType bound) {
        String name = ((TypePattern.Variable) variablePattern).name();
        bounds.merge(name, bound, (a, b) -> union(List.of(a, b)));
    }

    /** 未知输入（实参推断不出）为变量保留 Unknown 下界。 */
    private static void mergeUnknownBound(Map<String, ValueType> bounds, TypePattern pattern) {
        if (pattern instanceof TypePattern.Variable variable) {
            bounds.merge(variable.name(), WIDE_UNKNOWN, (a, b) -> union(List.of(a, b)));
        } else if (pattern instanceof TypePattern.SequenceOf sequence
                && sequence.element() instanceof TypePattern.Variable variable) {
            bounds.merge(variable.name(), WIDE_UNKNOWN, (a, b) -> union(List.of(a, b)));
        } else if (pattern instanceof TypePattern.RecordOf record
                && record.value() instanceof TypePattern.Variable variable) {
            bounds.merge(variable.name(), WIDE_UNKNOWN, (a, b) -> union(List.of(a, b)));
        }
    }

    private static ValueType instantiate(Rule rule, Map<String, ValueType> bounds, int argumentCount,
            CallSite callSite) {
        if (rule instanceof Rule.Constant constant) {
            return constantType(constant.pattern());
        }
        if (rule instanceof Rule.Argument argument) {
            return argumentOrWide(argument.index(), argumentCount, callSite);
        }
        if (rule instanceof Rule.ArgumentOr argumentOr) {
            if (argumentOr.index() < argumentCount) {
                return argumentOrWide(argumentOr.index(), argumentCount, callSite);
            }
            return instantiate(argumentOr.fallback(), bounds, argumentCount, callSite);
        }
        if (rule instanceof Rule.VariableRef variable) {
            // 完全没有信息的变量按宽上界处理，不拿 Never 伪装推断结果。
            return bounds.getOrDefault(variable.name(), WIDE_UNKNOWN);
        }
        if (rule instanceof Rule.UnionOfRules union) {
            List<ValueType> alternatives = new ArrayList<>();
            for (Rule alternative : union.alternatives()) {
                alternatives.add(instantiate(alternative, bounds, argumentCount, callSite));
            }
            return union(alternatives);
        }
        if (rule instanceof Rule.Apply apply) {
            return apply(apply, bounds, argumentCount, callSite);
        }
        return WIDE_UNKNOWN;
    }

    private static ValueType argumentOrWide(int index, int argumentCount, CallSite callSite) {
        if (index >= argumentCount) {
            return WIDE_UNKNOWN;
        }
        return callSite.argumentType(index).orElse(WIDE_UNKNOWN);
    }

    private static ValueType apply(Rule.Apply apply, Map<String, ValueType> bounds, int argumentCount,
            CallSite callSite) {
        return switch (apply.op()) {
            case SEQUENCE_ELEMENT ->
                sequenceElement(operandValue(apply.operands().get(0), bounds, argumentCount, callSite));
            case SEQUENCE_MAP ->
                sequenceMap(operandValue(apply.operands().get(0), bounds, argumentCount, callSite), bounds);
            case SEQUENCE_PRESERVE -> operandValue(apply.operands().get(0), bounds, argumentCount, callSite);
            case SEQUENCE_SUBSET ->
                sequenceSubset(operandValue(apply.operands().get(0), bounds, argumentCount, callSite));
            case SEQUENCE_FILTER -> sequenceSubset(argumentOrWide(0, argumentCount, callSite));
            case RECORD_OVERLAY -> recordOverlay(apply.operands().get(0), bounds, argumentCount, callSite);
        };
    }

    private static ValueType operandValue(Operand operand, Map<String, ValueType> bounds, int argumentCount,
            CallSite callSite) {
        if (operand instanceof Operand.RuleOperand ruleOperand) {
            return instantiate(ruleOperand.rule(), bounds, argumentCount, callSite);
        }
        throw new IllegalStateException("argumentsFrom is only legal for record.overlay");
    }

    /** 元素上界；空数组的元素为 Never——本系统无独立 Never 节点，用空联合语义的最接近表达：宽上界降级不安全，改为返回 empty[]。 */
    private static ValueType sequenceElement(ValueType source) {
        if (source instanceof EmptyArrayType) {
            // 空数组没有元素值可产出；返回 empty[] 让下游不虚构元素证据。
            return source;
        }
        return elementType(source).orElse(WIDE_UNKNOWN);
    }

    /** sequence.map：数量保留；元素替换为已求解的变量/body 结果；元素存在证据随映射结果失效。 */
    private static ValueType sequenceMap(ValueType source, Map<String, ValueType> bounds) {
        ValueType element = bounds.get("T");
        if (element == null) {
            element = elementType(source).orElse(WIDE_UNKNOWN);
        }
        if (source instanceof EmptyArrayType) {
            return source;
        }
        return new ArrayType(element, false);
    }

    /** sequence.subset：元素上界保留，数量下界降 0，元素存在证据删除。 */
    private static ValueType sequenceSubset(ValueType source) {
        if (source instanceof ArrayType array) {
            return array.withElementPresence(false);
        }
        return source;
    }

    /** record.overlay：有序覆盖。未知（无类型）实参可能覆盖前字段——保留存在性、值并入宽上界。 */
    private static ValueType recordOverlay(Operand operand, Map<String, ValueType> bounds, int argumentCount,
            CallSite callSite) {
        if (!(operand instanceof Operand.ArgumentsFrom from)) {
            return operandValue(operand, bounds, argumentCount, callSite);
        }
        Map<String, ValueType> merged = new LinkedHashMap<>();
        boolean anyUnknown = false;
        for (int index = from.index(); index < argumentCount; index++) {
            Optional<ValueType> actual = callSite.argumentType(index);
            if (actual.isPresent() && actual.get() instanceof ObjectType object) {
                merged.putAll(object.fields());
            } else if (actual.isEmpty()) {
                anyUnknown = true;
            } else {
                // 非对象实参已被模式检查报告；overlay 不采纳其值。
                anyUnknown = true;
            }
        }
        if (anyUnknown) {
            // 未知额外字段可能覆盖任意前字段：存在性保留，值类型并入宽上界（不虚假保留确定值类型）。
            merged.replaceAll((key, value) -> union(List.of(value, WIDE_UNKNOWN)));
        }
        if (merged.isEmpty() && anyUnknown) {
            return WIDE_UNKNOWN;
        }
        return new ObjectType(merged);
    }

    private static Optional<ValueType> elementType(ValueType type) {
        if (type instanceof ArrayType array) {
            return Optional.of(array.elementType());
        }
        if (type instanceof EmptyArrayType) {
            return Optional.empty();
        }
        if (type instanceof UnionType union && isSequenceLike(union)) {
            List<ValueType> elements = union.alternatives().stream().map(RuleEvaluator::elementType)
                    .flatMap(Optional::stream).toList();
            return elements.isEmpty() ? Optional.empty() : Optional.of(union(elements));
        }
        return Optional.empty();
    }

    private static ValueType constantType(TypePattern pattern) {
        if (pattern instanceof TypePattern.Scalar scalar) {
            return new Primitive(switch (scalar.kind()) {
                case NUMBER -> PrimitiveKind.NUMBER;
                case STRING -> PrimitiveKind.STRING;
                case BOOLEAN -> PrimitiveKind.BOOLEAN;
                case NULL -> PrimitiveKind.NULL;
            });
        }
        if (pattern instanceof TypePattern.AnySequence) {
            // 固定 array 返回但无元素信息：空数组是最粗的安全上界表达之外的保守选择——
            // 按设计"object/array 仅作为返回类型时给出最粗类型"，无元素证据时给 EmptyArrayType。
            return new EmptyArrayType();
        }
        if (pattern instanceof TypePattern.AnyRecord) {
            return new ObjectType(Map.of());
        }
        return WIDE_UNKNOWN;
    }

    static ValueType union(List<ValueType> values) {
        return ValueTypeOps.union(values);
    }

    private static String displayPattern(TypePattern pattern) {
        if (pattern instanceof TypePattern.Scalar scalar) {
            return scalar.kind().displayName();
        }
        if (pattern instanceof TypePattern.AnyRecord) {
            return "object";
        }
        if (pattern instanceof TypePattern.AnySequence) {
            return "array";
        }
        if (pattern instanceof TypePattern.SequenceOf sequence) {
            return displayPattern(sequence.element()) + "[]";
        }
        if (pattern instanceof TypePattern.RecordOf record) {
            return "Record<string, " + displayPattern(record.value()) + ">";
        }
        if (pattern instanceof TypePattern.Variable variable) {
            return variable.name();
        }
        return "any";
    }
}
