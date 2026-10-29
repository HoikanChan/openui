/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm;

import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Injectable monotonic clock. The 20-second budget gates second and later incremental repairs only.
 */
public record GenerationTiming(LongSupplier nanoTime, Scheduler scheduler) {
    public static final long BUDGET_NANOS = TimeUnit.SECONDS.toNanos(20);
    private static final ScheduledThreadPoolExecutor TIMER = timer();

    public GenerationTiming {
        Objects.requireNonNull(nanoTime);
        Objects.requireNonNull(scheduler);
    }

    /** Retained for source compatibility; generation no longer schedules budget cancellation. */
    @FunctionalInterface
    public interface Scheduler {
        /** Schedule without blocking; return an idempotent cancellation action. */
        Runnable schedule(Runnable action, long delayNanos);
    }

    public static GenerationTiming system() {
        return new GenerationTiming(
                System::nanoTime,
                (action, delay) -> {
                    var task = TIMER.schedule(action, Math.max(0, delay), TimeUnit.NANOSECONDS);
                    return () -> task.cancel(false);
                });
    }

    private static ScheduledThreadPoolExecutor timer() {
        var timer =
                new ScheduledThreadPoolExecutor(
                        1, Thread.ofPlatform().daemon().name("genui-deadline-", 0).factory());
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }
}
