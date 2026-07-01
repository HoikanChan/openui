package com.huawei.cloudsop.genui.core.validation.semantic;

import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Component catalog derived from a merged {@link GenerationContract}.
 *
 * <p>Mirrors the TS {@code ParamMap} ({@code Map<name, {params: ParamDef[]}>}) that {@code
 * materialize.ts} consults via {@code ctx.cat}. For each component this extracts the ordered
 * positional parameters (from {@code propsSchema.properties} insertion order), which are required
 * (from {@code propsSchema.required}), each param's declared default, and the raw per-param schema
 * fragment for nested-prop validation.
 *
 * <p>Signature-only components ({@code x-openui-signature} with no {@code properties}) are recorded
 * so existence checks pass, but with an empty param list and {@code signatureOnly=true} so the walker
 * skips prop-level rules.
 */
public final class ContractCatalog {

  private static final String SIGNATURE_KEY = "x-openui-signature";

  private final Map<String, ComponentDef> byName;
  private final String rootName;

  private ContractCatalog(Map<String, ComponentDef> byName, String rootName) {
    this.byName = byName;
    this.rootName = rootName;
  }

  /** Build a catalog from a merged generation contract. */
  public static ContractCatalog from(GenerationContract contract) {
    Map<String, ComponentDef> byName = new LinkedHashMap<>();
    if (contract != null) {
      for (Map.Entry<String, ComponentPromptSpec> entry : contract.components().entrySet()) {
        byName.put(entry.getKey(), buildDef(entry.getKey(), entry.getValue()));
      }
    }
    String root = contract == null ? null : contract.root();
    return new ContractCatalog(Collections.unmodifiableMap(byName), root);
  }

  /** The contract's configured root component name (may be {@code null}). */
  public String root() {
    return rootName;
  }

  /** {@code true} iff {@code name} is a known catalog component. */
  public boolean isKnown(String name) {
    return byName.containsKey(name);
  }

  /** The component definition for {@code name}, or {@code null} if not a catalog component. */
  public ComponentDef get(String name) {
    return byName.get(name);
  }

  @SuppressWarnings("unchecked")
  private static ComponentDef buildDef(String name, ComponentPromptSpec spec) {
    Map<String, Object> schema = spec.propsSchema();

    // Signature-only (no structured properties) → existence-only, no prop checks.
    Object propertiesRaw = schema.get("properties");
    if (!(propertiesRaw instanceof Map<?, ?> propertiesMap) || propertiesMap.isEmpty()) {
      boolean signatureOnly = schema.containsKey(SIGNATURE_KEY);
      return new ComponentDef(name, List.of(), signatureOnly);
    }

    List<String> required = readRequired(schema.get("required"));

    List<ParamDef> params = new ArrayList<>();
    for (Map.Entry<?, ?> entry : propertiesMap.entrySet()) {
      String paramName = String.valueOf(entry.getKey());
      Object paramSchema = entry.getValue();
      boolean isRequired = required.contains(paramName);
      boolean hasDefault = false;
      Object defaultValue = null;
      if (paramSchema instanceof Map<?, ?> ps && ps.containsKey("default")) {
        hasDefault = true;
        defaultValue = ((Map<String, Object>) ps).get("default");
      }
      params.add(new ParamDef(paramName, isRequired, hasDefault, defaultValue, paramSchema));
    }
    return new ComponentDef(name, params, false);
  }

  private static List<String> readRequired(Object requiredRaw) {
    if (!(requiredRaw instanceof List<?> list)) return List.of();
    List<String> out = new ArrayList<>();
    for (Object item : list) out.add(String.valueOf(item));
    return out;
  }
}
