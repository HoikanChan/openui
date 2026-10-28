/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

import com.huawei.cloudsop.genui.core.contract.CapabilityGapSpec;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;

import java.util.List;
import java.util.Set;

/**
 * 面向修复的未解析引用与未知组件提示路由（设计决策 11.2 / #75）。
 *
 * <p>
 * 增强信息<em>只</em>放在 {@code hint} 字段中，保持问题的 {@code message} 稳定。
 *
 * <p>
 * 提示文案与 {@link #isRootCauseHint} 判定逻辑刻意放在同一个类中：{@code ValidationIssueReducer} 使用该判定来豁免
 * 携带根因提示的问题不被级联抑制。两者放在一起意味着一处会破坏判定逻辑的文案改动会直接导致 {@code RepairHintsTest} 失败，而不是默默地抑制掉本应解释失败原因的提示。
 *
 * @since 2026
 */
public final class RepairHints {

    /**
     * 模型常常泄漏进 openui-lang 的 JavaScript 全局对象与关键字。{@code new} 这类关键字之所以出现，是因为解析器 将其暴露为普通引用（例如来自 {@code new Date()}）。同时也是
     * openui-lang 内置函数的名称（如 {@code Set}） 会优先被内置函数这一档匹配到。
     */
    private static final Set<String> JS_GLOBAL_NAMES = Set.of("Math", "JSON", "Date", "String", "Number", "Boolean",
            "Array", "Object", "RegExp", "Map", "Set", "Promise", "Intl", "NaN", "Infinity", "undefined", "globalThis",
            "window", "document", "console", "parseInt", "parseFloat", "isNaN", "isFinite", "new", "typeof",
            "instanceof", "function", "async", "await");

    /** 缺失 '@' 提示的根因前缀；由 {@code RepairHintsTest} 固定断言。 */
    private static final String DID_YOU_MEAN_PREFIX = "did you mean \"@";

    /** JS 全局对象提示的根因标记；由 {@code RepairHintsTest} 固定断言。 */
    private static final String JS_GLOBAL_MARKER = "is a JavaScript global";

    /** 能力边界越界提示的根因标记；由 {@code RepairHintsTest} 固定断言。 */
    private static final String CAPABILITY_GAP_MARKER = "exceeds the openui-lang capability boundary";

    private RepairHints() {
    }

    /**
     * 将一个未解析的引用名路由为修复提示：
     *
     * <ol>
     * <li>精确（区分大小写）匹配内置函数名 → 模型忘记了前导 {@code @}；
     * <li>已知的 JS 全局对象/关键字 → 该 JS 子集在 openui-lang 中不存在，引导使用内置函数；
     * <li>其余情况 → 提示在前面定义该语句（绝不建议“把它作为外部引用传入”：外部引用是 SDK 调用方的 API， 修复模型无法据此采取行动）。
     * </ol>
     *
     * @param name
     *            未解析的引用名
     * @return 修复提示文案
     */
    public static String unresolvedRefHint(String name) {
        // 契约 builtin 或 Action 步骤名均需 @ 前缀调用——两档提示一致。
        if (BuiltinCatalog.getDefault().isBuiltin(name) || BuiltinCatalog.isActionName(name)) {
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
     * 将一个未知组件/算子名按默认 builtin 词表与生效契约的 {@code capabilityGaps} 做三档路由（#75）：
     *
     * <ol>
     * <li>命中契约 builtin 或 Action step → 提示补前导 {@code @}；
     * <li>命中 {@code capabilityGaps.aliases}（忽略前导 {@code @}）→ 能力边界 Root-cause Hint；
     * <li>其余未知名称 → 提示使用契约已有组件改写。
     * </ol>
     *
     * @param name
     *            未知组件/调用名
     * @param capabilityGaps
     *            当前生效契约的能力缺口列表，可为 {@code null}
     * @return 修复提示文案
     */
    public static String unknownComponentHint(String name, List<CapabilityGapSpec> capabilityGaps) {
        return unknownComponentHint(name, BuiltinCatalog.getDefault(), capabilityGaps);
    }

    /**
     * 将一个未知组件/算子名按指定 builtin 词表与生效契约的 {@code capabilityGaps} 做三档路由（#75）。
     *
     * @param name
     *            未知组件/调用名
     * @param builtins
     *            当前 builtin 词表
     * @param capabilityGaps
     *            当前生效契约的能力缺口列表，可为 {@code null}
     * @return 修复提示文案
     */
    public static String unknownComponentHint(String name, BuiltinCatalog builtins,
            List<CapabilityGapSpec> capabilityGaps) {
        String normalized = CapabilityGapSpec.normalizeAlias(name);
        BuiltinCatalog effectiveBuiltins = builtins == null ? BuiltinCatalog.getDefault() : builtins;
        if (effectiveBuiltins.isBuiltin(normalized) || BuiltinCatalog.isActionName(normalized)) {
            return DID_YOU_MEAN_PREFIX + normalized + "\"? openui-lang builtins must be called with a leading '@'";
        }
        CapabilityGapSpec gap = findCapabilityGapByAlias(normalized, capabilityGaps);
        if (gap != null) {
            return "\"" + normalized + "\" (" + gap.name() + ") " + CAPABILITY_GAP_MARKER
                    + " — if the input data already contains pre-aggregated/grouped rows, display that existing data "
                    + "directly; otherwise do not try to implement or approximate \"" + gap.name() + "\"; instead: "
                    + gap.fallback() + ". Redefine this statement id with a valid expression and make `"
                    + gap.noticeStatement()
                    + "` (in the same language as the user's request) the first child of `root`";
        }
        return "\"" + normalized + "\" does not exist in the current contract — rewrite using components listed in "
                + "the current contract";
    }

    /**
     * 在当前生效契约的 {@code capabilityGaps} 中按别名查找命中的能力缺口（忽略前导 {@code @}）。
     *
     * @param name
     *            调用名
     * @param capabilityGaps
     *            能力缺口列表
     * @return 命中的 {@link CapabilityGapSpec}，未命中时返回 {@code null}
     */
    public static CapabilityGapSpec findCapabilityGapByAlias(String name, List<CapabilityGapSpec> capabilityGaps) {
        String normalized = CapabilityGapSpec.normalizeAlias(name);
        if (normalized.isEmpty() || capabilityGaps == null) {
            return null;
        }
        for (CapabilityGapSpec gap : capabilityGaps) {
            if (gap != null && gap.aliases().contains(normalized)) {
                return gap;
            }
        }
        return null;
    }

    /**
     * 判断提示是否为“缺失前导 {@code @}”根因提示。
     */
    public static boolean isMissingAtHint(String hint) {
        return hint != null && hint.startsWith(DID_YOU_MEAN_PREFIX);
    }

    /**
     * 判断提示是否为“能力边界越界”根因提示。
     */
    public static boolean isCapabilityGapHint(String hint) {
        return hint != null && hint.contains(CAPABILITY_GAP_MARKER);
    }

    /**
     * 当提示本身已经点明根因（缺失 '@'、JS 全局对象或能力边界越界）时返回 {@code true}。此类问题在修复提示词中 豁免于级联抑制：该语句可能同时携带一个语法错误，但这条提示 —— 而非 token 层面的症状 ——
     * 才是可操作的 解释。
     *
     * @param hint
     *            提示文案，可为 {@code null}
     * @return 提示本身即为根因时返回 {@code true}
     */
    public static boolean isRootCauseHint(String hint) {
        return hint != null && (hint.startsWith(DID_YOU_MEAN_PREFIX) || hint.contains(JS_GLOBAL_MARKER)
                || hint.contains(CAPABILITY_GAP_MARKER));
    }
}
