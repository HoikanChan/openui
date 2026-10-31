/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 请求数据模型：原始 JSON、描述与可选的特征化类型旁注。
 *
 * @param description 面向提示词的数据模型描述
 * @param raw 原始数据模型 JSON，保持字段顺序
 * @param shapeSidecar 特征化后生成的 TS 类型旁注；未特征化时为 {@code null}
 * @since 2026
 */
public record DataModelSpec(String description, Map<String, Object> raw, String shapeSidecar) {
    /** 规范化构造：{@code null} 值归一为空 Map，其余改为保序不可变包装。 */
    public DataModelSpec {
        raw = raw == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(raw));
    }

    /**
     * 不带类型旁注的兼容构造。
     *
     * @param description 面向提示词的数据模型描述
     * @param raw 原始数据模型 JSON
     */
    public DataModelSpec(String description, Map<String, Object> raw) {
        this(description, raw, null);
    }
}
