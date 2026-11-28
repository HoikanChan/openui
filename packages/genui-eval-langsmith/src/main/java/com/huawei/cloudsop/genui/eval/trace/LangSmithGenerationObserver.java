/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.trace;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerationResult;
import com.huawei.cloudsop.genui.core.llm.GenerationObserver;
import com.huawei.cloudsop.genui.core.llm.LlmUsage;
import com.huawei.cloudsop.genui.core.llm.UiGenerationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;
import com.huawei.cloudsop.genui.eval.langsmith.BatchRunBuffer;
import com.huawei.cloudsop.genui.eval.langsmith.DottedOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将 {@link GenerationObserver} 生命周期回调映射为 LangSmith 嵌套 Run 树： 顶层案例 Run（{@code chain}，绑定 {@code
 * session_id} 与 {@code reference_example_id}）→ 逐轮 LLM 调用子 Run（{@code llm}，含真实请求体和 token 用量）+ 校验器子
 * Run（{@code tool}，含 raw/actionable 诊断）。
 *
 * @since 2026
 */
public final class LangSmithGenerationObserver implements GenerationObserver {
    private final BatchRunBuffer buffer;
    private final String sessionId;
    private final String referenceExampleId;
    private final String caseId;
    private final String rootRunId;
    private final String rootName;
    private final Map<String, Object> rootInputs;
    private final Map<String, Object> rootMetadata;

    private volatile String rootDottedOrder;
    private volatile boolean rootPosted;
    private final Map<String, ActiveLlmCall> activeCalls = new ConcurrentHashMap<>();
    private final List<CapturedLlmCall> capturedCalls =
            java.util.Collections.synchronizedList(new ArrayList<>());

    private record ActiveLlmCall(
            String runId, String dottedOrder, Instant startTime, String requestBodyJson) {}

    /**
     * 单次模型调用的本地记录（供离线 HTML 报告展示真实发送的 prompt，不再事后重建）。
     *
     * @param roundIndex 轮次序号
     * @param kind 调用类型
     * @param model 模型名
     * @param requestBodyJson 实际发出的请求体 JSON
     * @param rawOutput 模型原始输出
     * @param usage 可选 token 用量
     * @param durationMs 耗时（毫秒）
     * @param error 错误信息，成功时为 null
     * @since 2026
     */
    public record CapturedLlmCall(
            int roundIndex,
            String kind,
            String model,
            String requestBodyJson,
            String rawOutput,
            LlmUsage usage,
            long durationMs,
            String error) {}

    /**
     * 为单条样例创建 LangSmith 追踪观测器。
     *
     * @param buffer 批量 Run 上报器；离线模式可传 {@code null}（此时仍采集真实调用记录）
     * @param sessionId LangSmith 实验会话 ID，可为 null
     * @param referenceExampleId LangSmith 数据集样例 ID，可为 null
     * @param caseId 样例业务 ID
     * @param rootName 顶层 Run 展示名称
     * @param rootInputs 顶层 Run 输入摘要
     * @param rootMetadata 顶层 Run 元数据
     */
    public LangSmithGenerationObserver(
            BatchRunBuffer buffer,
            String sessionId,
            String referenceExampleId,
            String caseId,
            String rootName,
            Map<String, Object> rootInputs,
            Map<String, Object> rootMetadata) {
        this.buffer = buffer;
        this.sessionId = sessionId;
        this.referenceExampleId = referenceExampleId;
        this.caseId = caseId;
        this.rootRunId = UUID.randomUUID().toString();
        this.rootName = rootName == null || rootName.isBlank() ? "genui_case" : rootName;
        this.rootInputs = rootInputs == null ? Map.of() : new LinkedHashMap<>(rootInputs);
        this.rootMetadata = rootMetadata == null ? Map.of() : new LinkedHashMap<>(rootMetadata);
    }

    /**
     * 返回本案例对应的 LangSmith 顶层 Run UUID（用于关联 Feedback 和前端视觉打分）。
     *
     * @return 顶层 Run UUID
     */
    public String rootRunId() {
        return rootRunId;
    }

    /**
     * 返回本案例采集到的全部真实模型调用记录（不可变快照）。
     *
     * @return 调用记录列表
     */
    public List<CapturedLlmCall> capturedCalls() {
        synchronized (capturedCalls) {
            return List.copyOf(capturedCalls);
        }
    }

    /**
     * 返回本案例累计的 token 用量汇总；没有任何调用返回用量时返回 {@code null}。
     *
     * @return 汇总 token 用量或 {@code null}
     */
    public LlmUsage totalUsage() {
        int prompt = 0;
        int completion = 0;
        int total = 0;
        boolean any = false;
        synchronized (capturedCalls) {
            for (CapturedLlmCall call : capturedCalls) {
                if (call.usage() != null) {
                    prompt += call.usage().promptTokens();
                    completion += call.usage().completionTokens();
                    total += call.usage().totalTokens();
                    any = true;
                }
            }
        }
        return any ? new LlmUsage(prompt, completion, total) : null;
    }

    /**
     * 显式启动顶层 Run（用于 validation-only 等不走 {@code GenUiGenerator} 的场景）。
     *
     * @param startTime 启动时刻
     */
    public void ensureRootStarted(Instant startTime) {
        if (rootPosted) {
            return;
        }
        synchronized (this) {
            if (rootPosted) {
                return;
            }
            Instant effective = startTime == null ? Instant.now() : startTime;
            rootDottedOrder = DottedOrder.root(effective, rootRunId);
            rootPosted = true;
            if (buffer == null) {
                return;
            }
            LinkedHashMap<String, Object> run = new LinkedHashMap<>();
            run.put("id", rootRunId);
            run.put("trace_id", rootRunId);
            run.put("dotted_order", rootDottedOrder);
            run.put("name", rootName);
            run.put("run_type", "chain");
            run.put("start_time", effective.toString());
            run.put("inputs", rootInputs);
            if (sessionId != null) {
                run.put("session_id", sessionId);
            }
            if (referenceExampleId != null) {
                run.put("reference_example_id", referenceExampleId);
            }
            LinkedHashMap<String, Object> meta = new LinkedHashMap<>(rootMetadata);
            meta.put("case_id", caseId);
            run.put("extra", Map.of("metadata", meta));
            buffer.enqueuePost(run);
        }
    }

    /**
     * 显式结束顶层 Run 并补全输出与总用量。
     *
     * @param outputs 顶层输出摘要
     * @param error 可选错误信息
     * @param endTime 结束时刻
     */
    public void finishRoot(Map<String, Object> outputs, String error, Instant endTime) {
        ensureRootStarted(endTime);
        if (buffer == null) {
            return;
        }
        Instant effective = endTime == null ? Instant.now() : endTime;
        LinkedHashMap<String, Object> patch = new LinkedHashMap<>();
        patch.put("id", rootRunId);
        patch.put("trace_id", rootRunId);
        patch.put("dotted_order", rootDottedOrder);
        patch.put("end_time", effective.toString());
        if (sessionId != null) {
            patch.put("session_id", sessionId);
        }
        if (outputs != null) {
            LinkedHashMap<String, Object> mergedOutputs = new LinkedHashMap<>(outputs);
            LlmUsage usage = totalUsage();
            if (usage != null) {
                mergedOutputs.put("usage_metadata", usageMetadataMap(usage));
            }
            patch.put("outputs", mergedOutputs);
        }
        if (error != null && !error.isBlank()) {
            patch.put("error", error);
        }
        buffer.enqueuePatch(patch);
    }

    /**
     * 记录一次独立校验（如 validation-only 或初始复现检查）为子 {@code tool} Run。
     *
     * @param name 子 Run 名称
     * @param dsl 被校验 DSL
     * @param result 校验结果
     * @param startTime 开始时刻
     * @param endTime 结束时刻
     */
    public void recordStandaloneValidation(
            String name, String dsl, ValidationResult result, Instant startTime, Instant endTime) {
        ensureRootStarted(startTime);
        if (buffer == null) {
            return;
        }
        String childId = UUID.randomUUID().toString();
        String order = DottedOrder.child(rootDottedOrder, startTime, childId);
        LinkedHashMap<String, Object> run = new LinkedHashMap<>();
        run.put("id", childId);
        run.put("trace_id", rootRunId);
        run.put("parent_run_id", rootRunId);
        run.put("dotted_order", order);
        run.put("name", name);
        run.put("run_type", "tool");
        run.put("start_time", startTime.toString());
        run.put("end_time", endTime.toString());
        if (sessionId != null) {
            run.put("session_id", sessionId);
        }
        run.put("inputs", Map.of("dsl", dsl == null ? "" : dsl));
        run.put("outputs", validationOutputMap(result));
        buffer.enqueuePost(run);
    }

    @Override
    public void onSessionStart(
            UiGenerationRequest request,
            String model,
            boolean validationEnabled,
            long startedAtEpochMs) {
        ensureRootStarted(Instant.ofEpochMilli(startedAtEpochMs));
    }

    @Override
    public void onLlmCallStart(LlmCallStartEvent event) {
        Instant started = Instant.ofEpochMilli(event.startedAtEpochMs());
        ensureRootStarted(started);
        String callKey = callKey(event.roundIndex(), event.kind());
        String childId = UUID.randomUUID().toString();
        String order = DottedOrder.child(rootDottedOrder, started, childId);
        activeCalls.put(
                callKey, new ActiveLlmCall(childId, order, started, event.requestBodyJson()));
        if (buffer == null) {
            return;
        }
        LinkedHashMap<String, Object> run = new LinkedHashMap<>();
        run.put("id", childId);
        run.put("trace_id", rootRunId);
        run.put("parent_run_id", rootRunId);
        run.put("dotted_order", order);
        run.put("name", "llm_" + event.kind() + "_round_" + event.roundIndex());
        run.put("run_type", "llm");
        run.put("start_time", started.toString());
        if (sessionId != null) {
            run.put("session_id", sessionId);
        }
        run.put("inputs", parseLlmInputs(event.requestBodyJson()));
        run.put(
                "extra",
                Map.of(
                        "metadata",
                        Map.of(
                                "ls_provider",
                                "openai-compatible",
                                "ls_model_name",
                                event.model() == null ? "unknown" : event.model(),
                                "round_index",
                                event.roundIndex(),
                                "call_kind",
                                event.kind())));
        buffer.enqueuePost(run);
    }

    @Override
    public void onLlmCallEnd(LlmCallEndEvent event) {
        String callKey = callKey(event.roundIndex(), event.kind());
        ActiveLlmCall active = activeCalls.remove(callKey);
        capturedCalls.add(
                new CapturedLlmCall(
                        event.roundIndex(),
                        event.kind(),
                        event.model(),
                        active == null ? null : active.requestBodyJson(),
                        event.rawOutput(),
                        event.usage(),
                        event.durationMs(),
                        event.error()));
        if (buffer == null || active == null) {
            return;
        }
        LinkedHashMap<String, Object> patch = new LinkedHashMap<>();
        patch.put("id", active.runId());
        patch.put("trace_id", rootRunId);
        patch.put("parent_run_id", rootRunId);
        patch.put("dotted_order", active.dottedOrder());
        patch.put("end_time", Instant.ofEpochMilli(event.endedAtEpochMs()).toString());
        if (sessionId != null) {
            patch.put("session_id", sessionId);
        }
        String rawText = event.rawOutput() == null ? "" : event.rawOutput();
        LinkedHashMap<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("role", "assistant");
        outputs.put("content", rawText);
        outputs.put("rawOutput", rawText);
        if (event.usage() != null) {
            outputs.put("usage_metadata", usageMetadataMap(event.usage()));
        }
        patch.put("outputs", outputs);
        if (event.error() != null && !event.error().isBlank()) {
            patch.put("error", event.error());
        }
        buffer.enqueuePatch(patch);
    }

    private static Map<String, Object> usageMetadataMap(LlmUsage usage) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("input_tokens", usage.promptTokens());
        map.put("output_tokens", usage.completionTokens());
        map.put("total_tokens", usage.totalTokens());
        return map;
    }

    @Override
    public void onRoundValidated(
            int roundIndex, RoundEndResult roundResult, long completedAtEpochMs) {
        Instant completed = Instant.ofEpochMilli(completedAtEpochMs);
        ensureRootStarted(completed);
        if (buffer == null || roundResult == null || roundResult.validationResult() == null) {
            return;
        }
        String childId = UUID.randomUUID().toString();
        String order = DottedOrder.child(rootDottedOrder, completed, childId);
        LinkedHashMap<String, Object> run = new LinkedHashMap<>();
        run.put("id", childId);
        run.put("trace_id", rootRunId);
        run.put("parent_run_id", rootRunId);
        run.put("dotted_order", order);
        run.put("name", "validate_round_" + roundIndex);
        run.put("run_type", "tool");
        run.put("start_time", completed.toString());
        run.put("end_time", completed.toString());
        if (sessionId != null) {
            run.put("session_id", sessionId);
        }
        run.put(
                "inputs",
                Map.of(
                        "currentDefinitions",
                        roundResult.currentDefinitions() == null
                                ? ""
                                : roundResult.currentDefinitions()));
        run.put("outputs", validationOutputMap(roundResult.validationResult()));
        buffer.enqueuePost(run);
    }

    @Override
    public void onSessionEnd(
            GenUiGenerationResult result, long durationMs, long endedAtEpochMs, String error) {
        // Root run completion is finalized by the experiment runner with full case verdict outputs.
    }

    private static String callKey(int roundIndex, String kind) {
        return roundIndex + ":" + kind;
    }

    private static Map<String, Object> parseLlmInputs(String requestBodyJson) {
        if (requestBodyJson == null || requestBodyJson.isBlank()) {
            return Map.of();
        }
        try {
            return Json.asObject(Json.parse(requestBodyJson), "llm request");
        } catch (RuntimeException ignored) {
            return Map.of("rawRequest", requestBodyJson);
        }
    }

    private static Map<String, Object> validationOutputMap(ValidationResult result) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("status", result.status().name());
        out.put(
                "actionableIssueCodes",
                result.actionableIssues().stream()
                        .map(ValidationIssue::code)
                        .distinct()
                        .sorted()
                        .toList());
        out.put(
                "rawIssueCodes",
                result.issues().stream().map(ValidationIssue::code).distinct().sorted().toList());
        out.put("actionableIssueCount", result.actionableIssues().size());
        out.put("rawIssueCount", result.issues().size());
        return out;
    }
}
