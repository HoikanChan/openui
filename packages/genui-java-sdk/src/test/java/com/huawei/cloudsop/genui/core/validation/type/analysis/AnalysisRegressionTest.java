package com.huawei.cloudsop.genui.core.validation.type.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Map;

class AnalysisRegressionTest {

    @Test
    void closedAndOpenRecordUnionCannotProveAdditionalPropertiesClosed() {
        Type closed = Types.record(Map.of(), Type.Extra.closed());
        Type open = Types.record(Map.of(), Type.Extra.open(Types.scalar(Type.ScalarKind.STRING)));
        Fact fact = Fact.of(Types.union(java.util.List.of(closed, open)));

        Checks.CheckResult result = Checks.check(fact,
                new SchemaCompiler.ExtraClosed("/additionalProperties"), null);

        assertEquals(Checks.Verdict.UNPROVEN, result.verdict());
        assertTrue(result.reasons().contains(Checks.Reason.INSUFFICIENT_EVIDENCE));
    }

    @Test
    void overlayBranchBudgetReturnsSafeSummaryWithPrecisionLoss() {
        Type number = Types.scalar(Type.ScalarKind.NUMBER);
        Type string = Types.scalar(Type.ScalarKind.STRING);
        Fact alternatives = Fact.of(Types.union(java.util.List.of(
                Types.record(Map.of("value", Type.Field.required(number)), Type.Extra.closed()),
                Types.record(Map.of("value", Type.Field.required(string)), Type.Extra.closed()))));
        var tracker = new AnalysisBudget(4096, 1, 16, 256, 2048).tracker();
        TypeOps ops = new TypeOps(tracker);

        Fact result = ops.overlay(java.util.List.of(alternatives));

        assertEquals(Type.Unknown.INSTANCE, result.type());
        assertTrue(result.losses().contains(Fact.Loss.PRECISION_BUDGET));
        assertTrue(tracker.precisionLost());
    }

    @Test
    void overlayTypeNodeBudgetReturnsSafeSummaryWithPrecisionLoss() {
        var tracker = new AnalysisBudget(0, 64, 16, 256, 2048).tracker();
        TypeOps ops = new TypeOps(tracker);

        Fact result = ops.overlay(java.util.List.of(Fact.of(Types.record(Map.of(), Type.Extra.closed()))));

        assertEquals(Type.Unknown.INSTANCE, result.type());
        assertTrue(result.losses().contains(Fact.Loss.PRECISION_BUDGET));
        assertTrue(tracker.precisionLost());
    }

    @Test
    void sampleBudgetPreservesPrecisionLossAndUnknownRemainder() {
        Fact fact = SnapshotFacts.typeOfValue(java.util.List.of("known", "also valid"), "data.values", 0,
                new AnalysisBudget(4096, 64, 16, 1, 2048).tracker());

        assertTrue(fact.losses().contains(Fact.Loss.PRECISION_BUDGET));
        assertEquals(Type.Unknown.INSTANCE, ((Type.Sequence) fact.type()).element());
        assertEquals(Checks.Verdict.UNPROVEN, Checks.check(fact,
                new Expectation.SequenceOf(new Expectation.Accept(Types.scalar(Type.ScalarKind.STRING))),
                "data.values").verdict());
    }
}
