package com.huawei.cloudsop.genui.core.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DataModelSpec(String description, Map<String, Object> raw) {
  public DataModelSpec {
    raw = raw == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(raw));
  }
}
