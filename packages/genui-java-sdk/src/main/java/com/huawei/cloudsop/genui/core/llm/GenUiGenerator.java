/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenUIExtension;
import com.huawei.cloudsop.genui.core.llm.extract.OpenuiCodeExtractor;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatCompletionRequest;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatCompletionResponse;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatMessage;
import com.huawei.cloudsop.genui.core.llm.stream.SseDeltaParser;
import com.huawei.cloudsop.genui.core.llm.transport.HttpClientLlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptAssemblyResult;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;
import com.huawei.cloudsop.genui.core.prompt.characterize.CharacterizationConfig;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig;
import com.huawei.cloudsop.genui.core.validation.OpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.repair.RepairLoop;
import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 公开生成入口。同步生成直接返回提取结果;流式生成实时转发,轮末校验当前完整程序,失败时默认增量修复, 修复失败或模型请求失败时整页降级为 Markdown。
 *
 * @since 2026
 */
public final class GenUiGenerator {
    private final GenerationSdk sdk;
    private final GenUiLlmConfig config;
    private final LlmTransport transport;
    private final GenUiValidationConfig validationConfig;
    private final OpenuiLangValidator validator;
    private GenerationTiming timing = GenerationTiming.system();
    private String systemPromptOverride;

    private GenUiGenerator(
            GenerationSdk sdk,
            GenUiLlmConfig config,
            LlmTransport transport,
            GenUiValidationConfig validationConfig,
            OpenuiLangValidator validator) {
        this.sdk = Objects.requireNonNull(sdk, "sdk must not be null");
        this.config = config == null ? GenUiLlmConfig.defaults() : config;
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.validationConfig =
                validationConfig == null ? GenUiValidationConfig.enabled() : validationConfig;
        this.validator = validator == null ? new DefaultOpenuiLangValidator() : validator;
    }

    /** Backwards-compatible private constructor (no validation config). */
    private GenUiGenerator(GenerationSdk sdk, GenUiLlmConfig config, LlmTransport transport) {
        this(sdk, config, transport, null, null);
    }

    /**
     * 使用默认配置与内置 HTTP transport 创建生成器。
     *
     * @return 使用 {@link GenUiLlmConfig#defaults()} 的生成器实例
     */
    public static GenUiGenerator create() {
        return create(GenUiLlmConfig.defaults());
    }

    /**
     * 使用指定配置与内置 HTTP transport 创建生成器。
     *
     * @param config LLM 配置,为 null 时使用 {@link GenUiLlmConfig#defaults()}
     * @return 使用内置 transport 的生成器实例
     */
    public static GenUiGenerator create(GenUiLlmConfig config) {
        GenUiLlmConfig effectiveConfig = config == null ? GenUiLlmConfig.defaults() : config;
        return new GenUiGenerator(
                GenerationSdk.create(),
                effectiveConfig,
                new HttpClientLlmTransport(effectiveConfig));
    }

    /**
     * 使用指定特征化配置与内置 HTTP transport 创建生成器。
     *
     * @param config LLM 配置,为 null 时使用 {@link GenUiLlmConfig#defaults()}
     * @param characterization 特征化配置,为 null 时使用 {@link CharacterizationConfig#defaults()}
     * @return 使用内置 transport 的生成器实例
     */
    public static GenUiGenerator create(
            GenUiLlmConfig config, CharacterizationConfig characterization) {
        GenUiLlmConfig effectiveConfig = config == null ? GenUiLlmConfig.defaults() : config;
        GenerationSdk generationSdk =
                GenerationSdk.builder()
                        .characterization(
                                characterization == null
                                        ? CharacterizationConfig.defaults()
                                        : characterization)
                        .build();
        return new GenUiGenerator(
                generationSdk, effectiveConfig, new HttpClientLlmTransport(effectiveConfig));
    }

    /**
     * 使用调用方提供的 transport 创建生成器,例如在测试中替换模型请求。
     *
     * @param config LLM 配置,为 null 时使用 {@link GenUiLlmConfig#defaults()}
     * @param transport 模型传输实现,不能为 null
     * @return 绑定该 transport 的生成器实例
     */
    public static GenUiGenerator withTransport(GenUiLlmConfig config, LlmTransport transport) {
        return new GenUiGenerator(GenerationSdk.create(), config, transport);
    }

    /**
     * 使用指定特征化配置和自定义 transport 创建生成器。
     *
     * <p>{@code characterization} 为 null 时使用 {@link CharacterizationConfig#defaults()}; {@code
     * config} 为 null 时使用 {@link GenUiLlmConfig#defaults()}。
     *
     * @param config LLM 配置,可为 null
     * @param characterization 特征化配置,可为 null
     * @param transport 模型传输实现,不能为 null
     * @return 绑定上述依赖的生成器实例
     */
    public static GenUiGenerator withTransport(
            GenUiLlmConfig config,
            CharacterizationConfig characterization,
            LlmTransport transport) {
        GenerationSdk generationSdk =
                GenerationSdk.builder()
                        .characterization(
                                characterization == null
                                        ? CharacterizationConfig.defaults()
                                        : characterization)
                        .build();
        return new GenUiGenerator(generationSdk, config, transport);
    }

    /**
     * 使用自定义 transport 与显式校验/修复配置创建生成器,便于测试或宿主自管连接。
     *
     * <p>{@code validationConfig} 为 null 时使用 {@link GenUiValidationConfig#enabled()}; {@code
     * validator} 为 null 时使用 {@link DefaultOpenuiLangValidator}。
     *
     * @param config LLM 配置,为 null 时使用 {@link GenUiLlmConfig#defaults()}
     * @param transport 模型传输实现,不能为 null
     * @param validationConfig 校验/修复配置,可为 null
     * @param validator DSL 校验器,可为 null
     * @return 绑定上述依赖的生成器实例
     */
    public static GenUiGenerator withTransport(
            GenUiLlmConfig config,
            LlmTransport transport,
            GenUiValidationConfig validationConfig,
            OpenuiLangValidator validator) {
        return new GenUiGenerator(
                GenerationSdk.create(), config, transport, validationConfig, validator);
    }

    /**
     * 使用调用方自建的 {@link GenerationSdk}(例如注入外部 base contract)与自定义 transport 创建生成器。
     *
     * @param sdk 已配置的生成 SDK,不能为 null
     * @param config LLM 配置,为 null 时使用 {@link GenUiLlmConfig#defaults()}
     * @param transport 模型传输实现,不能为 null
     * @return 绑定上述依赖的生成器实例
     */
    public static GenUiGenerator withTransport(
            GenerationSdk sdk, GenUiLlmConfig config, LlmTransport transport) {
        return new GenUiGenerator(sdk, config, transport);
    }

    /**
     * 使用调用方自建 SDK 与显式校验/修复配置创建生成器(测试或宿主关闭修复时使用)。
     *
     * @param sdk 已配置的生成 SDK,不能为 null
     * @param config LLM 配置,为 null 时使用 {@link GenUiLlmConfig#defaults()}
     * @param transport 模型传输实现,不能为 null
     * @param validationConfig 校验/修复配置,为 null 时使用 {@link GenUiValidationConfig#enabled()}
     * @param validator DSL 校验器,为 null 时使用 {@link DefaultOpenuiLangValidator}
     * @return 绑定上述依赖的生成器实例
     */
    public static GenUiGenerator withTransport(
            GenerationSdk sdk,
            GenUiLlmConfig config,
            LlmTransport transport,
            GenUiValidationConfig validationConfig,
            OpenuiLangValidator validator) {
        return new GenUiGenerator(sdk, config, transport, validationConfig, validator);
    }

    /**
     * 向底层 SDK 注册扩展契约,后续生成都会带上该扩展。
     *
     * @param extension 扩展定义,不能为 null
     * @return 当前实例,便于链式调用
     */
    public GenUiGenerator register(GenUIExtension extension) {
        sdk.register(extension);
        return this;
    }

    /**
     * 同步生成:单次模型请求后直接返回提取的 DSL,不校验、不修复、不自动降级。
     *
     * <p>{@code request} 为 null 时按空请求处理。{@code renderComponentsKey} 为 {@code "Markdown"} 时改走独立
     * Markdown 路径,不发起 DSL 请求。
     *
     * @param request 生成请求,可为 null
     * @return 提取出的 DSL 与请求回显的数据模型
     * @throws GenerationSdkException 模型请求失败时抛出,原始异常作为 cause 保留
     */
    public GenUiGenerationResult generate(UiGenerationRequest request) {
        UiGenerationRequest effective =
                request == null ? UiGenerationRequest.builder().build() : request;
        if ("Markdown".equals(effective.renderComponentsKey())) {
            return markdown().generate(effective);
        }
        RequestBody body = buildRequest(toPromptRequest(effective), effective, false);
        try {
            String content =
                    ChatCompletionResponse.parse(transport.post(body.json())).firstContent();
            return new GenUiGenerationResult(
                    OpenuiCodeExtractor.extract(content), effective.response());
        } catch (LlmTransportException error) {
            throw new GenerationSdkException("Model request failed: " + error.getMessage(), error);
        }
    }

    /**
     * 设置时钟与预算调度器,返回共享同一 SDK 和配置的新实例。
     *
     * <p>副本共享 SDK 已注册的契约,但不共享调用期状态。请在开始生成前配置。
     *
     * @param value 时钟与调度器,不能为 null
     * @return 使用该计时配置的新生成器实例
     */
    public GenUiGenerator withTiming(GenerationTiming value) {
        GenUiGenerator copy = scoped(transport);
        copy.timing = Objects.requireNonNull(value);
        return copy;
    }

    /**
     * 覆盖装配出的 system prompt,供宿主做调试注入。
     *
     * <p>返回新实例,当前实例不受影响;{@code prompt} 为 null 表示继续使用 SDK 装配结果。
     *
     * @param prompt 宿主提供的 system prompt,可为 null
     * @return 使用该 prompt 的新生成器实例
     */
    public GenUiGenerator withSystemPrompt(String prompt) {
        GenUiGenerator copy = scoped(transport);
        copy.systemPromptOverride = prompt;
        return copy;
    }

    private GenUiGenerator scoped(LlmTransport scopedTransport) {
        GenUiGenerator copy =
                new GenUiGenerator(sdk, config, scopedTransport, validationConfig, validator);
        copy.timing = timing;
        copy.systemPromptOverride = systemPromptOverride;
        return copy;
    }

    private MarkdownGeneration markdown() {
        return new MarkdownGeneration(config, transport);
    }

    /**
     * 流式生成 DSL,默认在失败时降级到内置 Markdown 路径。
     *
     * <p>首次生成与第一次修复允许跑完,20 秒预算只约束后续修复。预算耗尽且 DSL 仍无效、或模型 请求失败时,发送整页 {@code replace} 帧并转入独立 Markdown
     * 流程。整个调用只以一个 {@code done} 结束;Markdown 请求失败会先发送 {@code error}。
     *
     * @param request 生成请求,为 null 时按空请求处理
     * @param sink 渲染帧接收器,不能为 null
     * @return 本次生成的最终结果;降级成功时保留 DSL 会话的轮次记录
     */
    public GenUiGenerationResult generateStream(
            UiGenerationRequest request, Consumer<RenderStreamEnvelope> sink) {
        return generateStream(
                request,
                sink,
                (original, reason, output) -> markdown().generateStream(original, output));
    }

    /**
     * 流式生成,DSL 会话失败时调用 {@code fallback} 完成整页 Markdown 替换。
     *
     * <p>{@code sink} 与 {@code fallback} 不能为 null;{@code request} 为 null 时按空请求处理。{@code
     * renderComponentsKey} 为 {@code "Markdown"} 时直接走独立 Markdown 路径,不经过 DSL 生成。
     *
     * @param request 生成请求,可为 null
     * @param sink 渲染帧接收器,不能为 null
     * @param fallback DSL 失败后的整页降级实现,不能为 null
     * @return 本次生成的最终结果
     */
    public GenUiGenerationResult generateStream(
            UiGenerationRequest request,
            Consumer<RenderStreamEnvelope> sink,
            MarkdownFallback fallback) {
        long started = timing.nanoTime().getAsLong();
        Objects.requireNonNull(sink, "sink must not be null");
        Objects.requireNonNull(fallback, "fallback must not be null");
        UiGenerationRequest effective =
                request == null ? UiGenerationRequest.builder().build() : request;
        if ("Markdown".equals(effective.renderComponentsKey())) {
            return markdown().generateStream(effective, sink);
        }
        GenerationSession session = new GenerationSession(transport, timing, started, sink);
        var outcome =
                session.run(
                        () -> {
                            session.ensureInitial(effective.response());
                            return scoped(session)
                                    .generateStreamInternal(effective, session::emit, session);
                        });
        session.ensureInitial(effective.response());
        if (outcome.failure() == null) {
            sink.accept(RenderStreamEnvelope.done(session.nextSeq()));
            return outcome.result();
        }
        sink.accept(RenderStreamEnvelope.replace(session.nextSeq(), outcome.failure()));
        try {
            GenUiGenerationResult result =
                    fallback.generate(
                            effective,
                            outcome.failure(),
                            event -> {
                                if (!RenderStreamEnvelope.TYPE_DATA_MODEL.equals(event.type())
                                        && !RenderStreamEnvelope.TYPE_DONE.equals(event.type())) {
                                    sink.accept(
                                            new RenderStreamEnvelope(
                                                    event.type(),
                                                    session.nextSeq(),
                                                    event.content()));
                                }
                            });
            sink.accept(RenderStreamEnvelope.done(session.nextSeq()));
            return result.withEndReason(outcome.failure(), roundsOf(outcome.result()));
        } catch (GenerationSdkException error) {
            sink.accept(
                    RenderStreamEnvelope.error(
                            session.nextSeq(),
                            "MARKDOWN_REQUEST_FAILED",
                            error.getMessage(),
                            true));
            sink.accept(RenderStreamEnvelope.done(session.nextSeq()));
            return new GenUiGenerationResult("", effective.response())
                    .withEndReason(outcome.failure(), roundsOf(outcome.result()));
        }
    }

    private static List<RoundEndResult> roundsOf(GenUiGenerationResult result) {
        return result == null ? List.of() : result.rounds();
    }

    /** 首轮和修复由同一 repair 会话管理;调用级预算、终止与降级留在生成入口。 */
    private GenUiGenerationResult generateStreamInternal(
            UiGenerationRequest request,
            Consumer<RenderStreamEnvelope> sink,
            GenerationSession budget) {
        GenUIPromptRequest promptRequest = toPromptRequest(request);
        RequestBody body = buildRequest(promptRequest, request, true);
        Map<String, Object> dataModel = request.response();
        if (validationConfig.validationEnabled()) {
            RepairLoop repair =
                    new RepairLoop(
                            new RepairLoop.Context(
                                    body.systemPrompt(),
                                    userContext(request),
                                    sdk.mergedContract(promptRequest),
                                    dataModel,
                                    validationConfig.maxIncrementalAttempts()),
                            validator,
                            config,
                            transport,
                            budget::expiredNow,
                            delta -> sink.accept(RenderStreamEnvelope.dsl(0, delta)));
            budget.partialSnapshot(() -> repair.snapshot(GenerationEndReason.MODEL_REQUEST_FAILED));
            try {
                GenUiGenerationResult result = repair.run(body.json());
                if (result.validationStatus() == ValidationStatus.INVALID) {
                    budget.terminate(result.endReason());
                }
                return result;
            } catch (LlmTransportException error) {
                sink.accept(
                        RenderStreamEnvelope.error(
                                0, "LLM_STREAM_FAILED", error.getMessage(), true));
                return repair.snapshot(GenerationEndReason.MODEL_REQUEST_FAILED);
            }
        }

        // With validation disabled, retain partial streamed output on request failure.
        StringBuffer accumulated = new StringBuffer();
        budget.partialSnapshot(
                () ->
                        new GenUiGenerationResult(
                                OpenuiCodeExtractor.extract(accumulated.toString()),
                                dataModel,
                                null,
                                null,
                                GenerationEndReason.MODEL_REQUEST_FAILED,
                                List.of()));
        try (InputStream stream = transport.postStream(body.json())) {
            SseDeltaParser.parse(
                    stream,
                    delta -> {
                        sink.accept(RenderStreamEnvelope.dsl(0, delta));
                        accumulated.append(delta);
                    });
            return new GenUiGenerationResult(
                    OpenuiCodeExtractor.extract(accumulated.toString()), dataModel);
        } catch (LlmTransportException | IOException error) {
            sink.accept(
                    RenderStreamEnvelope.error(0, "LLM_STREAM_FAILED", error.getMessage(), true));
            return new GenUiGenerationResult(
                    OpenuiCodeExtractor.extract(accumulated.toString()),
                    dataModel,
                    null,
                    null,
                    GenerationEndReason.MODEL_REQUEST_FAILED,
                    List.of());
        }
    }

    private RequestBody buildRequest(
            GenUIPromptRequest promptRequest, UiGenerationRequest request, boolean stream) {
        GenUIPromptAssemblyResult assembly = sdk.assemblePrompt(promptRequest);
        String systemPrompt =
                systemPromptOverride == null ? assembly.prompt() : systemPromptOverride;
        List<ChatMessage> messages =
                List.of(ChatMessage.system(systemPrompt), ChatMessage.user(userMessage(request)));
        return new RequestBody(
                ChatCompletionRequest.of(config, null, messages, stream).toJson(), systemPrompt);
    }

    private record RequestBody(String json, String systemPrompt) {}

    private GenUIPromptRequest toPromptRequest(UiGenerationRequest request) {
        if (request.includeBasic() != null || !request.specifiedComponents().isEmpty()) {
            throw new IllegalArgumentException(
                    "Component filtering (includeBasic/specifiedComponents) is not supported by this SDK version");
        }
        List<String> extraRules =
                request.suggestion() == null || request.suggestion().isBlank()
                        ? List.of()
                        : List.of(request.suggestion());

        DataModelSpec dataModel =
                request.response().isEmpty()
                        ? null
                        : new DataModelSpec("Response data", request.response());
        return new GenUIPromptRequest(
                request.extensionId(),
                dataModel,
                extraRules,
                request.editMode(),
                request.inlineMode(),
                request.toolCalls(),
                request.bindings());
    }

    private String userMessage(UiGenerationRequest request) {
        return userContext(request) + " /no_think";
    }

    private String userContext(UiGenerationRequest request) {
        StringBuilder message = new StringBuilder();
        if (request.userInput() != null) {
            message.append(request.userInput());
        }
        if (!request.request().isEmpty()) {
            if (!message.isEmpty()) {
                message.append("\n\n");
            }
            message.append("## Request Context\n```json\n")
                    .append(Json.stringifyPretty(request.request()))
                    .append("\n```");
        }
        return message.toString();
    }
}
