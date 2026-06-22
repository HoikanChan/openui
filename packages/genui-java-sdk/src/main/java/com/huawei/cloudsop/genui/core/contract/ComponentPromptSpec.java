package com.huawei.cloudsop.genui.core.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ComponentPromptSpec(String description, Map<String, Object> propsSchema) {
  public ComponentPromptSpec {
    propsSchema =
        propsSchema == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(propsSchema));
  }

  /** Legacy signature-based fixture/caller constructor while prompt goldens migrate to propsSchema. */
  public ComponentPromptSpec(String signature, String description) {
    this(description, Map.of("x-openui-signature", signature));
  }

  /** Legacy signature accessor for prompt alignment tests. */
  public String signature() {
    Object signature = propsSchema.get("x-openui-signature");
    return signature == null ? null : String.valueOf(signature);
  }
}
