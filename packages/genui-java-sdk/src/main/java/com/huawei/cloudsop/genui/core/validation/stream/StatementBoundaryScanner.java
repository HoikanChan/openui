/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.stream;

import com.huawei.cloudsop.genui.core.validation.parser.OpenuiLexer;
import com.huawei.cloudsop.genui.core.validation.parser.OpenuiPreprocessor;
import com.huawei.cloudsop.genui.core.validation.parser.ParseMode;
import com.huawei.cloudsop.genui.core.validation.parser.StatementSplitter;
import com.huawei.cloudsop.genui.core.validation.parser.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * 随着原始 LLM 增量数据到达，增量识别<em>已完成</em>的 openui-lang 语句。
 *
 * <p>
 * <b>思路 —— 复用而非重造。</b>本扫描器不重新实现深度/字符串/三元/围栏规则。每次收到增量时，它都会对整个累积
 * 缓冲区运行既有的第 2 章流水线：{@link OpenuiPreprocessor#preprocess(String)}（围栏提取 + 去注释，
 * <em>不做</em>自动闭合以保留未完成状态）→ {@link OpenuiLexer#tokenize} → {@link StatementSplitter#split}。
 * 语句边界正是 {@code StatementSplitter} 已经计算好的“深度为 0 / 字符串外 / 三元表达式外”换行符，因此与解析器
 * 之间零漂移风险。
 *
 * <p>
 * <b>完整性规则。</b>由于流只会不断追加，切分器返回的语句中<em>除最后一条外</em>全部是确定性终结的（后续出现
 * 的深度 0 边界闭合了它们），因此都是完整的。最后一条语句可能仍是部分接收的尾部，因此会被搁置为待定，
 * 直到其后出现下一条语句（证明其已完整）或 {@link #drainAtEnd()} 被调用。完整语句的数量是单调递增的，
 * 因此只需记住已经暴露过多少条（{@code emittedCount}）。
 *
 * <p>
 * 返回的语句文本是<em>预处理后</em>缓冲区中该语句 {@link StatementSplitter.RawStmt#span()} 对应的裁剪片段
 * —— 即与校验器解析时相同的规范化文本，而非原始增量文本。
 *
 * @since 2026
 */
public final class StatementBoundaryScanner {

    private final StringBuilder rawBuffer = new StringBuilder();

    /** 已通过 {@link #onDelta} / {@link #drainAtEnd} 暴露出的完整语句数量。 */
    private int emittedCount = 0;

    /**
     * 追加 {@code delta} 并返回因此变为完整的语句。
     *
     * @param delta
     *            新到达的增量文本，可为 {@code null}
     * @return 新变为完整的语句列表（可能为空）
     */
    public List<String> onDelta(String delta) {
        if (delta != null && !delta.isEmpty()) {
            rawBuffer.append(delta);
        }
        return newlyComplete(false);
    }

    /**
     * 标记流结束：所有剩余的切分语句（包括此前处于待定状态的最后一条尾部）现在均确定性完整。返回尚未暴露过
     * 的语句。
     *
     * @return 尚未暴露过的语句列表
     */
    public List<String> drainAtEnd() {
        return newlyComplete(true);
    }

    /**
     * 获取目前为止累积的完整原始（未预处理）缓冲区内容。
     *
     * @return 原始缓冲区文本
     */
    public String rawBuffer() {
        return rawBuffer.toString();
    }

    /**
     * 丢弃全部已缓冲的原始输入与已暴露语句的记账信息。用于后续章节的重新请求接管：原始流中部分接收（现已
     * 放弃）的尾部不得泄漏进接续流的语句边界判定。接续流的增量数据从一个全新的缓冲区开始。
     */
    public void reset() {
        rawBuffer.setLength(0);
        emittedCount = 0;
    }

    private List<String> newlyComplete(boolean atEnd) {
        // Mid-stream: preprocess WITHOUT auto-close so an unclosed tail stays a single (still-pending)
        // statement and is not surfaced early. At end-of-stream: auto-close (STREAMING mode) so the
        // final pending tail is repaired into a well-formed, FINAL-validatable statement.
        String cleaned = atEnd
                ? OpenuiPreprocessor.process(rawBuffer.toString(), ParseMode.STREAMING).text()
                : OpenuiPreprocessor.preprocess(rawBuffer.toString());
        if (cleaned.isBlank()) {
            return List.of();
        }
        List<Token> tokens = OpenuiLexer.tokenize(cleaned);
        List<StatementSplitter.RawStmt> stmts = StatementSplitter.split(tokens);

        // All-but-last are complete mid-stream; at end, the last is complete too.
        int completeCount = atEnd ? stmts.size() : Math.max(0, stmts.size() - 1);
        if (completeCount <= emittedCount) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (int i = emittedCount; i < completeCount; i++) {
            String text = sliceOf(cleaned, stmts.get(i));
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        emittedCount = completeCount;
        return result;
    }

    /** Slice the preprocessed text for a statement's span, falling back to the whole statement. */
    private static String sliceOf(String cleaned, StatementSplitter.RawStmt stmt) {
        int start = stmt.span().startOffset();
        int end = stmt.span().endOffset();
        if (start >= 0 && end >= 0 && start <= end && end <= cleaned.length()) {
            return cleaned.substring(start, end).trim();
        }
        return cleaned.trim();
    }
}
