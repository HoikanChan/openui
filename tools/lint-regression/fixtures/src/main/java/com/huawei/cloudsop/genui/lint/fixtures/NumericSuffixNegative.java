/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

class NumericSuffixNegative {
    private long assigned = 10L;
    private float ratio = 2F;
    private long field;

    long read() {
        return 3L;
    }

    float readFloat() {
        return 4F;
    }

    void write() {
        field = 5L;
        consume(6L);
        consumeFloat(7F);
        int intValue = 8;
        double doubleValue = 9;
        long[] values = {11L, 12L};
        long expression = 13 + 14;
        long cast = (long) 15;
        consumeInt(16);
    }

    void consume(long value) {
    }

    void consumeFloat(float value) {
    }

    void consumeInt(int value) {
    }
}
