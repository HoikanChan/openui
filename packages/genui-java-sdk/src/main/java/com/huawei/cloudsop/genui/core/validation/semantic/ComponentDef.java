/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

import java.util.List;

/**
 * 单个组件的目录视图：有序参数列表，以及标记“仅签名”组件（携带 {@code x-openui-signature} 但没有
 * {@code properties} 的 schema）的标志。
 *
 * <p>
 * 对应 TS 侧 {@code ParamMap} 条目的取值部分（{@code &#123; params: ParamDef[] &#125;}）。仅签名组件可以校验
 * 是否存在，但不能做属性级规则校验 —— 遍历器必须跳过缺失/空值/多余/嵌套等检查以避免误报。
 *
 * @param name
 *            组件类型名
 * @param params
 *            有序位置参数列表（仅签名组件时为空）
 * @param signatureOnly
 *            仅有 {@code x-openui-signature} 提示可用时为 {@code true}
 *
 * @since 2026
 */
public record ComponentDef(String name, List<ParamDef> params, boolean signatureOnly) {

    /**
     * 紧凑构造方法，将参数列表归一化为不可变列表。
     */
    public ComponentDef {
        params = params == null ? List.of() : List.copyOf(params);
    }
}
