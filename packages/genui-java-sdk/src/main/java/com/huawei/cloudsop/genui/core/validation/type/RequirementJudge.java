/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import com.huawei.cloudsop.genui.core.validation.type.CheckResult.Verdict;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.Accept;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.AllOf;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.AnyOf;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.ExactlyOne;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.Predicate;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.RequireField;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.Unsupported;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ArrayType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ComponentType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.EmptyArrayType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ObjectType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.Primitive;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.StringDomainType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.UnionType;

import java.util.ArrayList;
import java.util.List;

/**
 * The composite-requirement decider (#82). It checks a {@link Fact} against an {@link Expectation}, combining composite
 * sub-requirements on the SAME observed value or an aligned partition before aggregating (design §6.4).
 *
 * <p>
 * The quantifier is "there exists one value that violates every candidate", NOT "each candidate has some value that
 * violates it". Internally each observed value / aligned partition is judged PASS / FAIL / UNKNOWN per sub-requirement;
 * a partition FAIL means every value in it fails, not merely that one counterexample was found in it. Only after
 * combining on a single point do the results aggregate to a public MATCH / MISMATCH / UNPROVEN:
 *
 * <ul>
 * <li>MATCH requires proving the whole check scope passes — observed samples passing while some part is uncovered is
 * not enough.</li>
 * <li>MISMATCH requires a valid observed value of the current check target that makes the whole composite FAIL, or the
 * composite failing on all possible values while the target provably exists (for array elements, an element must be
 * provably present — the empty-array possibility is never ignored).</li>
 * <li>An abstract partition failing without proof that the current value falls into it (or that the array contains such
 * an element) is UNPROVEN.</li>
 * </ul>
 *
 * <p>
 * The judge takes its input as a {@link Fact} so evidence/context/upstream-error identity participates in every
 * conclusion and cache key; the full evidence algebra is plugged in by #80 at integration.
 */
public final class RequirementJudge {

    /** Per-point verdict for one observed value / one aligned partition against one sub-requirement. */
    private enum Point {
        PASS, FAIL, UNKNOWN
    }

    /**
     * A fact-free cache key for pure type-inclusion relationships (sameType / proveIncluded). It depends only on the
     * type's structural identity — never on evidence, diagnostics, context or budget — so it stays valid across facts.
     */
    public static String typeOnlyKey(ValueType type) {
        return type.displayName();
    }

    /** Check {@code fact} against {@code expectation}, returning MATCH / MISMATCH / UNPROVEN. */
    public CheckResult check(Fact fact, Expectation expectation) {
        String key = fact.factKey() + "=>" + expectation.displayName();
        Verdict verdict = verdictFor(fact, expectation);
        return switch (verdict) {
            case MATCH -> CheckResult.match(key);
            case MISMATCH -> CheckResult.mismatch(counterExampleOf(fact), key);
            case UNPROVEN -> CheckResult.unproven(unprovenReason(fact, expectation), key);
        };
    }

    /** The public verdict: aggregate the per-point combination across the fact's aligned partitions. */
    private Verdict verdictFor(Fact fact, Expectation expectation) {
        if (expectation instanceof Unsupported) {
            return Verdict.UNPROVEN;
        }
        // Array targets are judged per element, honouring the empty-array possibility.
        if (fact.type() instanceof ArrayType array) {
            return aggregateElements(fact, array.elementType(), expectation, false);
        }
        if (fact.type() instanceof EmptyArrayType) {
            return Verdict.MATCH; // no element exists, so no element counterexample; vacuously satisfies all-elements
        }
        return combineOnSameValue(fact, fact.type(), expectation);
    }

    /**
     * Aggregate an element requirement over an array. A possibly-empty array can neither claim MATCH from missing
     * elements nor report MISMATCH without a provably-present violating element.
     */
    private Verdict aggregateElements(Fact fact, ValueType elementType, Expectation elementExpectation,
            boolean lengthKnownPositive) {
        if (elementType instanceof ValueType.UnknownType) {
            return Verdict.UNPROVEN; // uncovered elements: no full MATCH, no counterexample
        }
        if (elementType instanceof ValueType.NeverType) {
            // Never element with length allowed to be zero normalizes to a known-empty array (no element evidence).
            return Verdict.MATCH;
        }
        List<ValueType> partitions = partitionsOf(elementType);
        boolean anyUnknown = false;
        for (ValueType partition : partitions) {
            switch (combineOnSameValue(fact, partition, elementExpectation)) {
                case MISMATCH -> {
                    if (fact.hasCounterExampleEvidence()) {
                        return Verdict.MISMATCH; // one observed element fails the whole composite
                    }
                    anyUnknown = true; // abstract failure without a usable observation -> cannot prove
                }
                case UNPROVEN -> anyUnknown = true;
                case MATCH -> {
                    // this partition satisfied; keep checking the rest
                }
            }
        }
        return anyUnknown ? Verdict.UNPROVEN : Verdict.MATCH;
    }

    /**
     * Combine a (possibly composite) requirement on ONE aligned partition / observed value. MISMATCH here means the
     * whole requirement FAILS on this single value; it is the caller's job to decide whether that single value is a
     * usable observed counterexample.
     */
    private Verdict combineOnSameValue(Fact fact, ValueType value, Expectation expectation) {
        if (expectation instanceof Unsupported) {
            return Verdict.UNPROVEN;
        }
        if (value instanceof UnionType union && !(expectation instanceof AnyOf) && !(expectation instanceof ExactlyOne)
                && !(expectation instanceof AllOf)) {
            // A non-composite requirement against a union value: every alternative must pass on its own value. A
            // branch failing without usable observation evidence is not a proven counterexample (same rule as §6.4).
            boolean unproven = false;
            for (ValueType alternative : union.alternatives()) {
                Verdict v = combineOnSameValue(fact, alternative, expectation);
                if (v == Verdict.MISMATCH) {
                    if (fact.hasCounterExampleEvidence()) {
                        return Verdict.MISMATCH;
                    }
                    unproven = true;
                    continue;
                }
                unproven |= v == Verdict.UNPROVEN;
            }
            return unproven ? Verdict.UNPROVEN : Verdict.MATCH;
        }
        if (expectation instanceof Accept accept) {
            Verdict verdict = acceptOnSingleValue(value, accept.type());
            // A proven type conflict on an abstract possibility (no observed counterexample) is UNPROVEN, not MISMATCH.
            return verdict == Verdict.MISMATCH && !fact.hasCounterExampleEvidence() ? Verdict.UNPROVEN : verdict;
        }
        if (expectation instanceof RequireField requireField) {
            return requireFieldOnSingleValue(fact, value, requireField);
        }
        if (expectation instanceof SchemaExpectationCompiler.ElementRequirement elementRequirement) {
            return elementRequirementOnSingleValue(fact, value, elementRequirement);
        }
        if (expectation instanceof SchemaExpectationCompiler.ExtraFieldsRequirement extraFieldsRequirement) {
            return extraFieldsOnSingleValue(fact, value, extraFieldsRequirement);
        }
        if (expectation instanceof Predicate) {
            return Verdict.UNPROVEN; // predicates are preserved, not provable in this subset
        }
        // Composite: evaluate EVERY candidate on the SAME value, then combine per the quantifier table (design §6.4).
        List<Expectation> candidates = candidatesOf(expectation);
        List<Point> points = new ArrayList<>(candidates.size());
        for (Expectation candidate : candidates) {
            points.add(pointOnSingleValue(fact, value, candidate));
        }
        return switch (compositeKind(expectation)) {
            case ALL -> allOf(points);
            case ANY -> anyOf(points);
            case EXACTLY_ONE -> exactlyOne(points);
        };
    }

    /** Reduce a single value against a single candidate to PASS / FAIL / UNKNOWN (partition-level truth). */
    private Point pointOnSingleValue(Fact fact, ValueType value, Expectation candidate) {
        if (value instanceof UnionType union) {
            // The candidate must hold across the whole partition; split only to keep a consistent truth per point.
            boolean unknown = false;
            for (ValueType alternative : union.alternatives()) {
                Point p = pointOnSingleValue(fact, alternative, candidate);
                if (p == Point.FAIL) {
                    return Point.FAIL;
                }
                unknown |= p == Point.UNKNOWN;
            }
            return unknown ? Point.UNKNOWN : Point.PASS;
        }
        return switch (combineOnSameValue(fact, value, candidate)) {
            case MATCH -> Point.PASS;
            case MISMATCH -> Point.FAIL;
            case UNPROVEN -> Point.UNKNOWN;
        };
    }

    private Verdict allOf(List<Point> points) {
        boolean unknown = false;
        for (Point p : points) {
            if (p == Point.FAIL) {
                return Verdict.MISMATCH;
            }
            unknown |= p == Point.UNKNOWN;
        }
        return unknown ? Verdict.UNPROVEN : Verdict.MATCH;
    }

    private Verdict anyOf(List<Point> points) {
        boolean unknown = false;
        for (Point p : points) {
            if (p == Point.PASS) {
                return Verdict.MATCH;
            }
            unknown |= p == Point.UNKNOWN;
        }
        return unknown ? Verdict.UNPROVEN : Verdict.MISMATCH;
    }

    private Verdict exactlyOne(List<Point> points) {
        int pass = 0;
        boolean unknown = false;
        for (Point p : points) {
            if (p == Point.PASS) {
                pass++;
            } else if (p == Point.UNKNOWN) {
                unknown = true;
            }
        }
        if (pass == 1 && !unknown) {
            return Verdict.MATCH;
        }
        if (pass >= 2 || (pass == 0 && !unknown)) {
            return Verdict.MISMATCH;
        }
        return Verdict.UNPROVEN;
    }

    /** Whether a single observed value is provably accepted by a target value type. */
    private Verdict acceptOnSingleValue(ValueType value, ValueType target) {
        if (target instanceof ValueType.UnknownType) {
            return Verdict.MATCH; // explicit "accept anything" is provably satisfiable
        }
        if (value instanceof ValueType.UnknownType) {
            return Verdict.UNPROVEN; // unknown input proves neither inclusion nor a counterexample
        }
        if (value instanceof ValueType.NeverType) {
            return Verdict.MATCH; // no value exists to violate the target
        }
        if (target instanceof ValueType.NeverType) {
            return Verdict.MISMATCH;
        }
        if (target instanceof StringDomainType domain) {
            if (value instanceof StringDomainType observed) {
                return domain.values().containsAll(observed.values()) ? Verdict.MATCH : Verdict.MISMATCH;
            }
            if (value instanceof Primitive p && p.kind() == ValueType.PrimitiveKind.STRING) {
                return Verdict.UNPROVEN; // a general string may or may not land in the literal domain
            }
            return Verdict.MISMATCH;
        }
        if (value instanceof StringDomainType) {
            return target instanceof Primitive p && p.kind() == ValueType.PrimitiveKind.STRING
                    ? Verdict.MATCH
                    : Verdict.MISMATCH;
        }
        if (value instanceof Primitive p && target instanceof Primitive q) {
            return p.kind() == q.kind() ? Verdict.MATCH : Verdict.MISMATCH;
        }
        if (value instanceof ArrayType && target instanceof ArrayType) {
            return Verdict.UNPROVEN; // element-wise provenance is the caller's job; treated conservatively here
        }
        if (value instanceof EmptyArrayType && target instanceof ArrayType) {
            return Verdict.MATCH;
        }
        if (value instanceof ObjectType && target instanceof ObjectType) {
            return Verdict.UNPROVEN; // field-level checks flow through RequireField / AllOf, not raw object accept
        }
        if (value instanceof ComponentType a && target instanceof ComponentType b) {
            return a.name().equals(b.name()) ? Verdict.MATCH : Verdict.MISMATCH;
        }
        if (value.getClass() != target.getClass()) {
            return Verdict.MISMATCH;
        }
        return Verdict.UNPROVEN;
    }

    private Verdict requireFieldOnSingleValue(Fact fact, ValueType value, RequireField requireField) {
        if (value instanceof ObjectType object) {
            ValueType fieldValue = object.fields().get(requireField.name());
            if (fieldValue == null) {
                // Closed observed object without the field: the required field is provably absent.
                return Verdict.MISMATCH;
            }
            return combineOnSameValue(fact, fieldValue, requireField.expectation());
        }
        if (value instanceof ValueType.UnknownType) {
            return Verdict.UNPROVEN;
        }
        return Verdict.UNPROVEN; // non-object value: field presence is not provable in this subset
    }

    private Verdict elementRequirementOnSingleValue(Fact fact, ValueType value,
            SchemaExpectationCompiler.ElementRequirement elementRequirement) {
        if (value instanceof ArrayType array) {
            return aggregateElements(fact, array.elementType(), elementRequirement.element(), false);
        }
        if (value instanceof EmptyArrayType) {
            return Verdict.MATCH; // no element exists to violate the element requirement
        }
        if (value instanceof ValueType.UnknownType) {
            return Verdict.UNPROVEN;
        }
        return Verdict.MISMATCH; // a non-array value cannot satisfy an element-wise array requirement
    }

    private Verdict extraFieldsOnSingleValue(Fact fact, ValueType value,
            SchemaExpectationCompiler.ExtraFieldsRequirement extraFieldsRequirement) {
        // The subset does not model an open/closed object tail on proven ObjectType values; the additionalProperties
        // constraint is preserved but not provable here, so it stays UNPROVEN rather than fabricating a MATCH.
        if (value instanceof ObjectType || value instanceof ValueType.UnknownType) {
            return Verdict.UNPROVEN;
        }
        return Verdict.MISMATCH;
    }

    private List<ValueType> partitionsOf(ValueType type) {
        if (type instanceof UnionType union) {
            return union.alternatives();
        }
        return List.of(type);
    }

    private List<Expectation> candidatesOf(Expectation expectation) {
        if (expectation instanceof AnyOf any) {
            return any.candidates();
        }
        if (expectation instanceof ExactlyOne one) {
            return one.candidates();
        }
        if (expectation instanceof AllOf all) {
            return all.parts();
        }
        return List.of(expectation);
    }

    private Composite compositeKind(Expectation expectation) {
        if (expectation instanceof AnyOf) {
            return Composite.ANY;
        }
        if (expectation instanceof ExactlyOne) {
            return Composite.EXACTLY_ONE;
        }
        return Composite.ALL;
    }

    private enum Composite {
        ALL, ANY, EXACTLY_ONE
    }

    private String counterExampleOf(Fact fact) {
        return fact.type().displayName();
    }

    private String unprovenReason(Fact fact, Expectation expectation) {
        if (expectation instanceof Unsupported unsupported) {
            return "unsupported schema keyword: " + unsupported.keyword() + " at " + unsupported.schemaPath();
        }
        if (!fact.losses().isEmpty()) {
            return "precision loss: " + fact.losses();
        }
        return "insufficient evidence to prove or refute";
    }
}
