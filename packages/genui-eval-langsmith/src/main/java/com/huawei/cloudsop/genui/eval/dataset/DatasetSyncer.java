/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.dataset;

import com.huawei.cloudsop.genui.eval.langsmith.LangSmithClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 幂等数据集同步器：按 {@code case_id} 匹配远端样例，按 {@code content_sha256} 决定新增、更新或跳过， 避免重复建例和破坏历史实验引用。
 *
 * @since 2026
 */
public final class DatasetSyncer {
    private final LangSmithClient client;

    /**
     * 单次数据集同步结果。
     *
     * @param datasetId 数据集 UUID
     * @param datasetName 数据集名称
     * @param created 新建样例数
     * @param updated 更新样例数
     * @param unchanged 内容未变化跳过数
     * @param exampleIdByCaseId {@code caseId -> LangSmith example UUID} 映射
     * @since 2026
     */
    public record SyncSummary(
            String datasetId,
            String datasetName,
            int created,
            int updated,
            int unchanged,
            Map<String, String> exampleIdByCaseId) {

        /** 构造不可变映射副本。 */
        public SyncSummary {
            exampleIdByCaseId =
                    exampleIdByCaseId == null ? Map.of() : Map.copyOf(exampleIdByCaseId);
        }
    }

    /**
     * 创建同步器。
     *
     * @param client LangSmith REST 客户端
     */
    public DatasetSyncer(LangSmithClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /**
     * 将本地样例幂等同步到指定 LangSmith 数据集。
     *
     * @param datasetName 数据集名称
     * @param description 数据集描述
     * @param datasetMetadata 数据集元数据
     * @param cases 待同步的本地样例列表
     * @return 同步统计与 {@code caseId -> exampleId} 映射
     */
    public SyncSummary sync(
            String datasetName,
            String description,
            Map<String, Object> datasetMetadata,
            List<DatasetCase> cases) {
        LangSmithClient.DatasetRecord dataset =
                client.upsertDataset(datasetName, description, datasetMetadata);
        List<LangSmithClient.ExampleRecord> remoteExamples = client.listExamples(dataset.id());
        Map<String, LangSmithClient.ExampleRecord> byCaseId = indexByCaseId(remoteExamples);

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        LinkedHashMap<String, String> idMap = new LinkedHashMap<>();

        for (DatasetCase local : cases) {
            LangSmithClient.ExampleRecord existing = byCaseId.get(local.caseId());
            if (existing == null) {
                LangSmithClient.ExampleRecord added =
                        client.createExample(
                                dataset.id(), local.inputs(), local.outputs(), local.metadata());
                idMap.put(local.caseId(), added.id());
                created++;
                continue;
            }
            String remoteHash = readMetadataString(existing.metadata(), "content_sha256");
            if (local.contentSha256().equals(remoteHash)) {
                idMap.put(local.caseId(), existing.id());
                unchanged++;
            } else {
                client.updateExample(
                        existing.id(), local.inputs(), local.outputs(), local.metadata());
                idMap.put(local.caseId(), existing.id());
                updated++;
            }
        }
        return new SyncSummary(dataset.id(), dataset.name(), created, updated, unchanged, idMap);
    }

    private static Map<String, LangSmithClient.ExampleRecord> indexByCaseId(
            List<LangSmithClient.ExampleRecord> examples) {
        LinkedHashMap<String, LangSmithClient.ExampleRecord> map = new LinkedHashMap<>();
        for (LangSmithClient.ExampleRecord example : examples) {
            String caseId = readMetadataString(example.metadata(), "case_id");
            if (caseId == null
                    && example.inputs() != null
                    && example.inputs().get("caseId") != null) {
                caseId = String.valueOf(example.inputs().get("caseId"));
            }
            if (caseId != null && !caseId.isBlank()) {
                map.put(caseId, example);
            }
        }
        return map;
    }

    private static String readMetadataString(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
