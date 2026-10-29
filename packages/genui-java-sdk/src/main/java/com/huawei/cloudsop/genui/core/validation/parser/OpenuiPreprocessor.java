/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import com.huawei.cloudsop.genui.core.llm.extract.OpenuiCodeExtractor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 将 LLM 响应清洗为可解析的 openui-lang 源码。
 *
 * <p>处理流程对应 {@code parser.ts} 中 TS 侧的 {@code preprocess()}：围栏代码提取 → 去除注释 → 空白归一化。 围栏处理<em>委托</em>给
 * {@link OpenuiCodeExtractor}（SDK 已有的 markdown 围栏逻辑）而非重新实现，以保持 两者一致。
 *
 * <p>去注释与自动闭合共用 {@link #scan(String)} 的一次字符扫描：去注释按行维护引号状态（字符串不跨行），自动闭合按整个文本
 * 维护引号状态（未闭合字符串延续到下一行），两种状态彼此独立。{@link #process(String, ParseMode)} 在 {@link ParseMode#STREAMING}
 * 模式下使用扫描结果补齐未闭合的括号/字符串（对应 {@code statements.ts} 中的 {@code autoClose}），实现部分输入的容错解析。
 *
 * @since 2026
 */
public final class OpenuiPreprocessor {

    private OpenuiPreprocessor() {}

    /**
     * 预处理结果：清洗后的文本，以及是否触发了自动闭合修复。
     *
     * @param text 清洗后的文本
     * @param wasIncomplete 是否因自动闭合而修复过输入
     * @since 2026
     */
    public record Result(String text, boolean wasIncomplete) {}

    /** 一次扫描的产物：去注释后的文本、结束时仍未闭合的引号，以及由内到外排列的待闭合括号。 */
    private record Scan(String cleaned, char openQuote, String closers) {

        boolean wasIncomplete() {
            return openQuote != 0 || !closers.isEmpty();
        }

        /** 以 {@code text} 为最终文本计算自动闭合后缀。转义状态不单独保存：字符串仍未闭合时，末尾连续反斜杠为奇数 即表示最后一个反斜杠仍在转义。 */
        String closeSuffix(String text) {
            StringBuilder suffix = new StringBuilder();
            if (openQuote != 0) {
                if (endsWithDanglingEscape(text)) {
                    suffix.append('\\');
                }
                suffix.append(openQuote);
            }
            return suffix.append(closers).toString();
        }

        private static boolean endsWithDanglingEscape(String text) {
            int backslashes = 0;
            for (int i = text.length() - 1; i >= 0 && text.charAt(i) == '\\'; i--) {
                backslashes++;
            }
            return (backslashes & 1) == 1;
        }
    }

    /**
     * 去除围栏与注释并归一化空白，不做自动闭合。等价于 TS 侧一次性清理的 {@code preprocess()}。
     *
     * @param input 原始输入，可为 {@code null}
     * @return 清洗后的文本
     */
    public static String preprocess(String input) {
        return scan(stripFences(input)).cleaned().trim();
    }

    /**
     * 按指定模式执行完整预处理。在 {@link ParseMode#STREAMING} 下，清洗后的文本会被自动闭合， {@code wasIncomplete} 反映是否执行过修复；在
     * {@link ParseMode#FINAL} 下文本原样返回， {@code wasIncomplete} 恒为 {@code false}。
     *
     * @param input 原始输入
     * @param mode 解析模式
     * @return 预处理结果
     */
    public static Result process(String input, ParseMode mode) {
        Scan scan = scan(stripFences(input));
        String cleaned = scan.cleaned().trim();
        if (mode == ParseMode.STREAMING) {
            return new Result(cleaned + scan.closeSuffix(cleaned), scan.wasIncomplete());
        }
        return new Result(cleaned, false);
    }

    /**
     * Close a model round at its exact raw lexical state before another request is appended. Unlike
     * validation preprocessing, this preserves whitespace inside an unfinished string. A trailing
     * line comment must end before any generated closing delimiters are written.
     *
     * @param raw model output already forwarded to the frontend
     * @return only the closing suffix and statement separator, or an empty string
     */
    public static String streamRoundBoundary(String raw) {
        String input = raw == null ? "" : raw;
        Scan scan = scan(input);
        String cleaned = scan.cleaned();
        String suffix = scan.closeSuffix(cleaned);
        boolean trailingComment =
                input.substring(input.lastIndexOf('\n') + 1).length()
                        > cleaned.substring(cleaned.lastIndexOf('\n') + 1).length();
        if (suffix.isEmpty() && input.endsWith("\n")) {
            return "";
        }
        return (trailingComment ? "\n" : "") + suffix + "\n";
    }

    private static String stripFences(String input) {
        if (input == null) {
            return "";
        }
        return OpenuiCodeExtractor.extract(input.trim());
    }

    /**
     * 单次扫描同时完成去注释与自动闭合分析。
     *
     * <p>{@code commentQuote} 是去注释用的按行引号状态：每行重置，与 {@code parser.ts} 的 {@code stripComments} 一致；
     * {@code autoQuote}/{@code brackets} 是自动闭合用的全局状态：字符串可跨行，只在引号外统计括号。注释内容不进入
     * 自动闭合状态，等价于先去掉注释再对结果做自动闭合。
     */
    private static Scan scan(String input) {
        StringBuilder out = new StringBuilder(input.length());
        Deque<Character> brackets = new ArrayDeque<>();

        char commentQuote = 0;
        boolean commentEscaped = false;
        char autoQuote = 0;
        boolean autoEscaped = false;
        int lineStart = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (commentQuote == 0
                    && (c == '#'
                            || (c == '/'
                                    && i + 1 < input.length()
                                    && input.charAt(i + 1) == '/'))) {
                trimTrailingWhitespace(out, lineStart);
                if (c == '/') {
                    i++;
                }
                while (i + 1 < input.length() && input.charAt(i + 1) != '\n') {
                    i++;
                }
                continue;
            }

            out.append(c);

            if (c == '\n') {
                commentQuote = 0;
                commentEscaped = false;
                lineStart = out.length();
            } else if (commentQuote != 0) {
                if (commentEscaped) {
                    commentEscaped = false;
                } else if (c == '\\') {
                    commentEscaped = i + 1 < input.length() && input.charAt(i + 1) != '\n';
                } else if (c == commentQuote) {
                    commentQuote = 0;
                }
            } else if (c == '"' || c == '\'') {
                commentQuote = c;
            }

            if (autoEscaped) {
                autoEscaped = false;
            } else if (c == '\\' && autoQuote != 0) {
                autoEscaped = true;
            } else if (autoQuote != 0) {
                if (c == autoQuote) {
                    autoQuote = 0;
                }
            } else if (c == '"' || c == '\'') {
                autoQuote = c;
            } else if (c == '(' || c == '[' || c == '{') {
                brackets.push(c);
            } else if (c == ')' && !brackets.isEmpty() && brackets.peek() == '(') {
                brackets.pop();
            } else if (c == ']' && !brackets.isEmpty() && brackets.peek() == '[') {
                brackets.pop();
            } else if (c == '}' && !brackets.isEmpty() && brackets.peek() == '{') {
                brackets.pop();
            }
        }

        StringBuilder closers = new StringBuilder(brackets.size());
        for (char open : brackets) {
            closers.append(open == '(' ? ')' : open == '[' ? ']' : '}');
        }
        return new Scan(out.toString(), autoQuote, closers.toString());
    }

    private static void trimTrailingWhitespace(StringBuilder out, int lineStart) {
        int keep = out.length();
        while (keep > lineStart && Character.isWhitespace(out.charAt(keep - 1))) {
            keep--;
        }
        out.setLength(keep);
    }
}
