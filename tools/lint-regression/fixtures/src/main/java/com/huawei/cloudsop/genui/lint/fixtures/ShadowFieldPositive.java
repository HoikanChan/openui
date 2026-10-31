/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

class ShadowFieldPositive {
    private int count;
    private String label;

    void locals() {
        int count = 1;
        String label = "local";
    }

    void forEach(List<String> values) {
        for (String label : values) {
            consume(label);
        }
    }

    void catchParameter() {
        try {
            throw new IllegalStateException("fixture");
        } catch (RuntimeException count) {
            consume(count.getMessage());
        }
    }

    void resource() throws IOException {
        try (InputStream label = InputStream.nullInputStream()) {
            consume(label.toString());
        }
    }

    void lambdaParameter() {
        Function<String, String> identity = label -> label;
        consume(identity.apply("value"));
    }

    void consume(String value) {
    }
}
