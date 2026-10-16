/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.extract.OpenuiCodeExtractor;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatCompletionResponse;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatMessage;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.validation.OpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 协调反思式修复(设计决策 #6 / #8)。本类自身不持有任何校验或门控逻辑 —— 全量修复的重新校验复用注入的 {@link OpenuiLangValidator}。
 *
 * <p>
 * 入口 {@link #repairFull} —— 通过 {@code transport.post} 做同步整份 DSL 修复。每次尝试都以 {@link ValidationMode#FINAL}
 * 重新校验;一旦某次尝试有效或尝试次数耗尽即返回。
 *
 * @since 2026
 */
public final class RepairCoordinator {

    private final LlmTransport transport;

    private final OpenuiLangValidator validator;

    private final RepairPolicy policy;

    private Set<String> externalRefs = Set.of();

    /** 修复复验所携带的渲染数据模型;非空时类型校验(数据路径/prop 类型)在每轮复验中生效。 */
    private Map<String, Object> dataModel;

    private BodyBuilder bodyBuilder = (messages, stream) -> {
        throw new IllegalStateException("BodyBuilder not configured");
    };

    /**
     * 构造修复协调器。
     *
     * @param transport
     *            LLM 传输层
     * @param validator
     *            用于重新校验的校验器
     * @param policy
     *            修复策略
     */
    public RepairCoordinator(LlmTransport transport, OpenuiLangValidator validator, RepairPolicy policy) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    // ── SYNC full repair ──────────────────────────────────────────────────────

    /**
     * 尝试整份 DSL 修复,最多进行 {@link RepairPolicy#maxAttempts()} 次。每次尝试都基于<em>最近一次</em>失败的 DSL + 问题列表构建全量修复提示词、发出请求、提取修正后的
     * DSL,并以 FINAL 模式重新校验。返回第一个有效的 结果,否则返回最后一次(仍无效/出错)的结果。
     *
     * @param userIntent
     *            原始用户意图
     * @param invalidDsl
     *            未通过轮末统一校验的当前有效 DSL
     * @param initialResult
     *            初次失败的校验结果(作为第 1 次尝试的问题来源)
     * @param contract
     *            用于签名提示与重新校验的合并合约
     * @param rootName
     *            预期的根组件名
     * @return 全量修复结果
     */
    public FullRepairOutcome repairFull(String userIntent, String invalidDsl, ValidationResult initialResult,
            GenerationContract contract, String rootName) {
        return repairFull(userIntent, invalidDsl, initialResult, contract, rootName, null);
    }

    /**
     * 尝试整份 DSL 修复,并把初次生成的完整 system prompt 传递给修复模型作为语言/组件契约上下文。
     *
     * @param userIntent
     *            原始用户意图
     * @param invalidDsl
     *            未通过轮末统一校验的当前有效 DSL
     * @param initialResult
     *            初次失败的校验结果(作为第 1 次尝试的问题来源)
     * @param contract
     *            用于签名提示与重新校验的合并合约
     * @param rootName
     *            预期的根组件名
     * @param originalSystemPrompt
     *            初次生成请求使用的完整 system prompt,可为 {@code null}
     * @return 全量修复结果
     */
    public FullRepairOutcome repairFull(String userIntent, String invalidDsl, ValidationResult initialResult,
            GenerationContract contract, String rootName, String originalSystemPrompt) {
        String currentDsl = invalidDsl;
        ValidationResult currentResult = initialResult;
        List<FullRepairOutcome.AttemptRecord> attemptLog = new ArrayList<>();
        long deadlineNanos = deadline(policy.timeout());

        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            // 说明:超时判断发生在两次尝试之间;单次进行中的 transport.post 本身并未独立限时
            // (需要传输层自身的读超时支持)。
            if (timedOut(deadlineNanos)) {
                return FullRepairOutcome.timedOut(currentDsl, currentResult, attemptLog);
            }

            List<ChatMessage> messages = ReaskPromptBuilder.buildFullRepair(userIntent, currentDsl,
                    currentResult == null ? List.of() : currentResult.issues(), contract, originalSystemPrompt);

            String extracted;
            try {
                String body = bodyBuilder.build(messages, false);
                String response = transport.post(body);
                String content = ChatCompletionResponse.parse(response).firstContent();
                extracted = OpenuiCodeExtractor.extract(content);
            } catch (LlmTransportException error) {
                // 修复过程中传输层失败 —— 暴露最后一次已知结果,标记为传输错误。
                attemptLog.add(new FullRepairOutcome.AttemptRecord(attempt, false, null));
                return FullRepairOutcome.transportError(currentDsl, currentResult, attemptLog);
            }

            ValidationResult revalidated = validateFinal(extracted, contract, rootName);
            currentDsl = extracted;
            currentResult = revalidated;
            attemptLog.add(new FullRepairOutcome.AttemptRecord(attempt, revalidated.isValid(), revalidated.issues()));

            if (revalidated.isValid()) {
                return FullRepairOutcome.repaired(extracted, revalidated, attemptLog);
            }
        }
        return FullRepairOutcome.stillInvalid(currentDsl, currentResult, attemptLog);
    }

    // ── request-body assembly seam ────────────────────────────────────────────

    /**
     * 从修复对话消息构建传输层请求体。由生成器注入,使协调器复用<em>同一套</em> {@code ChatCompletionRequest.of(config, ...).toJson()}
     * 形状,而无需依赖生成器的私有配置。
     *
     * @since 2026
     */
    @FunctionalInterface
    public interface BodyBuilder {

        /**
         * 构建请求体。
         *
         * @param messages
         *            对话消息列表
         * @param stream
         *            是否为流式请求
         * @return 序列化后的请求体
         */
        String build(List<ChatMessage> messages, boolean stream);
    }

    /**
     * 配置修复请求体如何序列化(由生成器一次性注入)。
     *
     * @param bodyBuilder
     *            请求体构建器
     * @return 本实例,便于链式调用
     */
    public RepairCoordinator withBodyBuilder(BodyBuilder bodyBuilder) {
        this.bodyBuilder = Objects.requireNonNull(bodyBuilder, "bodyBuilder");
        return this;
    }

    /**
     * 配置修复复验所用的外部引用名集合(如宿主数据绑定 {@code data}),需与生成管线初次校验一致(由生成器 一次性注入)。
     *
     * @param externalRefs
     *            外部引用名集合;{@code null} 视同空集合
     * @return 本实例,便于链式调用
     */
    public RepairCoordinator withExternalRefs(Set<String> externalRefs) {
        this.externalRefs = externalRefs == null ? Set.of() : Set.copyOf(externalRefs);
        return this;
    }

    /**
     * 配置修复复验所携带的渲染数据模型。设置后每轮修复复验都会运行数据路径/prop 类型校验(与初次生成校验同源); 未设置时修复复验不做数据类型校验。
     *
     * @param dataModel
     *            渲染数据模型;{@code null} 表示不启用
     * @return 本实例,便于链式调用
     */
    public RepairCoordinator withDataModel(Map<String, Object> dataModel) {
        this.dataModel = dataModel;
        return this;
    }

    /**
     * 获取当前使用的修复策略。
     *
     * @return 修复策略
     */
    public RepairPolicy policy() {
        return policy;
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private ValidationResult validateFinal(String dsl, GenerationContract contract, String rootName) {
        ValidationRequest request = ValidationRequest.builder().dsl(dsl).contract(contract).rootName(rootName)
                .externalRefs(externalRefs).dataModel(dataModel).mode(ValidationMode.FINAL).build();
        return validator.validate(request);
    }

    private static long deadline(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return 0L; // 无截止时间
        }
        return System.nanoTime() + timeout.toNanos();
    }

    private static boolean timedOut(long deadlineNanos) {
        return deadlineNanos != 0L && System.nanoTime() >= deadlineNanos;
    }

    /**
     * 一次完整(同步)修复尝试序列的结果。结果区分四种终态({@link OutcomeKind}),并携带逐次尝试的复验记录 ({@link AttemptRecord}),供 bench 等调用方度量修复过程;传输失败的尝试其
     * {@code issues} 为 {@code null}。超时只发生在两次尝试之间,因此 {@code attemptLog} 只含已完成的尝试。
     *
     * @param kind
     *            终态类别:修复成功 / 预算耗尽仍无效 / 传输错误 / 超时
     * @param dsl
     *            最后一次修复的 DSL 文本({@code kind} 为 REPAIRED 时即为有效文本)
     * @param result
     *            最后一次的校验结果(REPAIRED 时即为有效结果;超时且无任何尝试时为初次失败结果)
     * @param attemptLog
     *            逐次尝试的复验记录,按尝试顺序排列;不会为 {@code null}
     *
     * @since 2026
     */
    public record FullRepairOutcome(OutcomeKind kind, String dsl, ValidationResult result,
            List<AttemptRecord> attemptLog) {

        /**
         * 修复序列的终态类别。
         *
         * @since 2026
         */
        public enum OutcomeKind {
            /** 某次尝试复验通过。 */
            REPAIRED,
            /** 尝试预算耗尽,最后一次复验仍无效 —— 模型修不动。 */
            STILL_INVALID,
            /** 传输层失败,序列中止 —— 基础设施噪声,不算“修不动”。 */
            TRANSPORT_ERROR,
            /** 两次尝试之间命中墙钟预算。 */
            TIMED_OUT
        }

        /**
         * 单次修复尝试的复验记录。
         *
         * @param attempt
         *            尝试序号(从 1 开始)
         * @param valid
         *            该次尝试产出的 DSL 复验是否有效;传输失败时恒为 {@code false}
         * @param issues
         *            该次复验发现的问题;传输失败的尝试为 {@code null}
         *
         * @since 2026
         */
        public record AttemptRecord(int attempt, boolean valid, List<ValidationIssue> issues) {

            /**
             * 紧凑构造方法:非 {@code null} 的 issues 归一化为不可变列表({@code null} 保留,表示传输失败)。
             */
            public AttemptRecord {
                issues = issues == null ? null : List.copyOf(issues);
            }
        }

        static FullRepairOutcome repaired(String dsl, ValidationResult result, List<AttemptRecord> attemptLog) {
            return new FullRepairOutcome(OutcomeKind.REPAIRED, dsl, result, List.copyOf(attemptLog));
        }

        static FullRepairOutcome stillInvalid(String dsl, ValidationResult result, List<AttemptRecord> attemptLog) {
            return new FullRepairOutcome(OutcomeKind.STILL_INVALID, dsl, result, List.copyOf(attemptLog));
        }

        static FullRepairOutcome transportError(String dsl, ValidationResult result, List<AttemptRecord> attemptLog) {
            return new FullRepairOutcome(OutcomeKind.TRANSPORT_ERROR, dsl, result, List.copyOf(attemptLog));
        }

        static FullRepairOutcome timedOut(String dsl, ValidationResult result, List<AttemptRecord> attemptLog) {
            return new FullRepairOutcome(OutcomeKind.TIMED_OUT, dsl, result, List.copyOf(attemptLog));
        }

        /**
         * 修复后的 DSL 复验为有效时返回 {@code true}。
         *
         * @return 是否修复成功
         */
        public boolean repaired() {
            return kind == OutcomeKind.REPAIRED;
        }

        /**
         * 序列因超时而中止时返回 {@code true}。
         *
         * @return 是否超时
         */
        public boolean timedOut() {
            return kind == OutcomeKind.TIMED_OUT;
        }

        /**
         * 实际执行的尝试次数(含传输失败的那次)。
         *
         * @return 尝试次数
         */
        public int attempts() {
            return attemptLog.size();
        }
    }
}
