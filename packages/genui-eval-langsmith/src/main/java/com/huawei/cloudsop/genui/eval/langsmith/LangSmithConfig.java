/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.langsmith;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * LangSmith Cloud REST API v1 连接配置。
 *
 * @param endpoint API 根地址，默认 {@link #DEFAULT_ENDPOINT}
 * @param apiKey LangSmith API 密钥（请求头 {@code x-api-key}）
 * @param workspaceId 可选租户/工作区 ID（请求头 {@code X-Tenant-Id}）
 * @param httpsProxy 可选 HTTPS 代理 URL
 * @param failedTracesDir 上报失败时的本地落盘目录，可为 {@code null}
 * @param requestTimeout 单次 HTTP 请求超时
 * @param maxRetries 遇到 429/5xx 时的最大重试次数
 * @since 2026
 */
public record LangSmithConfig(
        String endpoint,
        String apiKey,
        String workspaceId,
        String httpsProxy,
        Path failedTracesDir,
        Duration requestTimeout,
        int maxRetries) {

    /** LangSmith 官方云端默认端点。 */
    public static final String DEFAULT_ENDPOINT = "https://api.smith.langchain.com";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30L);
    private static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * 规范化端点与超时默认值。
     *
     * @throws IllegalArgumentException 缺少 apiKey 时抛出
     */
    public LangSmithConfig {
        endpoint = normalizeEndpoint(endpoint);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("LANGSMITH_API_KEY is required");
        }
        requestTimeout = requestTimeout == null ? DEFAULT_TIMEOUT : requestTimeout;
        maxRetries = Math.max(0, maxRetries);
    }

    /**
     * 从合并后的环境映射构建配置；缺少 {@code LANGSMITH_API_KEY} 时返回 {@code null}。
     *
     * @param env 环境变量映射
     * @return LangSmith 配置或 {@code null}
     */
    public static LangSmithConfig fromEnvOrNull(Map<String, String> env) {
        if (env == null) {
            return null;
        }
        String apiKey = env.get("LANGSMITH_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String endpoint = env.getOrDefault("LANGSMITH_ENDPOINT", DEFAULT_ENDPOINT);
        String workspaceId = env.get("LANGSMITH_WORKSPACE_ID");
        String proxy = env.get("HTTPS_PROXY");
        String failedDir = env.get("LANGSMITH_FAILED_TRACES_DIR");
        Path failedPath = failedDir == null || failedDir.isBlank() ? null : Path.of(failedDir);
        return new LangSmithConfig(
                endpoint,
                apiKey.strip(),
                workspaceId == null || workspaceId.isBlank() ? null : workspaceId.strip(),
                proxy == null || proxy.isBlank() ? null : proxy.strip(),
                failedPath,
                DEFAULT_TIMEOUT,
                DEFAULT_MAX_RETRIES);
    }

    private static String normalizeEndpoint(String raw) {
        String value = raw == null || raw.isBlank() ? DEFAULT_ENDPOINT : raw.strip();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
