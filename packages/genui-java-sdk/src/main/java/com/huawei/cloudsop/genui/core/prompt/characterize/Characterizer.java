/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import com.huawei.cloudsop.genui.core.Json;

import java.util.Map;

/**
 * Entry point for the characterization gate: decides whether a host-data map is small enough to leave untouched, or
 * large enough to warrant {@link ShapeWalker} reduction.
 *
 * <p>
 * This task intentionally stops at producing a {@link Characterized} result. Wiring this into {@code PromptAssembler} /
 * {@code DataModelSpec} (rendering the inferred {@link ShapeNode} to a TypeScript sidecar and reassembling the prompt)
 * is a later task; see the class-level note in the task brief for the intended split. Callers that already have a
 * {@code DataModelSpec.raw()} map should pass it directly to {@link #characterize(Map, CharacterizationConfig)}.
 *
 * @since 2026
 */
public final class Characterizer {
    private Characterizer() {
    }

    /**
     * Runs the characterization gate over a raw host-data map.
     *
     * <ol>
     * <li>{@code raw == null || raw.isEmpty()} → pass-through: {@code sample = raw}, {@code shape
     *       = null} (nothing to characterize).
     * <li>{@code !cfg.enabled() || Json.stringify(raw).length() <= cfg.triggerBytes()} → pass-through:
     * {@code sample = raw}, {@code shape = null} (small data ⇒ zero behavior change).
     * <li>Otherwise, delegates to {@link ShapeWalker#walk(Object, CharacterizationConfig, int)} at depth 0.
     * </ol>
     */
    public static Characterized characterize(Map<String, Object> raw, CharacterizationConfig cfg) {
        if (raw == null || raw.isEmpty()) {
            return new Characterized(raw, null);
        }
        if (!cfg.enabled() || Json.stringify(raw).length() <= cfg.triggerBytes()) {
            return new Characterized(raw, null);
        }
        return ShapeWalker.walk(raw, cfg, 0);
    }
}
