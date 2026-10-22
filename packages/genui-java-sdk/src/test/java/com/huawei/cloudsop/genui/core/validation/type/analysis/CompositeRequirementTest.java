package com.huawei.cloudsop.genui.core.validation.type.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Finite-model regression for #82 composite requirements on the #80 kernel: AnyOf/ExactlyOne combine sub-requirements
 * on the SAME observed value (never stitching per-candidate counterexamples), the empty-array possibility is honoured,
 * and unsupported schema keywords stay UNPROVEN rather than compiling to a wild-pass.
 */
class CompositeRequirementTest {

    private static Type number() {
        return Types.scalar(Type.ScalarKind.NUMBER);
    }

    private static Type string() {
        return Types.scalar(Type.ScalarKind.STRING);
    }

    private static Type boolean_() {
        return Types.scalar(Type.ScalarKind.BOOLEAN);
    }

    private static Expectation.Accept accept(Type type) {
        return new Expectation.Accept(type);
    }

    @Test
    void anyOfMatchesWhenEachElementSatisfiesSomeCandidate() {
        // [1, "x"]: a non-empty sequence whose elements are number|string; every element passes AnyOf(number,string)
        // on its own value.
        Fact fact = Fact.of(Types.sequence(Types.union(List.of(number(), string())), 1, Type.Sequence.UNBOUNDED));
        Checks.CheckResult result = Checks.check(fact,
                new Expectation.SequenceOf(new Expectation.AnyOf(List.of(accept(number()), accept(string())))), null);
        assertEquals(Checks.Verdict.MATCH, result.verdict());
    }

    @Test
    void anyOfDoesNotStitchCounterexamplesAcrossCandidates() {
        // The same [1,"x"] element union: candidate number has counterexample "x", candidate string has counterexample
        // 1, but no single element value violates both -> MATCH, not a stitched MISMATCH.
        Fact fact = Fact.of(Types.sequence(Types.union(List.of(number(), string())), 1, Type.Sequence.UNBOUNDED));
        Checks.CheckResult result = Checks.check(fact,
                new Expectation.SequenceOf(new Expectation.AnyOf(List.of(accept(number()), accept(string())))), null);
        assertEquals(Checks.Verdict.MATCH, result.verdict(),
                "different candidates failing on different values is not a violation");
    }

    @Test
    void anyOfMismatchWhenOneValueViolatesAllCandidates() {
        // A non-empty sequence of booleans: each boolean element is disjoint from both number and string, so the SAME
        // element violates every candidate -> a real element counterexample -> MISMATCH.
        Fact fact = Fact.of(Types.sequence(boolean_(), 1, Type.Sequence.UNBOUNDED));
        Checks.CheckResult result = Checks.check(fact,
                new Expectation.SequenceOf(new Expectation.AnyOf(List.of(accept(number()), accept(string())))), null);
        assertEquals(Checks.Verdict.MISMATCH, result.verdict());
    }

    @Test
    void exactlyOneMismatchWhenOneValueSatisfiesTwoBranches() {
        // ExactlyOne(number, Unknown) over the value 1: the same value satisfies both branches -> MISMATCH.
        Fact fact = Fact.of(number());
        Checks.CheckResult result = Checks.check(fact,
                new Expectation.ExactlyOne(List.of(accept(number()), accept(Type.Unknown.INSTANCE))), null);
        assertEquals(Checks.Verdict.MISMATCH, result.verdict());
    }

    @Test
    void exactlyOneMatchesWhenEachValueSatisfiesExactlyOneBranch() {
        // ExactlyOne(number, string): 1 hits only number, "x" hits only string -> each satisfies exactly one.
        Fact numberFact = Fact.of(number());
        Fact stringFact = Fact.of(string());
        Expectation exactlyOne = new Expectation.ExactlyOne(List.of(accept(number()), accept(string())));
        assertEquals(Checks.Verdict.MATCH, Checks.check(numberFact, exactlyOne, null).verdict());
        assertEquals(Checks.Verdict.MATCH, Checks.check(stringFact, exactlyOne, null).verdict());
    }

    @Test
    void emptyArrayPossibilityPreventsProvingMismatchFromElementPartitions() {
        // A possibly-empty sequence (minLength 0) with no element evidence cannot be proven MISMATCH on its element
        // requirement, and cannot be proven full MATCH either -> UNPROVEN (empty-array possibility not ignored).
        Type possiblyEmpty = Types.sequence(boolean_(), 0, 5);
        Fact fact = Fact.of(possiblyEmpty);
        Checks.CheckResult result = Checks.check(fact,
                new Expectation.SequenceOf(new Expectation.AnyOf(List.of(accept(number()), accept(string())))), null);
        assertEquals(Checks.Verdict.UNPROVEN, result.verdict());
    }

    @Test
    void unsupportedKeywordStaysUnprovenNotWildPass() {
        // An unsupported schema keyword (oneOf/min/pattern) compiles to Unsupported -> UNPROVEN, never a fake MATCH.
        Checks.CheckResult result = Checks.check(Fact.of(number()), new Expectation.Unsupported("/rules", "minLength"),
                null);
        assertEquals(Checks.Verdict.UNPROVEN, result.verdict());
        assertTrue(result.reasons().contains(Checks.Reason.UNSUPPORTED_RULE));
    }

    @Test
    void compilerKeepsUnsupportedKeywordAsUnsupported() {
        // SchemaCompiler must preserve an unrecognized keyword as Unsupported (not drop it into a clean pass).
        Expectation compiled = SchemaCompiler.compileParam(Map.of("type", "number", "minLength", 2), "/rules");
        Checks.CheckResult result = Checks.check(Fact.of(number()), compiled, null);
        assertEquals(Checks.Verdict.UNPROVEN, result.verdict(),
                "minLength must remain an UNPROVEN requirement, not be silently dropped");
    }

    @Test
    void requiredFieldAbsentFromClosedRecordIsMismatch() {
        // A closed record provably lacking the required field -> MISMATCH.
        Fact fact = Fact.of(Types.record(Map.of(), Type.Extra.closed()));
        Checks.CheckResult result = Checks.check(fact, new Expectation.RequireField("id", accept(number())), null);
        assertEquals(Checks.Verdict.MISMATCH, result.verdict());
    }
}
