/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.cli;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;
import com.huawei.cloudsop.genui.eval.ab.GenerationAbExperimentRunner;
import com.huawei.cloudsop.genui.eval.corpus.ValidationCorpusExperimentRunner;
import com.huawei.cloudsop.genui.eval.dataset.CorpusLoaders;
import com.huawei.cloudsop.genui.eval.dataset.DatasetCase;
import com.huawei.cloudsop.genui.eval.dataset.DatasetSyncer;
import com.huawei.cloudsop.genui.eval.langsmith.EnvConfig;
import com.huawei.cloudsop.genui.eval.langsmith.LangSmithClient;
import com.huawei.cloudsop.genui.eval.langsmith.LangSmithConfig;
import com.huawei.cloudsop.genui.eval.langsmith.SharedOpenAiTransport;
import com.huawei.cloudsop.genui.eval.repair.RepairExperimentRunner;
import com.huawei.cloudsop.genui.eval.report.OfflineHtmlReporter;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一的 LangSmith 生成/校验/修复评估 CLI。支持以下四个子命令。
 *
 * <ul>
 *   <li>{@code sync-dataset}: 幂等同步 {@code repair-cases}、{@code validation-test} 或 {@code fixtures}
 *       到 LangSmith Dataset
 *   <li>{@code eval-repair}: 运行修复基准（validation-only 或 {@code --repair}），同步上传 LangSmith 并生成离线 HTML
 *       报告
 *   <li>{@code eval-ab}: 运行端到端 A/B 生成对照（{@code --arm=off|on}），支持本地/内网 OpenAI 兼容模型与离线 HTML 报告
 *   <li>{@code eval-corpus}: 运行 {@code validation-test/} 人工标注覆盖率实验，上传 LangSmith 并生成离线 HTML 报告
 * </ul>
 *
 * @since 2026
 */
public final class LangSmithEvalCli {
    private static final int EXIT_USAGE = 2;
    private static final int DEFAULT_CONCURRENCY = 4;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private LangSmithEvalCli() {}

    /**
     * CLI 入口。参数错误返回退出码 2，运行期错误返回退出码 1。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        PrintStream out =
                new PrintStream(
                        new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        try {
            Map<String, String> env = EnvConfig.resolve(EnvConfig.findRepoRoot().resolve(".env"));
            run(args, env, out);
        } catch (IllegalArgumentException error) {
            System.err.println("[genui-eval-langsmith] " + error.getMessage());
            printUsage();
            System.exit(EXIT_USAGE);
        } catch (Exception error) {
            System.err.println("[genui-eval-langsmith] " + error.getMessage());
            System.exit(1);
        }
    }

    /**
     * 程序化执行 CLI 子命令，便于测试与外部编排复用。
     *
     * @param args 命令行参数
     * @param env 环境变量映射
     * @param out 标准输出流
     * @throws IllegalArgumentException 参数缺失或未知子命令时抛出
     * @throws Exception 执行失败时抛出
     */
    public static void run(String[] args, Map<String, String> env, PrintStream out)
            throws Exception {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("Missing subcommand");
        }
        String command = args[0];
        Map<String, String> opts = parseOptions(args, 1);
        switch (command) {
            case "sync-dataset" -> runSyncDataset(opts, env, out);
            case "eval-repair" -> runEvalRepair(opts, env, out);
            case "eval-ab" -> runEvalAb(opts, env, out);
            case "eval-corpus" -> runEvalCorpus(opts, env, out);
            default -> throw new IllegalArgumentException("Unknown subcommand: " + command);
        }
    }

    static Map<String, String> parseOptions(String[] args, int startIndex) {
        LinkedHashMap<String, String> opts = new LinkedHashMap<>();
        int index = startIndex;
        while (index < args.length) {
            String arg = args[index];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected positional argument: " + arg);
            }
            int eq = arg.indexOf('=');
            if (eq > 2) {
                opts.put(arg.substring(2, eq), arg.substring(eq + 1));
                index++;
            } else {
                String key = arg.substring(2);
                if (index + 1 < args.length && !args[index + 1].startsWith("--")) {
                    opts.put(key, args[index + 1]);
                    index += 2;
                } else {
                    opts.put(key, "true");
                    index++;
                }
            }
        }
        return opts;
    }

    private static void runSyncDataset(
            Map<String, String> opts, Map<String, String> env, PrintStream out) {
        LangSmithClient client = requireLangSmithClient(env);
        String type = requireOpt(opts, "type");
        Path source = Path.of(requireOpt(opts, "source"));
        String datasetName = opts.getOrDefault("dataset", "genui-" + type);
        Integer limit = optionalInt(opts, "limit");

        List<DatasetCase> cases = loadCasesByType(type, source, limit);
        Map<String, Object> meta = baseMetadata(type);
        DatasetSyncer.SyncSummary summary =
                new DatasetSyncer(client)
                        .sync(datasetName, "GenUI synced corpus (" + type + ")", meta, cases);
        out.println(Json.stringify(summaryMap(summary)));
    }

    private static void runEvalRepair(
            Map<String, String> opts, Map<String, String> env, PrintStream out) throws Exception {
        boolean repair = isFlagTrue(opts.get("repair"));
        List<CorpusLoaders.LoadedRepairCase> cases =
                CorpusLoaders.loadRepairCases(
                        Path.of(requireOpt(opts, "cases")), optionalInt(opts, "limit"));
        String model = repair ? requireValue(env.get("LLM_MODEL"), "LLM_MODEL") : null;
        SharedOpenAiTransport transport = repair ? buildTransport(env) : null;
        LangSmithSessionContext ls =
                maybeStartSession(
                        opts,
                        env,
                        opts.getOrDefault("dataset", "genui-repair-cases"),
                        repair ? "repair" : "repair-validation-only",
                        cases.stream().map(CorpusLoaders.LoadedRepairCase::toDatasetCase).toList(),
                        model);
        Path outJsonl = opts.containsKey("out") ? Path.of(opts.get("out")) : null;
        RepairExperimentRunner.RunOutput result =
                executeRepair(opts, cases, transport, model, ls, outJsonl, out);
        writeRepairHtmlIfEnabled(opts, outJsonl, result, cases);
    }

    private static RepairExperimentRunner.RunOutput executeRepair(
            Map<String, String> opts,
            List<CorpusLoaders.LoadedRepairCase> cases,
            SharedOpenAiTransport transport,
            String model,
            LangSmithSessionContext ls,
            Path outJsonl,
            PrintStream out)
            throws Exception {
        PrintStream jsonlStream = openJsonlStream(outJsonl, out);
        try {
            RepairExperimentRunner runner =
                    new RepairExperimentRunner(
                            new RepairExperimentRunner.Options(
                                    transport,
                                    model,
                                    intOpt(opts, "max-attempts", DEFAULT_MAX_ATTEMPTS),
                                    Duration.ofMinutes(longOpt(opts, "timeout-minutes", 0L)),
                                    transport != null,
                                    isFlagTrue(opts.get("include-usage")),
                                    ls.client(),
                                    ls.sessionId(),
                                    ls.exampleIds(),
                                    jsonlStream));
            return runner.runAll(cases, intOpt(opts, "concurrency", DEFAULT_CONCURRENCY));
        } finally {
            if (jsonlStream != out) {
                jsonlStream.close();
            }
        }
    }

    private static void writeRepairHtmlIfEnabled(
            Map<String, String> opts,
            Path outJsonl,
            RepairExperimentRunner.RunOutput result,
            List<CorpusLoaders.LoadedRepairCase> cases)
            throws IOException {
        Path htmlPath = resolveOfflineHtmlPath(opts, outJsonl, "repair");
        if (htmlPath == null) {
            return;
        }
        String sourceLabel = outJsonl == null ? "stdout" : fileName(outJsonl);
        OfflineHtmlReporter.writeRepairReport(result.emittedLines(), cases, htmlPath, sourceLabel);
        System.err.println(
                "[genui-eval-langsmith] offline HTML report: " + htmlPath.toAbsolutePath());
    }

    private static void runEvalAb(
            Map<String, String> opts, Map<String, String> env, PrintStream out) throws Exception {
        String arm = requireOpt(opts, "arm");
        String model = opts.getOrDefault("model", requireValue(env.get("LLM_MODEL"), "LLM_MODEL"));
        GenUiLlmConfig llmConfig =
                GenUiLlmConfig.builder()
                        .defaultModel(model)
                        .temperature(Double.parseDouble(opts.getOrDefault("temperature", "0")))
                        .enableThinking(false)
                        .includeStreamUsage(isFlagTrue(opts.get("include-usage")))
                        .build();
        List<CorpusLoaders.LoadedAbFixture> fixtures =
                CorpusLoaders.loadAbFixtures(
                        Path.of(requireOpt(opts, "fixtures")), optionalInt(opts, "limit"));
        LangSmithSessionContext ls =
                maybeStartSession(
                        opts,
                        env,
                        opts.getOrDefault("dataset", "genui-ab-fixtures"),
                        "ab-" + arm,
                        fixtures.stream()
                                .map(CorpusLoaders.LoadedAbFixture::toDatasetCase)
                                .toList(),
                        model);
        Path outDir = opts.containsKey("out-dir") ? Path.of(opts.get("out-dir")) : null;
        GenerationAbExperimentRunner runner =
                new GenerationAbExperimentRunner(
                        new GenerationAbExperimentRunner.Options(
                                buildTransport(env),
                                arm,
                                llmConfig,
                                ls.client(),
                                ls.sessionId(),
                                ls.exampleIds(),
                                outDir));
        GenerationAbExperimentRunner.AbRunOutput result =
                runner.runAll(fixtures, intOpt(opts, "concurrency", DEFAULT_CONCURRENCY));
        emitAbOutputAndHtml(opts, arm, model, ls.sessionId(), result, out);
    }

    private static void emitAbOutputAndHtml(
            Map<String, String> opts,
            String arm,
            String model,
            String sessionId,
            GenerationAbExperimentRunner.AbRunOutput result,
            PrintStream out)
            throws IOException {
        out.println(
                Json.stringify(
                        Map.of(
                                "arm",
                                arm,
                                "model",
                                model,
                                "langsmithSessionId",
                                sessionId == null ? "" : sessionId,
                                "summaryScores",
                                result.summaryScores(),
                                "fixtures",
                                result.metrics())));
        Path htmlPath = resolveOfflineHtmlPath(opts, null, "ab-" + arm);
        if (htmlPath != null) {
            OfflineHtmlReporter.writeTableReport(
                    "GenUI A/B Evaluation (" + arm + ", " + model + ")",
                    result.summaryScores(),
                    result.metrics(),
                    htmlPath);
            System.err.println(
                    "[genui-eval-langsmith] offline HTML report: " + htmlPath.toAbsolutePath());
        }
    }

    private static SharedOpenAiTransport buildTransport(Map<String, String> env) {
        return new SharedOpenAiTransport(
                env.get("LLM_BASE_URL"), env.get("LLM_API_KEY"), env.get("HTTPS_PROXY"));
    }

    private static void runEvalCorpus(
            Map<String, String> opts, Map<String, String> env, PrintStream out) throws Exception {
        Path corpusDir = Path.of(requireOpt(opts, "dir"));
        List<CorpusLoaders.LoadedValidationTestCase> cases =
                CorpusLoaders.loadValidationTestCases(corpusDir);

        LangSmithSessionContext ls =
                maybeStartSession(
                        opts,
                        env,
                        opts.getOrDefault("dataset", "genui-validation-test"),
                        "validation-corpus",
                        cases.stream()
                                .map(CorpusLoaders.LoadedValidationTestCase::toDatasetCase)
                                .toList(),
                        null);

        ValidationCorpusExperimentRunner runner =
                new ValidationCorpusExperimentRunner(ls.client(), ls.sessionId(), ls.exampleIds());
        ValidationCorpusExperimentRunner.CorpusRunOutput result = runner.runAll(cases);

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", result.total());
        summary.put("statuses", result.statusCounts());
        summary.put("coverage", result.coverageCounts());
        summary.put("summaryScores", result.summaryScores());
        if (ls.sessionId() != null) {
            summary.put("langsmithSessionId", ls.sessionId());
        }
        out.println(Json.stringify(summary));

        Path htmlPath = resolveOfflineHtmlPath(opts, null, "validation-corpus");
        if (htmlPath != null) {
            LinkedHashMap<String, Object> cards = new LinkedHashMap<>();
            cards.put("total", result.total());
            cards.put("statuses", result.statusCounts());
            cards.put("coverage", result.coverageCounts());
            cards.putAll(result.summaryScores());
            OfflineHtmlReporter.writeTableReport(
                    "GenUI Validation Corpus Evaluation", cards, result.caseResults(), htmlPath);
            System.err.println(
                    "[genui-eval-langsmith] offline HTML report: " + htmlPath.toAbsolutePath());
        }
    }

    private record LangSmithSessionContext(
            LangSmithClient client, String sessionId, Map<String, String> exampleIds) {}

    private static LangSmithSessionContext maybeStartSession(
            Map<String, String> opts,
            Map<String, String> env,
            String datasetName,
            String track,
            List<DatasetCase> cases,
            String model) {
        if (isFlagTrue(opts.get("offline"))) {
            return new LangSmithSessionContext(null, null, Map.of());
        }
        LangSmithConfig config = LangSmithConfig.fromEnvOrNull(env);
        if (config == null) {
            return new LangSmithSessionContext(null, null, Map.of());
        }
        LangSmithClient client = new LangSmithClient(config);
        Map<String, Object> meta = baseMetadata(track);
        if (model != null) {
            meta.put("model", model);
        }
        DatasetSyncer.SyncSummary synced =
                new DatasetSyncer(client).sync(datasetName, "GenUI " + track, meta, cases);
        String experimentName =
                opts.getOrDefault(
                        "experiment", "genui-" + track + "-" + LocalDateTime.now().format(STAMP));
        LangSmithClient.SessionRecord session =
                client.createExperimentSession(experimentName, synced.datasetId(), meta);
        System.err.println(
                "[genui-eval-langsmith] experiment="
                        + session.name()
                        + " sessionId="
                        + session.id()
                        + " dataset="
                        + datasetName);
        return new LangSmithSessionContext(client, session.id(), synced.exampleIdByCaseId());
    }

    private static List<DatasetCase> loadCasesByType(String type, Path source, Integer limit) {
        return switch (type) {
            case "repair-cases" ->
                    CorpusLoaders.loadRepairCases(source, limit).stream()
                            .map(CorpusLoaders.LoadedRepairCase::toDatasetCase)
                            .toList();
            case "validation-test" ->
                    CorpusLoaders.loadValidationTestCases(source).stream()
                            .map(CorpusLoaders.LoadedValidationTestCase::toDatasetCase)
                            .toList();
            case "ab-fixtures" ->
                    CorpusLoaders.loadAbFixtures(source, limit).stream()
                            .map(CorpusLoaders.LoadedAbFixture::toDatasetCase)
                            .toList();
            default -> throw new IllegalArgumentException("Unsupported corpus --type: " + type);
        };
    }

    private static Map<String, Object> baseMetadata(String track) {
        LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
        meta.put("track", track);
        meta.put("contract_version", GenerationContractLoader.loadDefault().contractVersion());
        meta.put("contract_sha256", CorpusLoaders.defaultContractSha256());
        return meta;
    }

    private static Map<String, Object> summaryMap(DatasetSyncer.SyncSummary summary) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("datasetId", summary.datasetId());
        map.put("datasetName", summary.datasetName());
        map.put("created", summary.created());
        map.put("updated", summary.updated());
        map.put("unchanged", summary.unchanged());
        return map;
    }

    private static LangSmithClient requireLangSmithClient(Map<String, String> env) {
        LangSmithConfig config = LangSmithConfig.fromEnvOrNull(env);
        if (config == null) {
            throw new IllegalArgumentException(
                    "LANGSMITH_API_KEY is required (env or repo-root .env)");
        }
        return new LangSmithClient(config);
    }

    private static PrintStream openJsonlStream(Path outJsonl, PrintStream fallback)
            throws IOException {
        if (outJsonl == null) {
            return fallback;
        }
        Path parent = outJsonl.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return new PrintStream(Files.newOutputStream(outJsonl), true, StandardCharsets.UTF_8);
    }

    private static Path resolveOfflineHtmlPath(
            Map<String, String> opts, Path outJsonl, String prefix) {
        if (isFlagTrue(opts.get("no-html"))) {
            return null;
        }
        String explicit = opts.getOrDefault("offline-report", opts.get("html"));
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit);
        }
        if (outJsonl != null) {
            String name = fileName(outJsonl);
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            Path parent = outJsonl.getParent();
            return parent == null ? Path.of(base + ".html") : parent.resolve(base + ".html");
        }
        return Path.of(
                "target",
                "langsmith-reports",
                prefix + "-" + LocalDateTime.now().format(STAMP) + ".html");
    }

    private static boolean isFlagTrue(String value) {
        return "true".equals(value) || "TRUE".equals(value) || "1".equals(value);
    }

    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    private static String requireOpt(Map<String, String> opts, String key) {
        return requireValue(opts.get(key), "--" + key);
    }

    private static String requireValue(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required setting: " + label);
        }
        return value;
    }

    private static int intOpt(Map<String, String> opts, String key, int defaultValue) {
        String raw = opts.get(key);
        return raw == null || raw.isBlank() ? defaultValue : Integer.parseInt(raw);
    }

    private static long longOpt(Map<String, String> opts, String key, long defaultValue) {
        String raw = opts.get(key);
        return raw == null || raw.isBlank() ? defaultValue : Long.parseLong(raw);
    }

    private static Integer optionalInt(Map<String, String> opts, String key) {
        String raw = opts.get(key);
        return raw == null || raw.isBlank() ? null : Integer.parseInt(raw);
    }

    private static void printUsage() {
        System.err.println(
                "Usage:\n"
                        + "  sync-dataset --type=repair-cases|validation-test|ab-fixtures --source=<dir> [--dataset=<name>] [--limit=N]\n"
                        + "  eval-repair  --cases=<dir> [--repair] [--max-attempts=3] [--concurrency=4] [--include-usage] [--offline] [--offline-report=<file.html>]\n"
                        + "  eval-ab      --fixtures=<dir> --arm=off|on [--concurrency=4] [--include-usage] [--out-dir=<dir>] [--offline] [--offline-report=<file.html>]\n"
                        + "  eval-corpus  --dir=<validation-test-dir> [--offline] [--offline-report=<file.html>]");
    }
}
