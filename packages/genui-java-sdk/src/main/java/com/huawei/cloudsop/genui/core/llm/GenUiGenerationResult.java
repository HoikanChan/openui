package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 结构化生成结果:同步与流式生成统一返回的可渲染结果。
 *
 * <p>{@code dsl} 是 {@code OpenuiCodeExtractor} 提取后的完整 openui-lang;{@code dataModel} 是
 * {@code UiGenerationRequest.response()} 的防御性不可变拷贝,请求未提供数据时为空 map。
 *
 * <p>{@code validationStatus} / {@code validationResult} 携带本次生成的最终校验状态与详情,供服务层
 * 做缓存决策(仅 VALID/REPAIRED 才缓存,见 design Section 9)与日志记录。校验被禁用
 * ({@link com.huawei.cloudsop.genui.core.validation.ValidationConfigMode#DISABLED}) 时二者均为
 * {@code null}。
 */
public record GenUiGenerationResult(
    String dsl,
    Map<String, Object> dataModel,
    ValidationStatus validationStatus,
    ValidationResult validationResult) {
  public GenUiGenerationResult {
    dataModel = immutableOrderedMap(dataModel);
  }

  /**
   * Backward-compatible constructor: no validation status/result available (e.g. validation
   * disabled, or a call site predating validation support).
   */
  public GenUiGenerationResult(String dsl, Map<String, Object> dataModel) {
    this(dsl, dataModel, null, null);
  }

  private static Map<String, Object> immutableOrderedMap(Map<String, Object> value) {
    return value == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
  }
}
