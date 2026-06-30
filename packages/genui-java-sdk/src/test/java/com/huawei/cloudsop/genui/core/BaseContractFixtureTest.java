/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;

import org.junit.jupiter.api.Test;

class BaseContractFixtureTest {
    @Test
    void loadsGeneratedDslEngineBaseContractResource() {
        GenerationContract contract = GenerationContractLoader.loadDefault();

        assertEquals("@cloudsop/openui-react-ui-dsl@0.1.0", contract.contractVersion());
        assertEquals("Stack", contract.root());
        assertTrue(contract.components().containsKey("Stack"));
        assertFalse(contract.components().get("Stack").propsSchema().isEmpty());
        assertTrue(contract.componentGroups().stream()
                .anyMatch(group -> group.name().equals("Layout") && group.components().contains("Stack")));
        assertFalse(contract.additionalRules().isEmpty());
        assertFalse(contract.examples().isEmpty());
    }
}
