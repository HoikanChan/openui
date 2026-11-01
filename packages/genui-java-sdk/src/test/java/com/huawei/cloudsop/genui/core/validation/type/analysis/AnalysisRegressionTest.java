package com.huawei.cloudsop.genui.core.validation.type.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class AnalysisRegressionTest {

    @Test
    void closedAndOpenRecordUnionCannotProveAdditionalPropertiesClosed() {
        Type closed = Types.record(Map.of(), Type.Extra.closed());
        Type open = Types.record(Map.of(), Type.Extra.open(Types.scalar(Type.ScalarKind.STRING)));
        Fact fact = Fact.of(Types.union(java.util.List.of(closed, open)));

        Checks.CheckResult result =
                Checks.check(
                        fact,
                        new SchemaCompiler.ExtraClosed("/additionalProperties", Set.of()),
                        null);

        assertEquals(Checks.Verdict.UNPROVEN, result.verdict());
        assertTrue(result.reasons().contains(Fact.Loss.INSUFFICIENT_EVIDENCE));
    }

    @Test
    void structuralKeyKeepsLiteralBranchesWithSeparatorsDistinct() {
        Type singleValueWithComma = Types.stringLiterals(Set.of("a,b"));
        Type twoValues = Types.stringLiterals(Set.of("a", "b"));

        assertEquals(
                2, TypeOps.flatten(Types.union(List.of(singleValueWithComma, twoValues))).size());
        assertEquals(1, TypeOps.flatten(Types.union(List.of(twoValues, twoValues))).size());
    }

    @Test
    void unionBranchLimitLossReachesReturnedFact() {
        Fact object = alternatives(65);
        Fact result = new TypeOps().readField(object, "value");

        assertEquals(Type.Unknown.INSTANCE, result.type());
        assertTrue(result.losses().contains(Fact.Loss.PRECISION_BUDGET));
    }

    @Test
    void overlayBranchLimitReturnsSafeSummary() {
        Fact result = new TypeOps().overlay(List.of(alternatives(9), alternatives(8)));

        assertEquals(Type.Unknown.INSTANCE, result.type());
        assertTrue(result.losses().contains(Fact.Loss.PRECISION_BUDGET));
    }

    @Test
    void sampleLimitPreservesUnknownRemainder() {
        Fact fact =
                SnapshotFacts.typeOfValue(
                        java.util.Collections.nCopies(257, "known"), "data.values", 0);

        assertTrue(fact.losses().contains(Fact.Loss.PRECISION_BUDGET));
        assertEquals(Type.Unknown.INSTANCE, ((Type.Sequence) fact.type()).element());
        assertEquals(
                Checks.Verdict.UNPROVEN,
                Checks.check(
                                fact,
                                new Expectation.SequenceOf(
                                        new Expectation.Accept(
                                                Types.scalar(Type.ScalarKind.STRING))),
                                "data.values")
                        .verdict());
    }

    private static Fact alternatives(int count) {
        List<Type> branches =
                java.util.stream.IntStream.range(0, count)
                        .mapToObj(
                                index ->
                                        Types.record(
                                                Map.of(
                                                        "value",
                                                        Type.Field.required(
                                                                Types.stringLiterals(
                                                                        Set.of("value-" + index)))),
                                                Type.Extra.closed()))
                        .toList();
        return Fact.of(Types.union(branches));
    }
}
