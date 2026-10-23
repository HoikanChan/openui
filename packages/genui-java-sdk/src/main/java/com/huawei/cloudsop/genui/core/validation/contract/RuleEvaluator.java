/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.contract;

import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.Operand;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.Rule;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.TypePattern;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.TypeRule;
import com.huawei.cloudsop.genui.core.validation.type.analysis.Checks;
import com.huawei.cloudsop.genui.core.validation.type.analysis.Expectation;
import com.huawei.cloudsop.genui.core.validation.type.analysis.Fact;
import com.huawei.cloudsop.genui.core.validation.type.analysis.Type;
import com.huawei.cloudsop.genui.core.validation.type.analysis.TypeOps;
import com.huawei.cloudsop.genui.core.validation.type.analysis.Types;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * 结构化类型规则的求值器：把契约编译产物（{@link TypeRule}）落到 #80 类型内核（Type/Fact/TypeOps/Checks）上。
 *
 * <p>
 * 本类只持有规则语法与内核之间的映射，不含第二套类型语义：
 *
 * <ul>
 * <li>arity 与逐参模式检查（variadic 每个实参都检查）;</li>
 * <li>单次调用内的泛型求解：重复出现的类型变量合并下界（取并集），未知输入（Unknown 类型或带 loss 的事实） 并入 Unknown 下界——已知实参不能把未知输入盖掉;</li>
 * <li>运算按种类分派到 {@link TypeOps}（证据传播由内核承载：preserve 保留元素存在证据、subset 删除）。</li>
 * </ul>
 *
 * 结果带内核 Fact：Unknown 结果记 {@link Fact.Loss#UNKNOWN_INPUT}，未绑定变量记
 * {@link Fact.Loss#UNBOUND_TYPE_VARIABLE}——未证明对覆盖可见，不伪装成精确类型。
 *
 * @since 2026
 */
public final class RuleEvaluator {

    private RuleEvaluator() {
    }

    /** 宿主调用现场：实参事实、运算入口与诊断上报。 */
    public interface CallSite {
        /** 第 index 个实参的内核事实。 */
        Fact argument(int index);

        /** 内核类型运算入口（携带本次会话预算）。 */
        TypeOps ops();

        /** 实参 index 不满足调用形式的 accept 模式（pattern 按规则文本渲染）。 */
        void reportArgumentMismatch(int index, String expectedPattern, Fact actual);

        /** 实参数量不满足调用形式。 */
        void reportArityViolation(int minimum, OptionalInt maximum, int actual);
    }

    /**
     * 对一次调用求值：数量检查 → 逐参模式检查 → 变量下界合并 → 代入结果规则。
     *
     * @return 结果事实；信息不足时为 Unknown + 对应 loss，不是无类型
     */
    public static Fact evaluate(TypeRule rule, int argumentCount, CallSite callSite) {
        checkArity(rule.arity(), argumentCount, callSite);
        Map<String, Type> lowerBounds = new LinkedHashMap<>();
        List<RulePlan.TypeRule.ParamLayout> layouts = rule.parameters();
        for (int index = 0; index < argumentCount; index++) {
            TypePattern pattern = patternFor(layouts, index);
            if (pattern == null) {
                continue;
            }
            Fact actual = callSite.argument(index);
            checkPattern(pattern, actual, index, callSite);
            mergeLowerBound(lowerBounds, pattern, actual);
        }
        return instantiate(rule.result(), lowerBounds, argumentCount, callSite);
    }

    private static void checkArity(TypeRule.Arity arity, int actual, CallSite callSite) {
        if (actual < arity.min() || (arity.max().isPresent() && actual > arity.max().getAsInt())) {
            callSite.reportArityViolation(arity.min(), arity.max(), actual);
        }
    }

    /** 各位置的参数模式：非 rest 位置取自身模式，rest 位置之后的实参共用 rest 模式。 */
    private static TypePattern patternFor(List<TypeRule.ParamLayout> layouts, int index) {
        if (index < layouts.size()) {
            return layouts.get(index).accept();
        }
        if (!layouts.isEmpty() && layouts.get(layouts.size() - 1).rest()) {
            return layouts.get(layouts.size() - 1).accept();
        }
        return null;
    }

    /** 模式检查：编译为内核 Expectation，经 {@link Checks#check} 判定；只有 MISMATCH 上报。 */
    private static void checkPattern(TypePattern pattern, Fact actual, int index, CallSite callSite) {
        Expectation expectation = toExpectation(pattern);
        if (expectation == null || actual.hasUpstreamErrors()) {
            return;
        }
        Checks.CheckResult result = Checks.check(actual, expectation, null);
        if (result.verdict() == Checks.Verdict.MISMATCH) {
            callSite.reportArgumentMismatch(index, displayPattern(pattern), actual);
        }
    }

    /** TypePattern → 内核 Expectation；变量与 any 无静态入口要求（变量在求解时收集约束）。 */
    private static Expectation toExpectation(TypePattern pattern) {
        if (pattern instanceof TypePattern.Any || pattern instanceof TypePattern.Variable) {
            return new Expectation.Accept(Type.Unknown.INSTANCE);
        }
        if (pattern instanceof TypePattern.Unknown) {
            return null;
        }
        if (pattern instanceof TypePattern.Scalar scalar) {
            return new Expectation.Accept(Types.scalar(switch (scalar.kind()) {
                case NUMBER -> Type.ScalarKind.NUMBER;
                case STRING -> Type.ScalarKind.STRING;
                case BOOLEAN -> Type.ScalarKind.BOOLEAN;
                case NULL -> Type.ScalarKind.NULL;
            }));
        }
        if (pattern instanceof TypePattern.AnyRecord) {
            return new Expectation.Accept(Types.record(Map.of(), Type.Extra.open(Type.Unknown.INSTANCE)));
        }
        if (pattern instanceof TypePattern.AnySequence) {
            return new Expectation.Accept(Types.unknownSequence());
        }
        if (pattern instanceof TypePattern.SequenceOf sequence) {
            Expectation element = toExpectation(sequence.element());
            return element == null
                    ? new Expectation.Accept(Types.unknownSequence())
                    : new Expectation.SequenceOf(element);
        }
        if (pattern instanceof TypePattern.RecordOf record) {
            Expectation value = toExpectation(record.value());
            return value == null || !(value instanceof Expectation.Accept accept)
                    ? new Expectation.Accept(Types.record(Map.of(), Type.Extra.open(Type.Unknown.INSTANCE)))
                    : new Expectation.Accept(Types.record(Map.of(), Type.Extra.open(accept.type())));
        }
        return null;
    }

    /**
     * 单次调用内的泛型求解：变量每出现一次收集一条下界，重复出现取并集；未知输入（Unknown 类型、 或带精度损失的事实）并入 Unknown 下界——其他已知实参不能把 T 收窄成虚假的具体类型。
     */
    private static void mergeLowerBound(Map<String, Type> bounds, TypePattern pattern, Fact actual) {
        if (pattern instanceof TypePattern.Variable variable) {
            mergeBound(bounds, variable.name(), unknownIfNotPrecise(actual));
            return;
        }
        if (pattern instanceof TypePattern.SequenceOf sequence
                && sequence.element() instanceof TypePattern.Variable variable) {
            if (actual.hasUpstreamErrors()) {
                return;
            }
            List<Type> elements = new ArrayList<>();
            for (Type branch : flatten(actual.type())) {
                if (branch instanceof Type.Sequence seq) {
                    elements.add(seq.element());
                } else {
                    // 非数组分支（含 Unknown）参与约束：不能跳过未知输入。
                    elements.add(Type.Unknown.INSTANCE);
                }
            }
            if (!elements.isEmpty()) {
                bounds.merge(variable.name(), Types.union(elements), Types::union);
            }
            return;
        }
        if (pattern instanceof TypePattern.RecordOf record && record.value() instanceof TypePattern.Variable variable) {
            if (actual.hasUpstreamErrors()) {
                return;
            }
            List<Type> values = new ArrayList<>();
            for (Type branch : flatten(actual.type())) {
                if (branch instanceof Type.RecordType recordType) {
                    recordType.fields().values().forEach(field -> values.add(field.type()));
                    if (!recordType.extra().isClosed()) {
                        values.add(recordType.extra().valueType());
                    }
                } else {
                    values.add(Type.Unknown.INSTANCE);
                }
            }
            if (!values.isEmpty()) {
                bounds.merge(variable.name(), Types.union(values), Types::union);
            }
        }
    }

    /** 带 loss 的事实按未知参与下界（其精确类型不可信）；精确事实用原类型。 */
    private static Type unknownIfNotPrecise(Fact fact) {
        return fact.losses().isEmpty() ? fact.type() : Type.Unknown.INSTANCE;
    }

    private static void mergeBound(Map<String, Type> bounds, String name, Type bound) {
        bounds.merge(name, bound, Types::union);
    }

    private static Fact instantiate(Rule rule, Map<String, Type> bounds, int argumentCount, CallSite callSite) {
        if (rule instanceof Rule.Constant constant) {
            return constantType(constant.pattern());
        }
        if (rule instanceof Rule.Argument argument) {
            return argumentFact(argument.index(), argumentCount, callSite);
        }
        if (rule instanceof Rule.ArgumentOr argumentOr) {
            if (argumentOr.index() < argumentCount) {
                return argumentFact(argumentOr.index(), argumentCount, callSite);
            }
            return instantiate(argumentOr.fallback(), bounds, argumentCount, callSite);
        }
        if (rule instanceof Rule.VariableRef variable) {
            Type bound = bounds.get(variable.name());
            return bound == null ? Fact.unknown(Fact.Loss.UNBOUND_TYPE_VARIABLE) : Fact.of(bound);
        }
        if (rule instanceof Rule.UnionOfRules union) {
            List<Type> alternatives = new ArrayList<>();
            Set<Fact.Loss> losses = new LinkedHashSet<>();
            for (Rule alternative : union.alternatives()) {
                Fact resolved = instantiate(alternative, bounds, argumentCount, callSite);
                alternatives.add(resolved.type());
                losses.addAll(resolved.losses());
            }
            return new Fact(Types.union(alternatives), Set.of(), losses, Set.of());
        }
        if (rule instanceof Rule.Apply apply) {
            return apply(apply, bounds, argumentCount, callSite);
        }
        return Fact.unknown(Fact.Loss.UNSUPPORTED_RULE);
    }

    private static Fact argumentFact(int index, int argumentCount, CallSite callSite) {
        if (index >= argumentCount) {
            return Fact.unknown(Fact.Loss.MISSING_CONTEXT);
        }
        return callSite.argument(index);
    }

    /** 运算分派：按 op 种类进入内核 TypeOps，不感知函数名。 */
    private static Fact apply(Rule.Apply apply, Map<String, Type> bounds, int argumentCount, CallSite callSite) {
        TypeOps ops = callSite.ops();
        return switch (apply.op()) {
            case SEQUENCE_PRESERVE ->
                ops.preserve(operandFact(apply.operands().get(0), bounds, argumentCount, callSite));
            case SEQUENCE_SUBSET -> ops.subset(operandFact(apply.operands().get(0), bounds, argumentCount, callSite));
            case SEQUENCE_ELEMENT -> ops.element(operandFact(apply.operands().get(0), bounds, argumentCount, callSite));
            case SEQUENCE_FLATTEN_ONE ->
                ops.flattenOne(operandFact(apply.operands().get(0), bounds, argumentCount, callSite));
            case SEQUENCE_SELECT -> ops.select(operandFact(apply.operands().get(0), bounds, argumentCount, callSite));
            case RECORD_OVERLAY -> recordOverlay(apply.operands().get(0), bounds, argumentCount, callSite, ops);
            case SHAPE_MAP_SCALAR_OR_ARRAY ->
                ops.mapScalarOrArray(operandFact(apply.operands().get(0), bounds, argumentCount, callSite));
        };
    }

    private static Fact recordOverlay(Operand operand, Map<String, Type> bounds, int argumentCount, CallSite callSite,
            TypeOps ops) {
        if (!(operand instanceof Operand.ArgumentsFrom from)) {
            return ops.overlay(List.of(operandFact(operand, bounds, argumentCount, callSite)));
        }
        List<Fact> operands = new ArrayList<>();
        for (int index = from.index(); index < argumentCount; index++) {
            operands.add(callSite.argument(index));
        }
        if (operands.isEmpty()) {
            return Fact.unknown(Fact.Loss.MISSING_CONTEXT);
        }
        return ops.overlay(operands);
    }

    private static Fact operandFact(Operand operand, Map<String, Type> bounds, int argumentCount, CallSite callSite) {
        if (operand instanceof Operand.RuleOperand ruleOperand) {
            return instantiate(ruleOperand.rule(), bounds, argumentCount, callSite);
        }
        // argumentsFrom 只对接受参数序列的运算（record.overlay）合法——编译器已强制。
        return Fact.unknown(Fact.Loss.UNSUPPORTED_RULE);
    }

    /** 固定结果类型；明确声明的粗输出（object/array/unknown）合法存在，Unknown 记 UNKNOWN_INPUT 使影响可见。 */
    private static Fact constantType(TypePattern pattern) {
        if (pattern instanceof TypePattern.Scalar scalar) {
            return Fact.of(Types.scalar(switch (scalar.kind()) {
                case NUMBER -> Type.ScalarKind.NUMBER;
                case STRING -> Type.ScalarKind.STRING;
                case BOOLEAN -> Type.ScalarKind.BOOLEAN;
                case NULL -> Type.ScalarKind.NULL;
            }));
        }
        if (pattern instanceof TypePattern.AnyRecord) {
            return Fact.of(Types.record(Map.of(), Type.Extra.open(Type.Unknown.INSTANCE)));
        }
        if (pattern instanceof TypePattern.AnySequence) {
            return Fact.of(Types.unknownSequence());
        }
        return Fact.unknown(Fact.Loss.UNKNOWN_INPUT);
    }

    private static List<Type> flatten(Type type) {
        return TypeOps.flatten(type);
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
