/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm;

import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * 可注入的单调时钟与调度器。20 秒预算只约束第二次及之后的增量修复。
 *
 * @param nanoTime 单调时钟来源,返回纳秒
 * @param scheduler 延迟动作调度器,保留用于源码兼容
 * @since 2026
 */
public record GenerationTiming(LongSupplier nanoTime, Scheduler scheduler) {
    public static final long BUDGET_NANOS = TimeUnit.SECONDS.toNanos(20L);
    private static final ScheduledThreadPoolExecutor TIMER = timer();

    /**
     * 校验时钟与调度器均不为 null。
     *
     * @throws NullPointerException 任一参数为 null 时抛出
     */
    public GenerationTiming {
        Objects.requireNonNull(nanoTime);
        Objects.requireNonNull(scheduler);
    }

    /**
     * 延迟动作调度器,保留用于源码兼容;生成流程已不再通过它调度预算取消。
     *
     * @since 2026
     */
    @FunctionalInterface
    public interface Scheduler {
        /**
         * 调度一次延迟动作,不阻塞调用线程。
         *
         * @param action 到期后执行的动作
         * @param delayNanos 延迟纳秒数,负数按 0 处理
         * @return 幂等的取消动作,重复调用无额外副作用
         */
        Runnable schedule(Runnable action, long delayNanos);
    }

    /**
     * 基于 {@link System#nanoTime()} 与共享守护线程定时器的默认实现。
     *
     * @return 使用系统时钟的计时配置
     */
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
                        1, Thread.ofPlatform().daemon().name("genui-deadline-", 0L).factory());
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }
}
