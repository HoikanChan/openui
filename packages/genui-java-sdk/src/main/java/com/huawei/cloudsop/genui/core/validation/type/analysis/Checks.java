package com.huawei.cloudsop.genui.core.validation.type.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检查层(#77 第 6 节):{@code check(Fact, Expectation)} 返回 MATCH / MISMATCH / UNPROVEN。
 *
 * <p>
 * 核心纪律:
 * <ul>
 * <li>只有已证明违反静态使用要求、或有适用于当前检查对象的真实反例,才 MISMATCH;其余记录 UNPROVEN。
 * <li>复合要求先对同一个值(或能对齐的共同分区)组合,再汇总:AnyOf 找的是“存在一个值违反全部候选”,
 * 不是“每个候选分别存在反例”。
 * <li>对未完整覆盖的样本不能证明全部通过;检查数组元素时不得忽略空数组可能性。
 * <li>上游错误身份随结果带出,报告层据此抑制连锁诊断。
 * </ul>
 *
 * @since 2026
 */
public final class Checks {

    private Checks() {
    }

    /** 公开检查结果三态。 */
    public enum Verdict {
        MATCH,
        MISMATCH,
        UNPROVEN
    }

    /** 未证明原因(面向报告;与 {@link Fact.Loss} 分开,检查自身也会发现原因)。 */
    public enum Reason {
        UNKNOWN_INPUT,
        UNSUPPORTED_RULE,
        MISSING_CONTEXT,
        PRECISION_BUDGET,
        NO_ELEMENT_EVIDENCE,
        BLOCKED_BY_ERROR,
        UNBOUND_TYPE_VARIABLE,
        /** 既有可能通过也有可能失败,且无适用于当前检查对象的证据。 */
        INSUFFICIENT_EVIDENCE
    }

    /** 一次检查的结论。 */
    public record CheckResult(Verdict verdict, Set<Reason> reasons, Set<String> upstreamErrors, String detail) {

        public CheckResult {
            reasons = reasons == null ? Set.of() : Set.copyOf(reasons);
            upstreamErrors = upstreamErrors == null ? Set.of() : Set.copyOf(upstreamErrors);
        }

        static CheckResult of(Verdict verdict, Set<Reason> reasons, Set<String> upstreamErrors, String detail) {
            return new CheckResult(verdict, reasons, upstreamErrors, detail);
        }

        static CheckResult match() {
            return new CheckResult(Verdict.MATCH, Set.of(), Set.of(), null);
        }

        static CheckResult mismatch(String detail) {
            return new CheckResult(Verdict.MISMATCH, Set.of(), Set.of(), detail);
        }

        static CheckResult unproven(Set<Reason> reasons, Set<String> upstreamErrors, String detail) {
            return new CheckResult(Verdict.UNPROVEN, reasons, upstreamErrors, detail);
        }
    }

    /** 内部三态:单个值或共同分区上的判定。分区上 FAIL 必须表示其中所有值都不满足。 */
    private enum Tri {
        PASS,
        FAIL,
        UNKNOWN
    }

    /**
     * 检查事实是否满足要求。{@code checkedPath} 是观察证据关联路径(如数据路径或 "literal");证据只在与检查
     * 对象路径一致时参与判定。
     */
    public static CheckResult check(Fact fact, Expectation expectation, String checkedPath) {
        if (fact.hasUpstreamErrors()) {
            return CheckResult.unproven(Set.of(Reason.BLOCKED_BY_ERROR), fact.upstreamErrors(), null);
        }
        if (expectation instanceof Expectation.Unsupported unsupported) {
            return CheckResult.unproven(Set.of(Reason.UNSUPPORTED_RULE), Set.of(),
                    "unsupported schema keyword " + unsupported.keyword() + " at " + unsupported.schemaPath());
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
        if (expectation instanceof Expectation.Predicate predicate) {
            return checkPredicate(fact, predicate);
        }
        if (expectation instanceof Expectation.AllOf allOf) {
            Set<Reason> reasons = new LinkedHashSet<>();
            for (Expectation part : allOf.expectations()) {
                CheckResult result = check(fact, part, checkedPath);
                if (result.verdict() == Verdict.MISMATCH) {
                    return result;
                }
                if (result.verdict() == Verdict.UNPROVEN) {
                    reasons.addAll(result.reasons());
                }
            }
            return reasons.isEmpty() ? CheckResult.match()
                    : CheckResult.unproven(reasons, fact.upstreamErrors(), null);
        }
        if (expectation instanceof Expectation.AnyOf anyOf) {
            return checkComposite(fact, anyOf.expectations(), CompositeKind.ANY_OF, checkedPath);
        }
        if (expectation instanceof Expectation.ExactlyOne exactlyOne) {
            return checkComposite(fact, exactlyOne.expectations(), CompositeKind.EXACTLY_ONE, checkedPath);
        }
        return CheckResult.unproven(Set.of(Reason.UNSUPPORTED_RULE), Set.of(), "unknown expectation kind");
    }

    private enum CompositeKind {
        ALL_OF,
        ANY_OF,
        EXACTLY_ONE
    }

    // ───────────────────────────────────────────────────────────────────────
    // Accept:类型包含 + 真实反例
    // ───────────────────────────────────────────────────────────────────────

    private static CheckResult checkAccept(Fact fact, Expectation.Accept accept, String checkedPath) {
        Type target = accept.type();
        if (target instanceof Type.Unknown) {
            return CheckResult.match();
        }
        Set<Reason> reasons = lossesAsReasons(fact);
        if (Relations.proveIncluded(fact.type(), target) == Relations.Outcome.PROVEN) {
            return reasons.isEmpty() ? CheckResult.match()
                    : CheckResult.unproven(reasons, Set.of(), "inclusion proven but analysis incomplete");
        }
        // 包含未证明:先查“全部可能值都不兼容”——数组情形须先证明当前确有元素(不能忽略空数组可能性)。
        if (Relations.proveDisjoint(fact.type(), target) == Relations.Outcome.PROVEN
                && existenceProven(fact, checkedPath)) {
            return CheckResult.mismatch("all possible values are disjoint from " + target.displayName());
        }
        // 再按分支找类型级确定反例(某分支与目标互斥且该分支不是纯未知)。
        if (definiteTypeCounterexample(fact.type(), target)) {
            return CheckResult.mismatch("values of type " + fact.type().displayName()
                    + " cannot satisfy " + target.displayName());
        }
        // 最后找观察证据中适用于当前检查对象的真实反例。
        Counterexample counterexample = findCounterexample(fact, accept, checkedPath);
        if (counterexample != null) {
            return CheckResult.mismatch(counterexample.detail());
        }
        reasons.add(Reason.INSUFFICIENT_EVIDENCE);
        if (fact.type() instanceof Type.Unknown) {
            reasons.add(Reason.UNKNOWN_INPUT);
        }
        return CheckResult.unproven(reasons, Set.of(),
                "cannot prove inclusion into " + target.displayName());
    }

    /** 观察证据中适用于当前检查对象、且确定违反要求的值。 */
    private static Counterexample findCounterexample(Fact fact, Expectation.Accept accept, String checkedPath) {
        for (Fact.Evidence evidence : fact.evidence()) {
            if (evidence instanceof Fact.Evidence.ObservedValue observed
                    && (checkedPath == null || checkedPath.equals(observed.path()))) {
                Fact valueFact = SnapshotFacts.typeOfValue(observed.value(), observed.path(), 0);
                if (valueFact.type() instanceof Type.Never) {
                    continue;
                }
                CheckResult valueResult = checkAccept(valueFact, accept, observed.path());
                if (valueResult.verdict() == Verdict.MISMATCH) {
                    return new Counterexample("observed counterexample at " + observed.path());
                }
            }
        }
        return null;
    }

    /**
     * 类型级确定反例:存在与目标互斥的非 Never 分支(联合其余分支可能通过,但当前分支本身确定不满足;
     * 仅当分支为具体类型时成立,Unknown 分支不算)。
     */
    private static boolean definiteTypeCounterexample(Type source, Type target) {
        for (Type branch : TypeOps.flatten(source)) {
            if (branch instanceof Type.Never || branch instanceof Type.Unknown) {
                continue;
            }
            if (Relations.proveDisjoint(branch, target) == Relations.Outcome.PROVEN) {
                // 数组分支允许零长度时,空数组不违反逐元素要求——不构成反例。
                if (branch instanceof Type.Sequence sequence && sequence.minLength() == 0) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private record Counterexample(String detail) {
    }

    /**
     * 当前检查对象确实含有值(全不兼容判定的前提):类型非 Never,且不是“可能是空数组”的无元素情形。
     */
    private static boolean existenceProven(Fact fact, String checkedPath) {
        if (fact.type() instanceof Type.Never) {
            return false;
        }
        for (Type branch : TypeOps.flatten(fact.type())) {
            if (branch instanceof Type.Sequence sequence && sequence.minLength() == 0) {
                // 空数组可能性不能忽略:没有元素证据就不能声称“全部值不兼容”。
                boolean hasElementEvidence = fact.evidence().stream()
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
    private static CheckResult checkExtraClosed(Fact fact, SchemaCompiler.ExtraClosed extraClosed, String checkedPath) {
        return checkExtraFields(fact, extraClosed.declaredFields(), new Expectation.Accept(Type.Never.INSTANCE),
                checkedPath);
    }

    /** additionalProperties: schema——对象额外字段(未列出部分)的值须满足要求。 */
    private static CheckResult checkExtraConstraint(Fact fact, SchemaCompiler.ExtraConstraint extraConstraint,
            String checkedPath) {
        return checkExtraFields(fact, extraConstraint.declaredFields(), extraConstraint.expectation(), checkedPath);
    }

    private static CheckResult checkExtraFields(Fact fact, Set<String> declaredFields, Expectation requirement,
            String checkedPath) {
        for (Fact.Evidence evidence : fact.evidence()) {
            if (evidence instanceof Fact.Evidence.ObservedValue observed
                    && checkedPath != null && checkedPath.equals(observed.path())
                    && observed.value() instanceof Map<?, ?> values) {
                for (Map.Entry<?, ?> entry : values.entrySet()) {
                    if (!declaredFields.contains(String.valueOf(entry.getKey()))
                            && check(SnapshotFacts.typeOfValue(entry.getValue(), null, 0), requirement, null)
                                    .verdict() == Verdict.MISMATCH) {
                        return CheckResult.mismatch("observed undeclared field violates additionalProperties");
                    }
                }
            }
        }
        boolean sawPass = false;
        boolean sawFail = false;
        Set<Reason> reasons = lossesAsReasons(fact);
        for (Type branch : TypeOps.flatten(fact.type())) {
            if (branch instanceof Type.Never) {
                continue;
            }
            if (!(branch instanceof Type.RecordType record)) {
                reasons.add(Reason.UNKNOWN_INPUT);
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
                reasons.add(Reason.INSUFFICIENT_EVIDENCE);
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
        reasons.add(Reason.INSUFFICIENT_EVIDENCE);
        return CheckResult.unproven(reasons, fact.upstreamErrors(), null);
    }

    // ───────────────────────────────────────────────────────────────────────
    // RequireField:字段存在性 + 嵌套值要求
    // ───────────────────────────────────────────────────────────────────────

    private static CheckResult checkRequireField(Fact fact, Expectation.RequireField requireField,
            String checkedPath) {
        Set<Reason> reasons = lossesAsReasons(fact);
        // 1. 存在性:先找真实反例(观察到的行缺字段),再做结构判定。
        Presence presence = fieldPresence(fact, requireField.name());
        for (Fact.Evidence evidence : fact.evidence()) {
            if (evidence instanceof Fact.Evidence.FieldPresence observed
                    && (checkedPath == null || checkedPath.equals(observed.path()))
                    && observed.hasMissingCounterexample()) {
                return CheckResult.mismatch("observed rows missing required field \"" + requireField.name() + "\"");
            }
        }
        switch (presence) {
            case DEFINITELY_PRESENT -> {
            }
            case OBSERVED_PARTIAL_MISSING -> {
                return CheckResult.mismatch("required field \"" + requireField.name()
                        + "\" is absent from some rows");
            }
            case DEFINITELY_ABSENT -> {
                return CheckResult.mismatch("required field \"" + requireField.name()
                        + "\" is absent from the proven row shape");
            }
            case UNKNOWN -> {
                reasons.add(Reason.INSUFFICIENT_EVIDENCE);
                if (fact.type() instanceof Type.Unknown) {
                    reasons.add(Reason.UNKNOWN_INPUT);
                }
                return CheckResult.unproven(reasons, fact.upstreamErrors(),
                        "cannot prove field \"" + requireField.name() + "\" exists");
            }
        }
        // 2. 值要求:字段存在且类型上界已知时,检查嵌套期望。
        Type fieldType = fieldTypeOf(fact.type(), requireField.name());
        if (fieldType == null) {
            return CheckResult.unproven(reasons, fact.upstreamErrors(), null);
        }
        return check(new Fact(fieldType, fieldEvidence(fact, requireField.name()), Set.of(),
                fact.upstreamErrors()), requireField.expectation(), checkedPath);
    }

    /**
     * 条件字段(properties 非 required 项):字段存在才查值要求;确知不存在即满足;存在性未知则未证明。
     */
    private static CheckResult checkConditionalField(Fact fact, Expectation.ConditionalField conditionalField,
            String checkedPath) {
        Set<Reason> reasons = lossesAsReasons(fact);
        Presence presence = fieldPresence(fact, conditionalField.name());
        switch (presence) {
            case DEFINITELY_ABSENT -> {
                return reasons.isEmpty() ? CheckResult.match()
                        : CheckResult.unproven(reasons, fact.upstreamErrors(), null);
            }
            case UNKNOWN -> {
                reasons.add(Reason.INSUFFICIENT_EVIDENCE);
                if (fact.type() instanceof Type.Unknown) {
                    reasons.add(Reason.UNKNOWN_INPUT);
                }
                return CheckResult.unproven(reasons, fact.upstreamErrors(), null);
            }
            default -> {
            }
        }
        Type fieldType = fieldTypeOf(fact.type(), conditionalField.name());
        if (fieldType == null) {
            return CheckResult.unproven(reasons, fact.upstreamErrors(), null);
        }
        return check(new Fact(fieldType, fieldEvidence(fact, conditionalField.name()), Set.of(),
                fact.upstreamErrors()), conditionalField.expectation(), checkedPath);
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
     * 逐元素检查:元素上界满足嵌套要求即 MATCH(空数组不破坏);存在确定违反嵌套要求的元素(类型互斥的
     * 非空分支,或观察到的实际元素)即 MISMATCH;否则 UNPROVEN。
     */
    private static CheckResult checkSequenceOf(Fact fact, Expectation.SequenceOf sequenceOf, String checkedPath) {
        Set<Reason> reasons = lossesAsReasons(fact);
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
                CheckResult elementResult = check(elementFact, sequenceOf.elementExpectation(), null);
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
            boolean elementExistence = sequence.minLength() > 0 || fact.evidence().stream()
                    .anyMatch(e -> e instanceof Fact.Evidence.ElementPresence);
            Set<Fact.Loss> elementLosses = new LinkedHashSet<>(fact.losses());
            if (elementExistence) {
                // 存在性已由证据/下界确证时,输入侧"可能为空"的丢失标记不再适用于逐元素判定
                // (#83 证据关联后元素类型可携带观察来源,该标记不能否决已观察到的存在)。
                elementLosses.remove(Fact.Loss.NO_ELEMENT_EVIDENCE);
            }
            Fact elementFact = new Fact(sequence.element(),
                    elementExistence ? Set.of(new Fact.Evidence.ElementPresence()) : Set.of(),
                    elementLosses, fact.upstreamErrors());
            CheckResult elementResult = check(elementFact, sequenceOf.elementExpectation(), null);
            if (elementResult.verdict() == Verdict.MISMATCH && elementExistence) {
                return CheckResult.mismatch("array elements cannot satisfy element requirement: "
                        + sequence.element().displayName());
            }
            if (elementResult.verdict() != Verdict.MATCH) {
                sawUnknown = true;
            }
        }
        if (sawUnknown) {
            reasons.add(Reason.INSUFFICIENT_EVIDENCE);
            return CheckResult.unproven(reasons, fact.upstreamErrors(), null);
        }
        return reasons.isEmpty() ? CheckResult.match()
                : CheckResult.unproven(reasons, fact.upstreamErrors(), null);
    }

    // ───────────────────────────────────────────────────────────────────────
    // Enum / Predicate
    // ───────────────────────────────────────────────────────────────────────

    private static CheckResult checkEnum(Fact fact, Expectation.EnumValues enumValues, String checkedPath) {
        Type literals = Types.stringLiterals(enumValues.values());
        return checkAccept(fact, new Expectation.Accept(literals), checkedPath);
    }

    private static CheckResult checkPredicate(Fact fact, Expectation.Predicate predicate) {
        Set<Reason> reasons = lossesAsReasons(fact);
        boolean possibleSequence = false;
        Long min = null;
        Long max = null;
        for (Type branch : TypeOps.flatten(fact.type())) {
            if (branch instanceof Type.Sequence sequence) {
                possibleSequence = true;
                min = min == null ? sequence.minLength() : Math.min(min, sequence.minLength());
                long branchMax = sequence.maxLength();
                max = max == null || branchMax == Type.Sequence.UNBOUNDED
                        ? (branchMax == Type.Sequence.UNBOUNDED ? null : Math.max(max, branchMax))
                        : Math.max(max, branchMax);
            } else if (branch instanceof Type.Unknown) {
                reasons.add(Reason.UNKNOWN_INPUT);
            } else {
                return CheckResult.mismatch("length predicate applied to " + branch.displayName());
            }
        }
        if (!possibleSequence && min == null) {
            reasons.add(Reason.INSUFFICIENT_EVIDENCE);
            return CheckResult.unproven(reasons, fact.upstreamErrors(), null);
        }
        return switch (predicate.kind()) {
            case MIN_LENGTH -> {
                if (min != null && min >= predicate.parameter()) {
                    yield CheckResult.match();
                }
                if (max != null && max < predicate.parameter()) {
                    yield CheckResult.mismatch("length upper bound " + max + " < required " + predicate.parameter());
                }
                reasons.add(Reason.NO_ELEMENT_EVIDENCE);
                yield CheckResult.unproven(reasons, fact.upstreamErrors(), null);
            }
            case MAX_LENGTH -> {
                if (max != null && max <= predicate.parameter()) {
                    yield CheckResult.match();
                }
                if (min != null && min > predicate.parameter()) {
                    yield CheckResult.mismatch("length lower bound " + min + " > allowed " + predicate.parameter());
                }
                reasons.add(Reason.INSUFFICIENT_EVIDENCE);
                yield CheckResult.unproven(reasons, fact.upstreamErrors(), null);
            }
        };
    }

    // ───────────────────────────────────────────────────────────────────────
    // 复合要求:先在同一个值/共同分区上组合,再汇总
    // ───────────────────────────────────────────────────────────────────────

    private static CheckResult checkComposite(Fact fact, List<Expectation> expectations, CompositeKind kind,
            String checkedPath) {
        // 1. 观察值路径:同一观察值上逐候选组合。
        List<Object> observedValues = observedValuesFor(fact, checkedPath);
        if (!observedValues.isEmpty()) {
            boolean sawFail = false;
            String failDetail = null;
            for (Object value : observedValues) {
                Fact valueFact = SnapshotFacts.typeOfValue(value, checkedPath, 0);
                Tri tri = combineOnValue(valueFact, expectations, kind, checkedPath);
                if (tri == Tri.FAIL) {
                    sawFail = true;
                    failDetail = "observed value at " + checkedPath + " fails composite requirement";
                }
            }
            if (sawFail) {
                return CheckResult.mismatch(failDetail);
            }
            // Observations can refute a requirement, but passing samples do not cover the type upper bound.
            // Continue with aligned type partitions to prove that every remaining possibility passes.
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
            Tri branchTri = combineOnType(branch, expectations, kind);
            if (branchTri == Tri.FAIL) {
                sawFail = true;
            } else if (branchTri == Tri.UNKNOWN) {
                sawUnknown = true;
            } else {
                sawPass = true;
            }
        }
        if (sawFail && !sawPass && !sawUnknown && !(fact.type() instanceof Type.Never)) {
            return CheckResult.mismatch("composite requirement fails on every possible partition");
        }
        if (!sawFail && !sawUnknown) {
            Set<Reason> reasons = lossesAsReasons(fact);
            return reasons.isEmpty() ? CheckResult.match()
                    : CheckResult.unproven(reasons, fact.upstreamErrors(), null);
        }
        Set<Reason> reasons = lossesAsReasons(fact);
        reasons.add(Reason.INSUFFICIENT_EVIDENCE);
        if (fact.type() instanceof Type.Unknown) {
            reasons.add(Reason.UNKNOWN_INPUT);
        }
        return CheckResult.unproven(reasons, fact.upstreamErrors(), null);
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
    private static Tri combineOnValue(Fact valueFact, List<Expectation> expectations, CompositeKind kind,
            String checkedPath) {
        List<Tri> outcomes = new ArrayList<>();
        for (Expectation expectation : expectations) {
            CheckResult result = check(valueFact, expectation, checkedPath);
            outcomes.add(switch (result.verdict()) {
                case MATCH -> Tri.PASS;
                case MISMATCH -> Tri.FAIL;
                case UNPROVEN -> Tri.UNKNOWN;
            });
        }
        return combine(outcomes, kind);
    }

    /** 抽象分区上的候选组合:各类要求按分区语义判定(SequenceOf 递归进元素;复合要求按分区组合)。 */
    private static Tri combineOnType(Type type, List<Expectation> expectations, CompositeKind kind) {
        List<Tri> outcomes = new ArrayList<>();
        for (Expectation expectation : expectations) {
            outcomes.add(evaluateOnType(type, expectation));
        }
        return combine(outcomes, kind);
    }

    /** 单一要求在抽象分区上的判定。 */
    private static Tri evaluateOnType(Type type, Expectation expectation) {
        if (expectation instanceof Expectation.Accept accept) {
            return acceptOnType(type, accept.type());
        }
        if (expectation instanceof Expectation.EnumValues enumValues) {
            return acceptOnType(type, Types.stringLiterals(enumValues.values()));
        }
        if (expectation instanceof Expectation.SequenceOf sequenceOf) {
            if (type instanceof Type.Unknown) {
                return Tri.UNKNOWN;
            }
            if (!(type instanceof Type.Sequence sequence)) {
                return Tri.FAIL;
            }
            if (sequence.maxLength() == 0) {
                return Tri.PASS;
            }
            List<Tri> elements = TypeOps.flatten(sequence.element()).stream()
                    .map(element -> evaluateOnType(element, sequenceOf.elementExpectation())).toList();
            if (elements.stream().allMatch(result -> result == Tri.PASS)) {
                return Tri.PASS;
            }
            return sequence.minLength() > 0 && elements.stream().allMatch(result -> result == Tri.FAIL)
                    ? Tri.FAIL : Tri.UNKNOWN;
        }
        if (expectation instanceof Expectation.RequireField
                || expectation instanceof SchemaCompiler.ExtraClosed
                || expectation instanceof SchemaCompiler.ExtraConstraint) {
            return switch (check(Fact.of(type), expectation, null).verdict()) {
                case MATCH -> Tri.PASS;
                case MISMATCH -> Tri.FAIL;
                case UNPROVEN -> Tri.UNKNOWN;
            };
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
        if (expectation instanceof Expectation.Unsupported) {
            return Tri.UNKNOWN;
        }
        return Tri.UNKNOWN;
    }

    /** 条件字段在抽象分区上的判定:确知无此字段的分区满足;有此字段则值须满足;开放尾部未知。 */
    private static Tri conditionalFieldOnType(Type type, Expectation.ConditionalField conditionalField) {
        if (!(type instanceof Type.RecordType record)) {
            return type instanceof Type.Unknown ? Tri.UNKNOWN : Tri.FAIL;
        }
        Type.Field field = record.fields().get(conditionalField.name());
        if (field == null) {
            return record.extra().isClosed() ? Tri.PASS : Tri.UNKNOWN;
        }
        return evaluateOnType(field.type(), conditionalField.expectation());
    }

    /** 分区值集合对 Accept 的判定:数组分区按“逐元素满足”语义——包含看元素,互斥看元素且忽略空数组。 */
    private static Tri acceptOnType(Type type, Type target) {
        if (Relations.proveIncluded(type, target) == Relations.Outcome.PROVEN) {
            return Tri.PASS;
        }
        if (Relations.proveDisjoint(type, target) == Relations.Outcome.PROVEN) {
            return Tri.FAIL;
        }
        return Tri.UNKNOWN;
    }

    private static Tri combine(List<Tri> outcomes, CompositeKind kind) {
        long pass = outcomes.stream().filter(o -> o == Tri.PASS).count();
        long fail = outcomes.stream().filter(o -> o == Tri.FAIL).count();
        return switch (kind) {
            case ALL_OF -> fail > 0 ? Tri.FAIL : pass == outcomes.size() ? Tri.PASS : Tri.UNKNOWN;
            case ANY_OF -> pass > 0 ? Tri.PASS : fail == outcomes.size() ? Tri.FAIL : Tri.UNKNOWN;
            case EXACTLY_ONE -> pass == 1 && fail == outcomes.size() - 1 ? Tri.PASS
                    : pass >= 2 || fail == outcomes.size() ? Tri.FAIL : Tri.UNKNOWN;
        };
    }

    /** Fact 损失映射为检查原因。 */
    static Set<Reason> lossesAsReasons(Fact fact) {
        Set<Reason> reasons = new LinkedHashSet<>();
        for (Fact.Loss loss : fact.losses()) {
            reasons.add(switch (loss) {
                case UNKNOWN_INPUT -> Reason.UNKNOWN_INPUT;
                case UNSUPPORTED_RULE -> Reason.UNSUPPORTED_RULE;
                case MISSING_CONTEXT -> Reason.MISSING_CONTEXT;
                case PRECISION_BUDGET -> Reason.PRECISION_BUDGET;
                case NO_ELEMENT_EVIDENCE -> Reason.NO_ELEMENT_EVIDENCE;
                case BLOCKED_BY_ERROR -> Reason.BLOCKED_BY_ERROR;
                case UNBOUND_TYPE_VARIABLE -> Reason.UNBOUND_TYPE_VARIABLE;
            });
        }
        return reasons;
    }
}
