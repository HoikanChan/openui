/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.validation.ValidationMetadata;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
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

    @Test
    void twoArgConstructorLeavesValidationFieldsNull() {
        GenUiGenerationResult result = new GenUiGenerationResult("root = Stack([])", Map.of());

        assertNull(result.validationStatus(), "backward-compatible ctor must default validationStatus to null");
        assertNull(result.validationResult(), "backward-compatible ctor must default validationResult to null");
    }

    @Test
    void canonicalConstructorCarriesValidationStatusAndResult() {
        ValidationResult validationResult = ValidationResult.valid("root = Stack([])", List.of(),
                new ValidationMetadata(1, "root", ValidationMode.FINAL, null));

        GenUiGenerationResult result = new GenUiGenerationResult("root = Stack([])", Map.of(), ValidationStatus.VALID,
                validationResult);

        assertEquals(ValidationStatus.VALID, result.validationStatus());
        assertEquals(validationResult, result.validationResult());
    }
}
