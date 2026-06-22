package com.huawei.cloudsop.genui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.cloudsop.genui.core.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.GenUIGeneration;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 锁住 SeedExtensionsRunner 的绑定路径:种子经 {@code objectMapper.readValue(in,
 * GenUIGeneration.class)} 走 ComponentPromptSpec 规范构造器 (description, propsSchema)。
 *
 * <p>历史隐患:种子若用旧 {@code signature} 形,该字段是未知属性被 Jackson 静默丢弃,组件在 prompt
 * 里没有 props(渲染成无参 {@code AlarmBadge()})。SeedRecoveryAfterRestartTest 只校验
 * generationId 集合,覆盖不到。本测试直读种子资源,断言 propsSchema 完整保真。
 */
class SeedComponentPropsSchemaRoundTripTest {
  // 关闭 FAIL_ON_UNKNOWN_PROPERTIES,对齐 Spring Boot 默认 ObjectMapper(SeedExtensionsRunner 用的就是它):
  // 这样旧 signature 形会像生产里那样被"静默丢弃"(而非抛 UnrecognizedProperty),
  // 由下面 propsSchema 非空断言来抓回归,忠实复现生产失败模式。
  private final ObjectMapper om =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  @Test
  void seedAlarmBadgeRetainsPropsSchemaThroughJacksonBinding() throws Exception {
    GenUIGeneration generation;
    try (InputStream in =
        getClass().getResourceAsStream("/seed/noe-biz-components.json")) {
      assertNotNull(in, "种子资源 seed/noe-biz-components.json 缺失");
      generation = om.readValue(in, GenUIGeneration.class);
    }

    ComponentPromptSpec alarmBadge = generation.components().get("AlarmBadge");
    assertNotNull(alarmBadge, "种子应包含 AlarmBadge 组件");

    Map<String, Object> propsSchema = alarmBadge.propsSchema();
    assertTrue(
        !propsSchema.isEmpty() && alarmBadge.signature() == null,
        "propsSchema 须非空且非旧 signature 形(否则被 Jackson 静默丢弃,组件无 props)");
    assertEquals("object", propsSchema.get("type"), "propsSchema.type 应为 object");

    @SuppressWarnings("unchecked")
    Map<String, Object> properties = (Map<String, Object>) propsSchema.get("properties");
    assertNotNull(properties, "propsSchema 应含 properties");
    assertTrue(
        properties.keySet().containsAll(Set.of("severity", "count", "label")),
        "AlarmBadge 三个 props 应全部保真,实际:" + properties.keySet());
  }
}
