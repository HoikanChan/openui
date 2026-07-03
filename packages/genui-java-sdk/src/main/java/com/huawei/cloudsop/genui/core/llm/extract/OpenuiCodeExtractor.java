/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.extract;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpenuiCodeExtractor {
    private static final Pattern CODE_BLOCK = Pattern.compile("```([^\\r\\n`]*)\\R?(.*?)```", Pattern.DOTALL);

    private OpenuiCodeExtractor() {
    }

    public static String extract(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        // TODO: Compare with the original internal CodeExtractor behavior before final migration.
        String preferred = extractPreferredBlock(raw);
        if (preferred != null) {
            return preferred;
        }

        String stackBlock = extractStackBlock(raw);
        if (stackBlock != null) {
            return stackBlock;
        }

        return raw.trim();
    }

    private static String extractPreferredBlock(String raw) {
        Matcher matcher = CODE_BLOCK.matcher(raw);
        while (matcher.find()) {
            String language = matcher.group(1).trim().toLowerCase(Locale.ROOT);
            if (language.equals("openui") || language.equals("openui-lang") || language.equals("text")) {
                return matcher.group(2).trim();
            }
        }
        return null;
    }

    private static String extractStackBlock(String raw) {
        Matcher matcher = CODE_BLOCK.matcher(raw);
        while (matcher.find()) {
            String content = matcher.group(2).trim();
            if (content.contains("root = Stack(") || content.contains("root = Stack([")) {
                return content;
            }
        }
        return null;
    }
}
