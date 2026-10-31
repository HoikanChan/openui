/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

class ShadowFieldNegative {
    private int count;

    void methodParameter(int count) {
        int other = count + 1;
        consume(other);
    }

    void localWithDifferentName() {
        int countValue = 2;
        consume(countValue);
    }

    void consume(int value) {
    }
}
