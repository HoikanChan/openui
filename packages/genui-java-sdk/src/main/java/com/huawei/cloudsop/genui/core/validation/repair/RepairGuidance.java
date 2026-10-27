/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

import com.huawei.cloudsop.genui.core.prompt.PromptResources;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationIssueReducer;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.semantic.RepairHints;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 增量修复提示词中“按诊断选取的专项指导”的唯一维护点。
 *
 * <p>
 * 本类刻意与提示词装配分离：新增一种校验错误时只在这里补一条规则，{@link IncrementalRepairPromptBuilder} 不需要改动。选择只看<em>当前</em>诊断的
 * {@code code}/{@code source}/{@code hint}，从不读取历史轮次的错误标签——同一语句在 last-wins 覆盖后，旧标签可能已经失效。
 *
 * <p>
 * 只输出与当前阻塞问题相关的指导；没有诊断时不生成任何「万能建议」。
 *
 * @since 2026
 */
public final class RepairGuidance {

    private static final String SYNTAX = "syntax";
    private static final String REFERENCE = "reference";

    private RepairGuidance() {
    }

    /**
     * 根据当前诊断选取去重后的专项修复指导（保持首次出现顺序）。
     *
     * @param issues
     *            当前轮末诊断；内部按 {@link ValidationIssueReducer#actionable(List)} 过滤派生噪声
     * @return 指导文案列表；无阻塞诊断时为空列表
     */
    public static List<String> forIssues(List<ValidationIssue> issues) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (ValidationIssue issue : ValidationIssueReducer.actionable(issues)) {
            if (issue == null || issue.severity() != ValidationSeverity.ERROR) {
                continue;
            }
            select(selected, issue);
        }
        return List.copyOf(selected);
    }

    private static void select(LinkedHashSet<String> selected, ValidationIssue issue) {
        String code = issue.code() == null ? "" : issue.code();
        String source = issue.source() == null ? "" : issue.source();
        String hint = issue.hint();

        if (SYNTAX.equals(source) || code.startsWith("syntax-")) {
            selected.add(PromptResources.load("prompts/repair/guidance/syntax-statement.txt"));
            selected.add(PromptResources.load("prompts/repair/guidance/syntax-arguments.txt"));
            selected.add(PromptResources.load("prompts/repair/guidance/syntax-language.txt"));
            return;
        }

        if (code.startsWith("unknown-component")) {
            if (RepairHints.isMissingAtHint(hint)) {
                selected.add(PromptResources.load("prompts/repair/guidance/missing-at.txt"));
                return;
            }
            if (RepairHints.isCapabilityGapHint(hint)) {
                selected.add(PromptResources.load("prompts/repair/guidance/capability-boundary.txt"));
                return;
            }
            selected.add(PromptResources.load("prompts/repair/guidance/unknown-component.txt"));
            return;
        }

        if ("unresolved-ref".equals(code) || REFERENCE.equals(source)) {
            selected.add(PromptResources.load("prompts/repair/guidance/reference-classification.txt"));
            selected.add(PromptResources.load("prompts/repair/guidance/reference-resolution.txt"));
            return;
        }

        if (code.startsWith("builtin-") || "excess-args".equals(code) || "missing-required".equals(code)
                || "null-required".equals(code) || "invalid-prop".equals(code) || "empty-component-slot".equals(code)
                || "component-slot-type-mismatch".equals(code)) {
            selected.add(PromptResources.load("prompts/repair/guidance/signature.txt"));
            return;
        }

        if ("root-missing".equals(code) || "root-not-renderable".equals(code)) {
            selected.add(PromptResources.load("prompts/repair/guidance/root.txt"));
            return;
        }

        if (code.startsWith("type-data-")) {
            selected.add(PromptResources.load("prompts/repair/guidance/data-path.txt"));
            selected.add(PromptResources.load("prompts/repair/guidance/data-statistics.txt"));
            selected.add(PromptResources.load("prompts/repair/guidance/data-subject.txt"));
            return;
        }

        if ("type-table-row-shape-mismatch".equals(code)) {
            selected.add(PromptResources.load("prompts/repair/guidance/table-row-shape.txt"));
        }

        if ("type-table-column-missing".equals(code)) {
            selected.add(PromptResources.load("prompts/repair/guidance/table-columns.txt"));
            selected.add(PromptResources.load("prompts/repair/guidance/table-missing-field.txt"));
            return;
        }

        if (code.startsWith("type-")) {
            selected.add(PromptResources.load("prompts/repair/guidance/types.txt"));
            selected.add(PromptResources.load("prompts/repair/guidance/type-data-honesty.txt"));
            return;
        }

        selected.add(PromptResources.load("prompts/repair/guidance/fallback.txt"));
    }
}
