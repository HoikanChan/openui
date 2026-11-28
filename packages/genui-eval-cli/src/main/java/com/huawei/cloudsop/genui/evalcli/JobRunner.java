/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.evalcli;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerationResult;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerator;
import com.huawei.cloudsop.genui.core.llm.GenerationEndReason;
import com.huawei.cloudsop.genui.eval.langsmith.BatchRunBuffer;
import com.huawei.cloudsop.genui.eval.trace.LangSmithGenerationObserver;

import java.io.PrintStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 有界线程池批量执行生成用例,按完成顺序向 stdout 逐行输出 JSONL。单用例失败输出 error 行、批次继续; 整批跑完即正常返回(exit 0),基础设施失败由调用方抛出。
 *
 * @since 2026
 */
final class JobRunner {
    private JobRunner() {}

    static void runAll(
            List<EvalCliMain.GenerationJob> jobs,
            int concurrency,
            GenUiGenerator generator,
            PrintStream out)
            throws InterruptedException {
        runAll(jobs, concurrency, generator, false, null, null, out);
    }

    static void runAll(
            List<EvalCliMain.GenerationJob> jobs,
            int concurrency,
            GenUiGenerator generator,
            boolean validate,
            BatchRunBuffer langSmithBuffer,
            String langSmithSessionId,
            PrintStream out)
            throws InterruptedException {
        ExecutorService pool =
                Executors.newFixedThreadPool(Math.min(concurrency, Math.max(jobs.size(), 1)));
        try {
            List<Future<?>> futures =
                    jobs.stream()
                            .map(
                                    job ->
                                            pool.submit(
                                                    () ->
                                                            runOne(
                                                                    job,
                                                                    generator,
                                                                    validate,
                                                                    langSmithBuffer,
                                                                    langSmithSessionId,
                                                                    out)))
                            .<Future<?>>map(f -> f)
                            .toList();
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException error) {
                    System.err.println(
                            "[genui-eval-cli] unexpected worker failure: " + error.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void runOne(
            EvalCliMain.GenerationJob job,
            GenUiGenerator generator,
            boolean validate,
            BatchRunBuffer langSmithBuffer,
            String langSmithSessionId,
            PrintStream out) {
        LinkedHashMap<String, Object> line = new LinkedHashMap<>();
        line.put("id", job.id());
        LangSmithGenerationObserver observer =
                createObserver(job, validate, langSmithBuffer, langSmithSessionId);
        GenUiGenerator effectiveGenerator =
                observer == null ? generator : generator.withObserver(observer);
        try {
            GenUiGenerationResult result =
                    EvalCliMain.GenerationJob.execute(job, effectiveGenerator, validate);
            String failure = dslFailure(result);
            String dsl = result.dsl() == null ? "" : result.dsl().strip();
            populateResult(line, failure, dsl);
            finishObserver(
                    line,
                    observer,
                    langSmithSessionId,
                    Map.of(
                            "status",
                            line.get("status"),
                            "dsl",
                            dsl,
                            "endReason",
                            result.endReason().name()),
                    failure);
        } catch (Exception error) {
            line.put("status", "error");
            line.put("error", String.valueOf(error.getMessage()));
            finishObserver(
                    line,
                    observer,
                    langSmithSessionId,
                    Map.of("status", "error"),
                    error.getMessage());
        }
        emit(out, Json.stringify(line));
    }

    private static LangSmithGenerationObserver createObserver(
            EvalCliMain.GenerationJob job,
            boolean validate,
            BatchRunBuffer buffer,
            String sessionId) {
        if (buffer == null) {
            return null;
        }
        return new LangSmithGenerationObserver(
                buffer,
                sessionId,
                null,
                job.id(),
                "eval_cli_" + job.id(),
                Map.of("caseId", job.id(), "userInput", job.userInput()),
                Map.of("validate", validate));
    }

    private static void populateResult(
            LinkedHashMap<String, Object> line, String failure, String dsl) {
        if (failure != null) {
            line.put("status", "error");
            line.put("error", dsl.isEmpty() ? failure + " (empty DSL)" : failure);
        } else if (dsl.isEmpty()) {
            line.put("status", "error");
            line.put("error", "LLM returned empty DSL");
        } else {
            line.put("status", "ok");
            line.put("dsl", dsl);
        }
    }

    private static void finishObserver(
            LinkedHashMap<String, Object> line,
            LangSmithGenerationObserver observer,
            String sessionId,
            Map<String, Object> outputs,
            String error) {
        if (observer == null) {
            return;
        }
        line.put("langsmithRunId", observer.rootRunId());
        if (sessionId != null) {
            line.put("langsmithSessionId", sessionId);
        }
        observer.finishRoot(outputs, error, Instant.now());
    }

    /**
     * A Markdown fallback also fills {@code result.dsl()} with the whole-page Markdown wrapper.
     * Only a DSL session that finished successfully may be reported as {@code ok}: validation
     * passed, or validation was explicitly disabled (nothing was checked). TIMEOUT /
     * MODEL_REQUEST_FAILED / VALIDATION_FAILED keep their reason.
     *
     * @param result generation result to classify
     * @return the failure reason, or {@code null} when the DSL session counts as successful
     */
    static String dslFailure(GenUiGenerationResult result) {
        GenerationEndReason reason = result.endReason();
        if (reason == GenerationEndReason.CHECK_PASSED) {
            return null;
        }
        if (reason == GenerationEndReason.UNCHECKED && result.validationStatus() == null) {
            return null;
        }
        return "DSL session did not produce a validated page: " + reason;
    }

    private static synchronized void emit(PrintStream out, String line) {
        out.println(line);
        out.flush();
    }
}
