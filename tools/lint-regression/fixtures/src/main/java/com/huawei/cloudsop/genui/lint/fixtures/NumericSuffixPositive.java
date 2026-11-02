/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

class NumericSuffixPositive {
    private long assigned = 1;
    private float ratio = 2;
    private long field;

    NumericSuffixPositive(long value) {
    }

    long read() {
        return 1;
    }

    float readFloat() {
        return 2;
    }

    void write() {
        field = 1;
        consume(1);
        consumeFloat(2);
        long local = 1;
        long negative = -1;
        float negativeFloat = -1;
        long[] values = {1, 2};
        varargs(1, 2);
        new NumericSuffixPositive(1);
    }

    void consume(long value) {
    }

    void consumeFloat(float value) {
    }

    void varargs(long... values) {
    }
}
