/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.evalcli;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerationResult;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerator;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 有界线程池批量执行生成用例,按完成顺序向 stdout 逐行输出 JSONL。单用例失败输出 error 行、批次继续; 整批跑完即正常返回(exit 0),基础设施失败由调用方抛出。
 */
final class JobRunner {
    private JobRunner() {
    }

    static void runAll(List<EvalCliMain.GenerationJob> jobs, int concurrency, GenUiGenerator generator, PrintStream out)
            throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(concurrency, Math.max(jobs.size(), 1)));
        try {
            List<Future<?>> futures = jobs.stream().map(job -> pool.submit(() -> runOne(job, generator, out)))
                    .<Future<?>>map(f -> f).toList();
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException error) {
                    // runOne 已把用例级异常转为 error 行;走到这里属于意外,记录后继续等其余用例
                    System.err.println("[genui-eval-cli] unexpected worker failure: " + error.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void runOne(EvalCliMain.GenerationJob job, GenUiGenerator generator, PrintStream out) {
        LinkedHashMap<String, Object> line = new LinkedHashMap<>();
        line.put("id", job.id());
        try {
            GenUiGenerationResult result = EvalCliMain.GenerationJob.execute(job, generator);
            String dsl = result.dsl() == null ? "" : result.dsl().strip();
            if (dsl.isEmpty()) {
                line.put("status", "error");
                line.put("error", "LLM returned empty DSL");
            } else {
                line.put("status", "ok");
                line.put("dsl", dsl);
            }
        } catch (Exception error) {
            line.put("status", "error");
            line.put("error", String.valueOf(error.getMessage()));
        }
        emit(out, Json.stringify(line));
    }

    private static synchronized void emit(PrintStream out, String line) {
        out.println(line);
        out.flush();
    }
}
