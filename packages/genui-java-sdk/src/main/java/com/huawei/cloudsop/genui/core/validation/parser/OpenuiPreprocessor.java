/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
 * <p>轮次边界使用相同的扫描结果，计算发送给前端的闭合符。
 *
 * @since 2026
 */
public final class OpenuiPreprocessor {

    private OpenuiPreprocessor() {}

    /**
     * 一次扫描的产物：去注释后的文本、结束时仍未闭合的引号，以及由内到外排列的待闭合括号。
     *
     * @since 2026
     */
    private record Scan(String cleaned, char openQuote, String closers) {

        /**
         * 以 {@code text} 为最终文本计算自动闭合后缀。转义状态不单独保存：字符串仍未闭合时，末尾连续反斜杠为奇数 即表示最后一个反斜杠仍在转义。
         *
         * @param text 最终文本
         * @return 需要追加的自动闭合后缀
         */
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
     *
     * @param input 原始输入文本
     * @return 去注释后的文本与自动闭合状态
     */
    private static Scan scan(String input) {
        ScanState state = new ScanState(input);
        int i = 0;
        while (i < input.length()) {
            if (startsLineComment(input, i, state.commentQuote)) {
                i = skipLineComment(input, i, state);
                continue;
            }
            char c = input.charAt(i);
            state.out.append(c);
            advanceCommentState(state, input, c, i);
            advanceAutoCloseState(state, c);
            i++;
        }
        return new Scan(state.out.toString(), state.autoQuote, buildClosers(state.brackets));
    }

    // 扫描过程中的可变状态：去注释输出缓冲、括号栈与引号/转义状态。
    private static final class ScanState {
        private final StringBuilder out;
        private final Deque<Character> brackets = new ArrayDeque<>();
        private char commentQuote;
        private boolean commentEscaped;
        private char autoQuote;
        private boolean autoEscaped;
        private int lineStart;

        private ScanState(String input) {
            this.out = new StringBuilder(input.length());
        }
    }

    /**
     * 当前位置是否开始一个行注释（{@code #} 或 {@code //}），且不在字符串内。
     *
     * @param input 输入文本
     * @param i 当前下标
     * @param commentQuote 当前去注释引号状态
     * @return 当前位置开始行注释时为 {@code true}
     */
    private static boolean startsLineComment(String input, int i, char commentQuote) {
        char c = input.charAt(i);
        return commentQuote == 0
                && (c == '#' || (c == '/' && i + 1 < input.length() && input.charAt(i + 1) == '/'));
    }

    /**
     * 跳过一个行注释，返回下一轮扫描位置。
     *
     * @param input 输入文本
     * @param i 行注释起始下标
     * @param state 当前扫描状态
     * @return 下一轮扫描位置
     */
    private static int skipLineComment(String input, int i, ScanState state) {
        char c = input.charAt(i);
        trimTrailingWhitespace(state.out, state.lineStart);
        if (c == '/') {
            i++;
        }
        while (i + 1 < input.length() && input.charAt(i + 1) != '\n') {
            i++;
        }
        return i + 1;
    }

    /**
     * 按行维护去注释用的引号状态；每行重置。
     *
     * @param state 当前扫描状态
     * @param input 输入文本
     * @param c 当前字符
     * @param i 当前下标
     */
    private static void advanceCommentState(ScanState state, String input, char c, int i) {
        if (c == '\n') {
            state.commentQuote = 0;
            state.commentEscaped = false;
            state.lineStart = state.out.length();
        } else if (state.commentQuote != 0) {
            if (state.commentEscaped) {
                state.commentEscaped = false;
            } else if (c == '\\') {
                state.commentEscaped = i + 1 < input.length() && input.charAt(i + 1) != '\n';
            } else if (c == state.commentQuote) {
                state.commentQuote = 0;
            }
        } else if (c == '"' || c == '\'') {
            state.commentQuote = c;
        }
    }

    /**
     * 维护跨行自动闭合用的引号与括号状态；注释内容不进入该状态。
     *
     * @param state 当前扫描状态
     * @param c 当前字符
     */
    private static void advanceAutoCloseState(ScanState state, char c) {
        if (state.autoEscaped) {
            state.autoEscaped = false;
        } else if (c == '\\' && state.autoQuote != 0) {
            state.autoEscaped = true;
        } else if (state.autoQuote != 0) {
            if (c == state.autoQuote) {
                state.autoQuote = 0;
            }
        } else if (c == '"' || c == '\'') {
            state.autoQuote = c;
        } else if (c == '(' || c == '[' || c == '{') {
            state.brackets.push(c);
        } else if (c == ')' && !state.brackets.isEmpty() && state.brackets.peek() == '(') {
            state.brackets.pop();
        } else if (c == ']' && !state.brackets.isEmpty() && state.brackets.peek() == '[') {
            state.brackets.pop();
        } else if (c == '}' && !state.brackets.isEmpty() && state.brackets.peek() == '{') {
            state.brackets.pop();
        }
    }

    /**
     * 由内到外生成未闭合括号对应的闭合符。
     *
     * @param brackets 未闭合括号栈
     * @return 由内到外排列的闭合符
     */
    private static String buildClosers(Deque<Character> brackets) {
        StringBuilder closers = new StringBuilder(brackets.size());
        for (char open : brackets) {
            closers.append(open == '(' ? ')' : open == '[' ? ']' : '}');
        }
        return closers.toString();
    }

    private static void trimTrailingWhitespace(StringBuilder out, int lineStart) {
        int keep = out.length();
        while (keep > lineStart && Character.isWhitespace(out.charAt(keep - 1))) {
            keep--;
        }
        out.setLength(keep);
    }
}
