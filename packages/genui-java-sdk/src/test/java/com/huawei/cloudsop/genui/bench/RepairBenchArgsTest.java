/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * {@link RepairBenchArgs} 参数解析：必填 / 默认值 / 非法值。
 *
 * @since 2026
 */
class RepairBenchArgsTest {

    @Test
    void defaultsApplied() {
        RepairBenchArgs args = RepairBenchArgs.parse(new String[] { "--cases=repair-cases" });
        assertEquals("repair-cases", args.casesDir());
        assertEquals(RepairBenchArgs.DEFAULT_CONCURRENCY, args.concurrency());
        assertEquals(RepairBenchArgs.DEFAULT_MAX_ATTEMPTS, args.maxAttempts());
        assertEquals(0, args.timeoutMinutes());
        assertEquals(null, args.limit(), "limit defaults to null (no limit)");
        assertEquals(false, args.repair(), "repair defaults to false (validation-only)");
    }

    @Test
    void allFlagsParsed() {
        RepairBenchArgs args = RepairBenchArgs.parse(new String[] { "--cases=c", "--concurrency=2",
                "--max-attempts=5", "--timeout-minutes=10", "--limit=3", "--repair" });
        assertEquals(2, args.concurrency());
        assertEquals(5, args.maxAttempts());
        assertEquals(10, args.timeoutMinutes());
        assertEquals(3, args.limit());
        assertEquals(true, args.repair());
    }

    @Test
    void missingCasesRejected() {
        assertThrows(IllegalArgumentException.class, () -> RepairBenchArgs.parse(new String[0]));
    }

    @Test
    void unknownArgRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RepairBenchArgs.parse(new String[] { "--cases=c", "--bogus=1" }));
    }

    @Test
    void nonNumericRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RepairBenchArgs.parse(new String[] { "--cases=c", "--concurrency=x" }));
    }

    @Test
    void nonPositiveConcurrencyRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RepairBenchArgs.parse(new String[] { "--cases=c", "--concurrency=0" }));
    }

    @Test
    void negativeTimeoutRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RepairBenchArgs.parse(new String[] { "--cases=c", "--timeout-minutes=-1" }));
    }

    @Test
    void zeroTimeoutAllowed() {
        RepairBenchArgs args = RepairBenchArgs.parse(new String[] { "--cases=c", "--timeout-minutes=0" });
        assertEquals(0, args.timeoutMinutes());
    }
}
