/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检查层(#77 第 6 节):{@code check(Fact, Expectation)} 返回 MATCH / MISMATCH / UNPROVEN。
 *
 * <p>核心纪律:
 *
 * <ul>
 *   <li>只有已证明违反静态使用要求、或有适用于当前检查对象的真实反例,才 MISMATCH;其余记录 UNPROVEN。
 *   <li>复合要求先对同一个值(或能对齐的共同分区)组合,再汇总:AnyOf 找的是“存在一个值违反全部候选”, 不是“每个候选分别存在反例”。
 *   <li>对未完整覆盖的样本不能证明全部通过;检查数组元素时不得忽略空数组可能性。
 *   <li>上游已报错的事实返回 UNPROVEN(BLOCKED_BY_ERROR),不产生独立反例。
 * </ul>
 *
 * @since 2026
 */
public final class Checks {

    private Checks() {}

    /** 公开检查结果三态。 */
    public enum Verdict {
        MATCH,
        MISMATCH,
        UNPROVEN
    }

    /** 一次检查的结论;未证明原因直接复用 {@link Fact.Loss}(含检查层专有的 INSUFFICIENT_EVIDENCE)。 */
    public record CheckResult(Verdict verdict, Set<Fact.Loss> reasons, String detail) {

        public CheckResult {
            reasons = reasons == null ? Set.of() : Set.copyOf(reasons);
        }

        static CheckResult match() {
            return new CheckResult(Verdict.MATCH, Set.of(), null);
        }

        static CheckResult mismatch(String detail) {
            return new CheckResult(Verdict.MISMATCH, Set.of(), detail);
        }

        static CheckResult unproven(Set<Fact.Loss> reasons, String detail) {
            return new CheckResult(Verdict.UNPROVEN, reasons, detail);
        }
    }

    /** 检查事实是否满足要求。{@code checkedPath} 是观察证据关联路径(如数据路径或 "literal");证据只在与检查 对象路径一致时参与判定。 */
    public static CheckResult check(Fact fact, Expectation expectation, String checkedPath) {
        if (fact.hasUpstreamErrors()) {
            return CheckResult.unproven(Set.of(Fact.Loss.BLOCKED_BY_ERROR), null);
        }
        if (expectation instanceof Expectation.Unsupported unsupported) {
            return CheckResult.unproven(
                    Set.of(Fact.Loss.UNSUPPORTED_RULE),
                    "unsupported schema keyword "
                            + unsupported.keyword()
                            + " at "
                            + unsupported.schemaPath());
        }
        if (expectation instanceof Expectation.Accept accept) {
            return checkAccept(fact, accept, checkedPath);
        }
        if (expectation instanceof SchemaCompiler.ExtraClosed extraClosed) {
            return checkExtraClosed(fact, extraClosed, checkedPath);
        }
        if (expectation instanceof SchemaCompiler.ExtraConstraint extraConstraint) {
            return checkExtraConstraint(fact, extraConstraint, checkedPath);
        }
        if (expectation instanceof Expectation.RequireField requireField) {
            return checkRequireField(fact, requireField, checkedPath);
        }
        if (expectation instanceof Expectation.ConditionalField conditionalField) {
            return checkConditionalField(fact, conditionalField, checkedPath);
        }
        if (expectation instanceof Expectation.EnumValues enumValues) {
            return checkEnum(fact, enumValues, checkedPath);
        }
        if (expectation instanceof Expectation.SequenceOf sequenceOf) {
            return checkSequenceOf(fact, sequenceOf, checkedPath);
        }
        if (expectation instanceof Expectation.AllOf allOf) {
            Set<Fact.Loss> reasons = new LinkedHashSet<>();
            for (Expectation part : allOf.expectations()) {
                CheckResult result = check(fact, part, checkedPath);
                if (result.verdict() == Verdict.MISMATCH) {
                    return result;
                }
                if (result.verdict() == Verdict.UNPROVEN) {
                    reasons.addAll(result.reasons());
                }
            }
            return reasons.isEmpty() ? CheckResult.match() : CheckResult.unproven(reasons, null);
        }
        if (expectation instanceof Expectation.AnyOf anyOf) {
            return checkComposite(fact, anyOf.expectations(), CompositeKind.ANY_OF, checkedPath);
        }
        if (expectation instanceof Expectation.ExactlyOne exactlyOne) {
            return checkComposite(
                    fact, exactlyOne.expectations(), CompositeKind.EXACTLY_ONE, checkedPath);
        }
        return CheckResult.unproven(Set.of(Fact.Loss.UNSUPPORTED_RULE), "unknown expectation kind");
    }

    private enum CompositeKind {
        ALL_OF,
        ANY_OF,
        EXACTLY_ONE
    }

    // ───────────────────────────────────────────────────────────────────────
    // Accept:类型包含 + 真实反例
    // ───────────────────────────────────────────────────────────────────────

    private static CheckResult checkAccept(
            Fact fact, Expectation.Accept accept, String checkedPath) {
        Type target = accept.type();
        if (target instanceof Type.Unknown) {
            return CheckResult.match();
        }
        Set<Fact.Loss> reasons = lossesAsReasons(fact);
        if (Relations.proveIncluded(fact.type(), target)) {
            return reasons.isEmpty()
                    ? CheckResult.match()
                    : CheckResult.unproven(reasons, "inclusion proven but analysis incomplete");
        }
        // 包含未证明:先查“全部可能值都不兼容”——数组情形须先证明当前确有元素(不能忽略空数组可能性)。
        if (Relations.proveDisjoint(fact.type(), target) && existenceProven(fact, checkedPath)) {
            return CheckResult.mismatch(
                    "all possible values are disjoint from " + target.displayName());
        }
        // 再按分支找类型级确定反例(某分支与目标互斥且该分支不是纯未知)。
        if (definiteTypeCounterexample(fact.type(), target)) {
            return CheckResult.mismatch(
                    "values of type "
                            + fact.type().displayName()
                            + " cannot satisfy "
                            + target.displayName());
        }
        // 最后找观察证据中适用于当前检查对象的真实反例。
        Counterexample counterexample = findCounterexample(fact, accept, checkedPath);
        if (counterexample != null) {
            return CheckResult.mismatch(counterexample.detail());
        }
        reasons.add(Fact.Loss.INSUFFICIENT_EVIDENCE);
        if (fact.type() instanceof Type.Unknown) {
            reasons.add(Fact.Loss.UNKNOWN_INPUT);
        }
        return CheckResult.unproven(reasons, "cannot prove inclusion into " + target.displayName());
    }

    /** 观察证据中适用于当前检查对象、且确定违反要求的值。 */
    private static Counterexample findCounterexample(
            Fact fact, Expectation.Accept accept, String checkedPath) {
        for (Fact.Evidence evidence : fact.evidence()) {
            if (evidence instanceof Fact.Evidence.ObservedValue observed
                    && (checkedPath == null || checkedPath.equals(observed.path()))) {
                Fact valueFact = SnapshotFacts.typeOfValue(observed.value(), observed.path(), 0);
                if (valueFact.type() instanceof Type.Never) {
                    continue;
                }
                CheckResult valueResult = checkObservedAccept(valueFact, accept, observed.path());
                if (valueResult.verdict() == Verdict.MISMATCH) {
                    return new Counterexample("observed counterexample at " + observed.path());
                }
            }
        }
        return null;
    }

    /**
     * 对观察值重建事实的 Accept 判定,不再下钻证据:类型判定(包含/互斥/确定反例)在该值自身类型上完成。 重建事实按路径携带与父级同源的证据,若回到完整 checkAccept
     * 会无限重访同一观察值(#83 评审发现的 终止性缺陷:`TextContent(@Sum(@Each([], "row", data.values)))` 栈溢出)。
     */
    private static CheckResult checkObservedAccept(
            Fact valueFact, Expectation.Accept accept, String checkedPath) {
        Type target = accept.type();
        if (target instanceof Type.Unknown) {
            return CheckResult.match();
        }
        if (Relations.proveIncluded(valueFact.type(), target)) {
            return CheckResult.match();
        }
        if (Relations.proveDisjoint(valueFact.type(), target)
                && existenceProven(valueFact, checkedPath)) {
            return CheckResult.mismatch(
                    "all possible values are disjoint from " + target.displayName());
        }
        if (definiteTypeCounterexample(valueFact.type(), target)) {
            return CheckResult.mismatch(
                    "values of type "
                            + valueFact.type().displayName()
                            + " cannot satisfy "
                            + target.displayName());
        }
        return CheckResult.unproven(
                Set.of(Fact.Loss.INSUFFICIENT_EVIDENCE),
                "cannot prove inclusion into " + target.displayName());
    }

    /** 类型级确定反例:存在与目标互斥的非 Never 分支(联合其余分支可能通过,但当前分支本身确定不满足; 仅当分支为具体类型时成立,Unknown 分支不算)。 */
    private static boolean definiteTypeCounterexample(Type source, Type target) {
        for (Type branch : TypeOps.flatten(source)) {
            if (branch instanceof Type.Never || branch instanceof Type.Unknown) {
                continue;
            }
            if (Relations.proveDisjoint(branch, target)) {
                // 数组分支允许零长度时,空数组不违反逐元素要求——不构成反例。
                if (branch instanceof Type.Sequence sequence && sequence.minLength() == 0) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private record Counterexample(String detail) {}

    /** 当前检查对象确实含有值(全不兼容判定的前提):类型非 Never,且不是“可能是空数组”的无元素情形。 */
    private static boolean existenceProven(Fact fact, String checkedPath) {
        if (fact.type() instanceof Type.Never) {
            return false;
        }
        for (Type branch : TypeOps.flatten(fact.type())) {
            if (branch instanceof Type.Sequence sequence && sequence.minLength() == 0) {
                // 空数组可能性不能忽略:没有元素证据就不能声称“全部值不兼容”。
                boolean hasElementEvidence =
                        fact.evidence().stream()
                                .anyMatch(e -> e instanceof Fact.Evidence.ElementPresence);
                if (!hasElementEvidence) {
                    return false;
                }
            }
        }
        return true;
    }

    // ───────────────────────────────────────────────────────────────────────
    // 额外字段约束(additionalProperties)
    // ───────────────────────────────────────────────────────────────────────

    /** additionalProperties: false——已列出字段之外的字段不得出现。 */
    private static CheckResult checkExtraClosed(
            Fact fact, SchemaCompiler.ExtraClosed extraClosed, String checkedPath) {
        return checkExtraFields(
                fact,
                extraClosed.declaredFields(),
                new Expectation.Accept(Type.Never.INSTANCE),
                checkedPath);
    }

    /** additionalProperties: schema——对象额外字段(未列出部分)的值须满足要求。 */
    private static CheckResult checkExtraConstraint(
            Fact fact, SchemaCompiler.ExtraConstraint extraConstraint, String checkedPath) {
        return checkExtraFields(
                fact, extraConstraint.declaredFields(), extraConstraint.expectation(), checkedPath);
    }

    private static CheckResult checkExtraFields(
            Fact fact, Set<String> declaredFields, Expectation requirement, String checkedPath) {
        for (Fact.Evidence evidence : fact.evidence()) {
            if (evidence instanceof Fact.Evidence.ObservedValue observed
                    && checkedPath != null
                    && checkedPath.equals(observed.path())
                    && observed.value() instanceof Map<?, ?> values) {
                for (Map.Entry<?, ?> entry : values.entrySet()) {
                    if (!declaredFields.contains(String.valueOf(entry.getKey()))
                            && check(
                                                    SnapshotFacts.typeOfValue(
                                                            entry.getValue(), null, 0),
                                                    requirement,
                                                    null)
                                            .verdict()
                                    == Verdict.MISMATCH) {
                        return CheckResult.mismatch(
                                "observed undeclared field violates additionalProperties");
                    }
                }
            }
        }
        boolean sawPass = false;
        boolean sawFail = false;
        Set<Fact.Loss> reasons = lossesAsReasons(fact);
        for (Type branch : TypeOps.flatten(fact.type())) {
            if (branch instanceof Type.Never) {
                continue;
            }
            if (!(branch instanceof Type.RecordType record)) {
                reasons.add(Fact.Loss.UNKNOWN_INPUT);
                continue;
            }
            boolean fails = false;
            boolean uncertain = false;
            for (Map.Entry<String, Type.Field> entry : record.fields().entrySet()) {
                if (declaredFields.contains(entry.getKey())) {
                    continue;
                }
                CheckResult result = check(Fact.of(entry.getValue().type()), requirement, null);
                if (result.verdict() == Verdict.MISMATCH) {
                    if (entry.getValue().presence() == Type.Presence.REQUIRED) {
                        fails = true;
                    } else {
                        uncertain = true;
                    }
                } else if (result.verdict() == Verdict.UNPROVEN) {
                    uncertain = true;
                    reasons.addAll(result.reasons());
                }
            }
            if (!record.extra().isClosed()) {
                CheckResult tail = check(Fact.of(record.extra().valueType()), requirement, null);
                if (tail.verdict() != Verdict.MATCH) {
                    uncertain = true;
                    reasons.addAll(tail.reasons());
                }
            }
            if (fails) {
                sawFail = true;
            } else if (uncertain) {
                reasons.add(Fact.Loss.INSUFFICIENT_EVIDENCE);
            } else {
                sawPass = true;
            }
        }
        if (sawFail && !sawPass && reasons.isEmpty()) {
            return CheckResult.mismatch("required extra field violates additionalProperties");
        }
        if (!sawFail && reasons.isEmpty()) {
            return CheckResult.match();
        }
        reasons.add(Fact.Loss.INSUFFICIENT_EVIDENCE);
        return CheckResult.unproven(reasons, null);
    }

    // ───────────────────────────────────────────────────────────────────────
    // RequireField:字段存在性 + 嵌套值要求
    // ───────────────────────────────────────────────────────────────────────

    private static CheckResult checkRequireField(
            Fact fact, Expectation.RequireField requireField, String checkedPath) {
        Set<Fact.Loss> reasons = lossesAsReasons(fact);
        // 1. 存在性:先找真实反例(观察到的行缺字段),再做结构判定。
        Presence presence = fieldPresence(fact, requireField.name());
        for (Fact.Evidence evidence : fact.evidence()) {
            if (evidence instanceof Fact.Evidence.FieldPresence observed
                    && (checkedPath == null || checkedPath.equals(observed.path()))
                    && observed.hasMissingCounterexample()) {
                return CheckResult.mismatch(
                        "observed rows missing required field \"" + requireField.name() + "\"");
            }
        }
        switch (presence) {
            case DEFINITELY_PRESENT -> {}
            case OBSERVED_PARTIAL_MISSING -> {
                return CheckResult.mismatch(
                        "required field \"" + requireField.name() + "\" is absent from some rows");
            }
            case DEFINITELY_ABSENT -> {
                return CheckResult.mismatch(
                        "required field \""
                                + requireField.name()
                                + "\" is absent from the proven row shape");
            }
            case UNKNOWN -> {
                reasons.add(Fact.Loss.INSUFFICIENT_EVIDENCE);
                if (fact.type() instanceof Type.Unknown) {
                    reasons.add(Fact.Loss.UNKNOWN_INPUT);
                }
                return CheckResult.unproven(
                        reasons, "cannot prove field \"" + requireField.name() + "\" exists");
            }
            default -> throw new IllegalStateException("unhandled presence " + presence);
        }
        // 2. 值要求:字段存在且类型上界已知时,检查嵌套期望。
        Type fieldType = fieldTypeOf(fact.type(), requireField.name());
        if (fieldType == null) {
            return CheckResult.unproven(reasons, null);
        }
        return check(
                new Fact(
                        fieldType,
                        fieldEvidence(fact, requireField.name()),
                        Set.of(),
                        fact.upstreamErrors()),
                requireField.expectation(),
                checkedPath);
    }

    /**
     * Table 列的字段存在性:任意一行带该字段即满足。
     *
     * <p>列渲染对缺值行天然容忍(渲染空单元格),因此"部分行有"是合法用法,不是静态错误——提示词侧的形状描述同样把这类字段标为 optional。只有全部行形状都证明没有该字段,才判
     * {@code type-table-column-missing}。与 {@link #checkRequireField} 的区别仅此一处:后者用于单对象 props 的
     * required 判定,部分缺失仍是错误。
     */
    static CheckResult checkColumnField(Fact fact, String field) {
        Presence presence = fieldPresence(fact, field);
        if (presence == Presence.DEFINITELY_ABSENT) {
            return CheckResult.mismatch(
                    "required field \"" + field + "\" is absent from the proven row shape");
        }
        if (presence == Presence.UNKNOWN) {
            Set<Fact.Loss> reasons = lossesAsReasons(fact);
            reasons.add(Fact.Loss.INSUFFICIENT_EVIDENCE);
            if (fact.type() instanceof Type.Unknown) {
                reasons.add(Fact.Loss.UNKNOWN_INPUT);
            }
            return CheckResult.unproven(reasons, "cannot prove field \"" + field + "\" exists");
        }
        return CheckResult.match();
    }

    /** 条件字段(properties 非 required 项):字段存在才查值要求;确知不存在即满足;存在性未知则未证明。 */
    private static CheckResult checkConditionalField(
            Fact fact, Expectation.ConditionalField conditionalField, String checkedPath) {
        Set<Fact.Loss> reasons = lossesAsReasons(fact);
        Presence presence = fieldPresence(fact, conditionalField.name());
        switch (presence) {
            case DEFINITELY_ABSENT -> {
                return reasons.isEmpty()
                        ? CheckResult.match()
                        : CheckResult.unproven(reasons, null);
            }
            case UNKNOWN -> {
                reasons.add(Fact.Loss.INSUFFICIENT_EVIDENCE);
                if (fact.type() instanceof Type.Unknown) {
                    reasons.add(Fact.Loss.UNKNOWN_INPUT);
                }
                return CheckResult.unproven(reasons, null);
            }
            default -> {}
        }
        Type fieldType = fieldTypeOf(fact.type(), conditionalField.name());
        if (fieldType == null) {
            return CheckResult.unproven(reasons, null);
        }
        return check(
                new Fact(
                        fieldType,
                        fieldEvidence(fact, conditionalField.name()),
                        Set.of(),
                        fact.upstreamErrors()),
                conditionalField.expectation(),
                checkedPath);
    }

    /**
     * 字段存在性:DEFINITELY_PRESENT(全部分支必有)/ DEFINITELY_ABSENT(结构必无)/
     * OBSERVED_PARTIAL_MISSING(结构必有于部分分支、必无于其余——行级部分缺失)/ UNKNOWN。
     */
    private enum Presence {
        DEFINITELY_PRESENT,
        DEFINITELY_ABSENT,
        OBSERVED_PARTIAL_MISSING,
        UNKNOWN
    }

    private static Presence fieldPresence(Fact fact, String name) {
        boolean possiblePresent = false;
        boolean possibleMissing = false;
        boolean anyBranch = false;
        for (Type branch : TypeOps.flatten(fact.type())) {
            anyBranch = true;
            if (branch instanceof Type.Unknown) {
                possiblePresent = true;
                possibleMissing = true;
                continue;
            }
            if (!(branch instanceof Type.RecordType record)) {
                possibleMissing = true;
                continue;
            }
            Type.Field field = record.fields().get(name);
            if (field != null && field.presence() == Type.Presence.REQUIRED) {
                possiblePresent = true;
            } else if (field != null || !record.extra().isClosed()) {
                possiblePresent = true;
                possibleMissing = true;
            } else {
                possibleMissing = true;
            }
        }
        if (possiblePresent && !possibleMissing) {
            return Presence.DEFINITELY_PRESENT;
        }
        if (!possiblePresent && possibleMissing) {
            return Presence.DEFINITELY_ABSENT;
        }
        if (possiblePresent && possibleMissing && anyBranch) {
            return Presence.OBSERVED_PARTIAL_MISSING;
        }
        return Presence.UNKNOWN;
    }

    private static Type fieldTypeOf(Type type, String name) {
        List<Type> values = new ArrayList<>();
        for (Type branch : TypeOps.flatten(type)) {
            if (branch instanceof Type.RecordType record) {
                Type.Field field = record.fields().get(name);
                if (field != null) {
                    values.add(field.type());
                } else if (!record.extra().isClosed()) {
                    values.add(record.extra().valueType());
                }
            } else if (branch instanceof Type.Unknown) {
                values.add(Type.Unknown.INSTANCE);
            } else {
                return null;
            }
        }
        return values.isEmpty() ? null : Types.union(values);
    }

    /** 字段级证据:整体 FieldPresence 证据适用于该字段;行级 ObservedValue 不下钻(本票只保留路径级)。 */
    private static Set<Fact.Evidence> fieldEvidence(Fact fact, String name) {
        Set<Fact.Evidence> result = new LinkedHashSet<>();
        for (Fact.Evidence evidence : fact.evidence()) {
            if (evidence instanceof Fact.Evidence.FieldPresence) {
                result.add(evidence);
            }
        }
        return result;
    }

    // ───────────────────────────────────────────────────────────────────────
    // SequenceOf:逐元素要求
    // ───────────────────────────────────────────────────────────────────────

    /**
     * 逐元素检查:元素上界满足嵌套要求即 MATCH(空数组不破坏);存在确定违反嵌套要求的元素(类型互斥的 非空分支,或观察到的实际元素)即 MISMATCH;否则 UNPROVEN。
     */
    private static CheckResult checkSequenceOf(
            Fact fact, Expectation.SequenceOf sequenceOf, String checkedPath) {
        Set<Fact.Loss> reasons = lossesAsReasons(fact);
        // 1. 观察到的实际数组元素:同一快照值逐个检查(真实反例)。证据可来自事实自身或调用方提供。
        List<Object> observedArrays = new ArrayList<>();
        for (Fact.Evidence evidence : fact.evidence()) {
            if (evidence instanceof Fact.Evidence.ObservedValue observed
                    && (checkedPath == null || checkedPath.equals(observed.path()))
                    && observed.value() instanceof List<?> list) {
                observedArrays.add(list);
            }
        }
        for (Object arrayValue : observedArrays) {
            for (Object element : (List<?>) arrayValue) {
                Fact elementFact = SnapshotFacts.typeOfValue(element, null, 0);
                CheckResult elementResult =
                        check(elementFact, sequenceOf.elementExpectation(), null);
                if (elementResult.verdict() == Verdict.MISMATCH) {
                    return CheckResult.mismatch(
                            "observed array element violates element requirement");
                }
            }
        }
        // 2. 类型上界:数组分支逐分支检查元素。
        boolean sawUnknown = false;
        for (Type branch : TypeOps.flatten(fact.type())) {
            if (branch instanceof Type.Unknown) {
                sawUnknown = true;
                continue;
            }
            if (!(branch instanceof Type.Sequence sequence)) {
                return CheckResult.mismatch(
                        "sequence requirement applied to " + branch.displayName());
            }
            // 元素存在性:数组下界大于零,或有元素存在证据,才允许“全部元素不兼容”判定。
            boolean elementExistence =
                    sequence.minLength() > 0
                            || fact.evidence().stream()
                                    .anyMatch(e -> e instanceof Fact.Evidence.ElementPresence);
            Set<Fact.Loss> elementLosses = new LinkedHashSet<>(fact.losses());
            if (elementExistence) {
                // 存在性已由证据/下界确证时,输入侧"可能为空"的丢失标记不再适用于逐元素判定
                // (#83 证据关联后元素类型可携带观察来源,该标记不能否决已观察到的存在)。
                elementLosses.remove(Fact.Loss.NO_ELEMENT_EVIDENCE);
            }
            Fact elementFact =
                    new Fact(
                            sequence.element(),
                            elementExistence
                                    ? Set.of(new Fact.Evidence.ElementPresence())
                                    : Set.of(),
                            elementLosses,
                            fact.upstreamErrors());
            CheckResult elementResult = check(elementFact, sequenceOf.elementExpectation(), null);
            if (elementResult.verdict() == Verdict.MISMATCH && elementExistence) {
                return CheckResult.mismatch(
                        "array elements cannot satisfy element requirement: "
                                + sequence.element().displayName());
            }
            if (elementResult.verdict() != Verdict.MATCH) {
                sawUnknown = true;
            }
        }
        if (sawUnknown) {
            reasons.add(Fact.Loss.INSUFFICIENT_EVIDENCE);
            return CheckResult.unproven(reasons, null);
        }
        return reasons.isEmpty() ? CheckResult.match() : CheckResult.unproven(reasons, null);
    }

    // ───────────────────────────────────────────────────────────────────────
    // Enum
    // ───────────────────────────────────────────────────────────────────────

    private static CheckResult checkEnum(
            Fact fact, Expectation.EnumValues enumValues, String checkedPath) {
        Type literals = Types.stringLiterals(enumValues.values());
        return checkAccept(fact, new Expectation.Accept(literals), checkedPath);
    }

    // ───────────────────────────────────────────────────────────────────────
    // 复合要求:先在同一个值/共同分区上组合,再汇总
    // ───────────────────────────────────────────────────────────────────────

    private static CheckResult checkComposite(
            Fact fact, List<Expectation> expectations, CompositeKind kind, String checkedPath) {
        // 1. 观察值路径:同一观察值上逐候选组合。
        List<Object> observedValues = observedValuesFor(fact, checkedPath);
        if (!observedValues.isEmpty()) {
            boolean sawFail = false;
            String failDetail = null;
            for (Object value : observedValues) {
                Fact valueFact = SnapshotFacts.typeOfValue(value, checkedPath, 0);
                Verdict verdict = combineOnValue(valueFact, expectations, kind, checkedPath);
                if (verdict == Verdict.MISMATCH) {
                    sawFail = true;
                    failDetail =
                            "observed value at " + checkedPath + " fails composite requirement";
                }
            }
            if (sawFail) {
                return CheckResult.mismatch(failDetail);
            }
            // Observations can refute a requirement, but passing samples do not cover the type
            // upper bound.
            // Continue with aligned type partitions to prove that every remaining possibility
            // passes.
        }
        // 2. 抽象路径:按联合分支逐分支组合(分支即分区),任一分支 FAIL 即该分区的所有值都失败。
        List<Type> branches = TypeOps.flatten(fact.type());
        boolean sawFail = false;
        boolean sawPass = false;
        boolean sawUnknown = false;
        for (Type branch : branches) {
            if (branch instanceof Type.Never) {
                continue;
            }
            Verdict branchVerdict = combineOnType(branch, expectations, kind);
            if (branchVerdict == Verdict.MISMATCH) {
                sawFail = true;
            } else if (branchVerdict == Verdict.UNPROVEN) {
                sawUnknown = true;
            } else {
                sawPass = true;
            }
        }
        if (sawFail && !sawPass && !sawUnknown && !(fact.type() instanceof Type.Never)) {
            return CheckResult.mismatch("composite requirement fails on every possible partition");
        }
        if (!sawFail && !sawUnknown) {
            Set<Fact.Loss> reasons = lossesAsReasons(fact);
            return reasons.isEmpty() ? CheckResult.match() : CheckResult.unproven(reasons, null);
        }
        Set<Fact.Loss> reasons = lossesAsReasons(fact);
        reasons.add(Fact.Loss.INSUFFICIENT_EVIDENCE);
        if (fact.type() instanceof Type.Unknown) {
            reasons.add(Fact.Loss.UNKNOWN_INPUT);
        }
        return CheckResult.unproven(reasons, null);
    }

    /** 适用于当前检查对象路径的观察值列表。 */
    private static List<Object> observedValuesFor(Fact fact, String checkedPath) {
        List<Object> values = new ArrayList<>();
        for (Fact.Evidence evidence : fact.evidence()) {
            if (evidence instanceof Fact.Evidence.ObservedValue observed
                    && (checkedPath == null || checkedPath.equals(observed.path()))) {
                values.add(observed.value());
            }
        }
        return values;
    }

    /** 同一确定值上的候选组合(每个候选先独立判定该值)。 */
    private static Verdict combineOnValue(
            Fact valueFact,
            List<Expectation> expectations,
            CompositeKind kind,
            String checkedPath) {
        List<Verdict> outcomes = new ArrayList<>();
        for (Expectation expectation : expectations) {
            outcomes.add(check(valueFact, expectation, checkedPath).verdict());
        }
        return combine(outcomes, kind);
    }

    /** 抽象分区上的候选组合:各类要求按分区语义判定(SequenceOf 递归进元素;复合要求按分区组合)。 */
    private static Verdict combineOnType(
            Type type, List<Expectation> expectations, CompositeKind kind) {
        List<Verdict> outcomes = new ArrayList<>();
        for (Expectation expectation : expectations) {
            outcomes.add(evaluateOnType(type, expectation));
        }
        return combine(outcomes, kind);
    }

    /** 单一要求在抽象分区上的判定。 */
    private static Verdict evaluateOnType(Type type, Expectation expectation) {
        if (expectation instanceof Expectation.Accept accept) {
            return acceptOnType(type, accept.type());
        }
        if (expectation instanceof Expectation.EnumValues enumValues) {
            return acceptOnType(type, Types.stringLiterals(enumValues.values()));
        }
        if (expectation instanceof Expectation.SequenceOf sequenceOf) {
            if (type instanceof Type.Unknown) {
                return Verdict.UNPROVEN;
            }
            if (!(type instanceof Type.Sequence sequence)) {
                return Verdict.MISMATCH;
            }
            if (sequence.maxLength() == 0) {
                return Verdict.MATCH;
            }
            List<Verdict> elements =
                    TypeOps.flatten(sequence.element()).stream()
                            .map(
                                    element ->
                                            evaluateOnType(
                                                    element, sequenceOf.elementExpectation()))
                            .toList();
            if (elements.stream().allMatch(result -> result == Verdict.MATCH)) {
                return Verdict.MATCH;
            }
            return sequence.minLength() > 0
                            && elements.stream().allMatch(result -> result == Verdict.MISMATCH)
                    ? Verdict.MISMATCH
                    : Verdict.UNPROVEN;
        }
        if (expectation instanceof Expectation.RequireField
                || expectation instanceof SchemaCompiler.ExtraClosed
                || expectation instanceof SchemaCompiler.ExtraConstraint) {
            return check(Fact.of(type), expectation, null).verdict();
        }
        if (expectation instanceof Expectation.ConditionalField conditionalField) {
            return conditionalFieldOnType(type, conditionalField);
        }
        if (expectation instanceof Expectation.AllOf allOf) {
            return combineOnType(type, allOf.expectations(), CompositeKind.ALL_OF);
        }
        if (expectation instanceof Expectation.AnyOf anyOf) {
            return combineOnType(type, anyOf.expectations(), CompositeKind.ANY_OF);
        }
        if (expectation instanceof Expectation.ExactlyOne exactlyOne) {
            return combineOnType(type, exactlyOne.expectations(), CompositeKind.EXACTLY_ONE);
        }
        return Verdict.UNPROVEN;
    }

    /** 条件字段在抽象分区上的判定:确知无此字段的分区满足;有此字段则值须满足;开放尾部未知。 */
    private static Verdict conditionalFieldOnType(
            Type type, Expectation.ConditionalField conditionalField) {
        if (!(type instanceof Type.RecordType record)) {
            return type instanceof Type.Unknown ? Verdict.UNPROVEN : Verdict.MISMATCH;
        }
        Type.Field field = record.fields().get(conditionalField.name());
        if (field == null) {
            return record.extra().isClosed() ? Verdict.MATCH : Verdict.UNPROVEN;
        }
        return evaluateOnType(field.type(), conditionalField.expectation());
    }

    /** 分区值集合对 Accept 的判定:数组分区按“逐元素满足”语义——包含看元素,互斥看元素且忽略空数组。 */
    private static Verdict acceptOnType(Type type, Type target) {
        if (Relations.proveIncluded(type, target)) {
            return Verdict.MATCH;
        }
        if (Relations.proveDisjoint(type, target)) {
            return Verdict.MISMATCH;
        }
        return Verdict.UNPROVEN;
    }

    private static Verdict combine(List<Verdict> outcomes, CompositeKind kind) {
        long pass = outcomes.stream().filter(o -> o == Verdict.MATCH).count();
        long fail = outcomes.stream().filter(o -> o == Verdict.MISMATCH).count();
        return switch (kind) {
            case ALL_OF ->
                    fail > 0
                            ? Verdict.MISMATCH
                            : pass == outcomes.size() ? Verdict.MATCH : Verdict.UNPROVEN;
            case ANY_OF ->
                    pass > 0
                            ? Verdict.MATCH
                            : fail == outcomes.size() ? Verdict.MISMATCH : Verdict.UNPROVEN;
            case EXACTLY_ONE ->
                    pass == 1 && fail == outcomes.size() - 1
                            ? Verdict.MATCH
                            : pass >= 2 || fail == outcomes.size()
                                    ? Verdict.MISMATCH
                                    : Verdict.UNPROVEN;
        };
    }

    /** Fact 损失直接作为检查原因的可变副本。 */
    static Set<Fact.Loss> lossesAsReasons(Fact fact) {
        return new LinkedHashSet<>(fact.losses());
    }
}
