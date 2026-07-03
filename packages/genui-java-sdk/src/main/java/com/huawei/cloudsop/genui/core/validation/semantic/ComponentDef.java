package com.huawei.cloudsop.genui.core.validation.semantic;

import java.util.List;

/**
 * The catalog view of a single component: its ordered parameter list plus a flag marking
 * signature-only components (schemas that carry {@code x-openui-signature} but no {@code properties}).
 *
 * <p>Mirrors the value side of the TS {@code ParamMap} entry ({@code { params: ParamDef[] }}).
 * Signature-only components can be validated for existence but not for prop-level rules — the walker
 * must skip missing/null/excess/nested checks to avoid false positives.
 *
 * @param name component type name
 * @param params ordered positional parameters (empty for signature-only components)
 * @param signatureOnly {@code true} when only a {@code x-openui-signature} hint is available
 */
public record ComponentDef(String name, List<ParamDef> params, boolean signatureOnly) {
  public ComponentDef {
    params = params == null ? List.of() : List.copyOf(params);
  }
}
