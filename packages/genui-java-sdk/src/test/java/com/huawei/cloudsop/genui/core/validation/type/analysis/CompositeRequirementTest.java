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

    @Test
    void possibleFailingBranchWithoutObservationIsUnproven() {
        Fact possible = Fact.of(Types.union(List.of(number(), boolean_())));
        Expectation requirement = new Expectation.AnyOf(List.of(accept(number()), accept(string())));
        assertEquals(Checks.Verdict.UNPROVEN, Checks.check(possible, requirement, "value").verdict());
    }

    @Test
    void passingObservationDoesNotCoverFailingPossibility() {
        Fact possible = Fact.of(Types.union(List.of(number(), boolean_())))
                .withEvidence(new Fact.Evidence.ObservedValue("value", 1));
        assertEquals(Checks.Verdict.UNPROVEN, Checks.check(possible,
                new Expectation.AnyOf(List.of(accept(number()), accept(string()))), "value").verdict());
    }

    @Test
    void wholeSequenceCannotSatisfyScalarComposite() {
        // 整体数组对标量复合要求:数组整体与候选值域互斥,确定 MISMATCH——空数组本身也是数组值,
        // 同样不满足标量要求;空值不承载反例的 #82 口径由逐元素(SequenceOf)路径承载,不适用整体判定。
        Expectation scalar = new Expectation.AnyOf(List.of(accept(number())));
        assertEquals(Checks.Verdict.MISMATCH,
                Checks.check(Fact.of(Types.sequence(number(), 0, 5)), scalar, null).verdict());
        assertEquals(Checks.Verdict.MISMATCH,
                Checks.check(Fact.of(Types.emptySequence()), scalar, null).verdict());
    }

    @Test
    void objectSchemaChecksRequiredAndExtraFields() {
        Fact empty = SnapshotFacts.typeOfValue(Map.of(), "value", 0);
        assertEquals(Checks.Verdict.MISMATCH, Checks.check(empty,
                SchemaCompiler.compileParam(Map.of("type", "object", "required", List.of("id")), "/text"), "value").verdict());
        Fact extra = SnapshotFacts.typeOfValue(Map.of("bad", true), "value", 0);
        for (Object policy : List.of(false, Map.of("type", "number"))) {
            assertEquals(Checks.Verdict.MISMATCH, Checks.check(extra,
                    SchemaCompiler.compileParam(Map.of("type", "object", "additionalProperties", policy), "/text"), "value").verdict());
        }
    }

    @Test
    void checkConclusionIsContextAndEvidenceSensitive() {
        Fact possible = Fact.of(Types.union(List.of(number(), boolean_())));
        Fact budgeted = possible.withLoss(Fact.Loss.PRECISION_BUDGET);
        Fact blocked = possible.withUpstreamError("data.items");
        Expectation requirement = new Expectation.AnyOf(List.of(accept(number()), accept(boolean_())));
        assertEquals(Checks.Verdict.MATCH, Checks.check(possible, requirement, "value").verdict());
        assertEquals(Checks.Verdict.UNPROVEN, Checks.check(budgeted, requirement, "value").verdict());
        Checks.CheckResult blockedResult = Checks.check(blocked, requirement, "value");
        assertEquals(Checks.Verdict.UNPROVEN, blockedResult.verdict());
        assertEquals(java.util.Set.of(Fact.Loss.BLOCKED_BY_ERROR), blockedResult.reasons());
        Expectation text = SchemaCompiler.compileParam(Map.of("type", "number"), "/text");
        assertEquals(Checks.Verdict.MATCH, Checks.check(Fact.of(number()), text, "value").verdict());
        assertEquals(Checks.Verdict.MISMATCH, Checks.check(Fact.of(Types.union(List.of(number(), boolean_())))
                .withEvidence(new Fact.Evidence.ObservedValue("value", true)), text, "value").verdict());
    }

    @Test
    void compositeObjectAndEmptySequenceRequirementsUseWholePartitions() {
        Expectation object = SchemaCompiler.compileParam(Map.of("anyOf", List.of(
                Map.of("type", "object", "required", List.of("id")), Map.of("type", "number"))), "/text");
        assertEquals(Checks.Verdict.MATCH, Checks.check(Fact.of(Types.record(
                Map.of("id", Type.Field.required(number())), Type.Extra.closed())), object, null).verdict());
        Expectation arrays = new Expectation.AnyOf(List.of(new Expectation.SequenceOf(accept(number()))));
        assertEquals(Checks.Verdict.MATCH, Checks.check(Fact.of(Types.emptySequence()), arrays, null).verdict());
        assertEquals(Checks.Verdict.UNPROVEN,
                Checks.check(Fact.of(Types.sequence(boolean_(), 0, 5)), arrays, null).verdict());
    }

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
        assertTrue(result.reasons().contains(Fact.Loss.UNSUPPORTED_RULE));
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
