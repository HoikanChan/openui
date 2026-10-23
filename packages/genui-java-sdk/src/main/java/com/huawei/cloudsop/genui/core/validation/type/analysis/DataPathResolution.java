/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

/**
 * 一条生成路径在一次 Render Data Model 快照上的解析结果。
 *
 * <p>
 * 解析结果直接携带类型内核的 {@link Type},不再经过第二套值类型代数;字段存在性计数保留为观察证据。
 *
 * @since 2026
 */
public sealed interface DataPathResolution permits DataPathResolution.Resolved, DataPathResolution.Missing,
        DataPathResolution.InvalidTraversal, DataPathResolution.Unprovable {

    String path();

    /**
     * 最后一步遍历的字段存在性计数。
     *
     * @param present
     *            有非 null 值的成员数
     * @param missing
     *            缺该字段的成员数(数组字段投影时可能出现)
     * @param nulls
     *            显式 null 值的成员数(null 不是缺失)
     * @param total
     *            参与该步的成员总数
     * @param plucked
     *            该步是否为数组字段投影(逐元素读取字段);投影结果是一个数组,元素来自 present/nulls 成员
     */
    record Evidence(int present, int missing, int nulls, int total, boolean plucked) {
    }

    record Resolved(String path, Type type, Evidence evidence) implements DataPathResolution {
    }

    record Missing(String path, String hint) implements DataPathResolution {
    }

    record InvalidTraversal(String path, String actualType, String hint) implements DataPathResolution {
    }

    record Unprovable(String path, String hint) implements DataPathResolution {
    }
}
