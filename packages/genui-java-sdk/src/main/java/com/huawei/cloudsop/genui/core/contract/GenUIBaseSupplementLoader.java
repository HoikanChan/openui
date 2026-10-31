/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads a {@link GenUIBaseSupplement} from JSON — the only supported construction path.
 *
 * <p>Unlike {@link GenerationContractLoader} (which tolerates unknown keys because
 * base-contract.json is machine-exported), supplement JSON is hand-written by the host, so unknown
 * top-level keys are rejected: a silently ignored typo (e.g. {@code additionalRule} missing the
 * trailing {@code s}) would make the host believe the section took effect. Allowed keys: {@code
 * supplementVersion} (informational, not reported anywhere), {@code components}, {@code
 * componentGroups}, {@code examples}, {@code additionalRules}, {@code capabilityGaps} — all
 * optional.
 *
 * @since 2026
 */
public final class GenUIBaseSupplementLoader {
    private static final Set<String> ALLOWED_KEYS =
            Set.of(
                    "supplementVersion",
                    "components",
                    "componentGroups",
                    "examples",
                    "additionalRules",
                    "capabilityGaps");

    private GenUIBaseSupplementLoader() {}

    /**
     * 解析补充 JSON 文本；出现未声明的顶层键时直接失败。
     *
     * @param json 补充文档 JSON 文本
     * @return 解析出的补充对象
     * @throws GenerationSdkException 当 JSON 含未声明的顶层键时
     */
    public static GenUIBaseSupplement fromJson(String json) {
        Map<String, Object> map = Json.asObject(Json.parse(json), "GenUIBaseSupplement");
        ArrayList<String> illegalKeys = new ArrayList<>();
        for (String key : map.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                illegalKeys.add(key);
            }
        }
        if (!illegalKeys.isEmpty()) {
            throw new GenerationSdkException(
                    "Illegal top-level key(s) in base supplement: "
                            + String.join(", ", illegalKeys)
                            + "; allowed keys are supplementVersion, components, "
                            + "componentGroups, examples, additionalRules, capabilityGaps");
        }
        Map<String, ComponentPromptSpec> components =
                GenerationContractLoader.componentMap(map.get("components"));
        List<CapabilityGapSpec> capabilityGaps =
                GenerationContractLoader.capabilityGaps(map.get("capabilityGaps"));
        GenerationContractLoader.validateCapabilityGaps(
                capabilityGaps, components.keySet(), List.of(), "base supplement");
        return new GenUIBaseSupplement(
                components,
                GenerationContractLoader.componentGroups(map.get("componentGroups")),
                GenerationContractLoader.strings(map.get("examples")),
                GenerationContractLoader.strings(map.get("additionalRules")),
                capabilityGaps);
    }

    /**
     * 从 classpath 资源加载补充 JSON。
     *
     * <p>优先使用线程上下文类加载器，缺失时回退到本类的类加载器。
     *
     * @param path classpath 资源路径
     * @return 解析出的补充对象
     * @throws GenerationSdkException 当资源不存在、读取失败或 JSON 解析失败时
     */
    public static GenUIBaseSupplement fromResource(String path) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = GenUIBaseSupplementLoader.class.getClassLoader();
        }
        try (InputStream input = loader.getResourceAsStream(path)) {
            if (input == null) {
                throw new GenerationSdkException("Base supplement resource not found: " + path);
            }
            return fromJson(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new GenerationSdkException(
                    "Failed to load base supplement resource: " + path, error);
        }
    }
}
