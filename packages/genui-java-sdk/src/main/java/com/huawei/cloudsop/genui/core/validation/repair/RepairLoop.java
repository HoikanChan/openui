/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.validation.repair;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerationResult;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;
import com.huawei.cloudsop.genui.core.llm.GenerationEndReason;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatCompletionRequest;
import com.huawei.cloudsop.genui.core.llm.stream.SseDeltaParser;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.validation.OpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationMetadata;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.parser.OpenuiPreprocessor;
import com.huawei.cloudsop.genui.core.validation.stream.ProgramRoundSession;
import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 单次流式生成的校验与增量修复。内部维护首轮和修复轮的当前程序、历史与中断记录;调用方只执行会话或读取快照。
 *
 * <p>首轮与首次修复允许完成,后续修复受调用方预算和次数上限约束。模型请求逐轮关闭,补丁实时输出;本类不发送终止帧,也不执行降级。
 *
 * @since 2026
 */
public final class RepairLoop {
    /** 模型请求中的增量修复标记,供日志与评测识别修复轮。 */
    public static final String INCREMENTAL_REPAIR_MARKER = "[REPAIR:INCREMENTAL]";

    /**
     * 本次修复使用的原始需求与契约。
     *
     * @param systemPrompt 首轮使用的完整 system prompt
     * @param userMessage 原始用户需求与请求上下文
     * @param contract 当前生效的完整合并契约
     * @param dataModel 渲染数据;空映射表示未提供数据模型
     * @param maxAttempts 修复次数上限;零表示只使用调用方预算
     * @since 2026
     */
    public record Context(
            String systemPrompt,
            String userMessage,
            GenerationContract contract,
            Map<String, Object> dataModel,
            int maxAttempts) {
        /**
         * 校验修复次数并复制数据模型,契约和数据模型必须已由调用方准备。
         *
         * @throws IllegalArgumentException 修复次数为负数
         */
        public Context {
            Objects.requireNonNull(contract, "contract");
            Objects.requireNonNull(dataModel, "dataModel");
            dataModel = Collections.unmodifiableMap(new LinkedHashMap<>(dataModel));
            if (maxAttempts < 0) {
                throw new IllegalArgumentException("maxAttempts must be nonnegative");
            }
        }
    }

    private final Context context;
    private final ProgramRoundSession program;
    private final GenUiLlmConfig repairConfig;
    private final LlmTransport transport;
    private final BooleanSupplier budgetExpired;
    private final Consumer<String> output;
    private final List<RoundEndResult> rounds = new ArrayList<>();
    private boolean inFlight;

    /**
     * 创建一次调用专属的修复会话;模型传输应使用宿主已有的取消与请求生命周期管理。
     *
     * @param context 原始生成上下文与修复次数上限
     * @param validator 当前程序的轮末校验器
     * @param config 模型配置;修复固定使用 temperature=0、关闭 thinking
     * @param transport 每轮模型请求的传输实现
     * @param budgetExpired 判断是否禁止启动第二次及之后的修复
     * @param output 实时接收 DSL 增量,包括跨轮所需的尾部闭合文本
     */
    public RepairLoop(
            Context context,
            OpenuiLangValidator validator,
            GenUiLlmConfig config,
            LlmTransport transport,
            BooleanSupplier budgetExpired,
            Consumer<String> output) {
        this.context = Objects.requireNonNull(context, "context");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.budgetExpired = Objects.requireNonNull(budgetExpired, "budgetExpired");
        this.output = Objects.requireNonNull(output, "output");
        Map<String, Object> dataModel = context.dataModel().isEmpty() ? null : context.dataModel();
        this.program =
                new ProgramRoundSession(
                        validator,
                        context.contract(),
                        context.contract().root(),
                        dataModel == null ? Set.of() : Set.of("data"),
                        dataModel);
        this.repairConfig =
                GenUiLlmConfig.builder()
                        .endpoint(config.endpoint())
                        .defaultModel(config.defaultModel())
                        .temperature(0)
                        .enableThinking(false)
                        .jsonObjectResponse(config.jsonObjectResponse())
                        .extraHeaders(config.extraHeaders())
                        .build();
    }

    /**
     * 执行首轮,失败时增量修复,直到当前程序有效或不再允许启动修复。
     *
     * <p>每个实例只执行一次。请求失败时保留中断轮,调用方可通过 {@link #snapshot} 读取;中断输出不做轮末校验。
     *
     * @param initialBody 已装配的首轮 chat-completion 请求 JSON
     * @return 最终程序、诊断、结束原因和完整轮次记录
     * @throws LlmTransportException 模型请求或读取失败
     * @throws IllegalStateException 同一实例已开始执行
     */
    public GenUiGenerationResult run(String initialBody) throws LlmTransportException {
        synchronized (this) {
            if (inFlight || !rounds.isEmpty()) {
                throw new IllegalStateException("Repair session already started");
            }
            inFlight = true;
        }
        consume(initialBody);
        RoundEndResult current = completeRound();
        int attempts = 0;
        while (current.validationResult().status() == ValidationStatus.INVALID
                && (attempts == 0 || !budgetExpired.getAsBoolean())
                && (context.maxAttempts() == 0 || attempts < context.maxAttempts())) {
            attempts++;
            String body = repairBody(current);
            String previousOutput = beginRepairRound();
            String separator = OpenuiPreprocessor.streamRoundBoundary(previousOutput);
            if (!separator.isEmpty()) {
                output.accept(separator);
            }
            consume(body);
            current = completeRound();
        }
        ValidationResult validation = current.validationResult();
        boolean valid = validation.status() == ValidationStatus.VALID;
        if (valid && attempts > 0) {
            validation = markRepaired(validation);
        }
        GenerationEndReason reason =
                valid
                        ? GenerationEndReason.CHECK_PASSED
                        : budgetExpired.getAsBoolean()
                                ? GenerationEndReason.TIMEOUT
                                : GenerationEndReason.VALIDATION_FAILED;
        return new GenUiGenerationResult(
                current.currentDefinitions(),
                context.dataModel(),
                validation.status(),
                validation,
                reason,
                List.copyOf(rounds));
    }

    /**
     * 读取异常终止时的当前程序及轮次;与完成轮提交使用同一个锁,不会重复记录或漏掉中断轮。
     *
     * @param reason 宿主决定的终止原因
     * @return 完成轮加上尚未校验的中断轮;不发输出、不执行校验
     */
    public synchronized GenUiGenerationResult snapshot(GenerationEndReason reason) {
        List<RoundEndResult> snapshot = new ArrayList<>(rounds);
        if (inFlight) {
            snapshot.add(
                    new RoundEndResult(
                            program.rawOutput(),
                            program.currentDefinitions(),
                            null,
                            program.elapsedMs(),
                            program.contractVersion(),
                            reason));
        }
        ValidationResult last =
                snapshot.isEmpty() ? null : snapshot.get(snapshot.size() - 1).validationResult();
        return new GenUiGenerationResult(
                program.currentDefinitions(),
                context.dataModel(),
                last == null ? null : last.status(),
                last,
                reason,
                snapshot);
    }

    private void consume(String body) throws LlmTransportException {
        try (InputStream stream = transport.postStream(body)) {
            SseDeltaParser.parse(
                    stream,
                    delta -> {
                        output.accept(delta);
                        synchronized (this) {
                            program.onDelta(delta);
                        }
                    });
        } catch (IOException error) {
            throw new LlmTransportException(error.getMessage(), error);
        }
    }

    private String repairBody(RoundEndResult current) {
        List<RoundEndResult> history;
        synchronized (this) {
            history =
                    rounds.size() <= 1
                            ? List.of()
                            : List.copyOf(rounds.subList(0, rounds.size() - 1));
        }
        return ChatCompletionRequest.of(
                        repairConfig,
                        null,
                        RepairPromptBuilder.build(
                                context.systemPrompt(),
                                context.userMessage(),
                                context.contract(),
                                current,
                                history),
                        true)
                .toJson();
    }

    private synchronized String beginRepairRound() {
        String previousOutput = program.rawOutput();
        program.beginRound();
        inFlight = true;
        return previousOutput;
    }

    private synchronized RoundEndResult completeRound() {
        RoundEndResult round = program.onRoundEnd();
        rounds.add(round);
        inFlight = false;
        return round;
    }

    private static ValidationResult markRepaired(ValidationResult result) {
        ValidationMetadata meta = result.metadata();
        LinkedHashMap<String, String> extra =
                new LinkedHashMap<>(meta == null || meta.extra() == null ? Map.of() : meta.extra());
        extra.put("repaired", "true");
        return ValidationResult.valid(
                result.normalizedDsl(),
                result.issues(),
                new ValidationMetadata(
                        meta == null ? 0 : meta.statementCount(),
                        meta == null ? null : meta.rootName(),
                        extra));
    }
}
