/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.evalcli;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerationResult;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerator;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eval Generation CLI:无状态计算器。用例清单进(--jobs JSON),生成的 openui-lang 逐行出(stdout JSONL); 所有文件簿记(快照、run 清单、prompt 存档)由 Node
 * 侧负责。用法:
 *
 * <pre>
 *   java -jar genui-eval-cli.jar generate --base-contract=&lt;path&gt; --jobs=&lt;path&gt; [--concurrency=6]
 *   java -jar genui-eval-cli.jar print-prompt --base-contract=&lt;path&gt;
 * </pre>
 *
 * LLM 连接配置经环境变量:LLM_BASE_URL / LLM_API_KEY / LLM_MODEL / HTTPS_PROXY(print-prompt 均不需要)。
 */
public final class EvalCliMain {
    /** 与 Node 侧 prompt-artifact 约定一致的规范 prompt 占位 dataModel。 */
    static final String PLACEHOLDER_KEY = "__EVAL_DATA_MODEL_PLACEHOLDER__";

    static final String PLACEHOLDER_VALUE = "Fixture data is recorded in report-data.json entries[].dataModel";

    private static final int DEFAULT_CONCURRENCY = 6;

    private static final int EXIT_USAGE = 2;

    private EvalCliMain() {
    }

    public static void main(String[] args) {
        // stdout 承载 prompt/DSL 内容,必须无条件 UTF-8,不随平台控制台编码漂移
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        try {
            run(args, out, System.getenv());
        } catch (CliUsageException error) {
            System.err.println("[genui-eval-cli] " + error.getMessage());
            System.exit(EXIT_USAGE);
        } catch (Exception error) {
            System.err.println("[genui-eval-cli] " + error.getMessage());
            System.exit(1);
        }
    }

    static void run(String[] args, PrintStream out, Map<String, String> env) throws Exception {
        CliArgs cli = CliArgs.parse(args);
        GenerationSdk sdk = loadSdk(cli.baseContractPath());
        if ("print-prompt".equals(cli.command())) {
            out.println(assembleCanonicalPrompt(sdk));
            return;
        }
        generate(cli, sdk, out, env);
    }

    static String assembleCanonicalPrompt(GenerationSdk sdk) {
        LinkedHashMap<String, Object> placeholder = new LinkedHashMap<>();
        placeholder.put(PLACEHOLDER_KEY, PLACEHOLDER_VALUE);
        // 与 GenUiGenerator.toPromptRequest 相同的 DataModelSpec 描述,保证存档与实际发送字节一致
        GenUIPromptRequest request = new GenUIPromptRequest(null, new DataModelSpec("Response data", placeholder),
                List.of(), null, null, null, null);
        return sdk.assemblePrompt(request).prompt();
    }

    private static void generate(CliArgs cli, GenerationSdk sdk, PrintStream out, Map<String, String> env)
            throws Exception {
        String model = env.get("LLM_MODEL");
        if (model == null || model.isBlank()) {
            throw new CliUsageException("LLM_MODEL is not set");
        }
        OpenAiCompatibleLlmTransport transport = new OpenAiCompatibleLlmTransport(env.get("LLM_BASE_URL"),
                env.get("LLM_API_KEY"), env.get("HTTPS_PROXY"));
        GenUiLlmConfig config = GenUiLlmConfig.builder().defaultModel(model).temperature(0).build();
        GenUiGenerator generator = GenUiGenerator.withTransport(sdk, config, transport);

        List<GenerationJob> jobs = GenerationJob.parseAll(readFile(cli.jobsPath(), "jobs"));
        JobRunner.runAll(jobs, cli.concurrency(), generator, out);
    }

    private static GenerationSdk loadSdk(String baseContractPath) {
        GenerationContract contract = GenerationContractLoader.fromJson(readFile(baseContractPath, "base contract"));
        return GenerationSdk.builder().baseContract(contract).build();
    }

    private static String readFile(String path, String label) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new CliUsageException("Cannot read " + label + " file: " + path + " (" + error.getMessage() + ")");
        }
    }

    /** 参数解析结果。command 为 generate 或 print-prompt。 */
    record CliArgs(String command, String baseContractPath, String jobsPath, int concurrency) {
        static CliArgs parse(String[] args) {
            if (args == null || args.length == 0) {
                throw new CliUsageException("Usage: generate --base-contract=<path> --jobs=<path> [--concurrency=N] | "
                        + "print-prompt --base-contract=<path>");
            }
            String command = normalizeCommand(args[0]);
            String baseContract = null;
            String jobs = null;
            int concurrency = DEFAULT_CONCURRENCY;
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                if (arg.startsWith("--base-contract=")) {
                    baseContract = arg.substring("--base-contract=".length());
                } else if (arg.startsWith("--jobs=")) {
                    jobs = arg.substring("--jobs=".length());
                } else if (arg.startsWith("--concurrency=")) {
                    concurrency = parseConcurrency(arg.substring("--concurrency=".length()));
                } else {
                    throw new CliUsageException("Unknown argument: " + arg);
                }
            }
            if (baseContract == null || baseContract.isBlank()) {
                throw new CliUsageException("--base-contract=<path> is required");
            }
            if ("generate".equals(command) && (jobs == null || jobs.isBlank())) {
                throw new CliUsageException("--jobs=<path> is required for generate");
            }
            return new CliArgs(command, baseContract, jobs, concurrency);
        }

        private static String normalizeCommand(String raw) {
            String command = raw.startsWith("--") ? raw.substring(2) : raw;
            if (!"generate".equals(command) && !"print-prompt".equals(command)) {
                throw new CliUsageException("Unknown command: " + raw);
            }
            return command;
        }

        private static int parseConcurrency(String raw) {
            try {
                int value = Integer.parseInt(raw);
                if (value < 1) {
                    throw new CliUsageException("--concurrency must be >= 1, got: " + raw);
                }
                return value;
            } catch (NumberFormatException error) {
                throw new CliUsageException("Invalid --concurrency value: " + raw);
            }
        }
    }

    /** 单个生成用例:{id, userInput, dataModel}。 */
    record GenerationJob(String id, String userInput, Map<String, Object> dataModel) {
        @SuppressWarnings("unchecked")
        static List<GenerationJob> parseAll(String jobsJson) {
            List<Object> entries = Json.asList(Json.parse(jobsJson), "jobs");
            return entries.stream().map(entry -> {
                Map<String, Object> map = Json.asObject(entry, "job");
                String id = String.valueOf(map.get("id"));
                if (map.get("id") == null || id.isBlank()) {
                    throw new CliUsageException("Job entry is missing required field: id");
                }
                String userInput = map.get("userInput") == null ? "" : String.valueOf(map.get("userInput"));
                Object rawModel = map.get("dataModel");
                Map<String, Object> dataModel = rawModel == null
                        ? Map.of()
                        : (Map<String, Object>) Json.asObject(rawModel, "dataModel");
                return new GenerationJob(id, userInput, dataModel);
            }).toList();
        }

        static GenUiGenerationResult execute(GenerationJob job, GenUiGenerator generator) {
            return generator.generate(com.huawei.cloudsop.genui.core.llm.UiGenerationRequest.builder()
                    .userInput(job.userInput()).response(new LinkedHashMap<>(job.dataModel())).build());
        }
    }

    static final class CliUsageException extends RuntimeException {
        CliUsageException(String message) {
            super(message);
        }
    }
}
