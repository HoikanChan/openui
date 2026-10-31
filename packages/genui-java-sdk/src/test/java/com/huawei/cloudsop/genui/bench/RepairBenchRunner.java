/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerationResult;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerator;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;
import com.huawei.cloudsop.genui.core.llm.GenerationEndReason;
import com.huawei.cloudsop.genui.core.llm.GenerationTiming;
import com.huawei.cloudsop.genui.core.llm.UiGenerationRequest;
import com.huawei.cloudsop.genui.core.llm.stream.SseFrames;
import com.huawei.cloudsop.genui.core.llm.transport.InMemoryLlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;
import com.huawei.cloudsop.genui.core.validation.parser.OpenuiParser;
import com.huawei.cloudsop.genui.core.validation.parser.ParseMode;
import com.huawei.cloudsop.genui.core.validation.parser.Program;
import com.huawei.cloudsop.genui.core.validation.parser.Statement;
import com.huawei.cloudsop.genui.core.validation.repair.RepairPromptBuilder;
import com.huawei.cloudsop.genui.core.validation.semantic.ContractCatalog;
import com.huawei.cloudsop.genui.core.validation.semantic.ProgramAnalysis;
import com.huawei.cloudsop.genui.core.validation.semantic.ProgramAnalyzer;
import com.huawei.cloudsop.genui.core.validation.semantic.RepairHints;
import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;
import com.huawei.cloudsop.genui.core.validation.type.analysis.BuiltinAnalysis;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * repair-bench 批量执行器。照 {@code JobRunner} 形态：有界固定线程池、按完成顺序逐行 JSONL、 {@code synchronized}
 * emit；加载阶段的语料损坏已在 loader 层整批终止（退出码 1），执行阶段单案例异常转 {@code error} 行不中断批次。
 *
 * <p>单案例流程（实现设计 §3）：案例契约现算 initial FINAL 校验 → 复现判定（非 INVALID 即 {@code not-reproduced}）→ 用公开 {@link
 * GenUiGenerator} 跑一次真实<b>流式</b>生成会话（{@code generateStream}；流式是唯一修复路径，同步 {@code generate}
 * 不再校验/修复）：transport 以 SSE 把案例 DSL 当作"历史初次生成"回放，修复轮转交真实模型 SSE，整页 Markdown 降级由本地桩 SSE
 * 承接，生成会话随后做轮末统一校验与增量修复 → 由轮记录映射输出状态。 全链路 issue code 一律取 <b>actionable</b> 视图（与修复提示词所见一致）。
 *
 * <p>每个案例独立构建 {@link GenerationSdk}（案例契约）与生成器，避免跨案例共享契约/会话状态。
 *
 * @since 2026
 */
final class RepairBenchRunner {

    private final LlmTransport transport;

    private final String model;

    private final int maxAttempts;

    private final Duration timeout;

    private final PrintStream out;

    /** 已写出的 JSONL 行；报告生成在批次结束后复用同一份数据，不再回读文件。 */
    private final List<String> emitted = java.util.Collections.synchronizedList(new ArrayList<>());

    /** 全批共享的确定性生成/修复配置：温度 0 + 不开 thinking（镜像 GenUiGenerator.repairConfig 的纪律）。 */
    private final GenUiLlmConfig repairConfig;

    /** 是否跑修复：false = 每案例只做 FINAL 校验（validation-only，不初始化 transport、零 LLM 调用）。 */
    private final boolean repair;

    RepairBenchRunner(
            LlmTransport transport,
            String model,
            int maxAttempts,
            Duration timeout,
            PrintStream out) {
        this(transport, model, maxAttempts, timeout, out, true);
    }

    RepairBenchRunner(
            LlmTransport transport,
            String model,
            int maxAttempts,
            Duration timeout,
            PrintStream out,
            boolean repair) {
        this.transport = transport;
        this.model = model;
        this.maxAttempts = maxAttempts;
        this.timeout = timeout == null ? Duration.ZERO : timeout;
        this.out = out;
        this.repair = repair;
        this.repairConfig =
                GenUiLlmConfig.builder()
                        .defaultModel(model)
                        .temperature(0)
                        .enableThinking(false)
                        .build();
    }

    /**
     * 跑完整批案例：逐案例行按完成顺序输出，批次末尾输出一行汇总。
     *
     * @param cases 已加载的案例（顺序即执行提交顺序）
     * @param concurrency 线程池大小
     * @throws InterruptedException 等待期间被中断
     */
    void runAll(List<RepairCasePack.RepairCase> cases, int concurrency)
            throws InterruptedException {
        long batchStart = System.currentTimeMillis();
        SummaryAccumulator summary = new SummaryAccumulator();
        ExecutorService pool =
                Executors.newFixedThreadPool(Math.min(concurrency, Math.max(cases.size(), 1)));
        try {
            List<Future<?>> futures =
                    cases.stream()
                            .map(benchCase -> pool.submit(() -> runOneSafely(benchCase, summary)))
                            .<Future<?>>map(f -> f)
                            .toList();
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException error) {
                    // runOneSafely 已把案例级异常转为 error 行；走到这里属于意外，记录后继续等其余案例
                    System.err.println(
                            "[repair-bench] unexpected worker failure: " + error.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        emit(
                Json.stringify(
                        summary.toLine(
                                model,
                                maxAttempts,
                                concurrency,
                                timeout,
                                System.currentTimeMillis() - batchStart)));
    }

    private void runOneSafely(RepairCasePack.RepairCase benchCase, SummaryAccumulator summary) {
        try {
            runOne(benchCase, summary);
        } catch (Exception error) {
            LinkedHashMap<String, Object> line = baseLine(benchCase);
            line.put("status", "error");
            line.put("success", false);
            line.put("reproduced", false);
            line.put("error", String.valueOf(error));
            emit(Json.stringify(line));
            summary.record("error", null);
        }
    }

    private void runOne(RepairCasePack.RepairCase benchCase, SummaryAccumulator summary) {
        long started = System.currentTimeMillis();

        // 与 ValidateCli 一致固定 data:bench 案例 DSL 全部引用 data(提示词契约根名),dataModel 为 null 只关
        // 类型校验;若随之清空 externalRefs,这些案例会被整批误报 unresolved-ref。
        Set<String> externalRefs = Set.of("data");

        // ── 步骤 1：复现判定（FINAL）──
        ValidationResult v0 =
                DefaultOpenuiLangValidator.INSTANCE.validate(
                        ValidationRequest.builder()
                                .dsl(benchCase.dsl())
                                .contract(benchCase.contract())
                                .rootName(
                                        benchCase.contract().root() == null
                                                ? "root"
                                                : benchCase.contract().root())
                                .externalRefs(externalRefs)
                                .dataModel(benchCase.dataModel())
                                .build());
        List<String> initialCodes = actionableCodes(v0);

        if (v0.status() != ValidationStatus.INVALID) {
            LinkedHashMap<String, Object> line = baseLine(benchCase);
            line.put("status", "not-reproduced");
            line.put("success", false);
            line.put("reproduced", false);
            line.put("initialIssueCodes", List.of());
            putInternalCodes(line, benchCase);
            line.put("durationMs", System.currentTimeMillis() - started);
            emit(Json.stringify(line));
            summary.record("not-reproduced", null);
            return;
        }

        // ── 步骤 1.5：validation-only（--repair 未开）时到此为止，不碰 transport ──
        if (!repair) {
            LinkedHashMap<String, Object> line = baseLine(benchCase);
            line.put("status", "invalid");
            line.put("success", false);
            line.put("reproduced", true);
            line.put("initialIssueCodes", initialCodes);
            putInternalCodes(line, benchCase);
            line.put("durationMs", System.currentTimeMillis() - started);
            emit(Json.stringify(line));
            summary.record("invalid", initialCodes);
            return;
        }

        // ── 步骤 2：真实流式生成会话（transport 以 SSE 重放历史 DSL 作为初次生成，轮末校验 + 增量修复）──
        GenerationSdk sdk = GenerationSdk.builder().baseContract(benchCase.contract()).build();
        CaseReplayTransport replay = new CaseReplayTransport(transport, benchCase.dsl(), timeout);
        GenUiGenerator generator =
                GenUiGenerator.withTransport(
                        sdk,
                        repairConfig,
                        replay,
                        GenUiValidationConfig.incremental(maxAttempts),
                        null);
        UiGenerationRequest request =
                UiGenerationRequest.builder()
                        .userInput(benchCase.originalIntent())
                        .response(benchCase.dataModel())
                        .build();
        // 流事件仅是生成过程的实时转发；bench 只消费返回结果与轮记录，不落盘前端帧。
        GenUiGenerationResult generated = generator.generateStream(request, event -> {});

        List<RoundEndResult> rounds = generated.rounds();
        RoundEndResult lastValidated = null;
        for (RoundEndResult round : rounds) {
            if (round.validationResult() != null) {
                lastValidated = round;
            }
        }
        // Strict success: the DSL session itself passed the check. CHECK_PASSED is only set on
        // validated programs, and
        // the equality guard rejects any wrapped fallback output, so a Markdown handoff can never
        // count as repaired.
        boolean repaired =
                generated.endReason() == GenerationEndReason.CHECK_PASSED
                        && generated.validationStatus() == ValidationStatus.VALID
                        && lastValidated != null
                        && lastValidated.validationResult().status() == ValidationStatus.VALID
                        && generated.dsl().equals(lastValidated.currentDefinitions());

        String status;
        if (repaired) {
            status = "repaired";
        } else if (replay.timedOut()) {
            status = "timeout";
        } else if (generated.endReason() == GenerationEndReason.TIMEOUT) {
            status = "timeout";
        } else if (generated.endReason() == GenerationEndReason.MODEL_REQUEST_FAILED) {
            status = "transport-error";
        } else {
            status = "still-invalid";
        }

        LinkedHashMap<String, Object> line = baseLine(benchCase);
        line.put("status", status);
        line.put("success", repaired);
        line.put("reproduced", true);
        line.put("initialIssueCodes", initialCodes);
        putInternalCodes(line, benchCase);
        List<Object> attempts = attempts(rounds);
        line.put("attemptCount", attempts.size());
        line.put("attempts", attempts);
        line.put("endReason", generated.endReason() == null ? null : generated.endReason().name());
        line.put("markdownFallback", generated.endReason() != GenerationEndReason.CHECK_PASSED);
        line.put("outputDsl", generated.dsl());
        line.put(
                "rounds",
                rounds.stream()
                        .map(
                                round -> {
                                    LinkedHashMap<String, Object> record = new LinkedHashMap<>();
                                    record.put("rawOutput", round.rawOutput());
                                    record.put("currentDefinitions", round.currentDefinitions());
                                    record.put("durationMs", round.durationMs());
                                    record.put(
                                            "endReason",
                                            round.endReason() == null
                                                    ? null
                                                    : round.endReason().name());
                                    record.put(
                                            "status",
                                            round.validationResult() == null
                                                    ? null
                                                    : round.validationResult().status().name());
                                    record.put(
                                            "issues",
                                            round.validationResult() == null
                                                    ? List.of()
                                                    : round.validationResult().issues());
                                    return record;
                                })
                        .toList());
        String finalDsl = lastValidated == null ? null : lastValidated.currentDefinitions();
        boolean initialHasCapabilityGap = hasCapabilityGapIssue(v0);
        CapabilityDslInspect dslInspect =
                inspectFinalDsl(finalDsl, benchCase.contract(), externalRefs);
        List<String> consecutiveUnmatched = consecutiveUnmatchedUnknownComponents(rounds);

        line.put(
                "finalIssueCodes",
                lastValidated == null
                        ? List.of()
                        : actionableCodes(lastValidated.validationResult()));
        line.put("finalDsl", finalDsl);
        line.put("capabilityNoticePresent", dslInspect.capabilityNoticePresent());
        line.put("remainingCapabilityGapAliases", dslInspect.remainingGapAliases());
        line.put("consecutiveUnmatchedUnknownComponents", consecutiveUnmatched);
        line.put("durationMs", System.currentTimeMillis() - started);
        emit(Json.stringify(line));
        summary.record(
                status, initialCodes, initialHasCapabilityGap, dslInspect, consecutiveUnmatched);
    }

    /**
     * 把流式生成会话的轮记录映射为逐 attempt 输出：首轮（index 0）是回放的案例 DSL，跳过；修复轮按轮末校验结论计入。 被中断（传输失败 / 超时 /
     * 预算耗尽）的最后一轮没有校验结论，仍记一条 {@code valid=false、issueCodes=null} 的尝试——它确实消耗了一次模型请求。
     */
    private static List<Object> attempts(List<RoundEndResult> rounds) {
        List<Object> attempts = new ArrayList<>();
        for (int index = 1; index < rounds.size(); index++) {
            RoundEndResult round = rounds.get(index);
            if (round.validationResult() == null) {
                continue;
            }
            attempts.add(
                    attemptLine(
                            attempts.size() + 1,
                            round.validationResult().isValid(),
                            actionableCodes(round.validationResult())));
        }
        boolean interruptedRepair =
                rounds.size() > 1 && rounds.get(rounds.size() - 1).validationResult() == null;
        if (interruptedRepair) {
            attempts.add(attemptLine(attempts.size() + 1, false, null));
        }
        return attempts;
    }

    private static LinkedHashMap<String, Object> attemptLine(
            int attempt, boolean valid, List<String> issueCodes) {
        LinkedHashMap<String, Object> attemptLine = new LinkedHashMap<>();
        attemptLine.put("attempt", attempt);
        attemptLine.put("valid", valid);
        attemptLine.put("issueCodes", issueCodes);
        return attemptLine;
    }

    private static LinkedHashMap<String, Object> baseLine(RepairCasePack.RepairCase benchCase) {
        LinkedHashMap<String, Object> line = new LinkedHashMap<>();
        line.put("type", "case");
        line.put("caseId", benchCase.id());
        return line;
    }

    private static void putInternalCodes(
            LinkedHashMap<String, Object> line, RepairCasePack.RepairCase benchCase) {
        if (benchCase.internalIssueCodes() != null) {
            line.put("internalIssueCodes", benchCase.internalIssueCodes());
        }
    }

    /** 全链路统一 actionable 视图（修复提示词消费的口径），升序去重。 */
    private static List<String> actionableCodes(ValidationResult result) {
        return actionableCodes(result.actionableIssues());
    }

    private static List<String> actionableCodes(List<ValidationIssue> issues) {
        return issues.stream().map(ValidationIssue::code).distinct().sorted().toList();
    }

    private static boolean hasCapabilityGapIssue(ValidationResult result) {
        if (result == null) {
            return false;
        }
        return result.actionableIssues().stream()
                .anyMatch(
                        i ->
                                "unknown-component".equals(i.code())
                                        && RepairHints.isCapabilityGapHint(i.hint()));
    }

    private record CapabilityDslInspect(
            boolean capabilityNoticePresent, List<String> remainingGapAliases) {}

    /** 检查修复后最终 DSL 的能力边界落点：可达语句中 {@code capabilityNotice} 是否按模板声明，以及仍调用了哪些能力缺口别名。 */
    private static CapabilityDslInspect inspectFinalDsl(
            String dsl, GenerationContract contract, Set<String> externalRefs) {
        if (dsl == null || dsl.isBlank() || contract == null) {
            return new CapabilityDslInspect(false, List.of());
        }
        BuiltinCatalog builtins = BuiltinCatalog.fromContract(contract);
        Program program = OpenuiParser.parse(dsl, ParseMode.FINAL, builtins);
        Map<String, Statement> stmts = program.currentById();
        if (stmts.isEmpty()) {
            return new CapabilityDslInspect(false, List.of());
        }
        String rootName = contract.root() == null ? "root" : contract.root();
        ProgramAnalysis analysis =
                new ProgramAnalyzer(ContractCatalog.from(contract), builtins)
                        .analyze(program, rootName, externalRefs);
        Set<String> reachable = reachableStatementIds(stmts, analysis.entryId(), builtins);

        Statement noticeStmt = stmts.get("capabilityNotice");
        boolean noticeDeclared =
                noticeStmt instanceof Statement.Value val
                        && val.expr() instanceof AstNode.Comp comp
                        && "TextCallout".equals(comp.name());
        boolean capabilityNoticePresent = noticeDeclared && reachable.contains("capabilityNotice");

        Set<String> remainingAliases = new TreeSet<>();
        for (Map.Entry<String, Statement> entry : stmts.entrySet()) {
            if (reachable.contains(entry.getKey())) {
                collectGapAliases(entry.getValue().expr(), contract, remainingAliases);
            }
        }
        return new CapabilityDslInspect(capabilityNoticePresent, List.copyOf(remainingAliases));
    }

    /** 从语义分析选定的入口语句出发，沿语句间的引用求可达语句 id 集合（不含孤立语句）。 */
    private static Set<String> reachableStatementIds(
            Map<String, Statement> stmts, String entryId, BuiltinCatalog builtins) {
        Set<String> reachable = new HashSet<>();
        if (entryId == null || !stmts.containsKey(entryId)) {
            return reachable;
        }
        Deque<Set<String>> scopeFrames = new ArrayDeque<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(entryId);
        reachable.add(entryId);
        while (!pending.isEmpty()) {
            String statementId = pending.poll();
            Statement statement = stmts.get(statementId);
            // 被引用的 Query/Mutation 声明在物化时按运行时值处理，不展开其表达式（与原可达语义一致）。
            if (!statementId.equals(entryId)
                    && (statement instanceof Statement.Query
                            || statement instanceof Statement.Mutation)) {
                continue;
            }
            Set<String> refs = new LinkedHashSet<>();
            collectRefs(statement.expr(), refs, scopeFrames, builtins);
            for (String ref : refs) {
                if (stmts.containsKey(ref) && reachable.add(ref)) {
                    pending.add(ref);
                }
            }
        }
        return reachable;
    }

    private static void collectRefs(
            AstNode node,
            Set<String> out,
            Deque<Set<String>> scopeFrames,
            BuiltinCatalog builtins) {
        if (node == null) {
            return;
        }
        if (node instanceof AstNode.Ref ref) {
            if (!inScope(scopeFrames, ref.n())) {
                out.add(ref.n());
            }
            return;
        }
        if (node instanceof AstNode.Comp comp) {
            collectCompRefs(comp, out, scopeFrames, builtins);
            return;
        }
        if (node instanceof AstNode.Arr arr) {
            for (AstNode el : arr.els()) {
                collectRefs(el, out, scopeFrames, builtins);
            }
            return;
        }
        if (node instanceof AstNode.Obj obj) {
            for (AstNode.Obj.Entry e : obj.entries()) {
                collectRefs(e.value(), out, scopeFrames, builtins);
            }
            return;
        }
        if (node instanceof AstNode.UnaryOp u) {
            collectRefs(u.operand(), out, scopeFrames, builtins);
            return;
        }
        if (node instanceof AstNode.BinOp b) {
            collectRefs(b.left(), out, scopeFrames, builtins);
            collectRefs(b.right(), out, scopeFrames, builtins);
            return;
        }
        if (node instanceof AstNode.Ternary t) {
            collectRefs(t.cond(), out, scopeFrames, builtins);
            collectRefs(t.then(), out, scopeFrames, builtins);
            collectRefs(t.otherwise(), out, scopeFrames, builtins);
            return;
        }
        if (node instanceof AstNode.Member m) {
            collectRefs(m.obj(), out, scopeFrames, builtins);
            return;
        }
        if (node instanceof AstNode.Index idx) {
            collectRefs(idx.obj(), out, scopeFrames, builtins);
            collectRefs(idx.index(), out, scopeFrames, builtins);
            return;
        }
        if (node instanceof AstNode.Assign a) {
            collectRefs(a.value(), out, scopeFrames, builtins);
        }
    }

    /** 惰性 builtin（{@code @Each}/{@code @Render}）的 binder 只作用于 body，body 内同名引用不算语句引用。 */
    private static void collectCompRefs(
            AstNode.Comp comp,
            Set<String> out,
            Deque<Set<String>> scopeFrames,
            BuiltinCatalog builtins) {
        BuiltinAnalysis.walkArguments(
                builtins,
                comp,
                BuiltinAnalysis.BinderPolicy.REQUIRE_ALL_VALID,
                new BuiltinAnalysis.ArgumentVisitor() {
                    @Override
                    public void value(AstNode argument, String binderName) {
                        if (binderName == null) {
                            collectRefs(argument, out, scopeFrames, builtins);
                        }
                    }

                    @Override
                    public void template(AstNode body, Set<String> binderNames) {
                        scopeFrames.push(binderNames);
                        try {
                            collectRefs(body, out, scopeFrames, builtins);
                        } finally {
                            scopeFrames.pop();
                        }
                    }
                });
    }

    private static boolean inScope(Deque<Set<String>> scopeFrames, String name) {
        for (Set<String> frame : scopeFrames) {
            if (frame.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static void collectGapAliases(
            AstNode node, GenerationContract contract, Set<String> out) {
        if (node == null) {
            return;
        }
        if (node instanceof AstNode.Comp comp) {
            if (RepairHints.findCapabilityGapByAlias(comp.name(), contract.capabilityGaps())
                    != null) {
                out.add(comp.name());
            }
            for (AstNode arg : comp.args()) {
                collectGapAliases(arg, contract, out);
            }
            return;
        }
        if (node instanceof AstNode.Arr arr) {
            for (AstNode el : arr.els()) {
                collectGapAliases(el, contract, out);
            }
            return;
        }
        if (node instanceof AstNode.Obj obj) {
            for (AstNode.Obj.Entry e : obj.entries()) {
                collectGapAliases(e.value(), contract, out);
            }
            return;
        }
        if (node instanceof AstNode.UnaryOp u) {
            collectGapAliases(u.operand(), contract, out);
            return;
        }
        if (node instanceof AstNode.BinOp b) {
            collectGapAliases(b.left(), contract, out);
            collectGapAliases(b.right(), contract, out);
            return;
        }
        if (node instanceof AstNode.Ternary t) {
            collectGapAliases(t.cond(), contract, out);
            collectGapAliases(t.then(), contract, out);
            collectGapAliases(t.otherwise(), contract, out);
            return;
        }
        if (node instanceof AstNode.Member m) {
            collectGapAliases(m.obj(), contract, out);
            return;
        }
        if (node instanceof AstNode.Index idx) {
            collectGapAliases(idx.obj(), contract, out);
            collectGapAliases(idx.index(), contract, out);
            return;
        }
        if (node instanceof AstNode.Assign a) {
            collectGapAliases(a.value(), contract, out);
        }
    }

    /**
     * 统计连续两轮校验中重复出现的“未命中 capabilityGaps.aliases 且非 builtin 漏 @”的同名 unknown-component（#71 D7 / #75
     * AC 6）。
     */
    private static List<String> consecutiveUnmatchedUnknownComponents(List<RoundEndResult> rounds) {
        if (rounds == null || rounds.size() < 2) {
            return List.of();
        }
        Set<String> repeated = new TreeSet<>();
        Set<String> prevUnmatched = null;
        for (RoundEndResult round : rounds) {
            if (round == null || round.validationResult() == null) {
                continue;
            }
            Set<String> currentUnmatched = new LinkedHashSet<>();
            for (ValidationIssue issue : round.validationResult().actionableIssues()) {
                if ("unknown-component".equals(issue.code())
                        && issue.component() != null
                        && !issue.component().isBlank()
                        && !RepairHints.isMissingAtHint(issue.hint())
                        && !RepairHints.isCapabilityGapHint(issue.hint())) {
                    currentUnmatched.add(issue.component());
                }
            }
            if (prevUnmatched != null) {
                for (String name : currentUnmatched) {
                    if (prevUnmatched.contains(name)) {
                        repeated.add(name);
                    }
                }
            }
            prevUnmatched = currentUnmatched;
        }
        return List.copyOf(repeated);
    }

    private synchronized void emit(String line) {
        emitted.add(line);
        out.println(line);
        out.flush();
    }

    /** 本次运行已写出的全部 JSONL 行（含末尾 summary），供报告生成复用，避免二次解析丢失字段。 */
    List<String> emittedLines() {
        synchronized (emitted) {
            return List.copyOf(emitted);
        }
    }

    /**
     * 单案例 transport 包装，三种请求都用 SSE 应答（{@code generateStream} 只走 {@code postStream}）：
     *
     * <ul>
     *   <li><b>初次生成</b>——把案例 DSL 作为一条 SSE 增量回放（bench 复现历史失败，不打模型）。
     *   <li><b>增量修复</b>——转交真实 transport 的 SSE 流；每次转交前检查 {@code --timeout-minutes} 墙钟预算，命中后抛 {@link
     *       LlmTransportException} 并记录原因。
     *   <li><b>整页 Markdown 降级</b>——返回本地桩 SSE（修复失败不应额外消耗一次模型调用）。
     * </ul>
     *
     * <p>修复尝试次数由生成会话的 {@code maxIncrementalAttempts} 控制（与 CLI 的 {@code --max-attempts}
     * 同值）；本包装只负责超时检查与请求分类。同步 {@code post} 显式拒绝：bench 全链路只走流式。
     */
    private static final class CaseReplayTransport implements InMemoryLlmTransport {

        /** 与 {@code MarkdownGeneration} 的固定 system 文案对齐，用于识别整页降级请求。 */
        private static final String MARKDOWN_SYSTEM_HINT =
                "Answer the user's request in Markdown only.";

        /** 修复失败时整页降级的桩内容；不进入判定，只让 Markdown 交接正常完成。 */
        private static final String MARKDOWN_STUB =
                "Incremental repair did not produce a valid page.";

        private final LlmTransport delegate;

        private final String initialDsl;

        private final Duration timeout;

        private final long repairStartedNanos = System.nanoTime();

        private final AtomicInteger repairCalls = new AtomicInteger();

        private volatile boolean timedOut;

        CaseReplayTransport(LlmTransport delegate, String initialDsl, Duration timeout) {
            this.delegate = delegate;
            this.initialDsl = initialDsl;
            this.timeout = timeout == null ? Duration.ZERO : timeout;
        }

        boolean timedOut() {
            return timedOut;
        }

        @Override
        public String post(String body) throws LlmTransportException {
            // bench 已完全迁到流式：generateStream 只经 newCall(stream=true)/postStream。保留同步入口只会
            // 形成未经测试的死支路，因此显式拒绝。
            throw new UnsupportedOperationException(
                    "repair-bench is streaming-only; use postStream");
        }

        @Override
        public InputStream postStream(String body) throws LlmTransportException {
            return switch (classify(body)) {
                case INITIAL -> sseResponse(initialDsl);
                case MARKDOWN -> sseResponse(MARKDOWN_STUB);
                case REPAIR -> repairStream(body);
            };
        }

        private InputStream repairStream(String body) throws LlmTransportException {
            checkTimeout();
            repairCalls.incrementAndGet();
            return delegate.postStream(body);
        }

        private void checkTimeout() throws LlmTransportException {
            if (!timeout.isZero() && System.nanoTime() - repairStartedNanos >= timeout.toNanos()) {
                timedOut = true;
                throw new LlmTransportException(
                        "repair timeout after " + repairCalls.get() + " attempt(s)");
            }
        }

        private static RequestKind classify(String body) {
            try {
                Map<String, Object> parsed = Json.asObject(Json.parse(body), "request body");
                List<Object> messages = Json.asList(parsed.get("messages"), "messages");
                StringBuilder system = new StringBuilder();
                for (Object item : messages) {
                    Map<String, Object> message = Json.asObject(item, "message");
                    if ("system".equals(message.get("role")) && message.get("content") != null) {
                        system.append(message.get("content"));
                    }
                }
                String text = system.toString();
                if (text.contains(RepairPromptBuilder.INCREMENTAL_REPAIR_MARKER)) {
                    return RequestKind.REPAIR;
                }
                if (text.contains(MARKDOWN_SYSTEM_HINT)) {
                    return RequestKind.MARKDOWN;
                }
                return RequestKind.INITIAL;
            } catch (RuntimeException error) {
                // 无法解析的请求按修复处理：交给真实 transport 暴露问题，而不是伪造初次生成。
                return RequestKind.REPAIR;
            }
        }

        /**
         * 单增量 SSE 流：一个 {@code delta.content} 帧 + {@code [DONE]}。SseDeltaParser 在 {@code [DONE]} 或
         * {@code finish_reason=stop} 时结束；这里用前者，避免额外的 finish 帧。
         */
        private static InputStream sseResponse(String content) {
            String delta =
                    "data: {\"choices\":[{\"delta\":{\"content\":"
                            + Json.stringify(content)
                            + "}}]}\n\n";
            return new ByteArrayInputStream(
                    (delta + SseFrames.done()).getBytes(StandardCharsets.UTF_8));
        }

        private enum RequestKind {
            INITIAL,
            REPAIR,
            MARKDOWN
        }
    }

    /**
     * 批次汇总累加器（worker 线程共享，全部方法 synchronized）。口径（实现设计 §5.2）：
     *
     * <ul>
     *   <li>主口径 = repaired / (repaired + still-invalid)——transport-error 与 timeout 剔除分母（基础设施噪声与
     *       自设预算不算"修不动"，可筛出重跑）；分母为 0 时 rate 为 null，并通过 {@code mainRateExcludes} 明示被剔除的状态。
     *   <li>端到端口径 = repaired / (repaired + still-invalid + timeout + transport-error +
     *       error)——不剔除任何运行期 失败，not-reproduced 仍排除（它不是修复尝试）。
     *   <li>副口径 = repaired / total（not-reproduced 计入惩罚，度量外部环境保真度）。
     *   <li>issueBuckets 仅统计 reproduced 案例，按 initialIssueCodes 分桶（一案例可入多桶）。
     * </ul>
     */
    private static final class SummaryAccumulator {
        private final Map<String, int[]> byStatus = new TreeMap<>();

        private final Map<String, Map<String, Integer>> buckets = new TreeMap<>();

        private int total;

        private int capabilityGapCases;

        private int capabilityGapRepaired;

        private int capabilityNoticeCount;

        private int casesWithRemainingGapCalls;

        private int consecutiveUnmatchedCases;

        private int consecutiveUnmatchedTotal;

        synchronized void record(String status, List<String> initialCodes) {
            record(status, initialCodes, false, null, List.of());
        }

        synchronized void record(
                String status,
                List<String> initialCodes,
                boolean initialHasCapabilityGap,
                CapabilityDslInspect dslInspect,
                List<String> consecutiveUnmatched) {
            total++;
            byStatus.computeIfAbsent(status, key -> new int[1])[0]++;
            if (initialCodes != null) {
                for (String code : initialCodes) {
                    Map<String, Integer> bucket =
                            buckets.computeIfAbsent(code, key -> new TreeMap<>());
                    bucket.put("cases", bucket.getOrDefault("cases", 0) + 1);
                    bucket.put(status, bucket.getOrDefault(status, 0) + 1);
                }
            }
            if (initialHasCapabilityGap) {
                capabilityGapCases++;
                if ("repaired".equals(status)) {
                    capabilityGapRepaired++;
                    if (dslInspect != null && dslInspect.capabilityNoticePresent()) {
                        capabilityNoticeCount++;
                    }
                }
            }
            if (dslInspect != null && !dslInspect.remainingGapAliases().isEmpty()) {
                casesWithRemainingGapCalls++;
            }
            if (consecutiveUnmatched != null && !consecutiveUnmatched.isEmpty()) {
                consecutiveUnmatchedCases++;
                consecutiveUnmatchedTotal += consecutiveUnmatched.size();
            }
        }

        synchronized Map<String, Object> toLine(
                String model, int maxAttempts, int concurrency, Duration timeout, long durationMs) {
            long repaired = count("repaired");
            long stillInvalid = count("still-invalid");

            LinkedHashMap<String, Object> line = new LinkedHashMap<>();
            line.put("type", "summary");
            line.put("model", model);
            line.put("maxAttempts", maxAttempts);
            line.put("concurrency", concurrency);
            line.put("timeoutMinutes", timeout.toMinutes());
            line.put("total", total);
            LinkedHashMap<String, Object> statusCounts = new LinkedHashMap<>();
            for (Map.Entry<String, int[]> entry : byStatus.entrySet()) {
                statusCounts.put(entry.getKey(), entry.getValue()[0]);
            }
            line.put("byStatus", statusCounts);

            long mainDenominator = repaired + stillInvalid;
            LinkedHashMap<String, Object> mainRate = new LinkedHashMap<>();
            mainRate.put("success", repaired);
            mainRate.put("denominator", mainDenominator);
            mainRate.put("rate", mainDenominator == 0 ? null : (double) repaired / mainDenominator);
            line.put("mainRate", mainRate);
            line.put(
                    "mainRateExcludes",
                    List.of("timeout", "transport-error", "error", "not-reproduced"));
            long endToEndDenominator =
                    repaired
                            + stillInvalid
                            + count("timeout")
                            + count("transport-error")
                            + count("error");
            LinkedHashMap<String, Object> endToEndRate = new LinkedHashMap<>();
            endToEndRate.put("success", repaired);
            endToEndRate.put("denominator", endToEndDenominator);
            endToEndRate.put(
                    "rate",
                    endToEndDenominator == 0 ? null : (double) repaired / endToEndDenominator);
            line.put("endToEndRate", endToEndRate);
            line.put(
                    "sessionBudgetSeconds",
                    java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(
                            GenerationTiming.BUDGET_NANOS));

            LinkedHashMap<String, Object> secondaryRate = new LinkedHashMap<>();
            secondaryRate.put("success", repaired);
            secondaryRate.put("denominator", total);
            secondaryRate.put("rate", total == 0 ? null : (double) repaired / total);
            line.put("secondaryRate", secondaryRate);

            LinkedHashMap<String, Object> issueBuckets = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Integer>> entry : buckets.entrySet()) {
                LinkedHashMap<String, Object> bucket = new LinkedHashMap<>();
                Map<String, Integer> counts = entry.getValue();
                bucket.put("cases", counts.get("cases"));
                putIfPresent(bucket, counts, "repaired");
                putIfPresent(bucket, counts, "still-invalid");
                putIfPresent(bucket, counts, "transport-error");
                putIfPresent(bucket, counts, "timeout");
                for (Map.Entry<String, Integer> extra : counts.entrySet()) {
                    bucket.putIfAbsent(extra.getKey(), extra.getValue());
                }
                issueBuckets.put(entry.getKey(), bucket);
            }
            line.put("issueBuckets", issueBuckets);
            LinkedHashMap<String, Object> capabilityBoundary = new LinkedHashMap<>();
            capabilityBoundary.put("capabilityGapCases", capabilityGapCases);
            capabilityBoundary.put("capabilityGapRepaired", capabilityGapRepaired);
            capabilityBoundary.put("capabilityNoticeCount", capabilityNoticeCount);
            capabilityBoundary.put(
                    "capabilityNoticeRate",
                    capabilityGapRepaired == 0
                            ? null
                            : (double) capabilityNoticeCount / capabilityGapRepaired);
            capabilityBoundary.put("casesWithRemainingGapCalls", casesWithRemainingGapCalls);
            capabilityBoundary.put(
                    "consecutiveUnmatchedUnknownComponentCases", consecutiveUnmatchedCases);
            capabilityBoundary.put(
                    "consecutiveUnmatchedUnknownComponentCount", consecutiveUnmatchedTotal);
            line.put("capabilityBoundary", capabilityBoundary);
            line.put("durationMs", durationMs);
            return line;
        }

        private static void putIfPresent(
                Map<String, Object> bucket, Map<String, Integer> counts, String key) {
            if (counts.containsKey(key)) {
                bucket.put(key, counts.get(key));
            }
        }

        private long count(String status) {
            int[] holder = byStatus.get(status);
            return holder == null ? 0 : holder[0];
        }
    }
}
