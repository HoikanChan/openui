/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

class NumericSuffixNegative {
    private long assigned = 1L;
    private float ratio = 2F;
    private long field;

    long read() {
        return 1L;
    }

    float readFloat() {
        return 2F;
    }

    void write() {
        field = 1L;
        consume(1L);
        consumeFloat(2F);
        int intValue = 1;
        double doubleValue = 2;
        long[] values = {1L, 2L};
        long expression = 1 + 2;
        long cast = (long) 1;
        consumeInt(1);
    }

    void consume(long value) {
    }

    void consumeFloat(float value) {
    }

    void consumeInt(int value) {
    }
}
