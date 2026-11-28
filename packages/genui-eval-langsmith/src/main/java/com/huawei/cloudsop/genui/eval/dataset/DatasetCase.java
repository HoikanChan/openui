/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.dataset;

import com.huawei.cloudsop.genui.core.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 同步到 LangSmith Dataset 的标准样例模型。用 {@code caseId} 作为幂等主键，用 {@code contentSha256} 判断是否需要更新。
 *
 * @param caseId 全语料唯一的样例 ID
 * @param inputs LangSmith example {@code inputs}（含显式 {@code hasDataModel} 保留 null 与空 map 区分）
 * @param outputs 参考输出或人工标注，可为 null
 * @param metadata 样例元数据，自动附加 {@code case_id} 与 {@code content_sha256}
 * @param contentSha256 输入与参考输出的规范 SHA-256 摘要
 * @since 2026
 */
public record DatasetCase(
        String caseId,
        Map<String, Object> inputs,
        Map<String, Object> outputs,
        Map<String, Object> metadata,
        String contentSha256) {

    /**
     * 构造并自动计算 {@code contentSha256}，把 {@code case_id} 与 {@code content_sha256} 写入元数据副本。
     *
     * @param caseId 样例 ID，非空
     * @param inputs 输入对象
     * @param outputs 输出对象，可为 null
     * @param extraMetadata 额外元数据，可为 null
     * @return 不可变语义的标准样例
     */
    public static DatasetCase of(
            String caseId,
            Map<String, Object> inputs,
            Map<String, Object> outputs,
            Map<String, Object> extraMetadata) {
        Objects.requireNonNull(caseId, "caseId");
        Map<String, Object> safeInputs = inputs == null ? Map.of() : new LinkedHashMap<>(inputs);
        Map<String, Object> safeOutputs = outputs == null ? null : new LinkedHashMap<>(outputs);
        LinkedHashMap<String, Object> hashTarget = new LinkedHashMap<>();
        hashTarget.put("inputs", safeInputs);
        hashTarget.put("outputs", safeOutputs);
        String sha = sha256Hex(Json.stringify(hashTarget));

        LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
        if (extraMetadata != null) {
            meta.putAll(extraMetadata);
        }
        meta.put("case_id", caseId);
        meta.put("content_sha256", sha);
        return new DatasetCase(caseId, safeInputs, safeOutputs, meta, sha);
    }

    /**
     * 计算文本的 UTF-8 SHA-256 小写十六进制摘要。
     *
     * @param text 原始文本
     * @return 64 位小写十六进制哈希
     * @throws IllegalStateException JVM 缺少 SHA-256 算法时抛出
     */
    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes =
                    digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
