package com.huawei.cloudsop.genui.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
  @Test
  void parsesStableContainersAndNormalizesNumberTypes() {
    Object parsed =
        Json.parse("{\"first\":1,\"second\":1.5,\"third\":1e3,\"items\":[2]}");
    Map<String, Object> object = Json.asObject(parsed, "value");

    assertEquals(
        List.of("first", "second", "third", "items"), new ArrayList<>(object.keySet()));
    assertInstanceOf(LinkedHashMap.class, object);
    assertInstanceOf(Long.class, object.get("first"));
    assertInstanceOf(Double.class, object.get("second"));
    assertInstanceOf(Double.class, object.get("third"));
    assertInstanceOf(ArrayList.class, object.get("items"));
    assertInstanceOf(Long.class, Json.asList(object.get("items"), "items").getFirst());
  }

  @Test
  void preservesCompactAndJavaScriptStylePrettyOutput() {
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("enabled", true);
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("value", 7.0);
    value.put("nested", List.of(nested));

    assertEquals("{\"value\":7,\"nested\":[{\"enabled\":true}]}", Json.stringify(value));
    assertEquals(
        """
        {
          "value": 7,
          "nested": [
            {
              "enabled": true
            }
          ]
        }""",
        Json.stringifyPretty(value));
  }

  @Test
  void stringifiesNonFiniteFloatingPointValuesAsNull() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("nan", Double.NaN);
    values.put("positiveInfinity", Double.POSITIVE_INFINITY);
    values.put("negativeInfinity", Float.NEGATIVE_INFINITY);

    assertEquals("{\"nan\":null,\"positiveInfinity\":null,\"negativeInfinity\":null}",
        Json.stringify(values));
  }

  @Test
  void wrapsInvalidJsonWithTheVendorFailureAsCause() {
    GenerationSdkException error =
        assertThrows(GenerationSdkException.class, () -> Json.parse("{"));

    assertInstanceOf(com.alibaba.fastjson2.JSONException.class, error.getCause());
  }
}
