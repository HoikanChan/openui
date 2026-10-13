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
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 协调反思式修复（设计决策 #6 / #8）。本类自身不持有任何校验或门控逻辑 —— 全量修复的重新校验复用注入的 {@link OpenuiLangValidator}；流式重新请求则驱动调用方提供的
 * {@link StreamConsumer}，让新的流经过<em>同一套</em> {@code StreamingValidationSession}/门控（不重复实现门控逻辑，不开并行流）。
 *
 * <p>
 * 两个入口：
 *
 * <ul>
 * <li>{@link #repairFull} —— 通过 {@code transport.post} 做同步整份 DSL 修复。每次尝试都以 {@link ValidationMode#FINAL}
 * 重新校验；一旦某次尝试有效或尝试次数耗尽即返回。
 * <li>{@link #reaskStream} —— 通过 {@code transport.postStream} 做流式接续修复：开启<em>一个</em>新流 （先取消再重新请求），并把其
 * {@link InputStream} 交给调用方的 {@link StreamConsumer}，由后者驱动其经过既有的 session/门控。
 * </ul>
 *
 * @since 2026
 */
public final class RepairCoordinator {

    private final LlmTransport transport;
    private final OpenuiLangValidator validator;
    private final RepairPolicy policy;
    private Set<String> externalRefs = Set.of();
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
     * 尝试整份 DSL 修复，最多进行 {@link RepairPolicy#maxAttempts()} 次。每次尝试都基于<em>最近一次</em>失败的 DSL + 问题列表构建全量修复提示词、发出请求、提取修正后的
     * DSL，并以 FINAL 模式重新校验。返回第一个有效的 结果，否则返回最后一次（仍无效/出错）的结果。
     *
     * @param userIntent
     *            原始用户意图
     * @param invalidDsl
     *            未通过初次 FINAL 校验的 DSL
     * @param initialResult
     *            初次失败的校验结果（作为第 1 次尝试的问题来源）
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
     * 尝试整份 DSL 修复，并把初次生成的完整 system prompt 传递给修复模型作为语言/组件契约上下文。
     *
     * @param userIntent
     *            原始用户意图
     * @param invalidDsl
     *            未通过初次 FINAL 校验的 DSL
     * @param initialResult
     *            初次失败的校验结果（作为第 1 次尝试的问题来源）
     * @param contract
     *            用于签名提示与重新校验的合并合约
     * @param rootName
     *            预期的根组件名
     * @param originalSystemPrompt
     *            初次生成请求使用的完整 system prompt，可为 {@code null}
     * @return 全量修复结果
     */
    public FullRepairOutcome repairFull(String userIntent, String invalidDsl, ValidationResult initialResult,
            GenerationContract contract, String rootName, String originalSystemPrompt) {
        String currentDsl = invalidDsl;
        ValidationResult currentResult = initialResult;
        long deadlineNanos = deadline(policy.timeout());

        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            // 说明：超时判断发生在两次尝试之间；单次进行中的 transport.post 本身并未独立限时
            // （需要传输层自身的读超时支持）。
            if (timedOut(deadlineNanos)) {
                return FullRepairOutcome.timedOut(currentDsl, currentResult, attempt - 1);
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
                // 修复过程中传输层失败 —— 暴露最后一次已知结果，标记为未修复。
                return FullRepairOutcome.failed(currentDsl, currentResult, attempt);
            }

            ValidationResult revalidated = validateFinal(extracted, contract, rootName);
            currentDsl = extracted;
            currentResult = revalidated;

            if (revalidated.isValid()) {
                return FullRepairOutcome.repaired(extracted, revalidated, attempt);
            }
        }
        return FullRepairOutcome.exhausted(currentDsl, currentResult, policy.maxAttempts());
    }

    // ── STREAMING reask-and-continue ──────────────────────────────────────────

    /**
     * 开启<em>一个</em>新的接续流（先取消再重新请求）并交给 {@code consumer}，由其负责让该流经过既有的
     * session/门控。受重新请求超时约束；尝试次数预算（重新请求的轮数）由调用方在多次调用之间自行控制。
     *
     * <p>
     * 实现说明：{@code statementRepairTimeout} 目前尚未在此处强制执行 —— 挂起的 SSE 读取会一直阻塞，直到 传输层自行关闭连接。要落实墙钟时间预算，需要传输层配合提供读超时能力。
     *
     * @param userIntent
     *            原始用户意图
     * @param acceptedPrefix
     *            目前为止已接受的有效 DSL
     * @param invalidStatement
     *            被搁置的无效语句
     * @param issues
     *            说明该无效语句的问题列表
     * @param contract
     *            合并合约（用于签名提示）
     * @param consumer
     *            调用方提供的桥接器，负责让新流经过 session/门控
     * @return 新流成功开启并被完整消费（消费过程中未触发 fail-fast）时为 {@code true}；开启流失败或消费方 报告失败时为 {@code false}
     */
    public boolean reaskStream(String userIntent, String acceptedPrefix, String invalidStatement,
            List<com.huawei.cloudsop.genui.core.validation.ValidationIssue> issues, GenerationContract contract,
            StreamConsumer consumer) {
        return reaskStream(userIntent, acceptedPrefix, invalidStatement, issues, contract, null, consumer);
    }

    /**
     * 开启一个新的接续流，并把初次生成的完整 system prompt 传递给修复模型作为语言/组件契约上下文。
     *
     * @param userIntent
     *            原始用户意图
     * @param acceptedPrefix
     *            目前为止已接受的有效 DSL
     * @param invalidStatement
     *            被搁置的无效语句
     * @param issues
     *            说明该无效语句的问题列表
     * @param contract
     *            合并合约（用于签名提示）
     * @param originalSystemPrompt
     *            初次生成请求使用的完整 system prompt，可为 {@code null}
     * @param consumer
     *            调用方提供的桥接器，负责让新流经过 session/门控
     * @return 新流成功开启并被完整消费（消费过程中未触发 fail-fast）时为 {@code true}；开启流失败或消费方 报告失败时为 {@code false}
     */
    public boolean reaskStream(String userIntent, String acceptedPrefix, String invalidStatement,
            List<com.huawei.cloudsop.genui.core.validation.ValidationIssue> issues, GenerationContract contract,
            String originalSystemPrompt, StreamConsumer consumer) {
        List<ChatMessage> messages = ReaskPromptBuilder.buildRepairAndContinue(userIntent, acceptedPrefix,
                invalidStatement, issues, contract, originalSystemPrompt);
        String body = bodyBuilder.build(messages, true);
        try (InputStream stream = transport.postStream(body)) {
            return consumer.consume(stream);
        } catch (LlmTransportException | IOException error) {
            return false;
        }
    }

    // ── request-body assembly seam ────────────────────────────────────────────

    /**
     * 从修复对话消息构建传输层请求体。由生成器注入，使协调器复用<em>同一套</em> {@code ChatCompletionRequest.of(config, ...).toJson()}
     * 形状，而无需依赖生成器的私有配置。
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
     * 让重新请求得到的接续流经过既有的 session/门控进行消费。
     *
     * @since 2026
     */
    @FunctionalInterface
    public interface StreamConsumer {
        /**
         * 消费接续流。
         *
         * @param stream
         *            接续流
         * @return 消费过程中未触发 fail-fast/校验失败时为 {@code true}，否则为 {@code false}
         * @throws IOException
         *             读取流时发生 I/O 错误
         */
        boolean consume(InputStream stream) throws IOException;
    }

    /**
     * 配置修复请求体如何序列化（由生成器一次性注入）。
     *
     * @param bodyBuilder
     *            请求体构建器
     * @return 本实例，便于链式调用
     */
    public RepairCoordinator withBodyBuilder(BodyBuilder bodyBuilder) {
        this.bodyBuilder = Objects.requireNonNull(bodyBuilder, "bodyBuilder");
        return this;
    }

    /**
     * 配置修复复验所用的外部引用名集合（如宿主数据绑定 {@code data}），需与生成管线初次校验一致（由生成器 一次性注入）。
     *
     * @param externalRefs
     *            外部引用名集合；{@code null} 视同空集合
     * @return 本实例，便于链式调用
     */
    public RepairCoordinator withExternalRefs(Set<String> externalRefs) {
        this.externalRefs = externalRefs == null ? Set.of() : Set.copyOf(externalRefs);
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
                .externalRefs(externalRefs).mode(ValidationMode.FINAL).build();
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
     * 一次完整（同步）修复尝试序列的结果。
     *
     * @param repaired
     *            修复后的 DSL 重新校验为有效时为 {@code true}
     * @param dsl
     *            最后一次修复的 DSL 文本（{@code repaired} 为真时即为有效文本）
     * @param result
     *            最后一次的校验结果（{@code repaired} 为真时即为有效结果）
     * @param attempts
     *            实际执行的尝试次数
     * @param timedOut
     *            因超时而中止序列时为 {@code true}
     *
     * @since 2026
     */
    public record FullRepairOutcome(boolean repaired, String dsl, ValidationResult result, int attempts,
            boolean timedOut) {

        static FullRepairOutcome repaired(String dsl, ValidationResult result, int attempts) {
            return new FullRepairOutcome(true, dsl, result, attempts, false);
        }

        static FullRepairOutcome exhausted(String dsl, ValidationResult result, int attempts) {
            return new FullRepairOutcome(false, dsl, result, attempts, false);
        }

        static FullRepairOutcome timedOut(String dsl, ValidationResult result, int attempts) {
            return new FullRepairOutcome(false, dsl, result, attempts, true);
        }

        static FullRepairOutcome failed(String dsl, ValidationResult result, int attempts) {
            return new FullRepairOutcome(false, dsl, result, attempts, false);
        }
    }
}
