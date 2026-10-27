/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UTF-8 classpath prompt resources with literal, single-pass variable substitution.
 *
 * @since 2026
 */
public final class PromptResources {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([A-Za-z_][A-Za-z0-9_]*)}}");

    private PromptResources() {
    }

    /** Reads and caches a resource by its absolute classpath name (without a leading slash). */
    public static String load(String resource) {
        Objects.requireNonNull(resource, "resource");
        return CACHE.computeIfAbsent(resource, PromptResources::read);
    }

    /**
     * Replaces each {{name}} in the resource once, without escaping or expanding inserted values. Missing or null
     * values fail with both the resource name and variable name.
     */
    public static String render(String resource, Map<String, String> variables) {
        Matcher matcher = VARIABLE.matcher(load(resource));
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = variables == null ? null : variables.get(name);
            if (value == null) {
                throw new IllegalArgumentException(
                        "Prompt resource '" + resource + "': missing or null variable '" + name + "'");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String read(String resource) {
        try (InputStream stream = PromptResources.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalArgumentException("Prompt resource not found: " + resource);
            }
            // Prompt text must be byte-identical on every platform, so a CRLF checkout is normalized to LF.
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read prompt resource: " + resource, exception);
        }
    }
}
