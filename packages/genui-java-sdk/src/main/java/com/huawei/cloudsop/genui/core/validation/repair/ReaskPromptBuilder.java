/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentPropsSchema;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatMessage;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.semantic.RepairHints;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为后续章节的两种修复形态（设计决策 #6）构建确定性的修复对话消息。纯函数、无副作用 —— 其输出文本被单元测试
 * 直接断言。
 *
 * <ul>
 * <li>{@link #buildFullRepair} —— “这是你产出的完整 DSL；它因以下机器可读问题未通过校验；请重新生成一份修正后的
 * 完整 openui-lang”。
 * <li>{@link #buildRepairAndContinue} —— “这是已接受的前缀（目前为止有效）；你的下一条语句无效，原因如下；请仅从该
 * 语句的修正版本开始，继续生成剩余 DSL”。
 * </ul>
 *
 * <p>
 * 问题描述块由结构化的 {@link ValidationIssue} 字段（code / message / hint / component / path）构建 ——
 * 绝不使用原始解析器异常文本。组件签名提示来自合并后的 {@link GenerationContract}，通过
 * {@link ComponentPropsSchema#formatSignature} 生成。
 *
 * <h2>级联抑制放在本类，而非校验器中（设计决策 11.4）</h2>
 *
 * <p>
 * 一次解析失败会扩散出派生噪声：该语句的表达式被拆解为虚假的 {@code excess-args}/{@code null-required}
 * 参数问题，模板片段暴露为 {@code unresolved-ref}，根节点停止物化（{@code root-not-renderable}）。本类仅在
 * “提示词视图”这一层过滤这些噪声 —— {@code ValidationResult} 本身保持完整。不要为了“整洁”把抑制逻辑挪进校验器：
 * 拦截语料库断言的是针对完整校验器输出的期望编码子集，TS 对齐测试数据镜像的是未抑制的 TS 解析器；一旦校验器
 * 开始丢弃问题，两者都会失败。例外：其 hint 本身就是根因（JS 全局函数 / 缺少 '@'，参见
 * {@link RepairHints#isRootCauseHint}）的 {@code unresolved-ref} 不会被抑制 —— 移除它会只剩下 token
 * 层面的症状。
 *
 * @since 2026
 */
public final class ReaskPromptBuilder {

    /** 机器可解析的标记，供测试/日志识别修复类系统消息（完整修复）。 */
    public static final String FULL_REPAIR_MARKER = "[REPAIR:FULL]";

    /** 机器可解析的标记，供测试/日志识别修复类系统消息（接续修复）。 */
    public static final String CONTINUE_REPAIR_MARKER = "[REPAIR:CONTINUE]";

    /** 同一语句语法失败所派生出的问题编码（是症状，不是根因）。 */
    private static final Set<String> DERIVED_CODES = Set.of("excess-args", "null-required", "unresolved-ref");

    private static final String ROOT_NOT_RENDERABLE = "root-not-renderable";

    private static final Pattern COMPONENT_CALL = Pattern.compile("(?<![@A-Za-z0-9_])([A-Z][A-Za-z0-9_]*)\\s*\\(");

    private ReaskPromptBuilder() {
    }

    /**
     * 完整修复提示词：整份无效 DSL + 问题列表 → 重新生成一份修正后的完整 DSL。
     *
     * @param userIntent
     *            原始用户消息/意图，可为 {@code null}
     * @param invalidDsl
     *            未通过校验的完整 DSL
     * @param issues
     *            说明失败原因的结构化校验问题
     * @param contract
     *            用于生成签名提示的合并合约，可为 {@code null}
     * @return 系统消息 + 用户消息组成的对话消息列表
     */
    public static List<ChatMessage> buildFullRepair(String userIntent, String invalidDsl, List<ValidationIssue> issues,
            GenerationContract contract) {
        return buildFullRepair(userIntent, invalidDsl, issues, contract, null);
    }

    /**
     * 完整修复提示词，附带初次生成时的完整 system prompt，供修复模型复用同一语言/组件契约。
     *
     * @param userIntent
     *            原始用户消息/意图，可为 {@code null}
     * @param invalidDsl
     *            未通过校验的完整 DSL
     * @param issues
     *            说明失败原因的结构化校验问题
     * @param contract
     *            用于生成签名提示的合并合约，可为 {@code null}
     * @param originalSystemPrompt
     *            初次生成请求使用的完整 system prompt，可为 {@code null}
     * @return 系统消息 + 用户消息组成的对话消息列表
     */
    public static List<ChatMessage> buildFullRepair(String userIntent, String invalidDsl, List<ValidationIssue> issues,
            GenerationContract contract, String originalSystemPrompt) {
        StringBuilder system = new StringBuilder();
        system.append(FULL_REPAIR_MARKER).append('\n');
        appendOriginalSystemPrompt(system, originalSystemPrompt);
        system.append("You previously produced an openui-lang document that FAILED validation. "
                + "Regenerate a CORRECTED, COMPLETE openui-lang document that fixes every issue below. "
                + "Return ONLY the corrected openui-lang inside a single ```openui fenced block. "
                + "Do not explain.\n\n");
        appendCompactSyntaxRules(system);
        appendSyntaxRepairHints(system, issues);
        appendIssues(system, issues);
        appendInvalidStatementSignatures(system, invalidDsl, issues, contract);

        StringBuilder user = new StringBuilder();
        if (userIntent != null && !userIntent.isBlank()) {
            user.append("## Original request\n").append(userIntent.trim()).append("\n\n");
        }
        user.append("## DSL that failed validation\n```openui\n").append(invalidDsl == null ? "" : invalidDsl.trim())
                .append("\n```");

        return List.of(ChatMessage.system(system.toString()), ChatMessage.user(user.toString()));
    }

    /**
     * 接续修复提示词：已接受的前缀有效；下一条语句无效；仅从该语句的修正版本开始继续生成剩余 DSL。
     *
     * @param userIntent
     *            原始用户消息/意图，可为 {@code null}
     * @param acceptedPrefix
     *            目前为止已接受的有效 DSL，可为空
     * @param invalidStatement
     *            未通过校验的那一条语句
     * @param issues
     *            该无效语句的结构化问题
     * @param contract
     *            用于生成签名提示的合并合约，可为 {@code null}
     * @return 系统消息 + 用户消息组成的对话消息列表
     */
    public static List<ChatMessage> buildRepairAndContinue(String userIntent, String acceptedPrefix,
            String invalidStatement, List<ValidationIssue> issues, GenerationContract contract) {
        return buildRepairAndContinue(userIntent, acceptedPrefix, invalidStatement, issues, contract, null);
    }

    /**
     * 接续修复提示词，附带初次生成时的完整 system prompt，供修复模型复用同一语言/组件契约。
     *
     * @param userIntent
     *            原始用户消息/意图，可为 {@code null}
     * @param acceptedPrefix
     *            目前为止已接受的有效 DSL，可为空
     * @param invalidStatement
     *            未通过校验的那一条语句
     * @param issues
     *            该无效语句的结构化问题
     * @param contract
     *            用于生成签名提示的合并合约，可为 {@code null}
     * @param originalSystemPrompt
     *            初次生成请求使用的完整 system prompt，可为 {@code null}
     * @return 系统消息 + 用户消息组成的对话消息列表
     */
    public static List<ChatMessage> buildRepairAndContinue(String userIntent, String acceptedPrefix,
            String invalidStatement, List<ValidationIssue> issues, GenerationContract contract,
            String originalSystemPrompt) {
        StringBuilder system = new StringBuilder();
        system.append(CONTINUE_REPAIR_MARKER).append('\n');
        appendOriginalSystemPrompt(system, originalSystemPrompt);
        system.append("You are continuing an openui-lang document. Everything in \"Accepted so far\" is ALREADY "
                + "valid and MUST NOT be repeated. Your previous NEXT statement was invalid. Produce a "
                + "CORRECTED version of that statement and then the REST of the document. Emit ONLY the "
                + "remaining openui-lang (do not re-emit the accepted prefix). Return the continuation "
                + "inside a single ```openui fenced block. Do not explain.\n\n");
        appendCompletionContract(system, acceptedPrefix);
        appendCompactSyntaxRules(system);
        appendSyntaxRepairHints(system, issues);
        appendIssues(system, issues);
        appendTargetedRewrites(system, invalidStatement, issues);
        appendInvalidStatementSignatures(system, invalidStatement, issues, contract);

        StringBuilder user = new StringBuilder();
        if (userIntent != null && !userIntent.isBlank()) {
            user.append("## Original request\n").append(userIntent.trim()).append("\n\n");
        }
        user.append("## Accepted so far (valid — do not repeat)\n```openui\n")
                .append(acceptedPrefix == null ? "" : acceptedPrefix.trim()).append("\n```\n\n");
        user.append("## Invalid next statement (fix and continue from here)\n```openui\n")
                .append(invalidStatement == null ? "" : invalidStatement.trim()).append("\n```");

        return List.of(ChatMessage.system(system.toString()), ChatMessage.user(user.toString()));
    }

    // ── 共享渲染逻辑 ────────────────────────────────────────────────────────

    private static void appendOriginalSystemPrompt(StringBuilder sb, String originalSystemPrompt) {
        if (originalSystemPrompt == null || originalSystemPrompt.isBlank()) {
            return;
        }
        sb.append("## Original generation system prompt\n");
        sb.append("Use this as the authoritative openui-lang language, component, builtin, and data contract. ")
                .append("The repair instructions below override only the response shape.\n\n");
        sb.append(originalSystemPrompt.trim()).append("\n\n");
        sb.append("## Repair instructions\n");
    }

    /**
     * 完整性契约。回放实验（V0 7/26 → V1 14/26）表明：接续修复失败的主因不是单语句修不对，而是修好后
     * 续写中途停笔或对同型错误只改第一处，导致文档不完整（root 缺失、下游引用未定义）。以下三条通用约束把
     * “修复一条语句”升级为“补全整个文档”，是修复率翻倍的主杠杆，不依赖任何按例定制的参数。
     */
    private static void appendCompletionContract(StringBuilder sb, String acceptedPrefix) {
        boolean rootDefined = acceptedPrefix != null
                && java.util.regex.Pattern.compile("(?m)^\\s*root\\s*=").matcher(acceptedPrefix).find();
        sb.append("## Completion contract (critical)\n");
        sb.append("- The same mistake pattern may recur later in the document. Apply the SAME correction to EVERY "
                + "later occurrence, not just the first.\n");
        sb.append("- Your continuation MUST complete the document: every identifier referenced anywhere (in the "
                + "accepted prefix or your continuation) must be defined by the end.\n");
        if (!rootDefined) {
            sb.append("- `root` is NOT defined yet. Your continuation MUST end with a `root = Stack([...])` statement "
                    + "wiring the top-level layout.\n");
        }
        sb.append("- Do NOT stop after the corrected statement. Keep writing until the document is complete.\n\n");
    }

    /**
     * 定向改写示例。回放实验（V1 14/26 → V2 ~16/26）表明：在通用契约之上，针对无效语句里检出的具体 JavaScript
     * 惯性泄漏给出 WRONG→RIGHT 对照，能稳定再修好 2~3 例。仅在检出对应模式时输出，避免噪声淹没提示。
     */
    private static void appendTargetedRewrites(StringBuilder sb, String invalidStatement, List<ValidationIssue> issues) {
        if (invalidStatement == null) {
            invalidStatement = "";
        }
        List<String> blocks = new ArrayList<>();
        if (invalidStatement.contains("=>") || invalidStatement.matches("(?s).*\\.map\\s*\\(.*")) {
            blocks.add("JavaScript array methods are INVALID. Use array pluck or @Each:\n"
                    + "WRONG: series = data.items.baseline.map(item => item.value)\n"
                    + "RIGHT: series = data.items.baseline.value\n"
                    + "RIGHT: series = @Each(data.items.baseline, \"item\", item.value)");
        }
        if (invalidStatement.matches("(?s).*\\.(toString|toFixed|join)\\s*\\(.*")) {
            blocks.add("JavaScript string/number methods are INVALID. Use concatenation or builtins:\n"
                    + "WRONG: label = value.toString()   RIGHT: label = \"\" + value\n"
                    + "WRONG: pct = value.toFixed(1)     RIGHT: pct = @FormatNumber(value, 1)");
        }
        boolean unknownComponent = issues != null && issues.stream().anyMatch(
                i -> i != null && "unknown-component".equals(i.code()));
        if (unknownComponent) {
            blocks.add("Only components listed under the signatures section exist. Never invent helpers "
                    + "(e.g. a lowercase cardHeader(...)); inline the expression or use a defined component.");
        }
        if (blocks.isEmpty()) {
            return;
        }
        sb.append("## Targeted rewrite examples\n");
        for (String block : blocks) {
            sb.append(block).append("\n\n");
        }
    }

    private static void appendCompactSyntaxRules(StringBuilder sb) {
        sb.append("## Compact openui-lang rules for repair\n");
        sb.append("1. Output only the requested openui-lang repair; do not add explanations.\n");
        sb.append("2. One statement has the form `name = Expression`; keep one complete assignment per statement.\n");
        sb.append("3. `root` must ultimately be a renderable component, usually `root = Stack([...])`.\n");
        sb.append("4. Arguments are POSITIONAL. Write `Stack(children, \"row\", \"l\")`, not named component args.\n");
        sb.append("5. Object literals are allowed only as expression values or object props; keep `{}`, `[]`, and `()` balanced.\n");
        sb.append("6. Builtins must start with `@`, for example `@Each(array, \"item\", expr)` and `@Render(\"v\", expr)`.\n");
        sb.append("7. Binder names are string literals and are only in scope inside that builtin template.\n");
        sb.append("8. JavaScript globals and methods are unavailable; use openui-lang builtins instead.\n\n");
    }

    private static void appendSyntaxRepairHints(StringBuilder sb, List<ValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        for (ValidationIssue issue : suppressCascades(issues)) {
            if (issue == null || issue.severity() != ValidationSeverity.ERROR || issue.code() == null
                    || !issue.code().startsWith("syntax-")) {
                continue;
            }
            String message = issue.message() == null ? "" : issue.message();
            if (message.contains("COLON")) {
                hints.add("Named component arguments are not supported; convert `foo: value` inside component calls "
                        + "to positional arguments, but keep object-literal keys when an object prop is required.");
            }
            if ("syntax-unclosed-bracket".equals(issue.code()) || message.toLowerCase(java.util.Locale.ROOT)
                    .contains("unclosed")) {
                hints.add("Close every `(`, `[`, and `{` opened by the invalid statement before starting another "
                        + "statement.");
            }
            if (message.contains("ARROW") || message.contains("FAT_ARROW")) {
                hints.add("Arrow functions are JavaScript and are not valid openui-lang; use @Each/@Render templates.");
            }
            if (message.contains("TEMPLATE")) {
                hints.add("Template strings are JavaScript and are not valid openui-lang; use double-quoted strings "
                        + "and `+` concatenation.");
            }
            hints.add("Regenerate the whole invalid statement using the signatures below instead of patching a token "
                    + "in place.");
        }
        if (hints.isEmpty()) {
            return;
        }
        sb.append("## Syntax repair hints\n");
        for (String hint : hints) {
            sb.append("- ").append(hint).append('\n');
        }
        sb.append('\n');
    }

    private static void appendIssues(StringBuilder sb, List<ValidationIssue> issues) {
        sb.append("## Validation issues to fix\n");
        if (issues == null || issues.isEmpty()) {
            sb.append("- (no structured issues supplied)\n");
            return;
        }
        for (ValidationIssue issue : suppressCascades(issues)) {
            if (issue == null || issue.severity() != ValidationSeverity.ERROR) {
                continue; // 机器可读：只有阻塞级别的问题才驱动修复
            }
            sb.append("- [").append(issue.code()).append("] ").append(issue.message());
            if (issue.component() != null && !issue.component().isBlank()) {
                sb.append(" (component: ").append(issue.component()).append(')');
            }
            if (issue.path() != null && !issue.path().isBlank()) {
                sb.append(" (at ").append(issue.path()).append(')');
            }
            appendLocation(sb, issue);
            if (issue.hint() != null && !issue.hint().isBlank()) {
                sb.append("\n  hint: ").append(issue.hint());
            }
            sb.append('\n');
        }
    }

    /**
     * 提示词视图级联过滤器（分层原理见类级 Javadoc）：
     *
     * <ol>
     * <li>语句级：某语句已有阻塞级语法问题时，其派生编码会被丢弃，携带根因提示的 unresolved-ref 除外；
     * <li>根节点收尾：只要还存在其他阻塞级问题，{@code root-not-renderable} 就会被丢弃 —— 那个其他问题已经
     * 解释了根节点为何未能物化。
     * </ol>
     */
    private static List<ValidationIssue> suppressCascades(List<ValidationIssue> issues) {
        Set<String> syntaxBrokenStmts = new LinkedHashSet<>();
        for (ValidationIssue issue : issues) {
            if (issue != null && issue.severity() == ValidationSeverity.ERROR && "syntax".equals(issue.source())
                    && issue.statementId() != null) {
                syntaxBrokenStmts.add(issue.statementId());
            }
        }
        List<ValidationIssue> kept = new ArrayList<>();
        boolean keptOtherError = false;
        for (ValidationIssue issue : issues) {
            if (issue == null) {
                continue;
            }
            boolean derivedOnBrokenStmt = issue.severity() == ValidationSeverity.ERROR
                    && DERIVED_CODES.contains(issue.code()) && issue.statementId() != null
                    && syntaxBrokenStmts.contains(issue.statementId()) && !RepairHints.isRootCauseHint(issue.hint());
            if (derivedOnBrokenStmt) {
                continue;
            }
            kept.add(issue);
            if (issue.severity() == ValidationSeverity.ERROR && !ROOT_NOT_RENDERABLE.equals(issue.code())) {
                keptOtherError = true;
            }
        }
        if (keptOtherError) {
            kept.removeIf(i -> ROOT_NOT_RENDERABLE.equals(i.code()));
        }
        return kept;
    }

    /**
     * 追加 {@code (stmt=id, line L:C)} 位置片段，便于修复模型在多语句文档中定位问题。未知部分（{@code null}
     * statementId、{@code -1} 行号）会被省略；两者都未知时不渲染任何内容。
     */
    private static void appendLocation(StringBuilder sb, ValidationIssue issue) {
        boolean hasStmt = issue.statementId() != null && !issue.statementId().isBlank();
        boolean hasLine = issue.line() > 0;
        if (!hasStmt && !hasLine) {
            return;
        }
        sb.append(" (");
        if (hasStmt) {
            sb.append("stmt=").append(issue.statementId());
        }
        if (hasLine) {
            if (hasStmt) {
                sb.append(", ");
            }
            sb.append("line ").append(issue.line()).append(':').append(Math.max(issue.column(), 1));
        }
        sb.append(')');
    }

    /**
     * 为问题中出现的每个组件名输出签名提示（使模型能看到它用错的东西的确切允许形状）。顺序确定：按问题出现顺序，
     * 并去重。
     */
    private static void appendInvalidStatementSignatures(StringBuilder sb, String invalidStatement,
            List<ValidationIssue> issues, GenerationContract contract) {
        if (contract == null || issues == null) {
            return;
        }
        Map<String, ComponentPromptSpec> components = contract.components();
        if (components == null || components.isEmpty()) {
            return;
        }
        Set<String> named = new LinkedHashSet<>();
        for (ValidationIssue issue : issues) {
            if (issue != null && issue.component() != null && !issue.component().isBlank()) {
                named.add(issue.component());
            }
        }
        if (invalidStatement != null && !invalidStatement.isBlank()) {
            Matcher matcher = COMPONENT_CALL.matcher(invalidStatement);
            while (matcher.find()) {
                named.add(matcher.group(1));
            }
        }
        List<String> lines = new ArrayList<>();
        for (String name : named) {
            ComponentPromptSpec spec = components.get(name);
            if (spec != null) {
                lines.add("- " + ComponentPropsSchema.formatSignature(name, spec));
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        sb.append("\n## Component signatures\n");
        for (String line : lines) {
            sb.append(line).append('\n');
        }
    }
}
