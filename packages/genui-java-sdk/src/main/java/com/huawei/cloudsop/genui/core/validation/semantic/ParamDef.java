package com.huawei.cloudsop.genui.core.validation.semantic;

/**
 * One positional parameter of a catalog component.
 *
 * <p>Mirrors the TS {@code ParamDef} in {@code packages/lang-core/src/parser/types.ts}
 * ({@code name}, {@code required}, {@code defaultValue}, {@code schema}). The parser maps a
 * component's positional args onto these param definitions, in order.
 *
 * @param name parameter name, e.g. {@code "title"}
 * @param required whether the component requires this parameter
 * @param hasDefault {@code true} iff a {@code default} was declared in the JSON schema (distinguishes
 *     "default is {@code null}" from "no default"); mirrors TS {@code defaultValue !== undefined}
 * @param defaultValue the declared default, or {@code null} when {@code hasDefault} is {@code false}
 * @param schema the raw JSON-schema fragment for this parameter (used for nested-prop validation), or
 *     {@code null}
 */
public record ParamDef(
    String name, boolean required, boolean hasDefault, Object defaultValue, Object schema) {}
