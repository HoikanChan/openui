package com.huawei.cloudsop.genui.core.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenUIExtensionTest {
  @Test
  void exposesExtensionContractType() {
    GenUIExtension extension =
        new GenUIExtension("extension-a", "v1", Map.of(), List.of(), List.of(), List.of(), List.of());

    assertEquals("extension-a", extension.extensionId());
  }
}
