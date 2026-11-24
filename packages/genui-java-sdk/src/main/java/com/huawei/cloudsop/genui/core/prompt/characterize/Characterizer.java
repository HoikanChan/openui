/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import java.util.Map;

/**
 * Backward-compatible entry point for the characterization gate, now a thin delegation to {@link
 * DataProfiler#characterize(Map, CharacterizationConfig)}.
 *
 * <p>New callers should prefer {@link DataProfiler} directly; this class remains so existing
 * callers keep compiling without changes.
 *
 * @since 2026
 */
public final class Characterizer {
    private Characterizer() {}

    /**
     * Runs the characterization gate over a raw host-data map.
     *
     * @param raw the raw data-model map to inspect; {@code null} or empty passes through unchanged
     * @param cfg the gate and sampling configuration; must not be {@code null}
     * @return the sample/shape pair, with a {@code null} shape when the gate passed through
     */
    public static Characterized characterize(Map<String, Object> raw, CharacterizationConfig cfg) {
        return DataProfiler.characterize(raw, cfg);
    }
}
