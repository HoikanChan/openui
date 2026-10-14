/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * repair-bench 入口：真实错误语料修复基准（内部迭代工具，test scope，永不进交付物）。
 * 案例包进 → stdout JSONL 出（无状态计算器）；run 目录 / manifest / 报告由外部脚本负责。
 *
 * <p>
 * 用法：
 *
 * <pre>{@code
 * java -cp <test-classpath> com.huawei.cloudsop.genui.bench.RepairBenchCli \
 *   --cases=repair-cases/ [--concurrency=4] [--max-attempts=3] [--timeout-minutes=0] [--limit=N] [--repair]
 * }</pre>
 *
 * 默认（不带 {@code --repair}）为 validation-only：每案例只做 FINAL 校验并输出 status + issueCodes，
 * 不读 .env、不建 transport、零 LLM 调用。带 {@code --repair} 才走完整修复（此时才需要 LLM 配置）。
 *
 * LLM 配置：{@code LLM_BASE_URL / LLM_API_KEY / LLM_MODEL / HTTPS_PROXY}，真实环境变量优先，其次从仓库根
 * {@code .env} 读取。
 *
 * <p>
 * 退出码：0 = 整批跑完；2 = 用法错误；1 = 基础设施失败（含加载阶段任何案例解析失败——语料是 git 静态资产，
 * 坏一个就是全坏，不静默降级）。
 *
 * @since 2026
 */
public final class RepairBenchCli {

    private RepairBenchCli() {
    }

    public static void main(String[] args) {
        RepairBenchArgs parsed;
        try {
            parsed = RepairBenchArgs.parse(args);
        } catch (IllegalArgumentException error) {
            System.err.println("usage: RepairBenchCli --cases=<dir> [--concurrency=4] [--max-attempts=3] "
                    + "[--timeout-minutes=0] [--limit=N] [--repair]");
            System.err.println("error: " + error.getMessage());
            System.exit(2);
            return;
        }

        List<RepairCasePack.RepairCase> cases;
        try {
            cases = RepairCasePack.load(Paths.get(parsed.casesDir()), parsed.limit());
        } catch (RepairCasePack.PackLoadException error) {
            System.err.println("[repair-bench] " + error.getMessage());
            System.exit(1);
            return;
        }

        Map<String, String> env = parsed.repair() ? BenchEnv.resolve(findRepoRoot().resolve(".env")) : Map.of();
        LlmTransport transport = null;
        String model = null;
        if (parsed.repair()) {
            model = env.get("LLM_MODEL");
            if (model == null || model.isBlank()) {
                System.err.println("[repair-bench] LLM_MODEL is not set (env or repo-root .env)");
                System.exit(1);
                return;
            }
            try {
                transport = new OpenAiCompatBenchTransport(env.get("LLM_BASE_URL"), env.get("LLM_API_KEY"),
                        env.get("HTTPS_PROXY"));
            } catch (IllegalArgumentException error) {
                System.err.println("[repair-bench] " + error.getMessage());
                System.exit(1);
                return;
            }
        }

        RepairBenchRunner runner = new RepairBenchRunner(transport, model, parsed.maxAttempts(),
                Duration.ofMinutes(parsed.timeoutMinutes()), System.out, parsed.repair());
        try {
            runner.runAll(cases, parsed.concurrency());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            System.err.println("[repair-bench] interrupted");
            System.exit(1);
        }
    }

    /** 定位仓库根（.env 所在）：用户目录或其祖先中第一个含 .git 者；找不到退回用户目录。 */
    private static Path findRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path current = dir;
        while (current != null) {
            if (java.nio.file.Files.exists(current.resolve(".git"))) {
                return current;
            }
            current = current.getParent();
        }
        return dir;
    }
}
