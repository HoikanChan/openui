package com.huawei.cloudsop.genui.core.validation.type.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class OverlayPropertiesTest {
    private final TypeOps ops = new TypeOps(AnalysisBudget.defaults().tracker());
    private final Type number = Types.scalar(Type.ScalarKind.NUMBER);
    private final Type string = Types.scalar(Type.ScalarKind.STRING);

    @Test
    void optionalFieldCanRetainEarlierExtraValue() {
        Type.RecordType result = overlay(Types.record(Map.of(), Type.Extra.open(number)),
                Types.record(Map.of("a", Type.Field.optional(string)), Type.Extra.closed()));
        assertEquals(Types.union(number, string), result.fields().get("a").type());
        assertEquals(Type.Presence.OPTIONAL, result.fields().get("a").presence());
    }

    @Test
    void openTailRetainsEarlierExtraValuesAndRequiredFields() {
        Type.RecordType result = overlay(Types.record(Map.of("a", Type.Field.required(number)), Type.Extra.open(number)),
                Types.record(Map.of(), Type.Extra.open(string)));
        assertEquals(Types.union(number, string), result.extra().valueType());
        assertEquals(Type.Presence.REQUIRED, result.fields().get("a").presence());
        assertEquals(Types.union(number, string), result.fields().get("a").type());
    }

    @Test
    void unknownExtraValuesWidenValueWithoutRemovingRequiredPresence() {
        Type.RecordType result = overlay(Types.record(Map.of("a", Type.Field.required(number)), Type.Extra.closed()),
                Types.record(Map.of(), Type.Extra.open(Type.Unknown.INSTANCE)));
        assertEquals(Type.Field.required(Type.Unknown.INSTANCE), result.fields().get("a"));
        assertEquals(Type.Unknown.INSTANCE, result.extra().valueType());
    }

    @Test
    void optionalAndDefiniteReplacementPreservePresence() {
        Type first = Types.record(Map.of("a", Type.Field.required(number)), Type.Extra.closed());
        Type.RecordType optional = overlay(first,
                Types.record(Map.of("a", Type.Field.optional(string)), Type.Extra.closed()));
        assertEquals(Type.Field.required(Types.union(number, string)), optional.fields().get("a"));
        Type.RecordType definite = overlay(first,
                Types.record(Map.of("a", Type.Field.required(string)), Type.Extra.closed()));
        assertEquals(Type.Field.required(string), definite.fields().get("a"));
    }

    @Test
    void finiteReferenceOverlayResultsStayInsideInferredUpperBound() {
        List<Map<String, Object>> objects = List.of(Map.of(), Map.of("a", 1), Map.of("a", "x"),
                Map.of("b", true), Map.of("a", "y", "b", false));
        for (Map<String, Object> left : objects) {
            for (Map<String, Object> right : objects) {
                Map<String, Object> merged = new LinkedHashMap<>(left);
                merged.putAll(right);
                Fact inferred = ops.overlay(List.of(SnapshotFacts.typeOfValue(left, null, 0),
                        SnapshotFacts.typeOfValue(right, null, 0)));
                assertEquals(Relations.Outcome.PROVEN,
                        Relations.proveIncluded(SnapshotFacts.typeOfValue(merged, null, 0).type(), inferred.type()));
                assertTrue(inferred.evidence().isEmpty(), "old operand observations must not survive replacement");
            }
        }
    }

    @Test
    void overlayKeepsInputPrecisionLossWithoutReusingValueEvidence() {
        Fact input = Fact.of(Types.record(Map.of("a", Type.Field.required(number)), Type.Extra.closed()))
                .withLosses(java.util.Set.of(Fact.Loss.NO_ELEMENT_EVIDENCE));
        Fact result = ops.overlay(List.of(input, Fact.of(Types.record(Map.of(), Type.Extra.closed()))));
        assertTrue(result.losses().contains(Fact.Loss.NO_ELEMENT_EVIDENCE));
    }

    private Type.RecordType overlay(Type left, Type right) {
        return (Type.RecordType) ops.overlay(List.of(Fact.of(left), Fact.of(right))).type();
    }
}
