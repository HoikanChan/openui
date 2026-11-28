/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.langsmith;

import com.huawei.cloudsop.genui.core.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步批量 Run 上报缓冲器。按条数或定时窗口合批提交到 {@code /api/v1/runs/batch}， 网络持续失败时按配置落盘到 {@code
 * LANGSMITH_FAILED_TRACES_DIR}。
 *
 * @since 2026
 */
public final class BatchRunBuffer implements AutoCloseable {
    private static final int DEFAULT_BATCH_SIZE = 25;
    private static final int DEFAULT_QUEUE_CAPACITY = 4096;
    private static final long DEFAULT_FLUSH_INTERVAL_MS = 1000L;
    private static final long MIN_FLUSH_INTERVAL_MS = 10L;

    private final LangSmithClient client;
    private final int batchSize;
    private final long flushIntervalMs;
    private final BlockingQueue<Entry> queue;
    private final Thread worker;
    private final AtomicInteger uploadedCount = new AtomicInteger();
    private final AtomicInteger failedBatchCount = new AtomicInteger();
    private volatile boolean closed;

    private static final Entry STOP_SIGNAL = new Entry(false, null);

    private record Entry(boolean patch, Map<String, Object> payload) {}

    /**
     * 使用默认批大小（25）和 1 秒刷新间隔创建缓冲器。
     *
     * @param client LangSmith 客户端
     */
    public BatchRunBuffer(LangSmithClient client) {
        this(client, DEFAULT_BATCH_SIZE, DEFAULT_FLUSH_INTERVAL_MS);
    }

    /**
     * 使用自定义批大小和刷新间隔创建缓冲器。
     *
     * @param client LangSmith 客户端
     * @param batchSize 单批最大条数
     * @param flushIntervalMs 最大等待刷新间隔（毫秒）
     */
    public BatchRunBuffer(LangSmithClient client, int batchSize, long flushIntervalMs) {
        this.client = Objects.requireNonNull(client, "client");
        this.batchSize = Math.max(1, batchSize);
        this.flushIntervalMs = Math.max(MIN_FLUSH_INTERVAL_MS, flushIntervalMs);
        this.queue = new LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);
        this.worker =
                Thread.ofPlatform()
                        .name("langsmith-batch-uploader")
                        .daemon(true)
                        .start(this::drainLoop);
    }

    /**
     * 入队一条新建 Run 事件。
     *
     * @param runPayload Run JSON map
     */
    public void enqueuePost(Map<String, Object> runPayload) {
        enqueue(new Entry(false, new LinkedHashMap<>(runPayload)));
    }

    /**
     * 入队一条更新 Run 事件。
     *
     * @param runPayload Run patch JSON map
     */
    public void enqueuePatch(Map<String, Object> runPayload) {
        enqueue(new Entry(true, new LinkedHashMap<>(runPayload)));
    }

    /**
     * 返回已成功上报的 Run 条数。
     *
     * @return 成功条数
     */
    public int uploadedCount() {
        return uploadedCount.get();
    }

    /**
     * 返回最终上报失败的批次数。
     *
     * @return 失败批次数
     */
    public int failedBatchCount() {
        return failedBatchCount.get();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                queue.put(STOP_SIGNAL);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                worker.interrupt();
            }
        }
        try {
            worker.join();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private void enqueue(Entry entry) {
        if (closed) {
            throw new IllegalStateException("BatchRunBuffer is already closed");
        }
        try {
            queue.put(entry);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new LangSmithException("Interrupted while enqueueing LangSmith run", error);
        }
    }

    private void drainLoop() {
        List<Entry> batch = new ArrayList<>(batchSize);
        boolean stopSeen = false;
        while (!stopSeen || !queue.isEmpty()) {
            try {
                Entry first = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (first != null) {
                    if (first == STOP_SIGNAL) {
                        stopSeen = true;
                    } else {
                        batch.add(first);
                    }
                    stopSeen = drainUpTo(batch, batchSize - batch.size()) || stopSeen;
                }
            } catch (InterruptedException ignored) {
                stopSeen = drainUpTo(batch, batchSize - batch.size()) || stopSeen;
            }
            if (!batch.isEmpty()) {
                sendBatch(batch);
                batch.clear();
            }
        }
    }

    private boolean drainUpTo(List<Entry> batch, int maxItems) {
        boolean stopSeen = false;
        for (int i = 0; i < maxItems; i++) {
            Entry next = queue.poll();
            if (next == null) {
                break;
            }
            if (next == STOP_SIGNAL) {
                stopSeen = true;
            } else {
                batch.add(next);
            }
        }
        return stopSeen;
    }

    private void sendBatch(List<Entry> batch) {
        List<Map<String, Object>> posts = new ArrayList<>();
        List<Map<String, Object>> patches = new ArrayList<>();
        for (Entry entry : batch) {
            if (entry.patch()) {
                patches.add(entry.payload());
            } else {
                posts.add(entry.payload());
            }
        }
        try {
            client.ingestRunsBatch(posts, patches);
            uploadedCount.addAndGet(batch.size());
        } catch (RuntimeException error) {
            failedBatchCount.incrementAndGet();
            System.err.println(
                    "[langsmith] failed to upload batch of "
                            + batch.size()
                            + " run(s): "
                            + error.getMessage());
            dumpFailedBatch(posts, patches);
        }
    }

    private void dumpFailedBatch(
            List<Map<String, Object>> posts, List<Map<String, Object>> patches) {
        Path dir = client.config().failedTracesDir();
        if (dir == null) {
            return;
        }
        try {
            Files.createDirectories(dir);
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("post", posts);
            payload.put("patch", patches);
            Path target = dir.resolve("failed-batch-" + UUID.randomUUID() + ".json");
            Files.writeString(target, Json.stringifyPretty(payload), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Best-effort local persistence; never crash evaluation worker.
        }
    }
}
