/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.semantic.RepairHints;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 增量修复专项指导的诊断路由；完整文案在 {@code prompts/repair/system.md} 中维护。
 *
 * <p>本类刻意与提示词装配分离：新增一种校验错误时只在这里补一条规则，{@link RepairPromptBuilder} 不需要改动。选择只看<em>当前</em>诊断的 {@code
 * code}/{@code source}/{@code hint}，从不读取历史轮次的错误标签——同一语句在 last-wins 覆盖后，旧标签可能已经失效。
 *
 * <p>只输出与当前阻塞问题相关的指导；没有诊断时不生成任何「万能建议」。
 *
 * @since 2026
 */
public final class RepairGuidance {

    private static final List<String> GUIDANCE_FLAGS =
            List.of(
                    "needsSyntaxGuidance",
                    "needsMissingAtGuidance",
                    "needsCapabilityGuidance",
                    "needsUnknownComponentGuidance",
                    "needsReferenceGuidance",
                    "needsSignatureGuidance",
                    "needsRootGuidance",
                    "needsDataReferenceGuidance",
                    "needsTableRowShapeGuidance",
                    "needsTableColumnGuidance",
                    "needsTypesGuidance",
                    "needsFallbackGuidance");

    private static final String SYNTAX = "syntax";
    private static final String REFERENCE = "reference";

    private RepairGuidance() {}

    /**
     * 根据当前诊断选取去重后的专项修复指导开关（输出顺序由模板决定）。
     *
     * @param issues 已去噪的当前轮末诊断
     * @return 指导开关；无阻塞诊断时全部为 false
     */
    static Map<String, Boolean> forActionableIssues(List<ValidationIssue> issues) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (ValidationIssue issue : issues) {
            select(selected, issue);
        }
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (String name : GUIDANCE_FLAGS) {
            flags.put(name, selected.contains(name));
        }
        flags.put("hasGuidance", !selected.isEmpty());
        return flags;
    }

    private static void select(LinkedHashSet<String> selected, ValidationIssue issue) {
        String code = issue.code() == null ? "" : issue.code();
        String source = issue.source() == null ? "" : issue.source();
        String hint = issue.hint();

        if (SYNTAX.equals(source) || code.startsWith("syntax-")) {
            selected.add("needsSyntaxGuidance");
            return;
        }

        if (code.startsWith("unknown-component")) {
            if (RepairHints.isMissingAtHint(hint)) {
                selected.add("needsMissingAtGuidance");
                return;
            }
            if (RepairHints.isCapabilityGapHint(hint)) {
                selected.add("needsCapabilityGuidance");
                return;
            }
            selected.add("needsUnknownComponentGuidance");
            return;
        }

        if ("unresolved-ref".equals(code) || REFERENCE.equals(source)) {
            selected.add("needsReferenceGuidance");
            return;
        }

        if (code.startsWith("builtin-")
                || "excess-args".equals(code)
                || "missing-required".equals(code)
                || "null-required".equals(code)
                || "invalid-prop".equals(code)
                || "empty-component-slot".equals(code)
                || "component-slot-type-mismatch".equals(code)) {
            selected.add("needsSignatureGuidance");
            return;
        }

        if ("root-missing".equals(code) || "root-not-renderable".equals(code)) {
            selected.add("needsRootGuidance");
            return;
        }

        if (code.startsWith("type-data-")) {
            selected.add("needsDataReferenceGuidance");
            return;
        }

        if ("type-table-row-shape-mismatch".equals(code)) {
            selected.add("needsTableRowShapeGuidance");
        }

        if ("type-table-column-missing".equals(code)) {
            selected.add("needsTableColumnGuidance");
            return;
        }

        if (code.startsWith("type-")) {
            selected.add("needsTypesGuidance");
            return;
        }

        selected.add("needsFallbackGuidance");
    }
}
