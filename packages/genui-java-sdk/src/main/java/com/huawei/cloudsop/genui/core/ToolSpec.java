package com.huawei.cloudsop.genui.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ToolSpec(
    String name,
    String description,
    Map<String, Object> inputSchema,
    Map<String, Object> outputSchema,
    ToolAnnotations annotations) {
  public ToolSpec {
    // Preserve schema property order — it drives signature field order and Query default shapes,
    // which must match the TypeScript prompt byte-for-byte (so NOT Map.copyOf, which reorders).
    inputSchema =
        inputSchema == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(inputSchema));
    outputSchema =
        outputSchema == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(outputSchema));
  }
}
