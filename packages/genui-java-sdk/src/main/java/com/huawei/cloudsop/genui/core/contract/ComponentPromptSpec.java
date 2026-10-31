/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个组件在提示词中的描述与结构化 props 模式。
 *
 * @param description 组件功能描述，写入提示词
 * @param propsSchema 组件 props 的 JSON Schema，字段顺序参与签名渲染
 * @since 2026
 */
public record ComponentPromptSpec(String description, Map<String, Object> propsSchema) {
    /** 规范化构造：{@code null} 模式归一为空 Map，其余改为保序不可变包装。 */
    public ComponentPromptSpec {
        propsSchema =
                propsSchema == null
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(propsSchema));
    }

    /**
     * 基于旧签名的 fixture/调用方兼容构造，提示词基线迁移到 propsSchema 期间使用。
     *
     * @param signature 旧版调用签名，存入 {@code x-openui-signature}
     * @param description 组件功能描述
     */
    public ComponentPromptSpec(String signature, String description) {
        this(description, Map.of("x-openui-signature", signature));
    }

    /**
     * 读取旧版签名值，供提示词对齐测试使用。
     *
     * @return {@code x-openui-signature} 的字符串值；未声明时为 {@code null}
     */
    public String signature() {
        Object signature = propsSchema.get("x-openui-signature");
        return signature == null ? null : String.valueOf(signature);
    }
}
