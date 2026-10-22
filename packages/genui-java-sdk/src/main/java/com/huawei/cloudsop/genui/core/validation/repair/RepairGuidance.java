/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

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
            selected.add("Rewrite the whole diagnosed statement instead of patching one token. Keep one complete "
                    + "`name = Expression` per statement.");
            selected.add("Arguments are positional: write `Name(value1, value2)`, not `Name(arg: value)`. "
                    + "Keep `key: value` only inside object literals `{...}`. Balance every `(`, `[`, `{`, "
                    + "and string quote.");
            selected.add("Arrow functions, template strings, and JavaScript statements are not openui-lang. "
                    + "Use double-quoted strings with `+` concatenation and `@Each`/`@Render` templates.");
            return;
        }

        if (code.startsWith("unknown-component")) {
            if (RepairHints.isMissingAtHint(hint)) {
                selected.add("Builtins and action steps require a leading `@`: rewrite the diagnosed call as "
                        + "`@Name(...)` matching the current contract signature.");
                return;
            }
            if (RepairHints.isCapabilityGapHint(hint)) {
                selected.add("Follow `## Capability boundary (overrides the original request)` above: stop trying to "
                        + "implement or approximate the unsupported operation, redefine every diagnosed statement id "
                        + "with a valid expression, display the real data using the fallback, and make "
                        + "`capabilityNotice` the first child of `root`.");
                return;
            }
            selected.add("Use only components listed in the current full contract. Copy the exact component name "
                    + "and positional argument order from its signature; a capitalized call is a component, "
                    + "not a builtin.");
            return;
        }

        if ("unresolved-ref".equals(code) || REFERENCE.equals(source)) {
            selected.add("Classify the unresolved name from its syntax: `@name(...)` is a builtin call, "
                    + "`Name(...)` is a component call, and a bare identifier is a statement reference. "
                    + "Only builtins take a leading `@`.");
            selected.add("If the name is a builtin or action step, add the leading `@`. If it is a statement, "
                    + "define it with that exact id or correct the reference. JavaScript globals are unavailable; "
                    + "replace them with contract builtins. Never invent an external reference, tool, or field.");
            return;
        }

        if (code.startsWith("builtin-") || "excess-args".equals(code) || "missing-required".equals(code)
                || "null-required".equals(code) || "invalid-prop".equals(code) || "empty-component-slot".equals(code)
                || "component-slot-type-mismatch".equals(code)) {
            selected.add("Match the current contract signature exactly: argument count, positional order, and "
                    + "parameter kind. Binder arguments are double-quoted names and are only in scope inside "
                    + "that builtin's template expression.");
            return;
        }

        if ("root-missing".equals(code) || "root-not-renderable".equals(code)) {
            selected.add("Keep exactly one reachable `root` statement whose expression is a renderable component "
                    + "call. Redefine `root` if needed; do not delete it.");
            return;
        }

        if (code.startsWith("type-data-")) {
            selected.add("`data.<path>` must exist in the caller-supplied data context. Do not invent fields, "
                    + "invent rows, or traverse a field that is not described there.");
            selected.add("Do not derive counts, sums, averages, or other statistics from sample rows as if they "
                    + "were the full dataset. Use @builtins over actual data references; if the value cannot be "
                    + "known, render the real known data and state the limitation in the page.");
            return;
        }

        if (code.startsWith("type-")) {
            selected.add("Match the declared prop, operator, chart, and table types. If a value's type cannot be "
                    + "proven from the available data, do not assert a definite mismatch; render real data and "
                    + "state the limitation in the page.");
            selected.add("Never fabricate data or statistics to satisfy a type. Change the expression to derive "
                    + "from actual references, or show the real available value.");
            return;
        }

        selected.add("Fix only the diagnosed statement and preserve every other definition exactly. Keep all "
                + "references resolvable and do not invent names, data, or functions.");
    }
}
