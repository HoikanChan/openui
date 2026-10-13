/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 附加在 {@link ValidationResult} 上的轻量遥测/生成元数据。保持精简 —— 高级字段属于后续章节的类型。
 *
 * @param statementCount
 *            语句数量
 * @param rootName
 *            DSL 中发现的根组件名，或 {@code null}
 * @param mode
 *            本次结果所用的校验模式
 * @param extra
 *            任意扩展数据；不会为 {@code null}，且不可变
 *
 * @since 2026
 */
public record ValidationMetadata(int statementCount, String rootName, ValidationMode mode,
        Map<String, String> extra) {

    /**
     * 紧凑构造方法，将 {@code extra} 归一化为不可变映射。
     */
    public ValidationMetadata {
        extra = extra == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extra));
    }
}
