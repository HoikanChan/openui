/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.extract;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从模型原始输出中提取 openui-lang 代码：优先取语言标记为 openui、openui-lang 或 text 的围栏代码块， 其次取内容包含 {@code root =
 * Stack(} 的代码块，都没有时回退到去除首尾空白后的原文。
 *
 * @since 2026
 */
public final class OpenuiCodeExtractor {
    private static final Pattern CODE_BLOCK =
            Pattern.compile("```([^\\r\\n`]*)\\R?(.*?)```", Pattern.DOTALL);

    private OpenuiCodeExtractor() {}

    /**
     * 提取可渲染的 DSL 文本。代码块内容会去除首尾空白；同一次输出中有多个候选块时取第一个命中的。
     *
     * <p>匹配顺序：语言标记为 openui、openui-lang 或 text 的围栏块；内容包含 {@code root = Stack(} 的围栏块；两者都没有时使用原文的 trim
     * 结果。
     *
     * @param raw 模型原始输出，可为 null 或空白
     * @return 提取到的 DSL；输入为 null 或空白时返回空字符串
     */
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
            if (language.equals("openui")
                    || language.equals("openui-lang")
                    || language.equals("text")) {
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
