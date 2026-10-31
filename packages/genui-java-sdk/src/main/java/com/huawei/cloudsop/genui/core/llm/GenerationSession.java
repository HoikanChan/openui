/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.llm.transport.LlmCall;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 单次调用的预算与输出所有权;所有模型调用(包括后续修复)都经由它。
 *
 * @since 2026
 */
final class GenerationSession implements LlmTransport {
    record Outcome(GenUiGenerationResult result, GenerationEndReason failure) {}

    private final LlmTransport delegate;
    private final GenerationTiming timing;
    private final long started;
    private final Consumer<RenderStreamEnvelope> sink;
    private final CompletableFuture<Outcome> completion = new CompletableFuture<>();
    private final Object lock = new Object();
    private final Object outputLock = new Object();
    private volatile boolean terminal;
    private boolean initialSent;
    private LlmCall active;
    private Thread worker;
    private int nextSeq = 1;

    /**
     * Snapshot of completed/interrupted rounds for an abnormal terminal (#58: timeout/model failure
     * keeps rounds).
     */
    private volatile Supplier<GenUiGenerationResult> partialSnapshot;

    GenerationSession(
            LlmTransport delegate,
            GenerationTiming timing,
            long started,
            Consumer<RenderStreamEnvelope> sink) {
        this.delegate = delegate;
        this.timing = timing;
        this.started = started;
        this.sink = sink;
    }

    Outcome run(Callable<GenUiGenerationResult> work) {
        worker =
                Thread.ofVirtual()
                        .unstarted(
                                () -> {
                                    synchronized (lock) {
                                        if (terminal) {
                                            return;
                                        }
                                    }
                                    try {
                                        GenUiGenerationResult result = work.call();
                                        synchronized (lock) {
                                            if (!terminal) {
                                                terminal = true;
                                                completion.complete(new Outcome(result, null));
                                            }
                                        }
                                    } catch (Throwable error) {
                                        synchronized (lock) {
                                            if (!terminal) {
                                                terminal = true;
                                                completion.completeExceptionally(error);
                                            }
                                        }
                                    }
                                });
        try {
            worker.start();
            Outcome outcome = completion.get();
            synchronized (outputLock) {
                // Finish an already-started write before the caller publishes replacement/done.
                // Cancellation uses a separate lock and never waits here.
                return outcome;
            }
        } catch (InterruptedException e) {
            fail(GenerationEndReason.MODEL_REQUEST_FAILED);
            Thread.currentThread().interrupt();
            throw new GenerationSdkException("Generation interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new GenerationSdkException("Generation failed", e.getCause());
        }
    }

    void emit(RenderStreamEnvelope event) {
        if (RenderStreamEnvelope.TYPE_DONE.equals(event.type())) {
            return;
        }
        if (RenderStreamEnvelope.TYPE_ERROR.equals(event.type())
                && event.content() instanceof Map<?, ?> content
                && "LLM_STREAM_FAILED".equals(content.get("code"))) {
            fail(GenerationEndReason.MODEL_REQUEST_FAILED);
            return;
        }
        synchronized (outputLock) {
            if (terminal || sink == null) {
                return;
            }
            if (RenderStreamEnvelope.TYPE_DATA_MODEL.equals(event.type())) {
                return;
            } // public entry sent seq=0
            sink.accept(new RenderStreamEnvelope(event.type(), nextSeq++, event.content()));
        }
    }

    int nextSeq() {
        return nextSeq++;
    }

    void ensureInitial(Map<String, Object> dataModel) {
        synchronized (outputLock) {
            if (!initialSent && sink != null) {
                initialSent = true;
                sink.accept(RenderStreamEnvelope.dataModel(dataModel));
            }
        }
    }

    /**
     * Publish a live snapshot so an abnormal terminal (timeout or transport failure) can retain
     * completed and interrupted round records (#58). The supplier must be cheap and must not emit
     * to the sink.
     */
    void partialSnapshot(Supplier<GenUiGenerationResult> supplier) {
        this.partialSnapshot = supplier;
    }

    /** Whether elapsed time prevents starting a second or later incremental repair. */
    boolean expiredNow() {
        return expired();
    }

    /**
     * End the whole session from the worker with a non-success reason (#58). Used when incremental
     * repair stops while the current program is still invalid, so the public entry performs exactly
     * one whole-page fallback.
     */
    void terminate(GenerationEndReason reason) {
        fail(reason);
    }

    private GenUiGenerationResult snapshotOrNull() {
        Supplier<GenUiGenerationResult> supplier = partialSnapshot;
        if (supplier == null) {
            return null;
        }
        try {
            return supplier.get();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean expired() {
        return timing.nanoTime().getAsLong() - started >= GenerationTiming.BUDGET_NANOS;
    }

    private void fail(GenerationEndReason reason) {
        LlmCall cancel;
        synchronized (lock) {
            if (terminal) {
                return;
            }
            terminal = true;
            cancel = active;
            active = null;
        }
        try {
            if (cancel != null) {
                cancel.cancel();
            }
        } finally {
            if (worker != null && worker != Thread.currentThread()) {
                worker.interrupt();
            }
            completion.complete(new Outcome(snapshotOrNull(), reason));
        }
    }

    private LlmCall open(String body, boolean stream) throws LlmTransportException {
        synchronized (lock) {
            if (terminal) {
                throw new LlmTransportException("Generation ended");
            }
            if (active != null) {
                throw new IllegalStateException("Previous model request is still open");
            }
            active = delegate.newCall(body, stream);
            return active;
        }
    }

    private void release(LlmCall call) {
        call.close();
        synchronized (lock) {
            if (active == call) {
                active = null;
            }
        }
    }

    /**
     * 执行一次非流式模型请求,返回完整响应体。
     *
     * @param body 完整的 chat-completion 请求 JSON
     * @return 响应体文本(UTF-8 解码)
     * @throws LlmTransportException 会话已结束或底层请求失败时抛出
     */
    @Override
    public String post(String body) throws LlmTransportException {
        LlmCall call = open(body, false);
        try (InputStream input = call.execute()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (LlmTransportException | IOException e) {
            fail(GenerationEndReason.MODEL_REQUEST_FAILED);
            throw new LlmTransportException("LLM request failed", e);
        } finally {
            release(call);
        }
    }

    /**
     * 发起一次流式模型请求,返回需由调用方关闭的响应流。
     *
     * @param body 完整的 chat-completion 请求 JSON
     * @return 响应流;关闭时释放底层调用并清除活动请求标记
     * @throws LlmTransportException 会话已结束、已有未关闭请求或底层请求失败时抛出
     */
    @Override
    public InputStream postStream(String body) throws LlmTransportException {
        LlmCall call = open(body, true);
        try {
            return new FilterInputStream(call.execute()) {
                @Override
                public void close() {
                    release(call);
                }
            };
        } catch (LlmTransportException | RuntimeException e) {
            fail(GenerationEndReason.MODEL_REQUEST_FAILED);
            release(call);
            throw e;
        }
    }
}
