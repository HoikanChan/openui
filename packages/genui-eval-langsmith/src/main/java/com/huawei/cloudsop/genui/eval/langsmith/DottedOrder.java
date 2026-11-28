/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.langsmith;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * LangSmith Run 树层级排序键生成器，遵循 {@code yyyyMMdd'T'HHmmssSSSSSS'Z'<runId>} 规范。
 *
 * @since 2026
 */
public final class DottedOrder {
    private static final DateTimeFormatter COMPACT_UTC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final int NANOS_PER_MICRO = 1_000;

    private DottedOrder() {}

    /**
     * 生成根 Run 的 {@code dotted_order}。
     *
     * @param startTime 开始时刻（UTC）
     * @param runId Run UUID
     * @return 规范格式的 dotted_order
     */
    public static String root(Instant startTime, String runId) {
        return segment(startTime, runId);
    }

    /**
     * 生成子 Run 的 {@code dotted_order}。
     *
     * @param parentDottedOrder 父 Run 的 dotted_order
     * @param startTime 子 Run 开始时刻（UTC）
     * @param runId 子 Run UUID
     * @return 拼接后的子 Run dotted_order
     */
    public static String child(String parentDottedOrder, Instant startTime, String runId) {
        return parentDottedOrder + "." + segment(startTime, runId);
    }

    private static String segment(Instant startTime, String runId) {
        Instant effective = startTime == null ? Instant.now() : startTime;
        int micros = effective.getNano() / NANOS_PER_MICRO;
        return COMPACT_UTC.format(effective) + String.format(Locale.ROOT, "%06dZ", micros) + runId;
    }
}
