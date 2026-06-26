package com.huawei.cloudsop.genui.core.llm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 结构化生成结果:同步与流式生成统一返回的可渲染结果。
 *
 * <p>{@code dsl} 是 {@code OpenuiCodeExtractor} 提取后的完整 openui-lang;{@code dataModel} 是
 * {@code UiGenerationRequest.response()} 的防御性不可变拷贝,请求未提供数据时为空 map。
 */
public record GenUiGenerationResult(String dsl, Map<String, Object> dataModel) {
  public GenUiGenerationResult {
    dataModel = immutableOrderedMap(dataModel);
  }

  private static Map<String, Object> immutableOrderedMap(Map<String, Object> value) {
    return value == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
  }
}
