/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

/**
 * Coarse layer of a {@link DataInsight}, ordered from cheapest gating checks to deepest per-field
 * analysis.
 *
 * @since 2026
 */
public enum InsightLevel {
    /** Payload-wide gate that can short-circuit lower layers (empty data, status-only ack). */
    L0_GATE,
    /** Container shape hints (top-level collections vs objects). */
    L1_CONTAINER,
    /** Dataset shape hints (rows, columns, cardinalities). */
    L2_DATASET,
    /** Per-field hints (enums, ranges, formats). */
    L3_FIELD
}
