/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

/**
 * 目录组件的单个位置参数。
 *
 * <p>
 * 对应 {@code packages/lang-core/src/parser/types.ts} 中 TS 侧的 {@code ParamDef}（{@code name}、
 * {@code required}、{@code defaultValue}、{@code schema}）。解析器按顺序把组件的位置参数映射到这些参数定义上。
 *
 * @param name
 *            参数名，如 {@code "title"}
 * @param required
 *            该组件是否要求此参数
 * @param hasDefault
 *            JSON schema 中声明了 {@code default} 时为 {@code true}（用于区分“默认值是 {@code null}”与
 *            “没有默认值”）；对应 TS 侧 {@code defaultValue !== undefined}
 * @param defaultValue
 *            声明的默认值，{@code hasDefault} 为 {@code false} 时为 {@code null}
 * @param schema
 *            该参数的原始 JSON-schema 片段（用于嵌套属性校验），或 {@code null}
 *
 * @since 2026
 */
public record ParamDef(String name, boolean required, boolean hasDefault, Object defaultValue, Object schema) {
}
