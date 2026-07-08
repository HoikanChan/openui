/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

import com.huawei.cloudsop.genui.core.validation.parser.Builtins;

import java.util.Set;

/**
 * 面向修复的未解析引用提示路由（设计决策 11.2）。
 *
 * <p>
 * 增强信息<em>只</em>放在 {@code hint} 字段中。问题的 {@code message} 受 TypeScript 对齐约束：
 * {@code CrossLanguageParityTest} 从 message 的首尾引号中提取被引用的名称，因此在 message 中添加带引号的内容
 * 会破坏名称提取。
 *
 * <p>
 * 提示文案与 {@link #isRootCauseHint} 判定逻辑刻意放在同一个类中：{@code ReaskPromptBuilder} 使用该判定来豁免
 * 携带根因提示的问题不被级联抑制。两者放在一起意味着一处会破坏判定逻辑的文案改动会直接导致
 * {@code RepairHintsTest} 失败，而不是默默地抑制掉本应解释失败原因的提示。
 *
 * @since 2026
 */
public final class RepairHints {

    /**
     * 模型常常泄漏进 openui-lang 的 JavaScript 全局对象与关键字。{@code new} 这类关键字之所以出现，是因为解析器
     * 将其暴露为普通引用（例如来自 {@code new Date()}）。同时也是 openui-lang 内置函数的名称（如 {@code Set}）
     * 会优先被内置函数这一档匹配到。
     */
    private static final Set<String> JS_GLOBAL_NAMES = Set.of("Math", "JSON", "Date", "String", "Number", "Boolean",
            "Array", "Object", "RegExp", "Map", "Set", "Promise", "Intl", "NaN", "Infinity", "undefined", "globalThis",
            "window", "document", "console", "parseInt", "parseFloat", "isNaN", "isFinite", "new", "typeof",
            "instanceof", "function", "async", "await");

    /** 缺失 '@' 提示的根因前缀；由 {@code RepairHintsTest} 固定断言。 */
    private static final String DID_YOU_MEAN_PREFIX = "did you mean \"@";

    /** JS 全局对象提示的根因标记；由 {@code RepairHintsTest} 固定断言。 */
    private static final String JS_GLOBAL_MARKER = "is a JavaScript global";

    private RepairHints() {
    }

    /**
     * 将一个未解析的引用名路由为修复提示：
     *
     * <ol>
     * <li>精确（区分大小写）匹配内置函数名 → 模型忘记了前导 {@code @}；
     * <li>已知的 JS 全局对象/关键字 → 该 JS 子集在 openui-lang 中不存在，引导使用内置函数；
     * <li>其余情况 → 提示在前面定义该语句（绝不建议“把它作为外部引用传入”：外部引用是 SDK 调用方的 API，
     * 修复模型无法据此采取行动）。
     * </ol>
     *
     * @param name
     *            未解析的引用名
     * @return 修复提示文案
     */
    public static String unresolvedRefHint(String name) {
        if (Builtins.isBuiltin(name)) {
            return DID_YOU_MEAN_PREFIX + name + "\"? openui-lang builtins must be called with a leading '@'";
        }
        if (JS_GLOBAL_NAMES.contains(name)) {
            return "\"" + name + "\" " + JS_GLOBAL_MARKER
                    + " — JS globals and methods are not available in openui-lang; use builtins like"
                    + " @Abs, @Round, @FormatNumber, @FormatDate instead";
        }
        return "define a statement named \"" + name + "\" earlier in the document";
    }

    /**
     * 当提示本身已经点明根因（缺失 '@' 或 JS 全局对象这两档）时返回 {@code true}。此类问题在重新请求提示词中
     * 豁免于级联抑制：该语句可能同时携带一个语法错误，但这条提示 —— 而非 token 层面的症状 —— 才是可操作的
     * 解释。
     *
     * @param hint
     *            提示文案，可为 {@code null}
     * @return 提示本身即为根因时返回 {@code true}
     */
    public static boolean isRootCauseHint(String hint) {
        return hint != null && (hint.startsWith(DID_YOU_MEAN_PREFIX) || hint.contains(JS_GLOBAL_MARKER));
    }
}
