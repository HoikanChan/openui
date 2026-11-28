/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.langsmith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一环境变量与 {@code .env} 文件加载器。系统环境变量优先，其次读取仓库根 {@code .env}。
 *
 * @since 2026
 */
public final class EnvConfig {
    /** 支持读取的标准环境变量键列表。 */
    public static final List<String> KNOWN_KEYS =
            List.of(
                    "LANGSMITH_API_KEY",
                    "LANGSMITH_ENDPOINT",
                    "LANGSMITH_WORKSPACE_ID",
                    "LANGSMITH_PROJECT",
                    "LANGSMITH_FAILED_TRACES_DIR",
                    "LLM_BASE_URL",
                    "LLM_API_KEY",
                    "LLM_MODEL",
                    "HTTPS_PROXY");

    private EnvConfig() {}

    /**
     * 合并系统环境变量和指定 {@code .env} 文件中的配置。
     *
     * @param dotEnvPath {@code .env} 文件路径，可为 null 或不存在
     * @return 合并后的非空键值映射
     */
    public static Map<String, String> resolve(Path dotEnvPath) {
        return resolve(System.getenv(), dotEnvPath);
    }

    /**
     * 合并给定环境变量映射与 {@code .env} 文件，便于单测注入环境变量。
     *
     * @param systemEnv 系统环境变量映射
     * @param dotEnvPath {@code .env} 文件路径，可为 null 或不存在
     * @return 合并后的非空键值映射
     */
    public static Map<String, String> resolve(Map<String, String> systemEnv, Path dotEnvPath) {
        Map<String, String> fileValues = readDotEnv(dotEnvPath);
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        for (String key : KNOWN_KEYS) {
            String value = systemEnv == null ? null : systemEnv.get(key);
            if (value == null || value.isBlank()) {
                value = fileValues.get(key);
            }
            if (value != null && !value.isBlank()) {
                merged.put(key, value.strip());
            }
        }
        return merged;
    }

    /**
     * 解析 {@code .env} 文件，跳过空行与 {@code #} 注释，自动剥离外层成对单双引号。
     *
     * @param path 文件路径
     * @return 文件中解析到的键值
     */
    public static Map<String, String> readDotEnv(Path path) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (path == null || !Files.isRegularFile(path)) {
            return values;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return values;
        }
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int sep = trimmed.indexOf('=');
            if (sep <= 0) {
                continue;
            }
            String key = trimmed.substring(0, sep).strip();
            String value = unquote(trimmed.substring(sep + 1).strip());
            values.put(key, value);
        }
        return values;
    }

    /**
     * 向上查找仓库根目录（包含 {@code .git} 的首个目录），找不到时回退到当前工作目录。
     *
     * @return 仓库根路径
     */
    public static Path findRepoRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path probe = current;
        while (probe != null) {
            if (Files.exists(probe.resolve(".git"))) {
                return probe;
            }
            probe = probe.getParent();
        }
        return current;
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
