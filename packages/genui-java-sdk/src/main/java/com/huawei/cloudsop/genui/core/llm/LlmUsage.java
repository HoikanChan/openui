/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import java.util.Map;

/**
 * LLM 单次调用的 token 用量统计,对应 OpenAI 兼容响应中的 {@code usage} 字段。
 *
 * @param promptTokens 输入提示词消耗的 token 数
 * @param completionTokens 模型生成消耗的 token 数
 * @param totalTokens 总 token 数
 * @since 2026
 */
public record LlmUsage(int promptTokens, int completionTokens, int totalTokens) {

    /**
     * 从解析后的 JSON {@code usage} 对象构造用量实例;缺少有效数值时返回 {@code null}。
     *
     * @param usageMap 响应体中的 {@code usage} map,可为 null
     * @return 解析得到的用量,或 {@code null}
     */
    public static LlmUsage fromMap(Map<?, ?> usageMap) {
        if (usageMap == null || usageMap.isEmpty()) {
            return null;
        }
        Integer prompt = readInt(usageMap.get("prompt_tokens"));
        Integer completion = readInt(usageMap.get("completion_tokens"));
        Integer total = readInt(usageMap.get("total_tokens"));
        if (prompt == null && completion == null && total == null) {
            return null;
        }
        int promptValue = prompt == null ? 0 : prompt;
        int completionValue = completion == null ? 0 : completion;
        int totalValue = total == null ? promptValue + completionValue : total;
        return new LlmUsage(promptValue, completionValue, totalValue);
    }

    private static Integer readInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.strip());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
