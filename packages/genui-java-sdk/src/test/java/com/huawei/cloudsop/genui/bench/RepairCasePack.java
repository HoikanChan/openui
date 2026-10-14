/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 修复案例包的目录扫描与契约解析。目录布局：
 *
 * <pre>
 * repair-cases/
 *   contract.json          # 语料级共享契约
 *   case-0001/
 *     case.json            # 必需：id / originalIntent? / dataModel?
 *     contract.json        # 可选：案例级契约，优先于语料级
 *     dsl.txt              # 必需：失败 DSL 原文（不包 fence，不 strip——存证优先）
 *     issues.json          # 可选：内部拦截报错 [{"code": "...", ...}]，仅透传
 * </pre>
 *
 * 契约解析链：{@code <case>/contract.json} &gt; 语料级 {@code contract.json} &gt; 报错终止。故意不做 SDK 默认契约兜底——
 * bench 的意义是保真"当时契约"，静默兜底会把契约漂移伪装成 not-reproduced。
 *
 * <p>
 * 语料是 git 静态资产：<b>任何</b>解析失败（case.json 非法 / dsl.txt 缺失或空 / contract 解析失败 / issues.json
 * 非法 / id 重复 / 零案例目录 / --cases 不是目录）都收集全部问题后整批终止，不提供降级跑法。
 *
 * @since 2026
 */
final class RepairCasePack {

    /** 加载阶段的结构性失败：报错信息列出全部问题案例。 */
    static final class PackLoadException extends Exception {
        PackLoadException(String message) {
            super(message);
        }
    }

    /**
     * 单个修复案例。
     *
     * @param id
     *            案例 id（以 case.json 的 {@code id} 字段为准，全语料唯一）
     * @param originalIntent
     *            原始用户意图；缺失为 {@code null}（ReaskPromptBuilder 对 null/blank 跳过 Original request 段）
     * @param dataModel
     *            渲染数据模型：缺失/显式 null → {@code null}（类型校验关闭）；{@code {}} → 空 map（具体空模型）。
     *            绝不能把缺失归一成空 map —— {@code ValidationRequest.dataModel} 区分两者
     * @param dsl
     *            失败 DSL 原文（不 strip）
     * @param contract
     *            该案例生效的契约（案例级优先于语料级）
     * @param internalIssueCodes
     *            issues.json 透传的内部拦截 code；文件缺失为 {@code null}（输出层据此缺省该字段）
     * @param dirName
     *            案例目录名（仅用于排序与定位）
     */
    record RepairCase(String id, String originalIntent, Map<String, Object> dataModel, String dsl,
            GenerationContract contract, List<String> internalIssueCodes, String dirName) {
    }

    /**
     * 扫描并加载整个案例包；结构性损坏时抛出 {@link PackLoadException}。
     *
     * @param casesRoot
     *            案例包根目录
     * @param limit
     *            只取按目录名排序后的前 N 个案例；{@code null} 表示全部
     * @return 按目录名排序的案例列表
     * @throws PackLoadException
     *             案例包结构性损坏（报错信息列出全部问题案例）
     */
    static List<RepairCase> load(Path casesRoot, Integer limit) throws PackLoadException {
        List<String> problems = new ArrayList<>();
        if (casesRoot == null || !Files.isDirectory(casesRoot)) {
            throw new PackLoadException("--cases is not a directory: " + casesRoot);
        }

        GenerationContract sharedContract = null;
        Path sharedContractPath = casesRoot.resolve("contract.json");
        if (Files.isRegularFile(sharedContractPath)) {
            try {
                sharedContract = parseContract(sharedContractPath);
            } catch (Exception error) {
                problems.add(problem(sharedContractPath, "corpus contract.json invalid: " + error.getMessage()));
            }
        }

        List<Path> caseDirs;
        try (Stream<Path> stream = Files.list(casesRoot)) {
            caseDirs = stream.filter(Files::isDirectory).map(Path::getFileName).map(Path::toString).sorted()
                    .map(casesRoot::resolve).toList();
        } catch (IOException error) {
            throw new PackLoadException("Failed to list cases directory " + casesRoot + ": " + error.getMessage());
        }

        List<Path> withCaseJson = new ArrayList<>();
        for (Path dir : caseDirs) {
            if (Files.isRegularFile(dir.resolve("case.json"))) {
                withCaseJson.add(dir);
            }
        }
        if (withCaseJson.isEmpty()) {
            problems.add(problem(casesRoot, "no case directories containing case.json found"));
        }

        if (limit != null && limit < withCaseJson.size()) {
            withCaseJson = new ArrayList<>(withCaseJson.subList(0, limit));
        }

        List<RepairCase> cases = new ArrayList<>();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        LinkedHashMap<String, String> idDirs = new LinkedHashMap<>();
        for (Path dir : withCaseJson) {
            RepairCase loaded = loadOne(dir, sharedContract, problems);
            if (loaded == null) {
                continue;
            }
            if (!ids.add(loaded.id())) {
                problems.add(problem(dir, "duplicate case id '" + loaded.id() + "' (also in " + idDirs.get(loaded.id())
                        + ")"));
                continue;
            }
            idDirs.put(loaded.id(), dir.getFileName().toString());
            cases.add(loaded);
        }

        if (!problems.isEmpty()) {
            throw new PackLoadException("Repair case pack is structurally broken (" + problems.size()
                    + " problem(s)):\n - " + String.join("\n - ", problems));
        }
        cases.sort(Comparator.comparing(RepairCase::dirName));
        return cases;
    }

    /**
     * 加载单个案例目录；所有问题记入 {@code problems} 并返回 {@code null}。
     */
    private static RepairCase loadOne(Path dir, GenerationContract sharedContract, List<String> problems) {
        Map<String, Object> caseJson;
        try {
            caseJson = Json.asObject(Json.parse(readUtf8(dir.resolve("case.json"))), "case.json");
        } catch (Exception error) {
            problems.add(problem(dir, "case.json invalid: " + error.getMessage()));
            return null;
        }

        Object idValue = caseJson.get("id");
        String id = idValue instanceof String text ? text : null;
        if (id == null || id.isBlank()) {
            problems.add(problem(dir, "case.json missing required non-empty string field 'id'"));
            return null;
        }

        Object intentValue = caseJson.get("originalIntent");
        String originalIntent = intentValue instanceof String text ? text : null;

        Map<String, Object> dataModel = null;
        if (caseJson.containsKey("dataModel") && caseJson.get("dataModel") != null) {
            try {
                dataModel = Json.asObject(caseJson.get("dataModel"), "dataModel");
            } catch (Exception error) {
                problems.add(problem(dir, "case.json 'dataModel' must be a JSON object: " + error.getMessage()));
                return null;
            }
        }

        Path dslPath = dir.resolve("dsl.txt");
        String dsl = null;
        if (!Files.isRegularFile(dslPath)) {
            problems.add(problem(dir, "dsl.txt missing"));
            return null;
        }
        try {
            dsl = readUtf8(dslPath);
        } catch (IOException error) {
            problems.add(problem(dir, "dsl.txt unreadable: " + error.getMessage()));
            return null;
        }
        if (dsl.isBlank()) {
            problems.add(problem(dir, "dsl.txt is empty"));
            return null;
        }

        GenerationContract contract = sharedContract;
        Path caseContractPath = dir.resolve("contract.json");
        if (Files.isRegularFile(caseContractPath)) {
            try {
                contract = parseContract(caseContractPath);
            } catch (Exception error) {
                problems.add(problem(dir, "contract.json invalid: " + error.getMessage()));
                return null;
            }
        }
        if (contract == null) {
            problems.add(problem(dir,
                    "no contract: neither <case>/contract.json nor corpus contract.json exists"));
            return null;
        }

        List<String> internalIssueCodes = null;
        Path issuesPath = dir.resolve("issues.json");
        if (Files.isRegularFile(issuesPath)) {
            try {
                internalIssueCodes = parseIssueCodes(issuesPath);
            } catch (Exception error) {
                problems.add(problem(dir, "issues.json invalid: " + error.getMessage()));
                return null;
            }
        }

        return new RepairCase(id, originalIntent, dataModel, dsl, contract, internalIssueCodes,
                dir.getFileName().toString());
    }

    private static GenerationContract parseContract(Path path) throws IOException {
        return GenerationContractLoader.fromJson(readUtf8(path));
    }

    private static List<String> parseIssueCodes(Path path) throws IOException {
        List<Object> entries = Json.asList(Json.parse(readUtf8(path)), "issues.json");
        List<String> codes = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            Map<String, Object> entry = Json.asObject(entries.get(i), "issues.json[" + i + "]");
            Object code = entry.get("code");
            if (!(code instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException("issues.json[" + i + "].code must be a non-empty string");
            }
            codes.add(text);
        }
        return codes;
    }

    private static String readUtf8(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String problem(Path dir, String detail) {
        return dir.getFileName() + ": " + detail;
    }
}
