/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.validation.RepairPolicyKind;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class RepairPolicyTest {

    @Test
    void fromAppliesDefaults() {
        RepairPolicy p = RepairPolicy.from(RepairPolicyKind.FINAL_REPAIR);
        assertEquals(RepairPolicyKind.FINAL_REPAIR, p.kind());
        assertEquals(1, p.maxAttempts(), "default maxAttempts is 1 (Decision #8)");
        assertEquals(Duration.ZERO, p.timeout());
        assertFalse(p.hasTimeout());
    }

    @Test
    void ofCustomAttempts() {
        RepairPolicy p = RepairPolicy.of(RepairPolicyKind.FINAL_REPAIR, 3);
        assertEquals(3, p.maxAttempts());
        assertEquals(RepairPolicyKind.FINAL_REPAIR, p.kind());
    }

    @Test
    void explicitTimeoutFlagsSet() {
        RepairPolicy p = new RepairPolicy(RepairPolicyKind.FINAL_REPAIR, 2, Duration.ofSeconds(5));
        assertTrue(p.hasTimeout());
    }

    @Test
    void rejectsZeroAttempts() {
        assertThrows(IllegalArgumentException.class, () -> new RepairPolicy(RepairPolicyKind.NONE, 0, Duration.ZERO));
    }

    @Test
    void rejectsNegativeTimeout() {
        assertThrows(IllegalArgumentException.class,
                () -> new RepairPolicy(RepairPolicyKind.NONE, 1, Duration.ofSeconds(-1)));
    }
}
