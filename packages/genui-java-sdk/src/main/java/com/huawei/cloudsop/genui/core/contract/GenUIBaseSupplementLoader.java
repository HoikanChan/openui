/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * Loads a {@link GenUIBaseSupplement} from JSON — the only supported construction path.
 *
 * <p>
 * Unlike {@link GenerationContractLoader} (which tolerates unknown keys because base-contract.json is
 * machine-exported), supplement JSON is hand-written by the host, so unknown top-level keys are rejected: a silently
 * ignored typo (e.g. {@code additionalRule} missing the trailing {@code s}) would make the host believe the section
 * took effect. Allowed keys: {@code supplementVersion} (informational, not reported anywhere), {@code components},
 * {@code componentGroups}, {@code examples}, {@code additionalRules} — all optional.
 */
public final class GenUIBaseSupplementLoader {
    private static final Set<String> ALLOWED_KEYS = Set.of("supplementVersion", "components", "componentGroups",
            "examples", "additionalRules");

    private GenUIBaseSupplementLoader() {
    }

    public static GenUIBaseSupplement fromJson(String json) {
        Map<String, Object> map = Json.asObject(Json.parse(json), "GenUIBaseSupplement");
        ArrayList<String> illegalKeys = new ArrayList<>();
        for (String key : map.keySet()) {
            if (!ALLOWED_KEYS.contains(key))
                illegalKeys.add(key);
        }
        if (!illegalKeys.isEmpty()) {
            throw new GenerationSdkException("Illegal top-level key(s) in base supplement: "
                    + String.join(", ", illegalKeys) + "; allowed keys are supplementVersion, components, "
                    + "componentGroups, examples, additionalRules");
        }
        return new GenUIBaseSupplement(GenerationContractLoader.componentMap(map.get("components")),
                GenerationContractLoader.componentGroups(map.get("componentGroups")),
                GenerationContractLoader.strings(map.get("examples")),
                GenerationContractLoader.strings(map.get("additionalRules")));
    }

    public static GenUIBaseSupplement fromResource(String path) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null)
            loader = GenUIBaseSupplementLoader.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream(path)) {
            if (input == null) {
                throw new GenerationSdkException("Base supplement resource not found: " + path);
            }
            return fromJson(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new GenerationSdkException("Failed to load base supplement resource: " + path, error);
        }
    }
}
