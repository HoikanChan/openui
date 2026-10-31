/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

class NumericSuffixPositive {
    private long assigned = 10;
    private float ratio = 2;
    private long field;

    NumericSuffixPositive(long value) {
    }

    long read() {
        return 3;
    }

    float readFloat() {
        return 4;
    }

    void write() {
        field = 5;
        consume(6);
        consumeFloat(7);
        long local = 8;
        long negative = -9;
        float negativeFloat = -10;
        long[] values = {11, 12};
        varargs(13, 14);
        new NumericSuffixPositive(15);
    }

    void consume(long value) {
    }

    void consumeFloat(float value) {
    }

    void varargs(long... values) {
    }
}
