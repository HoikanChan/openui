/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

import java.util.List;

class LoopModificationNegative {
    void readForVariable() {
        for (int i = 0; i < 3; i++) {
            int copy = i;
            consume(copy);
        }
    }

    void readForeachVariable(List<String> values) {
        for (String value : values) {
            String trimmed = value.trim();
            consume(trimmed);
        }
    }

    void whileLoop() {
        int i = 0;
        while (i < 3) {
            i++;
        }
    }

    void consume(Object value) {
    }
}
