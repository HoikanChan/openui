/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import com.huawei.cloudsop.genui.core.llm.extract.OpenuiCodeExtractor;

/**
 * 将 LLM 响应清洗为可解析的 openui-lang 源码。
 *
 * <p>
 * 处理流程对应 {@code parser.ts} 中 TS 侧的 {@code preprocess()}：围栏代码提取 → 去除注释 → 空白归一化。
 * 围栏处理<em>委托</em>给 {@link OpenuiCodeExtractor}（SDK 已有的 markdown 围栏逻辑）而非重新实现，以保持
 * 两者一致。
 *
 * <p>
 * {@link #process(String, ParseMode)} 在 {@link ParseMode#STREAMING} 模式下会额外运行 {@code autoClose}
 * （对应 {@code statements.ts}），修复未闭合的括号/字符串以实现部分输入的容错解析。
 *
 * @since 2026
 */
public final class OpenuiPreprocessor {

    private OpenuiPreprocessor() {
    }

    /**
     * 预处理结果：清洗后的文本，以及是否触发了自动闭合修复。
     *
     * @param text
     *            清洗后的文本
     * @param wasIncomplete
     *            是否因自动闭合而修复过输入
     *
     * @since 2026
     */
    public record Result(String text, boolean wasIncomplete) {
    }

    /**
     * 去除围栏与注释并归一化空白，不做自动闭合。等价于 TS 侧一次性清理的 {@code preprocess()}。
     *
     * @param input
     *            原始输入，可为 {@code null}
     * @return 清洗后的文本
     */
    public static String preprocess(String input) {
        if (input == null) {
            return "";
        }
        String stripped = OpenuiCodeExtractor.extract(input.trim());
        return stripComments(stripped).trim();
    }

    /**
     * 按指定模式执行完整预处理。在 {@link ParseMode#STREAMING} 下，清洗后的文本会被自动闭合，
     * {@code wasIncomplete} 反映是否执行过修复；在 {@link ParseMode#FINAL} 下文本原样返回，
     * {@code wasIncomplete} 恒为 {@code false}。
     *
     * @param input
     *            原始输入
     * @param mode
     *            解析模式
     * @return 预处理结果
     */
    public static Result process(String input, ParseMode mode) {
        String cleaned = preprocess(input);
        if (mode == ParseMode.STREAMING) {
            return autoClose(cleaned);
        }
        return new Result(cleaned, false);
    }

    /**
     * 去除出现在字符串字面量（{@code "} 与 {@code '} 两种定界符）之外的 {@code //} 与 {@code #} 行注释。
     * 对应 {@code parser.ts} 中的 {@code stripComments}。
     */
    static String stripComments(String input) {
        String[] lines = input.split("\n", -1);
        StringBuilder out = new StringBuilder(input.length());
        for (int li = 0; li < lines.length; li++) {
            if (li > 0) {
                out.append('\n');
            }
            out.append(stripCommentFromLine(lines[li]));
        }
        return out.toString();
    }

    private static String stripCommentFromLine(String line) {
        char inStr = 0; // 0 = not in string, else the quote char
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inStr != 0) {
                if (c == '\\' && i + 1 < line.length()) {
                    i++; // skip escaped char
                    continue;
                }
                if (c == inStr) {
                    inStr = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inStr = c;
                continue;
            }
            if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                return trimEnd(line.substring(0, i));
            }
            if (c == '#') {
                return trimEnd(line.substring(0, i));
            }
        }
        return line;
    }

    /**
     * 自动闭合未闭合的字符串与括号，使部分/流式输入也能无语法错误地解析。对应 {@code statements.ts} 中的
     * {@code autoClose}。
     */
    static Result autoClose(String input) {
        java.util.Deque<Character> stack = new java.util.ArrayDeque<>();
        char inStr = 0;
        boolean esc = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (esc) {
                esc = false;
                continue;
            }
            if (c == '\\' && inStr != 0) {
                esc = true;
                continue;
            }
            if (inStr != 0) {
                if (c == inStr) {
                    inStr = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inStr = c;
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                stack.push(c);
            } else if (c == ')' && !stack.isEmpty() && stack.peek() == '(') {
                stack.pop();
            } else if (c == ']' && !stack.isEmpty() && stack.peek() == '[') {
                stack.pop();
            } else if (c == '}' && !stack.isEmpty() && stack.peek() == '{') {
                stack.pop();
            }
        }

        boolean wasIncomplete = inStr != 0 || !stack.isEmpty();
        if (!wasIncomplete) {
            return new Result(input, false);
        }

        StringBuilder out = new StringBuilder(input);
        if (inStr != 0) {
            if (esc) {
                out.append('\\');
            }
            out.append(inStr);
        }
        // Deque iterates top-first, i.e. innermost bracket first — the correct close order.
        for (char open : stack) {
            out.append(open == '(' ? ')' : open == '[' ? ']' : '}');
        }
        return new Result(out.toString(), true);
    }

    private static String trimEnd(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }
}
