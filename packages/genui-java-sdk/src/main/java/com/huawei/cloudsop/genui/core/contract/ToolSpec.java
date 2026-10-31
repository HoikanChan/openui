/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 契约声明的工具：名称、说明、输入/输出 JSON Schema 与行为注解。
 *
 * <p>Schema 使用保序不可变 Map，字段顺序驱动签名与 Query 默认形态，必须与 TypeScript 提示词逐字节一致。
 *
 * @param name 工具名
 * @param description 面向模型的工具说明
 * @param inputSchema 输入参数 JSON Schema，保持字段顺序
 * @param outputSchema 输出结果 JSON Schema，保持字段顺序
 * @param annotations 读写与破坏性行为提示；可为 {@code null}
 * @since 2026
 */
public record ToolSpec(
        String name,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        ToolAnnotations annotations) {
    /** 规范化构造：{@code null} 模式归一为空 Map，其余改为保序不可变包装。 */
    public ToolSpec {
        // Preserve schema property order — it drives signature field order and Query default
        // shapes,
        // which must match the TypeScript prompt byte-for-byte (so NOT Map.copyOf, which reorders).
        inputSchema =
                inputSchema == null
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(inputSchema));
        outputSchema =
                outputSchema == null
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(outputSchema));
    }
}
