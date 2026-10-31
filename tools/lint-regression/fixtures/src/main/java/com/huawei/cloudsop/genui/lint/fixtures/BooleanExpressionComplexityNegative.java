/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

class BooleanExpressionComplexityNegative {
    static boolean check(boolean a, boolean b, boolean c, boolean d, boolean e, boolean f) {
        return a && b && c && d && e && f;
    }
}
