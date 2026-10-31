/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

class BooleanExpressionComplexityPositive {
    static boolean check(
            boolean a, boolean b, boolean c, boolean d, boolean e, boolean f, boolean g) {
        return a && b && c && d && e && f && g;
    }
}
