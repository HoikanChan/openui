/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.validation.type.CheckResult.Verdict;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.Accept;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.AnyOf;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.ExactlyOne;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.RequireField;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.Unsupported;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ArrayType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.EmptyArrayType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ObjectType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.Primitive;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.PrimitiveKind;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.StringDomainType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.UnionType;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finite-model checks for the composite-requirement decider (#82): AnyOf/ExactlyOne/AllOf combine sub-requirements on
 * the SAME observed value or an aligned partition before aggregation, never stitching counterexamples across
 * candidates.
 */
class RequirementJudgeTest {

    private final RequirementJudge judge = new RequirementJudge();

    private static Fact fact(ValueType type) {
        return Fact.adapt(type);
    }

    private static Accept number() {
        return new Accept(new Primitive(PrimitiveKind.NUMBER));
    }

    private static Accept string() {
        return new Accept(new Primitive(PrimitiveKind.STRING));
    }

    private static Accept unknownAccept() {
        return new Accept(ValueType.unknown());
    }

    @Test
    void anyOfMatchesWhenEachElementSatisfiesSomeCandidate() {
        // [1, "x"] : every element passes AnyOf(number,string) on its own value.
        ArrayType array = new ArrayType(
                new UnionType(List.of(new Primitive(PrimitiveKind.NUMBER), new StringDomainType(Set.of("x")))));
        CheckResult result = judge.check(fact(array), new AnyOf(List.of(number(), string())));
        assertEquals(Verdict.MATCH, result.verdict());
    }

    @Test
    void exactlyOneMatchesWhenEachElementSatisfiesExactlyOneCandidate() {
        // [1, "x"] : ExactlyOne(number,string) — 1 hits only number, "x" hits only string.
        ArrayType array = new ArrayType(
                new UnionType(List.of(new Primitive(PrimitiveKind.NUMBER), new StringDomainType(Set.of("x")))));
        CheckResult result = judge.check(fact(array), new ExactlyOne(List.of(number(), string())));
        assertEquals(Verdict.MATCH, result.verdict());
    }

    @Test
    void anyOfMismatchesOnlyWhenOneObservedValueFailsEveryCandidate() {
        // [1, "x", true] : the SAME observed true fails both number and string -> real counterexample.
        ArrayType array = new ArrayType(new UnionType(List.of(new Primitive(PrimitiveKind.NUMBER),
                new StringDomainType(Set.of("x")), new Primitive(PrimitiveKind.BOOLEAN))));
        CheckResult result = judge.check(fact(array), new AnyOf(List.of(number(), string())));
        assertEquals(Verdict.MISMATCH, result.verdict());
        assertTrue(result.counterExample().isPresent(), "must name the single value violating all candidates");
    }

    @Test
    void exactlyOneMismatchesWhenOneValueSatisfiesTwoBranches() {
        // ExactlyOne(number, Unknown) over [1,"x"]: the same value 1 satisfies both branches.
        ArrayType array = new ArrayType(
                new UnionType(List.of(new Primitive(PrimitiveKind.NUMBER), new StringDomainType(Set.of("x")))));
        CheckResult result = judge.check(fact(array), new ExactlyOne(List.of(number(), unknownAccept())));
        assertEquals(Verdict.MISMATCH, result.verdict());
        assertTrue(result.counterExample().isPresent());
    }

    @Test
    void emptyArrayDoesNotProveCompositeByMissingElements() {
        // A known-empty array has NO element to prove AnyOf(number,string) on. It must not claim full MATCH from
        // missing elements, nor report MISMATCH without a present counterexample -> UNPROVEN (review finding #1).
        CheckResult result = judge.check(fact(new EmptyArrayType()), new AnyOf(List.of(number(), string())));
        assertEquals(Verdict.UNPROVEN, result.verdict());
    }

    @Test
    void unknownElementDoesNotCreateFalseMatch() {
        // Element type unknown: cannot prove MATCH (uncovered), cannot prove MISMATCH (no counterexample).
        CheckResult result = judge.check(fact(new ArrayType(ValueType.unknown())),
                new AnyOf(List.of(number(), string())));
        assertEquals(Verdict.UNPROVEN, result.verdict());
    }

    @Test
    void allOfMismatchesWhenOneValueFailsOneCandidate() {
        ArrayType array = new ArrayType(
                new UnionType(List.of(new Primitive(PrimitiveKind.NUMBER), new StringDomainType(Set.of("x")))));
        CheckResult result = judge.check(fact(array), new Expectation.AllOf(List.of(number(), number())));
        assertEquals(Verdict.MISMATCH, result.verdict());
    }

    @Test
    void unsupportedIsAlwaysUnproven() {
        CheckResult result = judge.check(fact(new Primitive(PrimitiveKind.NUMBER)), new Unsupported("/x", "oneOf"));
        assertEquals(Verdict.UNPROVEN, result.verdict());
    }

    @Test
    void requireFieldMismatchWhenFieldValueFails() {
        ObjectType obj = new ObjectType(Map.of("a", new Primitive(PrimitiveKind.STRING)));
        CheckResult result = judge.check(fact(obj), new RequireField("a", number()));
        assertEquals(Verdict.MISMATCH, result.verdict());
    }

    @Test
    void requireFieldMismatchWhenFieldDefinitelyAbsent() {
        ObjectType closed = new ObjectType(Map.of());
        CheckResult result = judge.check(fact(closed), new RequireField("missing", number()));
        assertEquals(Verdict.MISMATCH, result.verdict());
    }

    @Test
    void elementCompositeDoesNotMatchWhenSomeElementFailsAllCandidates() {
        // [1,2] vs AnyOf(string,number): element 1 and 2 each pass number, so this MATCHes on the element reading.
        // The review guard is the INVERSE case below — a number array must NOT match a whole-array string target.
        ArrayType array = new ArrayType(new Primitive(PrimitiveKind.NUMBER));
        CheckResult result = judge.check(fact(array), new AnyOf(List.of(string(), number())));
        assertEquals(Verdict.MATCH, result.verdict());
    }

    @Test
    void numberArrayDoesNotMatchWholeArrayStringTarget() {
        // [1,2] vs AnyOf(string) / a string target read per element: every element violates string, so the whole
        // composite FAILs on each observed element -> MISMATCH (never a vacuous MATCH).
        ArrayType array = new ArrayType(new Primitive(PrimitiveKind.NUMBER));
        CheckResult result = judge.check(fact(array), new AnyOf(List.of(string())));
        assertEquals(Verdict.MISMATCH, result.verdict());
    }

    @Test
    void emptyArrayDoesNotSatisfyWholeArrayScalarRequirement() {
        // [] vs a scalar/element requirement: an empty array has no element to prove the requirement on, so it is
        // UNPROVEN — never a vacuous MATCH (review finding #1).
        CheckResult result = judge.check(fact(new EmptyArrayType()), new Accept(new Primitive(PrimitiveKind.STRING)));
        assertEquals(Verdict.UNPROVEN, result.verdict());
    }

    @Test
    void emptyArraySatisfiesElementWiseRequirementVacuously() {
        // [] vs an array-of-string element requirement: no element exists to violate it -> MATCH.
        CheckResult result = judge.check(fact(new EmptyArrayType()),
                new Expectation.Accept(new ArrayType(new Primitive(PrimitiveKind.STRING))));
        assertEquals(Verdict.MATCH, result.verdict());
    }

    @Test
    void concreteValueNeverMatchesAcceptNever() {
        // Accept(Never): no legal value can satisfy it; a concrete value must NOT be MATCH.
        CheckResult result = judge.check(fact(new Primitive(PrimitiveKind.NUMBER)), new Accept(ValueType.never()));
        assertEquals(Verdict.MISMATCH, result.verdict());
    }

    @Test
    void acceptNeverNotUsedAsWildPass() {
        // An array whose element requirement is Accept(Never) must not MATCH on present elements.
        ArrayType array = new ArrayType(new Primitive(PrimitiveKind.NUMBER));
        CheckResult result = judge.check(fact(array),
                new SchemaExpectationCompiler.ElementRequirement(new Accept(ValueType.never())));
        assertEquals(Verdict.MISMATCH, result.verdict());
    }

    @Test
    void sameObservedValueNotStitchedAcrossCandidates() {
        // [1, "x"] vs AnyOf(number,string): candidate number has counterexample "x", candidate string has
        // counterexample 1. These are DIFFERENT values; they must NOT be stitched into one MISMATCH.
        ArrayType array = new ArrayType(
                new UnionType(List.of(new Primitive(PrimitiveKind.NUMBER), new StringDomainType(Set.of("x")))));
        CheckResult result = judge.check(fact(array), new AnyOf(List.of(number(), string())));
        assertEquals(Verdict.MATCH, result.verdict(),
                "different candidates each having a counterexample on different values is not a violation");
    }
}
