/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

class GenUiGenerationResultTest {
    @Test
    void dataModelIsDefensivelyCopiedAndImmutable() {
        LinkedHashMap<String, Object> source = new LinkedHashMap<>();
        source.put("count", 1);

        GenUiGenerationResult result = new GenUiGenerationResult("root = Stack([])", source);

        source.put("extra", true);

        assertEquals(Map.of("count", 1), result.dataModel());
        assertThrows(UnsupportedOperationException.class, () -> result.dataModel().put("x", 1));
    }

    @Test
    void nullDataModelBecomesEmptyMap() {
        GenUiGenerationResult result = new GenUiGenerationResult("root = Stack([])", null);

        assertTrue(result.dataModel().isEmpty());
    }
}
