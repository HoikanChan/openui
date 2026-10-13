/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.validation.type.ValueType.Primitive;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.PrimitiveKind;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.UnionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValueTypesTest {

    @Test
    void normalizesNestedUnionsInStableOrder() {
        Primitive number = new Primitive(PrimitiveKind.NUMBER);
        Primitive string = new Primitive(PrimitiveKind.STRING);

        assertEquals(new UnionType(List.of(number, string)),
                ValueTypes.union(List.of(number, new UnionType(List.of(number, string)))));
    }

    @Test
    void emptyUnionIsNotNumeric() {
        assertFalse(ValueTypes.isNumber(new UnionType(List.of())));
        assertTrue(ValueTypes.isNumber(new UnionType(List.of(
                new Primitive(PrimitiveKind.NUMBER), new Primitive(PrimitiveKind.NUMBER)))));
    }
}
