package com.huawei.clodsop.genui.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
  @Test
  void stringifiesNonFiniteFloatingPointValuesAsNull() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("nan", Double.NaN);
    values.put("positiveInfinity", Double.POSITIVE_INFINITY);
    values.put("negativeInfinity", Float.NEGATIVE_INFINITY);

    assertEquals("{\"nan\":null,\"positiveInfinity\":null,\"negativeInfinity\":null}",
        Json.stringify(values));
  }
}
