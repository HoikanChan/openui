/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

import java.util.List;

class LoopModificationPositive {
    void assignForVariable() {
        for (int i = 0; i < 3; i++) {
            i = 1;
        }
    }

    void incrementForVariable() {
        for (int i = 0; i < 3; i++) {
            i++;
        }
    }

    void assignForeachVariable(List<String> values) {
        for (String value : values) {
            value = value.trim();
        }
    }
}
