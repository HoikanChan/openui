/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * One deterministic, rule-derived observation about a host-data payload, paired with a localized
 * prompt fragment that tells the model how to render that situation.
 *
 * <p>Instances are immutable; the constructor defensively copies {@code tags} and {@code slots}
 * into order-preserving unmodifiable collections so callers cannot mutate an insight after the
 * fact.
 *
 * @param id stable machine identifier of the rule that produced this insight (for example {@code
 *     "l0-empty-data"})
 * @param level coarse layer the rule belongs to
 * @param targetPath JSON path within the payload this insight describes
 * @param priority higher numbers win competition within the same layer and target path
 * @param tags searchable labels attached to this insight
 * @param slots rule-specific key/value payload consumed by downstream renderers
 * @param rulePrompt localized instruction fragment the model is expected to follow
 * @since 2026
 */
public record DataInsight(
        String id,
        InsightLevel level,
        String targetPath,
        int priority,
        Set<String> tags,
        Map<String, Object> slots,
        String rulePrompt) {

    /**
     * Builds an immutable insight, normalizing {@code null} collections to empty and copying the
     * rest into unmodifiable, order-preserving containers.
     *
     * @param id stable machine identifier of the rule
     * @param level coarse layer the rule belongs to
     * @param targetPath JSON path within the payload this insight describes
     * @param priority higher numbers win competition within the same layer and target path
     * @param tags searchable labels; {@code null} becomes {@code Set.of()}
     * @param slots rule-specific payload; {@code null} becomes {@code Map.of()}
     * @param rulePrompt localized instruction fragment
     */
    public DataInsight {
        tags = tags == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(tags));
        slots = slots == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(slots));
    }
}
