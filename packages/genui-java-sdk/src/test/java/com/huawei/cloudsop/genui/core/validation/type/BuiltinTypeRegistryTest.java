package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.huawei.cloudsop.genui.core.validation.parser.Builtins;

import org.junit.jupiter.api.Test;

class BuiltinTypeRegistryTest {

    @Test
    void coversEveryCurrentDataAndTemplateBuiltin() {
        assertEquals(Builtins.typeCheckedNames(), new BuiltinTypeRegistry().names());
    }
}
