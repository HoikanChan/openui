/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

/**
 * repair-bench 命令行参数。形态对齐 {@code ValidateCli}：{@code --key=value}，未知参数拒绝。
 *
 * @param casesDir
 *            案例包根目录（必填）
 * @param concurrency
 *            有界线程池大小（repair 单案例含多次 LLM 往返，默认低于 generate 的 6）
 * @param maxAttempts
 *            单案例修复尝试预算，映射 {@code RepairPolicy.of(FINAL_REPAIR, N)}
 * @param timeoutMinutes
 *            尝试之间的墙钟预算（分钟）；0 = 不启用，靠 transport 请求上限兜底
 * @param limit
 *            按目录名排序取前 N 个案例（冒烟用）；{@code null} = 全部
 *
 * @since 2026
 */
record RepairBenchArgs(String casesDir, int concurrency, int maxAttempts, long timeoutMinutes, Integer limit) {

    static final int DEFAULT_CONCURRENCY = 4;

    static final int DEFAULT_MAX_ATTEMPTS = 3;

    /**
     * 解析命令行参数。任何用法错误（缺必填 / 未知参数 / 非法值）抛出 {@link IllegalArgumentException}。
     *
     * @param args
     *            原始命令行参数
     * @return 解析结果
     */
    static RepairBenchArgs parse(String[] args) {
        String cases = null;
        int concurrency = DEFAULT_CONCURRENCY;
        int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        long timeoutMinutes = 0;
        Integer limit = null;
        for (String arg : args) {
            if (arg.startsWith("--cases=")) {
                cases = arg.substring("--cases=".length());
            } else if (arg.startsWith("--concurrency=")) {
                concurrency = parsePositiveInt(arg, "--concurrency");
            } else if (arg.startsWith("--max-attempts=")) {
                maxAttempts = parsePositiveInt(arg, "--max-attempts");
            } else if (arg.startsWith("--timeout-minutes=")) {
                timeoutMinutes = parseNonNegativeLong(arg, "--timeout-minutes");
            } else if (arg.startsWith("--limit=")) {
                limit = parsePositiveInt(arg, "--limit");
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        if (cases == null || cases.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: --cases=<dir>");
        }
        return new RepairBenchArgs(cases, concurrency, maxAttempts, timeoutMinutes, limit);
    }

    private static int parsePositiveInt(String arg, String name) {
        String value = arg.substring(name.length() + 1);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException(name + " must be >= 1, got: " + value);
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(name + " must be an integer, got: " + value);
        }
    }

    private static long parseNonNegativeLong(String arg, String name) {
        String value = arg.substring(name.length() + 1);
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new IllegalArgumentException(name + " must be >= 0, got: " + value);
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(name + " must be an integer, got: " + value);
        }
    }
}
