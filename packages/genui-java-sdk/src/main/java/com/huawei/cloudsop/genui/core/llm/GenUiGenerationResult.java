/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构化生成结果:同步与流式生成统一返回的可渲染结果。
 *
 * <p>
 * {@code dsl} 在同步生成时是直接提取的原始 DSL;流式校验开启时是同名语句以最新定义为准(last-wins)去重后的当前完整程序。{@code dataModel} 是
 * {@code UiGenerationRequest.response()} 的防御性不可变拷贝,请求未提供数据时为空 map。
 *
 * <p>
 * {@code validationStatus} / {@code validationResult} 携带轮末统一校验的最终状态与详情,供服务层 做缓存决策(仅最终 {@link ValidationStatus#VALID}
 * 才缓存;repair 成功以 VALID 加 metadata 标记表达,见 design Section 9)与日志记录。校验被禁用
 * ({@link com.huawei.cloudsop.genui.core.validation.ValidationConfigMode#DISABLED}) 或同步生成时二者均为 {@code null}。
 *
 * <p>
 * {@code endReason}(#57)区分检查通过、校验失败、未检查、超时与模型请求失败——DSL 会话结论在独立 Markdown 降级成功时仍保留。
 *
 * <p>
 * {@code rounds}(#56)是逐轮记录:每轮的原始输出、当前定义、诊断、耗时、契约版本与退出原因,供后续增量修复(#58)与评测复用。正常完成轮末检查的轮 {@code endReason} 为
 * {@code null}(结论见 {@code validationResult});中断轮记录会话终态原因。流中途失败仍保留已完成轮与中断轮,校验禁用时不产生轮记录。
 */
public record GenUiGenerationResult(String dsl, Map<String, Object> dataModel, ValidationStatus validationStatus,
        ValidationResult validationResult, GenerationEndReason endReason, List<RoundEndResult> rounds) {
    public GenUiGenerationResult {
        dataModel = immutableOrderedMap(dataModel);
        endReason = endReason == null ? GenerationEndReason.UNCHECKED : endReason;
        rounds = rounds == null ? List.of() : List.copyOf(rounds);
    }

    /**
     * Backward-compatible constructor: no validation status/result available (e.g. validation disabled, or a call site
     * predating validation support).
     */
    public GenUiGenerationResult(String dsl, Map<String, Object> dataModel) {
        this(dsl, dataModel, null, null);
    }

    public GenUiGenerationResult(String dsl, Map<String, Object> dataModel, ValidationStatus status,
            ValidationResult validation) {
        this(dsl, dataModel, status, validation,
                status == ValidationStatus.VALID
                        ? GenerationEndReason.CHECK_PASSED
                        : status == ValidationStatus.INVALID
                                ? GenerationEndReason.VALIDATION_FAILED
                                : GenerationEndReason.UNCHECKED,
                null);
    }

    public GenUiGenerationResult withEndReason(GenerationEndReason reason) {
        return new GenUiGenerationResult(dsl, dataModel, validationStatus, validationResult, reason, rounds);
    }

    /**
     * Keep the DSL rounds across a whole-page Markdown fallback: the returned DSL/Markdown content comes from the
     * fallback, while completed and interrupted round records come from the failed DSL session (#58).
     *
     * @param reason
     *            DSL session terminal reason
     * @param dslRounds
     *            DSL session round records; {@code null} keeps this result's rounds
     * @return result carrying the fallback content and the DSL session rounds
     */
    public GenUiGenerationResult withEndReason(GenerationEndReason reason, List<RoundEndResult> dslRounds) {
        return new GenUiGenerationResult(dsl, dataModel, validationStatus, validationResult, reason,
                dslRounds == null ? rounds : dslRounds);
    }

    private static Map<String, Object> immutableOrderedMap(Map<String, Object> value) {
        return value == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
