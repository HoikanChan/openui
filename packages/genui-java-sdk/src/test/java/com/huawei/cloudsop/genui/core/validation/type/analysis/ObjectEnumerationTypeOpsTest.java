/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ObjectEnumerationTypeOpsTest {
    private final TypeOps ops = new TypeOps(new AnalysisBudget.Tracker(AnalysisBudget.defaults()));
    private final Type number = Types.scalar(Type.ScalarKind.NUMBER);
    private final Type string = Types.scalar(Type.ScalarKind.STRING);

    @Test
    void entriesPreserveExactKeyValueBranches() {
        Fact source = Fact.of(Types.record(
                Map.of("port", Type.Field.required(number), "name", Type.Field.required(string)), Type.Extra.closed()));
        Type.Sequence entries = sequence(ops.entries(source));
        assertEquals(2, entries.minLength());
        assertEquals(2, entries.maxLength());
        assertEquals(Types.union(entry("port", number), entry("name", string)), entries.element());
    }

    @Test
    void optionalFieldsChangeCountsWithoutAddingNull() {
        Fact source = Fact.of(Types.record(
                Map.of("port", Type.Field.required(number), "name", Type.Field.optional(string)), Type.Extra.closed()));
        for (Fact result : List.of(ops.keys(source), ops.values(source), ops.entries(source))) {
            assertEquals(1, sequence(result).minLength());
            assertEquals(2, sequence(result).maxLength());
        }
        assertEquals(Types.union(number, string), sequence(ops.values(source)).element());
        assertEquals(Types.union(entry("port", number), entry("name", string)),
                sequence(ops.entries(source)).element());
    }

    @Test
    void emptyClosedAndOpenRecordsRemainDistinct() {
        Fact closed = Fact.of(Types.record(Map.of(), Type.Extra.closed()));
        for (Fact result : List.of(ops.keys(closed), ops.values(closed), ops.entries(closed))) {
            assertEquals(Types.emptySequence(), result.type());
        }
        Fact open = Fact.of(Types.record(Map.of(), Type.Extra.open(number)));
        assertEquals(Types.sequence(number, 0, Type.Sequence.UNBOUNDED), ops.values(open).type());
        assertEquals(Types.sequence(string, 0, Type.Sequence.UNBOUNDED), ops.keys(open).type());
        assertEquals(Types
                .sequence(Types.record(Map.of("key", Type.Field.required(string), "value", Type.Field.required(number)),
                        Type.Extra.closed()), 0, Type.Sequence.UNBOUNDED),
                ops.entries(open).type());
    }

    @Test
    void unknownAndNullFieldsAreEnumeratedAndOpenValuesRetained() {
        Fact unknown = Fact
                .of(Types.record(Map.of("mystery", Type.Field.required(Type.Unknown.INSTANCE)), Type.Extra.closed()));
        assertEquals(Types.sequence(Type.Unknown.INSTANCE, 1, 1), ops.values(unknown).type());
        assertEquals(Types.sequence(entry("mystery", Type.Unknown.INSTANCE), 1, 1), ops.entries(unknown).type());
        Type nil = Types.scalar(Type.ScalarKind.NULL);
        Fact withNull = Fact.of(Types.record(Map.of("nil", Type.Field.required(nil)), Type.Extra.open(number)));
        assertEquals(Types.sequence(Types.union(nil, number), 1, Type.Sequence.UNBOUNDED), ops.values(withNull).type());
    }

    @Test
    void smallReferenceEnumerationMatchesAllOptionalFieldRealizations() {
        // Independent finite reference: enumerate the concrete records with and without the optional name.
        for (boolean includeName : List.of(false, true)) {
            Map<String, Object> concrete = new LinkedHashMap<>();
            concrete.put("port", 80);
            if (includeName)
                concrete.put("name", "http");
            Type.RecordType declared = (Type.RecordType) Types.record(
                    Map.of("port", Type.Field.required(number), "name", Type.Field.optional(string)),
                    Type.Extra.closed());
            Type.Sequence result = sequence(ops.entries(Fact.of(declared)));
            assertTrue(concrete.size() >= result.minLength() && concrete.size() <= result.maxLength());
            List<Type> branches = TypeOps.flatten(result.element());
            concrete.forEach((key,
                    value) -> assertTrue(branches.contains(entry(key, value instanceof Number ? number : string))));
        }
    }

    @Test
    void unionObjectsDistributeWithoutInventingKeyValuePairs() {
        Fact source = Fact
                .of(Types.union(Types.record(Map.of("port", Type.Field.required(number)), Type.Extra.closed()),
                        Types.record(Map.of("name", Type.Field.required(string)), Type.Extra.closed())));
        assertEquals(
                Types.union(Types.sequence(entry("port", number), 1, 1), Types.sequence(entry("name", string), 1, 1)),
                ops.entries(source).type());
    }

    @Test
    void referenceOpenObjectCanContainAdditionalTypedEntries() {
        Type.Sequence result = sequence(ops
                .entries(Fact.of(Types.record(Map.of("port", Type.Field.required(number)), Type.Extra.open(string)))));
        assertEquals(1, result.minLength());
        assertEquals(Type.Sequence.UNBOUNDED, result.maxLength());
        // Concrete reference object includes an additional string value, alongside the numeric required field.
        Map<String, Object> reference = Map.of("port", 80, "protocol", "http");
        List<Type> alternatives = TypeOps.flatten(result.element());
        reference.forEach((key, value) -> {
            Type concrete = entry(key, value instanceof Number ? number : string);
            assertTrue(alternatives.stream()
                    .anyMatch(branch -> Relations.proveIncluded(concrete, branch)));
        });
    }

    private static Type entry(String key, Type value) {
        return Types.record(Map.of("key", Type.Field.required(Types.stringLiterals(Set.of(key))), "value",
                Type.Field.required(value)), Type.Extra.closed());
    }

    private static Type.Sequence sequence(Fact fact) {
        return assertInstanceOf(Type.Sequence.class, fact.type());
    }
}
