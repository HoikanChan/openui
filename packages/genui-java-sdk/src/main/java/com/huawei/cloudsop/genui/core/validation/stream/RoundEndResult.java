/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.stream;

import com.huawei.cloudsop.genui.core.llm.GenerationEndReason;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;

/**
 * 轮末统一校验的结果与记录(#56)。为后续增量修复(#58)与评测复用而记录本轮的原始输出、当前定义、诊断、耗时、契约版本与退出原因。
 *
 * @param rawOutput 本轮模型的原始输出(未经预处理,含围栏/注释)
 * @param currentDefinitions 当前有效定义——按首次出现顺序排列的同名 last-wins 语句文本
 * @param validationResult 轮末在当前有效定义上执行的一次 FINAL 统一校验结果
 * @param durationMs 本轮耗时(会话创建到轮末校验完成的墙钟毫秒数)
 * @param contractVersion 本轮生效的合并契约版本,或 {@code null}
 * @param endReason 该轮的退出原因。正常完成轮末检查时为 {@code null}(结论见 {@link #validationResult()});中断轮没有校验结论,
 *     以会话终态原因(如 {@link GenerationEndReason#TIMEOUT} / {@link
 *     GenerationEndReason#MODEL_REQUEST_FAILED})记录
 * @since 2026
 */
public record RoundEndResult(
        String rawOutput,
        String currentDefinitions,
        ValidationResult validationResult,
        long durationMs,
        String contractVersion,
        GenerationEndReason endReason) {}
