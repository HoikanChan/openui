/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 配置解析：真实环境变量优先，其次从仓库根 {@code .env} 读取。文件可选、不存在不报错；只解析 {@code KEY=VALUE} 行，{@code #} 注释与空行跳过。
 *
 * @since 2026
 */
final class BenchEnv {

    /** bench 消费的配置键（含代理）。 */
    static final List<String> KEYS =
            List.of("LLM_BASE_URL", "LLM_API_KEY", "LLM_MODEL", "HTTPS_PROXY");

    private BenchEnv() {}

    /**
     * 合并环境变量与 {@code .env} 文件：环境变量非空者胜出，文件只补齐缺省键。
     *
     * @param dotEnvPath 仓库根 .env 路径；不存在或不可读时按空文件处理
     * @return 合并后的配置视图（只含 {@link #KEYS} 中实际有值的键）
     */
    static Map<String, String> resolve(Path dotEnvPath) {
        Map<String, String> fileValues = readDotEnv(dotEnvPath);
        LinkedHashMap<String, String> resolved = new LinkedHashMap<>();
        for (String key : KEYS) {
            String envValue = System.getenv(key);
            if (envValue != null && !envValue.isBlank()) {
                resolved.put(key, envValue);
            } else {
                String fileValue = fileValues.get(key);
                if (fileValue != null && !fileValue.isBlank()) {
                    resolved.put(key, fileValue);
                }
            }
        }
        return resolved;
    }

    /**
     * 解析 .env 文件。只认 {@code KEY=VALUE} 行；{@code #} 开头与空行跳过；无 {@code =} 的行跳过。
     *
     * @param path .env 文件路径
     * @return 文件内解析到的键值（键去空格，值保留原样去首尾空格）
     */
    static Map<String, String> readDotEnv(Path path) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (path == null || !Files.isRegularFile(path)) {
            return values;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            return values;
        }
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = trimmed.substring(0, equals).strip();
            String value = trimmed.substring(equals + 1).strip();
            values.put(key, value);
        }
        return values;
    }
}
