package com.huawei.cloudsop.genui.core.prompt.characterize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CharacterizationConfigTest {
  @Test
  void defaultsMatchSpecifiedValues() {
    CharacterizationConfig config = CharacterizationConfig.defaults();

    assertTrue(config.enabled());
    assertEquals(2048, config.triggerBytes());
    assertEquals(3, config.sampleRows());
    assertEquals(80, config.maxStringLen());
    assertEquals(50, config.enumMaxDistinct());
    assertEquals(0.5, config.enumMaxRatio());
    assertEquals(10000, config.deepScanLimit());
  }

  @Test
  void builderOverridesSelectedFieldsAndKeepsRemainingDefaults() {
    CharacterizationConfig config = CharacterizationConfig.builder().sampleRows(5).build();

    assertEquals(5, config.sampleRows());
    assertTrue(config.enabled());
    assertEquals(2048, config.triggerBytes());
    assertEquals(80, config.maxStringLen());
    assertEquals(50, config.enumMaxDistinct());
    assertEquals(0.5, config.enumMaxRatio());
    assertEquals(10000, config.deepScanLimit());
  }

  @Test
  void builderOverridesEveryField() {
    CharacterizationConfig config =
        CharacterizationConfig.builder()
            .enabled(false)
            .triggerBytes(1)
            .sampleRows(7)
            .maxStringLen(10)
            .enumMaxDistinct(5)
            .enumMaxRatio(0.1)
            .deepScanLimit(20)
            .build();

    assertEquals(false, config.enabled());
    assertEquals(1, config.triggerBytes());
    assertEquals(7, config.sampleRows());
    assertEquals(10, config.maxStringLen());
    assertEquals(5, config.enumMaxDistinct());
    assertEquals(0.1, config.enumMaxRatio());
    assertEquals(20, config.deepScanLimit());
  }
}
