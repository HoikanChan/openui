/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.langsmith;

import com.huawei.cloudsop.genui.core.Json;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 JDK 21 {@link HttpClient} 与 fastjson2 的轻量 LangSmith REST API v1 客户端。 覆盖
 * datasets、examples、sessions（experiments）、runs/batch 和 feedback 五类端点，带 429/5xx 退避重试。
 *
 * @since 2026
 */
public final class LangSmithClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15L);
    private static final int PAGE_LIMIT = 100;
    private static final int HTTP_CLASS_DIVISOR = 100;
    private static final int HTTP_SUCCESS_CLASS = 2;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVER_ERROR_MIN = 500;
    private static final long INITIAL_BACKOFF_MS = 250L;
    private static final int ERROR_SNIPPET_LIMIT = 600;
    private static final int MAX_SCORE_SCALE = 4;

    private final LangSmithConfig config;
    private final HttpClient httpClient;

    /**
     * LangSmith 数据集摘要。
     *
     * @param id 数据集 UUID
     * @param name 数据集名称
     * @since 2026
     */
    public record DatasetRecord(String id, String name) {}

    /**
     * LangSmith 数据集样例记录。
     *
     * @param id 样例 UUID
     * @param datasetId 所属数据集 UUID
     * @param inputs 样例输入 JSON 对象
     * @param outputs 样例期望输出 JSON 对象，可为 null
     * @param metadata 样例元数据，可为 null
     * @since 2026
     */
    public record ExampleRecord(
            String id,
            String datasetId,
            Map<String, Object> inputs,
            Map<String, Object> outputs,
            Map<String, Object> metadata) {}

    /**
     * LangSmith 实验会话摘要。
     *
     * @param id 会话（实验）UUID
     * @param name 实验名称
     * @since 2026
     */
    public record SessionRecord(String id, String name) {}

    /**
     * 使用默认 JDK HttpClient 构造客户端。
     *
     * @param config LangSmith 连接配置
     */
    public LangSmithClient(LangSmithConfig config) {
        this(config, buildHttpClient(Objects.requireNonNull(config, "config")));
    }

    LangSmithClient(LangSmithConfig config, HttpClient httpClient) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    /**
     * 返回当前配置。
     *
     * @return LangSmith 配置
     */
    public LangSmithConfig config() {
        return config;
    }

    /**
     * 按名称查找数据集，不存在则创建。
     *
     * @param name 数据集唯一名称
     * @param description 数据集描述
     * @param metadata 可选元数据
     * @return 已存在或新建的数据集记录
     */
    public DatasetRecord upsertDataset(
            String name, String description, Map<String, Object> metadata) {
        DatasetRecord existing = findDatasetByName(name);
        if (existing != null) {
            return existing;
        }
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", description == null ? "" : description);
        body.put("data_type", "kv");
        if (metadata != null && !metadata.isEmpty()) {
            body.put("extra", Map.of("metadata", metadata));
        }
        Map<String, Object> created =
                Json.asObject(
                        Json.parse(send("POST", "/api/v1/datasets", Json.stringify(body))),
                        "dataset");
        return new DatasetRecord(str(created.get("id")), str(created.get("name")));
    }

    /**
     * 按精确名称查询数据集，不存在时返回 {@code null}。
     *
     * @param name 数据集名称
     * @return 数据集记录或 {@code null}
     */
    public DatasetRecord findDatasetByName(String name) {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
        String raw = send("GET", "/api/v1/datasets?name=" + encoded + "&limit=10", null);
        List<Object> list = Json.asList(Json.parse(raw), "datasets");
        for (Object item : list) {
            Map<String, Object> obj = Json.asObject(item, "dataset");
            if (name.equals(str(obj.get("name")))) {
                return new DatasetRecord(str(obj.get("id")), name);
            }
        }
        return null;
    }

    /**
     * 分页拉取指定数据集下的全部样例。
     *
     * @param datasetId 数据集 UUID
     * @return 全部样例列表
     */
    public List<ExampleRecord> listExamples(String datasetId) {
        List<ExampleRecord> all = new ArrayList<>();
        int offset = 0;
        String encoded = URLEncoder.encode(datasetId, StandardCharsets.UTF_8);
        while (true) {
            String path =
                    "/api/v1/examples?dataset="
                            + encoded
                            + "&limit="
                            + PAGE_LIMIT
                            + "&offset="
                            + offset;
            List<Object> page = Json.asList(Json.parse(send("GET", path, null)), "examples");
            for (Object item : page) {
                all.add(parseExample(Json.asObject(item, "example")));
            }
            if (page.size() < PAGE_LIMIT) {
                break;
            }
            offset += page.size();
        }
        return all;
    }

    /**
     * 在数据集中新建一条样例。
     *
     * @param datasetId 数据集 UUID
     * @param inputs 样例输入
     * @param outputs 样例期望输出，可为 null
     * @param metadata 样例元数据，可为 null
     * @return 创建后的样例记录
     */
    public ExampleRecord createExample(
            String datasetId,
            Map<String, Object> inputs,
            Map<String, Object> outputs,
            Map<String, Object> metadata) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("dataset_id", datasetId);
        body.put("inputs", inputs == null ? Map.of() : inputs);
        if (outputs != null) {
            body.put("outputs", outputs);
        }
        if (metadata != null && !metadata.isEmpty()) {
            body.put("metadata", metadata);
        }
        Map<String, Object> created =
                Json.asObject(
                        Json.parse(send("POST", "/api/v1/examples", Json.stringify(body))),
                        "example");
        return parseExample(created);
    }

    /**
     * 更新已有样例的输入、输出与元数据。
     *
     * @param exampleId 样例 UUID
     * @param inputs 新的输入
     * @param outputs 新的期望输出，可为 null
     * @param metadata 新的元数据，可为 null
     */
    public void updateExample(
            String exampleId,
            Map<String, Object> inputs,
            Map<String, Object> outputs,
            Map<String, Object> metadata) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", inputs == null ? Map.of() : inputs);
        if (outputs != null) {
            body.put("outputs", outputs);
        }
        if (metadata != null) {
            body.put("metadata", metadata);
        }
        send("PATCH", "/api/v1/examples/" + exampleId, Json.stringify(body));
    }

    /**
     * 创建一次绑定数据集的实验会话（LangSmith Experiment = Session）。
     *
     * @param name 实验名称
     * @param referenceDatasetId 关联的数据集 UUID，可为 null
     * @param metadata 实验元数据（如 git_commit、contract_version、model）
     * @return 创建后的会话记录
     */
    public SessionRecord createExperimentSession(
            String name, String referenceDatasetId, Map<String, Object> metadata) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("start_time", Instant.now().toString());
        if (referenceDatasetId != null && !referenceDatasetId.isBlank()) {
            body.put("reference_dataset_id", referenceDatasetId);
        }
        if (metadata != null && !metadata.isEmpty()) {
            body.put("extra", Map.of("metadata", metadata));
        }
        Map<String, Object> created =
                Json.asObject(
                        Json.parse(send("POST", "/api/v1/sessions", Json.stringify(body))),
                        "session");
        return new SessionRecord(str(created.get("id")), str(created.get("name")));
    }

    /**
     * 标记实验会话结束时间并补充终态元数据。
     *
     * @param sessionId 会话 UUID
     * @param endTime 结束时刻
     * @param metadata 可选更新元数据
     */
    public void finishSession(String sessionId, Instant endTime, Map<String, Object> metadata) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("end_time", (endTime == null ? Instant.now() : endTime).toString());
        if (metadata != null && !metadata.isEmpty()) {
            body.put("extra", Map.of("metadata", metadata));
        }
        send("PATCH", "/api/v1/sessions/" + sessionId, Json.stringify(body));
    }

    /**
     * 批量上报 Run 创建与更新事件。
     *
     * @param postRuns 新建 Run 负载列表
     * @param patchRuns 更新 Run 负载列表
     */
    public void ingestRunsBatch(
            List<Map<String, Object>> postRuns, List<Map<String, Object>> patchRuns) {
        if ((postRuns == null || postRuns.isEmpty())
                && (patchRuns == null || patchRuns.isEmpty())) {
            return;
        }
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        if (postRuns != null && !postRuns.isEmpty()) {
            body.put("post", postRuns);
        }
        if (patchRuns != null && !patchRuns.isEmpty()) {
            body.put("patch", patchRuns);
        }
        send("POST", "/api/v1/runs/batch", Json.stringify(body));
    }

    /**
     * 提交单条行级或实验级（Summary）Feedback。
     *
     * @param payload feedback 负载（行级含 {@code run_id}，实验级仅含 {@code session_id}）
     */
    public void createFeedback(Map<String, Object> payload) {
        send("POST", "/api/v1/feedback", Json.stringify(payload));
    }

    /**
     * 提交行级数值型 Feedback。
     *
     * @param runId 关联 Run UUID
     * @param sessionId 实验会话 UUID，可为 null
     * @param key 指标键名
     * @param score 数值分数
     * @param comment 可选文字说明
     */
    public void createRunScoreFeedback(
            String runId, String sessionId, String key, double score, String comment) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("run_id", runId);
        if (sessionId != null) {
            body.put("session_id", sessionId);
        }
        body.put("key", key);
        body.put("score", roundScore(score));
        if (comment != null && !comment.isBlank()) {
            body.put("comment", comment);
        }
        createFeedback(body);
    }

    /**
     * 提交行级分类型 Feedback（如状态标签）。
     *
     * @param runId 关联 Run UUID
     * @param sessionId 实验会话 UUID，可为 null
     * @param key 指标键名
     * @param value 分类字符串值
     * @param comment 可选文字说明
     */
    public void createRunValueFeedback(
            String runId, String sessionId, String key, String value, String comment) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("run_id", runId);
        if (sessionId != null) {
            body.put("session_id", sessionId);
        }
        body.put("key", key);
        body.put("value", value);
        if (comment != null && !comment.isBlank()) {
            body.put("comment", comment);
        }
        createFeedback(body);
    }

    /**
     * 提交实验级汇总 Feedback（不传 {@code run_id}，直接绑定 {@code session_id}）。
     *
     * @param sessionId 实验会话 UUID
     * @param key 汇总指标名（如 {@code main_repair_rate}）
     * @param score 数值分数（自动四舍五入至最多 4 位小数）
     * @param comment 可选口径说明
     */
    public void createSummaryFeedback(String sessionId, String key, double score, String comment) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("session_id", sessionId);
        body.put("key", key);
        body.put("score", roundScore(score));
        if (comment != null && !comment.isBlank()) {
            body.put("comment", comment);
        }
        createFeedback(body);
    }

    static double roundScore(double score) {
        if (!Double.isFinite(score)) {
            return 0.0;
        }
        return BigDecimal.valueOf(score)
                .setScale(MAX_SCORE_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String send(String method, String path, String jsonBody) {
        String url = config.endpoint() + path;
        int maxAttempts = config.maxRetries() + 1;
        long backoffMs = INITIAL_BACKOFF_MS;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpRequest request = buildRequest(method, url, jsonBody);
            try {
                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status / HTTP_CLASS_DIVISOR == HTTP_SUCCESS_CLASS) {
                    return response.body();
                }
                if (isRetryableStatus(status) && attempt < maxAttempts) {
                    sleepQuietly(backoffMs);
                    backoffMs *= 2L;
                    continue;
                }
                throw new LangSmithException(
                        "LangSmith "
                                + method
                                + " "
                                + path
                                + " failed with HTTP "
                                + status
                                + ": "
                                + snippet(response.body()),
                        status);
            } catch (IOException error) {
                if (attempt < maxAttempts) {
                    sleepQuietly(backoffMs);
                    backoffMs *= 2L;
                    continue;
                }
                throw new LangSmithException(
                        "LangSmith " + method + " " + path + " I/O error: " + error.getMessage(),
                        error);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new LangSmithException("LangSmith request interrupted", error);
            }
        }
        throw new LangSmithException("LangSmith request exhausted retries", -1);
    }

    private HttpRequest buildRequest(String method, String url, String jsonBody) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(config.requestTimeout())
                        .header("x-api-key", config.apiKey())
                        .header("Accept", "application/json");
        if (config.workspaceId() != null) {
            builder.header("X-Tenant-Id", config.workspaceId());
        }
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    private static HttpClient buildHttpClient(LangSmithConfig config) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT);
        URI target = URI.create(config.endpoint());
        if (!SharedOpenAiTransport.isLoopbackHost(target.getHost())) {
            ProxySelector proxy = SharedOpenAiTransport.resolveProxy(config.httpsProxy());
            if (proxy != null) {
                builder.proxy(proxy);
            }
        }
        return builder.build();
    }

    private static ExampleRecord parseExample(Map<String, Object> raw) {
        Map<String, Object> inputs = asOptionalMap(raw.get("inputs"));
        Map<String, Object> outputs = asOptionalMap(raw.get("outputs"));
        Map<String, Object> metadata = asOptionalMap(raw.get("metadata"));
        return new ExampleRecord(
                str(raw.get("id")), str(raw.get("dataset_id")), inputs, outputs, metadata);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asOptionalMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static boolean isRetryableStatus(int status) {
        return status == HTTP_TOO_MANY_REQUESTS || status >= HTTP_SERVER_ERROR_MIN;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= ERROR_SNIPPET_LIMIT
                ? text
                : text.substring(0, ERROR_SNIPPET_LIMIT) + "...";
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
