package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.postprocess.DslEscapes;

/**
 * Markdown渲染包裹器，将模型生成的 markdown 内容包裹为
 * {@code root = Stack([MarkDownRenderer("markdown_content")])}。
 *
 * <p>markdown 内容用双引号包裹，转义双引号和反斜杠（{@link DslEscapes#escapeString}），
 * 换行符原样保留。
 *
 * <p>当请求的 {@code ext.scenario} 为 {@code Canvas} 或 {@code Preview} 时，
 * 额外包裹为 {@code root = <Scenario>Card([Stack([MarkDownRenderer("...")])])}。
 *
 * <p>流式模式下，第一个 delta 前缀补 {@link #prefix}，
 * 最后一个 delta 后缀补 {@link #suffix}。由于 SDK 无法预知 LLM 何时结束流式输出，
 * 后缀在 {@link GenUiGenerator} 的流式完成回调中追加。
 *
 * @since 2026-09-08
 */
final class MarkdownRenderer {

    /** 内层表达式开头：Stack([MarkDownRenderer(" */
    private static final String INNER_OPEN = "Stack([MarkDownRenderer(\"";

    /** 内层表达式结尾：")]) */
    private static final String INNER_CLOSE = "\")])";

    private MarkdownRenderer() {
    }

    /**
     * 根据请求场景生成流式前缀。
     *
     * @param request 生成请求
     * @return 前缀字符串
     */
    static String prefix(UiGenerationRequest request) {
        String scenario = resolveScenario(request);
        if (scenario != null) {
            return "root = " + scenario + "Card([" + INNER_OPEN;
        }
        return "root = " + INNER_OPEN;
    }

    /**
     * 根据请求场景生成流式后缀。
     *
     * @param request 生成请求
     * @return 后缀字符串
     */
    static String suffix(UiGenerationRequest request) {
        String scenario = resolveScenario(request);
        if (scenario != null) {
            return INNER_CLOSE + "])";
        }
        return INNER_CLOSE;
    }

    /**
     * 将完整的 markdown 内容包裹为 DSL 字符串。
     * 转义双引号和反斜杠，换行符原样保留。
     *
     * @param request 生成请求
     * @param markdown 模型生成的 markdown 内容
     * @return 包裹后的 DSL 字符串
     */
    static String wrap(UiGenerationRequest request, String markdown) {
        String content = markdown == null || markdown.isBlank()
            ? "" : DslEscapes.escapeString(markdown.strip());
        String scenario = resolveScenario(request);
        if (scenario != null) {
            return "root = " + scenario + "Card([" + INNER_OPEN + content + INNER_CLOSE + "])";
        }
        return "root = " + INNER_OPEN + content + INNER_CLOSE;
    }

    /**
     * 转义流式增量中的双引号和反斜杠，换行符原样保留。
     *
     * @param delta 原始增量文本
     * @return 转义后的文本
     */
    static String escapeDelta(String delta) {
        return DslEscapes.escapeString(delta);
    }

    /**
     * 从请求的 ext.scenario 字段解析场景类型。
     *
     * @param request 生成请求
     * @return {@code "Canvas"} 或 {@code "Preview"}，非这些值时返回 {@code null}
     */
    private static String resolveScenario(UiGenerationRequest request) {
        if (request == null || request.ext().isEmpty()) {
            return null;
        }
        Object scenarioValue = request.ext().get("scenario");
        if (scenarioValue == null) {
            return null;
        }
        String scenario = scenarioValue.toString();
        if ("Canvas".equals(scenario) || "Preview".equals(scenario)) {
            return scenario;
        }
        return null;
    }
}
