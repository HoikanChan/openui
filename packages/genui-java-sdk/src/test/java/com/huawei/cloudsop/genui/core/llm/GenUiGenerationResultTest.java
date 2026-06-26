package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenUiGenerationResultTest {
  @Test
  void dataModelIsDefensivelyCopiedAndImmutable() {
    LinkedHashMap<String, Object> source = new LinkedHashMap<>();
    source.put("count", 1);

    GenUiGenerationResult result = new GenUiGenerationResult("root = Stack([])", source, "t-1");

    source.put("extra", true);

    assertEquals(Map.of("count", 1), result.dataModel());
    assertThrows(UnsupportedOperationException.class, () -> result.dataModel().put("x", 1));
  }

  @Test
  void nullDataModelBecomesEmptyMap() {
    GenUiGenerationResult result = new GenUiGenerationResult("root = Stack([])", null, null);

    assertTrue(result.dataModel().isEmpty());
  }
}
