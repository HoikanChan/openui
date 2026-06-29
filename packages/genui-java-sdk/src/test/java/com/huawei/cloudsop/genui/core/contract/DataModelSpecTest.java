package com.huawei.cloudsop.genui.core.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DataModelSpecTest {

  @Test
  void twoArgConstructorDefaultsShapeSidecarToNull() {
    DataModelSpec spec = new DataModelSpec("desc", Map.of("a", 1L));

    assertNull(spec.shapeSidecar());
    assertEquals("desc", spec.description());
  }

  @Test
  void threeArgConstructorCarriesShapeSidecar() {
    DataModelSpec spec = new DataModelSpec("desc", Map.of("a", 1L), "data: {\n  a: number\n}");

    assertEquals("data: {\n  a: number\n}", spec.shapeSidecar());
  }

  @Test
  void rawIsStillNormalizedToAnUnmodifiableLinkedHashMapCopy() {
    DataModelSpec spec = new DataModelSpec("desc", Map.of("a", 1L), null);

    assertThrows(UnsupportedOperationException.class, () -> spec.raw().put("b", 2L));
  }

  @Test
  void nullRawNormalizesToEmptyMapViaThreeArgConstructor() {
    DataModelSpec spec = new DataModelSpec("desc", null, null);

    assertEquals(Map.of(), spec.raw());
  }
}
