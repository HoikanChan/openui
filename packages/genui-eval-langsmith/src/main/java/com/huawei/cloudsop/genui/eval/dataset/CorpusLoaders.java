/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.dataset;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 本地三类评测语料加载器：{@code repair-cases}、{@code validation-test} 和端到端 A/B {@code fixtures}。
 *
 * @since 2026
 */
public final class CorpusLoaders {
    private static final Pattern ANNOTATION_LINE =
            Pattern.compile("\\\"id\\\":\\s*\\\"([^\\\"]+)\\\"\\s*,(.*)$");
    private static final String DEFAULT_CONTRACT_RESOURCE = "openui/base-contract.json";

    private CorpusLoaders() {}

    /**
     * 单个修复语料案例（供本地执行和 LangSmith 数据集同步共用）。
     *
     * @param id 案例唯一 ID
     * @param originalIntent 原始意图，可为 null
     * @param dataModel 数据模型；{@code null} 与空 map {@code {}} 语义不同
     * @param dsl 原始失败 DSL（不 strip）
     * @param contract 生效契约
     * @param contractJson 若使用外部自定义契约则为原文，默认 SDK 契约时为 {@code null}
     * @param contractSha256 生效契约 JSON 的 SHA-256
     * @param internalIssueCodes 可选内部拦截报错码
     * @param rootCause 可选根因说明
     * @param dirName 目录名（排序依据）
     * @since 2026
     */
    public record LoadedRepairCase(
            String id,
            String originalIntent,
            Map<String, Object> dataModel,
            String dsl,
            GenerationContract contract,
            String contractJson,
            String contractSha256,
            List<String> internalIssueCodes,
            String rootCause,
            String dirName) {

        /**
         * 转为 LangSmith 标准样例，保留 {@code hasDataModel} 与原始 DSL。
         *
         * @return 标准样例
         */
        public DatasetCase toDatasetCase() {
            LinkedHashMap<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("caseId", id);
            inputs.put("originalIntent", originalIntent);
            inputs.put("dsl", dsl);
            inputs.put("hasDataModel", dataModel != null);
            inputs.put("dataModel", dataModel);
            if (contractJson != null) {
                inputs.put("contractJson", contractJson);
            }
            LinkedHashMap<String, Object> outputs = new LinkedHashMap<>();
            if (internalIssueCodes != null) {
                outputs.put("internalIssueCodes", internalIssueCodes);
            }
            if (rootCause != null && !rootCause.isBlank()) {
                outputs.put("rootCause", rootCause);
            }
            LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
            meta.put("corpus_type", "repair-cases");
            meta.put("dir_name", dirName);
            meta.put("contract_version", contract.contractVersion());
            meta.put("contract_sha256", contractSha256);
            return DatasetCase.of(id, inputs, outputs.isEmpty() ? null : outputs, meta);
        }
    }

    /**
     * 单个 {@code validation-test} 人工标注语料案例。
     *
     * @param id 案例唯一 ID
     * @param sourceFile 来源 JSON 文件名
     * @param indexInFile 文件内序号（1 起）
     * @param dsl DSL 原文
     * @param dataModel 可选数据模型
     * @param annotation {@code error_detail.txt} 中的人工标注，无标注时为 null
     * @since 2026
     */
    public record LoadedValidationTestCase(
            String id,
            String sourceFile,
            int indexInFile,
            String dsl,
            Map<String, Object> dataModel,
            String annotation) {

        /**
         * 转为 LangSmith 标准样例。
         *
         * @return 标准样例
         */
        public DatasetCase toDatasetCase() {
            LinkedHashMap<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("caseId", id);
            inputs.put("dsl", dsl);
            inputs.put("hasDataModel", dataModel != null);
            inputs.put("dataModel", dataModel);
            LinkedHashMap<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("annotation", annotation);
            LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
            meta.put("corpus_type", "validation-test");
            meta.put("source_file", sourceFile);
            meta.put("index_in_file", indexInFile);
            return DatasetCase.of(id, inputs, outputs, meta);
        }
    }

    /**
     * 单个端到端 A/B 生成测试夹具（{@code meta.prompt} + {@code data}）。
     *
     * @param id 夹具 ID
     * @param prompt 用户输入提示词
     * @param dataModel 渲染数据对象，非对象结构时为 null
     * @param skipReason 若数据结构不满足 Java SDK 要求则记录原因
     * @param evalHints 可选评估提示
     * @param taxonomy 可选分类标签
     * @since 2026
     */
    public record LoadedAbFixture(
            String id,
            String prompt,
            Map<String, Object> dataModel,
            String skipReason,
            List<String> evalHints,
            List<String> taxonomy) {

        /**
         * 转为 LangSmith 标准样例。
         *
         * @return 标准样例
         */
        public DatasetCase toDatasetCase() {
            LinkedHashMap<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("caseId", id);
            inputs.put("prompt", prompt);
            inputs.put("hasDataModel", dataModel != null);
            inputs.put("dataModel", dataModel);
            if (skipReason != null) {
                inputs.put("skipReason", skipReason);
            }
            LinkedHashMap<String, Object> outputs = new LinkedHashMap<>();
            if (evalHints != null && !evalHints.isEmpty()) {
                outputs.put("evalHints", evalHints);
            }
            if (taxonomy != null && !taxonomy.isEmpty()) {
                outputs.put("taxonomy", taxonomy);
            }
            LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
            meta.put("corpus_type", "ab-fixtures");
            return DatasetCase.of(id, inputs, outputs.isEmpty() ? null : outputs, meta);
        }
    }

    /**
     * 加载 {@code repair-cases/} 目录；任何结构性损坏均一次性收集并抛 {@link IllegalArgumentException}。
     *
     * @param casesRoot 案例根目录
     * @param limit 可选数量上限（按目录名排序截取）
     * @return 按目录名排序的修复案例列表
     * @throws IllegalArgumentException 目录不存在或语料结构损坏时抛出
     */
    public static List<LoadedRepairCase> loadRepairCases(Path casesRoot, Integer limit) {
        if (casesRoot == null || !Files.isDirectory(casesRoot)) {
            throw new IllegalArgumentException(
                    "repair cases root is not a directory: " + casesRoot);
        }
        List<String> problems = new ArrayList<>();
        ContractHolder shared = loadSharedContract(casesRoot, problems);
        List<Path> caseDirs = listCaseDirs(casesRoot, limit, problems);

        List<LoadedRepairCase> loaded = new ArrayList<>();
        LinkedHashSet<String> seenIds = new LinkedHashSet<>();
        for (Path dir : caseDirs) {
            LoadedRepairCase item = loadSingleRepairCase(dir, shared, problems);
            if (item == null) {
                continue;
            }
            if (!seenIds.add(item.id())) {
                problems.add(dir.getFileName() + ": duplicate case id '" + item.id() + "'");
                continue;
            }
            loaded.add(item);
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(
                    "Repair case pack invalid ("
                            + problems.size()
                            + " problem(s)):\n - "
                            + String.join("\n - ", problems));
        }
        loaded.sort(Comparator.comparing(LoadedRepairCase::dirName));
        return loaded;
    }

    /**
     * 加载 {@code validation-test/} 目录下的 {@code error_detail.txt} 和所有 {@code *.json} 用例文件。
     *
     * @param validationTestDir {@code validation-test} 目录路径
     * @return 用例列表（按文件名与数组顺序）
     * @throws IllegalArgumentException 目录不存在或 JSON 解析失败时抛出
     */
    @SuppressWarnings("unchecked")
    public static List<LoadedValidationTestCase> loadValidationTestCases(Path validationTestDir) {
        if (validationTestDir == null || !Files.isDirectory(validationTestDir)) {
            throw new IllegalArgumentException(
                    "validation-test is not a directory: " + validationTestDir);
        }
        Map<String, String> annotations =
                readAnnotations(validationTestDir.resolve("error_detail.txt"));
        List<Path> jsonFiles = listJsonFiles(validationTestDir);
        List<LoadedValidationTestCase> results = new ArrayList<>();
        for (Path file : jsonFiles) {
            List<Object> entries = Json.asList(Json.parse(readUtf8(file)), file.toString());
            for (int i = 0; i < entries.size(); i++) {
                Map<String, Object> obj = Json.asObject(entries.get(i), "case");
                String id = String.valueOf(obj.get("id"));
                Object rawDm = obj.get("dataModel");
                Map<String, Object> dm =
                        rawDm instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
                results.add(
                        new LoadedValidationTestCase(
                                id,
                                fileName(file),
                                i + 1,
                                String.valueOf(obj.get("dsl")),
                                dm,
                                annotations.get(id)));
            }
        }
        return results;
    }

    /**
     * 加载端到端 A/B 生成夹具目录中的所有 {@code *.json} 文件。
     *
     * @param fixturesDir 夹具目录
     * @param limit 可选数量上限
     * @return 按 ID 排序的夹具列表
     * @throws IllegalArgumentException 目录不存在或夹具非法时抛出
     */
    @SuppressWarnings("unchecked")
    public static List<LoadedAbFixture> loadAbFixtures(Path fixturesDir, Integer limit) {
        if (fixturesDir == null || !Files.isDirectory(fixturesDir)) {
            throw new IllegalArgumentException("fixtures dir is not a directory: " + fixturesDir);
        }
        List<Path> files = listJsonFiles(fixturesDir);
        if (limit != null && limit < files.size()) {
            files = files.subList(0, limit);
        }
        List<LoadedAbFixture> results = new ArrayList<>();
        for (Path file : files) {
            String id = fileName(file).replaceFirst("\\.json$", "");
            Map<String, Object> env = Json.asObject(Json.parse(readUtf8(file)), id);
            Map<String, Object> meta = Json.asObject(env.get("meta"), "meta of " + id);
            String prompt = meta.get("prompt") == null ? "" : String.valueOf(meta.get("prompt"));
            Object data = env.get("data");
            Map<String, Object> dm =
                    data instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
            String skip =
                    dm != null
                            ? null
                            : "top-level data is "
                                    + (data == null ? "null" : data.getClass().getSimpleName())
                                    + "; Java SDK data model requires an object";
            results.add(
                    new LoadedAbFixture(
                            id,
                            prompt,
                            dm,
                            skip,
                            asStringList(meta.get("evalHints")),
                            asStringList(meta.get("taxonomy"))));
        }
        return results;
    }

    /**
     * 计算 SDK 内置默认契约文件的 SHA-256，供实验元数据固定契约版本。
     *
     * @return 默认契约 SHA-256
     * @throws IllegalStateException 读取内置契约资源失败时抛出
     */
    public static String defaultContractSha256() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(DEFAULT_CONTRACT_RESOURCE)) {
            if (in == null) {
                return DatasetCase.sha256Hex(
                        GenerationContractLoader.loadDefault().contractVersion());
            }
            return DatasetCase.sha256Hex(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("Failed reading default contract resource", error);
        }
    }

    private record ContractHolder(GenerationContract contract, String rawJson, String sha256) {}

    private static ContractHolder loadSharedContract(Path casesRoot, List<String> problems) {
        Path sharedPath = casesRoot.resolve("contract.json");
        if (Files.isRegularFile(sharedPath)) {
            try {
                String raw = readUtf8(sharedPath);
                return new ContractHolder(
                        GenerationContractLoader.fromJson(raw), raw, DatasetCase.sha256Hex(raw));
            } catch (RuntimeException error) {
                problems.add("contract.json invalid: " + error.getMessage());
                return null;
            }
        }
        return new ContractHolder(
                GenerationContractLoader.loadDefault(), null, defaultContractSha256());
    }

    private static List<Path> listCaseDirs(Path casesRoot, Integer limit, List<String> problems) {
        List<Path> dirs;
        try (Stream<Path> stream = Files.list(casesRoot)) {
            dirs =
                    stream.filter(Files::isDirectory)
                            .filter(dir -> Files.isRegularFile(dir.resolve("case.json")))
                            .sorted(Comparator.comparing(CorpusLoaders::fileName))
                            .toList();
        } catch (IOException error) {
            throw new IllegalArgumentException("Failed listing " + casesRoot, error);
        }
        if (dirs.isEmpty()) {
            problems.add(casesRoot.getFileName() + ": no case directories containing case.json");
            return List.of();
        }
        if (limit != null && limit < dirs.size()) {
            return dirs.subList(0, limit);
        }
        return dirs;
    }

    private static LoadedRepairCase loadSingleRepairCase(
            Path dir, ContractHolder shared, List<String> problems) {
        String dirName = fileName(dir);
        Map<String, Object> caseJson;
        try {
            caseJson = Json.asObject(Json.parse(readUtf8(dir.resolve("case.json"))), "case.json");
        } catch (RuntimeException error) {
            problems.add(dirName + ": case.json invalid: " + error.getMessage());
            return null;
        }
        String id = caseJson.get("id") instanceof String text ? text : null;
        if (id == null || id.isBlank()) {
            problems.add(dirName + ": case.json missing non-empty id");
            return null;
        }
        String intent = caseJson.get("originalIntent") instanceof String text ? text : null;
        String rootCause = caseJson.get("rootCause") instanceof String text ? text : null;
        Map<String, Object> dm = parseOptionalDataModel(dirName, caseJson, problems);
        if (dm == null && caseJson.get("dataModel") != null) {
            return null;
        }
        String dsl = readRequiredDsl(dir, dirName, problems);
        if (dsl == null) {
            return null;
        }
        ContractHolder effective = resolveCaseContract(dir, dirName, shared, problems);
        if (effective == null) {
            return null;
        }
        List<String> internalCodes =
                readOptionalIssues(dir.resolve("issues.json"), dirName, problems);
        return new LoadedRepairCase(
                id,
                intent,
                dm,
                dsl,
                effective.contract(),
                effective.rawJson(),
                effective.sha256(),
                internalCodes,
                rootCause,
                dirName);
    }

    private static Map<String, Object> parseOptionalDataModel(
            String dirName, Map<String, Object> caseJson, List<String> problems) {
        Object raw = caseJson.get("dataModel");
        if (raw == null) {
            return null;
        }
        try {
            return Json.asObject(raw, "dataModel");
        } catch (RuntimeException error) {
            problems.add(dirName + ": dataModel must be JSON object: " + error.getMessage());
            return null;
        }
    }

    private static String readRequiredDsl(Path dir, String dirName, List<String> problems) {
        Path dslPath = dir.resolve("dsl.txt");
        if (!Files.isRegularFile(dslPath)) {
            problems.add(dirName + ": dsl.txt missing");
            return null;
        }
        String dsl = readUtf8(dslPath);
        if (dsl.isBlank()) {
            problems.add(dirName + ": dsl.txt is empty");
            return null;
        }
        return dsl;
    }

    private static ContractHolder resolveCaseContract(
            Path dir, String dirName, ContractHolder shared, List<String> problems) {
        Path caseContract = dir.resolve("contract.json");
        if (!Files.isRegularFile(caseContract)) {
            return shared;
        }
        try {
            String raw = readUtf8(caseContract);
            return new ContractHolder(
                    GenerationContractLoader.fromJson(raw), raw, DatasetCase.sha256Hex(raw));
        } catch (RuntimeException error) {
            problems.add(dirName + ": contract.json invalid: " + error.getMessage());
            return null;
        }
    }

    private static List<String> readOptionalIssues(
            Path issuesPath, String dirName, List<String> problems) {
        if (!Files.isRegularFile(issuesPath)) {
            return null;
        }
        try {
            List<Object> entries = Json.asList(Json.parse(readUtf8(issuesPath)), "issues.json");
            List<String> codes = new ArrayList<>(entries.size());
            for (Object entry : entries) {
                Map<String, Object> map = Json.asObject(entry, "issue");
                Object code = map.get("code");
                if (!(code instanceof String text) || text.isBlank()) {
                    throw new IllegalArgumentException("issue missing code");
                }
                codes.add(text);
            }
            return codes;
        } catch (RuntimeException error) {
            problems.add(dirName + ": issues.json invalid: " + error.getMessage());
            return null;
        }
    }

    private static Map<String, String> readAnnotations(Path file) {
        LinkedHashMap<String, String> annotations = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return annotations;
        }
        for (String line : readUtf8(file).split("\\R")) {
            Matcher matcher = ANNOTATION_LINE.matcher(line.strip());
            if (matcher.find()) {
                annotations.put(matcher.group(1).strip(), matcher.group(2).strip());
            }
        }
        return annotations;
    }

    private static List<Path> listJsonFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> fileName(path).endsWith(".json"))
                    .sorted(Comparator.comparing(CorpusLoaders::fileName))
                    .toList();
        } catch (IOException error) {
            throw new IllegalArgumentException("Failed listing JSON files in " + dir, error);
        }
    }

    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }

    private static String readUtf8(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalArgumentException(
                    "Cannot read " + path + ": " + error.getMessage(), error);
        }
    }
}
