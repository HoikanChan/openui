/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 契约声明的能力边界缺口条目（#71 / #74）。
 *
 * @param id
 *            能力缺口唯一标识（如 {@code group-by}、{@code top-n}、{@code join}）
 * @param kind
 *            缺口类别：{@code "data"} 或 {@code "component"}（加载期强校验；当前仅路由数据类缺口，{@code "component"} 为扩展预留位，无差异化运行时分支）
 * @param name
 *            面向用户与提示词的能力名称（如 {@code "分组展示"}）
 * @param aliases
 *            模型常见幻觉调用名列表（不含前导 {@code @}，不可与已有组件名或 builtin 名重名）
 * @param phrases
 *            用户请求中的典型意图短语列表
 * @param fallback
 *            按真实数据展示的替代规则描述
 * @since 2026
 */
public record CapabilityGapSpec(String id, String kind, String name, List<String> aliases, List<String> phrases,
        String fallback) {

    /** 首轮生成提示词中展示的通用能力降级说明语句模板（单一事实来源）。 */
    public static final String NOTICE_STATEMENT_TEMPLATE = "capabilityNotice = TextCallout(\"info\", \"部分需求暂不支持\", \"暂不支持<name>，以下按原始数据展示\")";

    public CapabilityGapSpec {
        // 使用允许 null 元素的不可变包装，确保含 null 元素的列表能走到 GenerationContractLoader.validateCapabilityGaps 抛出语义化
        // GenerationSdkException，而非提前 NPE。
        aliases = aliases == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(aliases));
        phrases = phrases == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(phrases));
    }

    /** 生成当前能力缺口对应的 {@code capabilityNotice} 声明语句（单一事实来源）。 */
    public String noticeStatement() {
        return formatNoticeStatement(name);
    }

    /** 按给定缺口名称生成 {@code capabilityNotice} 声明语句。 */
    public static String formatNoticeStatement(String gapName) {
        return "capabilityNotice = TextCallout(\"info\", \"部分需求暂不支持\", \"暂不支持" + gapName + "，以下按原始数据展示\")";
    }

    /** 剥离可选前导 {@code @} 并去空白，供契约校验与修复路由共用。 */
    public static String normalizeAlias(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.strip();
        return trimmed.startsWith("@") ? trimmed.substring(1) : trimmed;
    }
}
