/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从合并后的 {@link GenerationContract} 派生出的组件目录。
 *
 * <p>
 * 对应 {@code materialize.ts} 通过 {@code ctx.cat} 查询的 TS 侧 {@code ParamMap}
 * （{@code Map<name, {params: ParamDef[]}>}）。对每个组件，本类提取有序位置参数（按
 * {@code propsSchema.properties} 的插入顺序）、哪些是必填（来自 {@code propsSchema.required}）、每个参数
 * 声明的默认值，以及用于嵌套属性校验的原始 per-param schema 片段。
 *
 * <p>
 * 仅签名组件（携带 {@code x-openui-signature} 但没有 {@code properties}）会被记录，使存在性检查得以通过，
 * 但参数列表为空且 {@code signatureOnly=true}，以便遍历器跳过属性级规则。
 *
 * @since 2026
 */
public final class ContractCatalog {

    private static final String SIGNATURE_KEY = "x-openui-signature";

    private final Map<String, ComponentDef> byName;
    private final String rootName;

    private ContractCatalog(Map<String, ComponentDef> byName, String rootName) {
        this.byName = byName;
        this.rootName = rootName;
    }

    /**
     * 从合并后的生成合约构建目录。
     *
     * @param contract
     *            合并后的生成合约，可为 {@code null}（此时得到空目录）
     * @return 组件目录
     */
    public static ContractCatalog from(GenerationContract contract) {
        Map<String, ComponentDef> byName = new LinkedHashMap<>();
        if (contract != null) {
            for (Map.Entry<String, ComponentPromptSpec> entry : contract.components().entrySet()) {
                byName.put(entry.getKey(), buildDef(entry.getKey(), entry.getValue()));
            }
        }
        String root = contract == null ? null : contract.root();
        return new ContractCatalog(Collections.unmodifiableMap(byName), root);
    }

    /**
     * 获取合约中配置的根组件名。
     *
     * @return 根组件名，可能为 {@code null}
     */
    public String root() {
        return rootName;
    }

    /**
     * 判断 {@code name} 是否为目录中已知的组件。
     *
     * @param name
     *            组件名
     * @return 是已知组件时返回 {@code true}
     */
    public boolean isKnown(String name) {
        return byName.containsKey(name);
    }

    /**
     * 获取 {@code name} 对应的组件定义。
     *
     * @param name
     *            组件名
     * @return 组件定义，不是目录组件时为 {@code null}
     */
    public ComponentDef get(String name) {
        return byName.get(name);
    }

    @SuppressWarnings("unchecked")
    private static ComponentDef buildDef(String name, ComponentPromptSpec spec) {
        Map<String, Object> schema = spec.propsSchema();

        // Signature-only (no structured properties) → existence-only, no prop checks.
        Object propertiesRaw = schema.get("properties");
        if (!(propertiesRaw instanceof Map<?, ?> propertiesMap) || propertiesMap.isEmpty()) {
            boolean signatureOnly = schema.containsKey(SIGNATURE_KEY);
            return new ComponentDef(name, List.of(), signatureOnly);
        }

        List<String> required = readRequired(schema.get("required"));

        List<ParamDef> params = new ArrayList<>();
        for (Map.Entry<?, ?> entry : propertiesMap.entrySet()) {
            String paramName = String.valueOf(entry.getKey());
            Object paramSchema = entry.getValue();
            boolean isRequired = required.contains(paramName);
            boolean hasDefault = false;
            Object defaultValue = null;
            if (paramSchema instanceof Map<?, ?> ps && ps.containsKey("default")) {
                hasDefault = true;
                defaultValue = ((Map<String, Object>) ps).get("default");
            }
            params.add(new ParamDef(paramName, isRequired, hasDefault, defaultValue, paramSchema));
        }
        return new ComponentDef(name, params, false);
    }

    private static List<String> readRequired(Object requiredRaw) {
        if (!(requiredRaw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            out.add(String.valueOf(item));
        }
        return out;
    }
}
