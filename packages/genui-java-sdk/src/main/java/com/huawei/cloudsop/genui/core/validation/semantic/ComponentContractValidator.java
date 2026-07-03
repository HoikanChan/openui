package com.huawei.cloudsop.genui.core.validation.semantic;

import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import java.util.List;
import java.util.Map;

/**
 * Contract checks for a single catalog component invocation.
 *
 * <p>Mirrors the validation portions of the {@code "Comp"} branch of {@code materializeValue} in
 * {@code packages/lang-core/src/parser/materialize.ts}: excess positional args, missing/null required
 * props (with a {@code defaultValue} fallback applied before reporting), and nested invalid props
 * ({@code validateNestedObjectProps}, including the removed {@code Col.options.format} hint).
 *
 * <p>Inputs are the already-materialized positional arg VALUES (plain Java objects: {@code Map},
 * {@code List}, {@code String}, ..., or {@code null} for unresolved refs / placeholders / dropped
 * components) — the same shape the TS walker holds in {@code props[name]} when it runs these checks.
 * The severity is chosen by the caller (mode-aware); complete-statement contract errors are ERROR.
 */
final class ComponentContractValidator {

  /** The exact removed-prop message from materialize.ts — LLMs hit this constantly. */
  static final String COL_OPTIONS_FORMAT_MESSAGE =
      "The `format` prop on Col options was removed. Use "
          + "@FormatDate/@FormatBytes/@FormatNumber/@FormatPercent/@FormatDuration in an expression "
          + "or @Render(...) instead.";

  private static final String SOURCE = "contract";

  private final ValidationSeverity severity;
  private final boolean retryable;
  private final List<ValidationIssue> sink;

  ComponentContractValidator(
      ValidationSeverity severity, boolean retryable, List<ValidationIssue> sink) {
    this.severity = severity;
    this.retryable = retryable;
    this.sink = sink;
  }

  /**
   * Validate a catalog component invocation. {@code argValues} are the materialized positional
   * arguments in source order; entries beyond {@code def.params().size()} are excess. A {@code null}
   * entry means the arg resolved to null (unresolved ref, placeholder, dropped nested component).
   * {@code argCount} is the raw number of positional args supplied (may exceed argValues size only if
   * excess were not materialized, but here they match).
   *
   * @return {@code true} if the component is "valid enough" to render (no missing/null required left
   *     after defaults) — mirrors TS returning a non-null ElementNode.
   */
  boolean validate(String componentName, ComponentDef def, List<Object> argValues, String statementId) {
    // Signature-only components: existence already confirmed by the caller; skip prop rules.
    if (def.signatureOnly()) {
      return true;
    }

    List<ParamDef> params = def.params();

    // Map positional args → named props (only up to the declared param count).
    // props tracks presence + value, mirroring TS `props[name] = materializeValue(...)`.
    java.util.LinkedHashMap<String, Object> props = new java.util.LinkedHashMap<>();
    for (int i = 0; i < params.size() && i < argValues.size(); i++) {
      ParamDef p = params.get(i);
      Object value = argValues.get(i);
      props.put(p.name(), value);
      validateNestedObjectProps(componentName, value, p.schema(), "/" + p.name(), statementId);
    }

    // Excess positional args (extra args are silently dropped in TS, but reported).
    if (argValues.size() > params.size()) {
      int excess = argValues.size() - params.size();
      emit(
          "excess-args",
          componentName,
          "",
          componentName
              + " takes "
              + params.size()
              + " arg(s), got "
              + argValues.size()
              + " ("
              + excess
              + " excess dropped)",
          statementId);
    }

    // Required props — apply defaultValue before reporting; missing vs null distinguished by presence.
    boolean renderable = true;
    for (ParamDef p : params) {
      if (!p.required()) continue;
      boolean present = props.containsKey(p.name());
      Object value = props.get(p.name());
      boolean invalid = !present || value == null;
      if (!invalid) continue;

      if (p.hasDefault()) {
        // Default fills the gap — no error (mirrors TS `props[p.name] = p.defaultValue; return false`).
        continue;
      }
      boolean isNull = present; // present but null → null-required; absent → missing-required
      emit(
          isNull ? "null-required" : "missing-required",
          componentName,
          "/" + p.name(),
          isNull
              ? "required field \"" + p.name() + "\" cannot be null"
              : "missing required field \"" + p.name() + "\"",
          statementId);
      renderable = false;
    }
    return renderable;
  }

  /**
   * Recursively validate an object-typed prop value against its schema, mirroring TS {@code
   * validateNestedObjectProps}. Only flags unknown keys when the schema forbids additional properties
   * ({@code additionalProperties === false}). The {@code Col.options.format} key gets the special
   * removed-prop hint.
   */
  @SuppressWarnings("unchecked")
  private void validateNestedObjectProps(
      String componentName, Object value, Object schema, String pathPrefix, String statementId) {
    Map<String, Object> objectSchema = resolveObjectSchema(schema);
    if (objectSchema == null || !(value instanceof Map<?, ?> record)) {
      return;
    }

    Object propertiesRaw = objectSchema.get("properties");
    Map<String, Object> properties =
        propertiesRaw instanceof Map<?, ?> pm ? (Map<String, Object>) pm : Map.of();
    boolean allowsAdditional = !Boolean.FALSE.equals(objectSchema.get("additionalProperties"));

    for (Map.Entry<?, ?> entry : record.entrySet()) {
      String key = String.valueOf(entry.getKey());
      Object nestedValue = entry.getValue();
      Object nestedSchema = properties.get(key);

      if (nestedSchema == null && !allowsAdditional) {
        boolean isRemovedFormatProp =
            "Col".equals(componentName) && "/options".equals(pathPrefix) && "format".equals(key);
        emit(
            "invalid-prop",
            componentName,
            pathPrefix + "/" + key,
            isRemovedFormatProp
                ? COL_OPTIONS_FORMAT_MESSAGE
                : "Unknown property \""
                    + key
                    + "\" on "
                    + componentName
                    + pathPrefix.replace("/", "."),
            statementId);
        continue;
      }
      if (nestedSchema != null) {
        validateNestedObjectProps(
            componentName, nestedValue, nestedSchema, pathPrefix + "/" + key, statementId);
      }
    }
  }

  /** Mirrors TS {@code resolveObjectSchema}: unwraps object / anyOf / oneOf to a properties schema. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> resolveObjectSchema(Object schema) {
    if (!(schema instanceof Map<?, ?> map)) return null;
    Map<String, Object> direct = (Map<String, Object>) map;

    Object type = direct.get("type");
    Object properties = direct.get("properties");
    if ("object".equals(type) && properties instanceof Map<?, ?>) {
      return direct;
    }

    Object variantsRaw = direct.get("anyOf");
    if (!(variantsRaw instanceof List<?>)) variantsRaw = direct.get("oneOf");
    if (variantsRaw instanceof List<?> variants) {
      for (Object variant : variants) {
        Map<String, Object> resolved = resolveObjectSchema(variant);
        if (resolved != null) return resolved;
      }
    }
    return null;
  }

  private void emit(
      String code, String component, String path, String message, String statementId) {
    sink.add(
        new ValidationIssue(
            code, severity, SOURCE, message, statementId, component, path, -1, -1, null, retryable));
  }
}
