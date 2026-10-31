/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

/**
 * 一条生成路径在一次 Render Data Model 快照上的解析结果。
 *
 * <p>解析结果直接携带类型内核的 {@link Type},不再经过第二套值类型代数;字段存在性计数保留为观察证据。
 *
 * @since 2026
 */
public sealed interface DataPathResolution
        permits DataPathResolution.Resolved,
                DataPathResolution.Missing,
                DataPathResolution.InvalidTraversal,
                DataPathResolution.Unprovable {

    /**
     * 本次解析的原始路径文本。
     *
     * @return 与生成表达式一致的 {@code data...} 路径
     */
    String path();

    /**
     * 最后一步遍历的字段存在性计数。
     *
     * @param present 有非 null 值的成员数
     * @param missing 缺该字段的成员数(数组字段投影时可能出现)
     * @param nulls 显式 null 值的成员数(null 不是缺失)
     * @param total 参与该步的成员总数
     * @param plucked 该步是否为数组字段投影(逐元素读取字段);投影结果是一个数组,元素来自 present/nulls 成员
     * @since 2026
     */
    record Evidence(int present, int missing, int nulls, int total, boolean plucked) {}

    /**
     * 成功解析:类型上界、最后一步证据与首次观察值。
     *
     * @param path 原始路径文本
     * @param type 遍历后提升出的类型上界
     * @param evidence 最后一步遍历的字段存在性计数
     * @param observedValue 首次遍历得到的快照值;数组字段投影路径不保留(与旧快照查值语义一致), 无观察值时可为 null
     * @since 2026
     */
    record Resolved(String path, Type type, Evidence evidence, Object observedValue)
            implements DataPathResolution {}

    /**
     * 路径在快照中不存在。
     *
     * @param path 原始路径文本
     * @param hint 修复提示
     * @since 2026
     */
    record Missing(String path, String hint) implements DataPathResolution {}

    /**
     * 遍历遇到与路径段不兼容的值。
     *
     * @param path 原始路径文本
     * @param actualType 实际值的粗粒度类型名(不含快照字面值)
     * @param hint 修复提示
     * @since 2026
     */
    record InvalidTraversal(String path, String actualType, String hint)
            implements DataPathResolution {}

    /**
     * 当前快照证据不足以判定遍历结果。
     *
     * @param path 原始路径文本
     * @param hint 补充证据的提示
     * @since 2026
     */
    record Unprovable(String path, String hint) implements DataPathResolution {}
}
