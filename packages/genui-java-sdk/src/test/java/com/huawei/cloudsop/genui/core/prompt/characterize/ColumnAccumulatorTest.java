/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColumnAccumulatorTest {
    @Test
    void tracksOccurrencesNullsAndDistinctStringsWithinCapacity() {
        ColumnAccumulator accumulator = new ColumnAccumulator(5);

        accumulator.observePresent("open");
        accumulator.observePresent("closed");
        accumulator.observeNull();
        accumulator.observeMissing();

        assertEquals(3, accumulator.occurrences());
        assertEquals(1, accumulator.nullCount());
        assertEquals(1, accumulator.missingCount());
        assertFalse(accumulator.distinctOverflowed());
        assertEquals(2, accumulator.distinctStrings().size());
        assertTrue(accumulator.distinctStrings().contains("open"));
        assertTrue(accumulator.distinctStrings().contains("closed"));
    }

    @Test
    void missingCountReflectsEveryRowThatLackedTheKey() {
        // Mirrors a column that first appears mid-array: rows 0-3 lack the key, rows 4-9 have it.
        ColumnAccumulator accumulator = new ColumnAccumulator(5);

        for (int i = 0; i < 4; i++) {
            accumulator.observeMissing();
        }
        for (int i = 0; i < 6; i++) {
            accumulator.observePresent("present");
        }

        assertEquals(4, accumulator.missingCount());
        assertEquals(6, accumulator.occurrences());
    }

    @Test
    void stopsGrowingDistinctSetOnceCapacityExceeded() {
        ColumnAccumulator accumulator = new ColumnAccumulator(2);

        accumulator.observePresent("a");
        accumulator.observePresent("b");
        accumulator.observePresent("c");
        accumulator.observePresent("d");

        assertTrue(accumulator.distinctOverflowed());
        // Capacity-bounded: stops collecting once it exceeds cap + 1 entries.
        assertTrue(accumulator.distinctStrings().size() <= 3);
    }

    @Test
    void tracksScalarTypesSeenAcrossRows() {
        ColumnAccumulator accumulator = new ColumnAccumulator(5);

        accumulator.observeScalarType(ScalarType.NUMBER);
        accumulator.observeScalarType(ScalarType.NUMBER);

        assertEquals(1, accumulator.scalarTypesSeen().size());
        assertTrue(accumulator.scalarTypesSeen().contains(ScalarType.NUMBER));

        accumulator.observeScalarType(ScalarType.BOOLEAN);
        assertEquals(2, accumulator.scalarTypesSeen().size());
    }
}
