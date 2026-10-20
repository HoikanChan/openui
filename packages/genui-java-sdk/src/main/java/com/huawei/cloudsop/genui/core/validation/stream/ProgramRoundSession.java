/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.stream;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.validation.OpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.parser.OpenuiLexer;
import com.huawei.cloudsop.genui.core.validation.parser.StatementSplitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 一轮模型输出的程序状态与轮末统一校验(#56;对应父 Spec #54 的 Program State &amp; Validation 模块)。
 *
 * <p>
 * <b>职责。</b>增量接收模型输出,{@link StatementBoundaryScanner} 以语法完整性识别完整语句后,按语句 id 维护 <b>同名 last-wins</b>
 * 的当前程序(新名称追加,同名覆盖,不支持删除)。轮结束({@link #onRoundEnd()})时对 <b>当前有效定义</b>——而非原始全文——执行<b>一次</b> FINAL
 * 统一校验(语法、引用、组件与算子契约、参数、数据路径、 静态类型、页面完整性),并返回 {@link RoundEndResult}(原始输出、当前定义、诊断、耗时、契约版本)。
 *
 * <p>
 * <b>轮内不做校验。</b>后置定义不提前报错;轮结束仍缺失的引用按错误处理。同一份输出任意分片、跨行传输,当前程序 与校验结论一致——每条完整语句只解析一次(不逐片段重放全部历史)。
 *
 * <p>
 * <b>历史定义。</b>被覆盖的语句不进入当前程序,其历史错误(语法/语义/类型)不再产生当前错误;原始输出保留在 {@link #rawOutput()} 供排查。语法错误语句的 id
 * 仍可提取,因此它保留在当前程序中,其语法错误在轮末被报告。
 *
 * <p>
 * 本类不发模型请求、不控制展示;校验使用当前请求生效契约(无第二份能力白名单)。public 方法均 {@code synchronized}: 实例跨轮复用,消费线程(流式泵/修复循环)与调度/快照线程可能并发访问。
 *
 * <p>
 * #58 起一个生成会话复用一个实例:{@link #beginRound()} 清空语句边界扫描器与本轮计时,但保留当前程序,使修复补丁按同名覆盖合并; 不同模型请求的半句绝不跨轮拼接。
 *
 * @since 2026
 */
public final class ProgramRoundSession {

    private StatementBoundaryScanner scanner = new StatementBoundaryScanner();
    private final OpenuiLangValidator validator;
    private final GenerationContract contract;
    private final String rootName;
    private final Set<String> externalRefs;
    private final Map<String, Object> dataModel;
    private final String contractVersion;
    private long startNanos;

    /** 语句 id → 最新语句文本(LinkedHashMap:同名 put 覆盖值、保留首次出现顺序)。 */
    private final LinkedHashMap<String, String> currentStatements = new LinkedHashMap<>();

    /**
     * 构造轮会话。
     *
     * @param validator
     *            轮末统一校验使用的校验器
     * @param contract
     *            当前请求生效的合并契约,可为 {@code null}
     * @param rootName
     *            预期的根组件名,可为 {@code null}
     * @param externalRefs
     *            运行时才解析的外部引用名集合(如宿主数据绑定 {@code data});{@code null} 视同空集合
     * @param dataModel
     *            完整 Render Data Model;{@code null} 表示不启用数据类型校验
     */
    public ProgramRoundSession(OpenuiLangValidator validator, GenerationContract contract, String rootName,
            Set<String> externalRefs, Map<String, Object> dataModel) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.contract = contract;
        this.rootName = rootName;
        this.externalRefs = externalRefs == null ? Set.of() : Set.copyOf(externalRefs);
        this.dataModel = dataModel == null ? null : Map.copyOf(dataModel);
        this.contractVersion = contract == null ? null : contract.contractVersion();
        this.startNanos = System.nanoTime();
    }

    /**
     * 开始新一轮模型输出(#58):重置语句边界扫描器与本轮计时,<b>保留</b>跨轮的同名 last-wins 当前程序。
     *
     * <p>
     * 修复请求是独立的模型输出,不得与上一轮半句拼接:新请求从空缓冲区开始,上一轮未完成的尾部已由 {@link #onRoundEnd()} / {@link #drainToCurrentDefinitions()} 处理。
     */
    public synchronized void beginRound() {
        scanner.reset();
        startNanos = System.nanoTime();
    }

    /**
     * 本轮已进行的墙钟毫秒数。供中断轮记录使用。
     *
     * @return 本轮耗时(毫秒)
     */
    public synchronized long elapsedMs() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * 本会话使用的合并契约版本;无契约时为 {@code null}。
     *
     * @return 契约版本或 {@code null}
     */
    public String contractVersion() {
        return contractVersion;
    }

    /**
     * 送入一段模型原始输出增量。语句以语法完整性为准识别;识别出的完整语句按 id 覆盖或追加进当前程序。
     *
     * @param delta
     *            增量文本,可为 {@code null}
     */
    public synchronized void onDelta(String delta) {
        for (String statement : scanner.onDelta(delta)) {
            apply(statement);
        }
    }

    /**
     * 轮结束:刷出并自动闭合尾部待定语句,然后对当前有效定义执行一次 FINAL 统一校验。
     *
     * @return 轮末结果与记录
     */
    public synchronized RoundEndResult onRoundEnd() {
        for (String statement : scanner.drainAtEnd()) {
            apply(statement);
        }
        String definitions = currentDefinitions();
        ValidationResult result = validator.validate(ValidationRequest.builder().dsl(definitions).contract(contract)
                .rootName(rootName).externalRefs(externalRefs).dataModel(dataModel).mode(ValidationMode.FINAL).build());
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        return new RoundEndResult(rawOutput(), definitions, result, durationMs, contractVersion, null);
    }

    /**
     * 获取当前有效定义——按首次出现顺序拼接的同名 last-wins 语句文本。
     *
     * @return 当前有效定义文本;空程序返回空字符串
     */
    public synchronized String currentDefinitions() {
        return String.join("\n", currentStatements.values());
    }

    /**
     * 尽力而为地刷出待定尾部并返回当前有效定义,<b>不</b>执行轮末校验。供流中途失败等不可信内容场景使用:返回已 累计内容的当前程序形态,但不产生校验结论或轮记录。
     *
     * @return 刷出待定尾部后的当前有效定义文本
     */
    public synchronized String drainToCurrentDefinitions() {
        for (String statement : scanner.drainAtEnd()) {
            apply(statement);
        }
        return currentDefinitions();
    }

    /**
     * 获取本轮原始输出(未经预处理,含围栏与注释)。
     *
     * @return 原始输出文本
     */
    public synchronized String rawOutput() {
        return scanner.rawBuffer();
    }

    /** 提取一条完整语句的 id(splitter 级,语法容错:表达式解析失败也能拿到 id),并按 last-wins 放入当前程序。 */
    private void apply(String statement) {
        String id = statementIdOf(statement);
        if (id == null) {
            return; // 连 id 形状都不具备的行(splitter 已跳过);不进入当前程序
        }
        currentStatements.put(id, statement);
    }

    /** 用既有 lexer + splitter 提取语句 id(复用 parser,不引入第二套识别规则)。 */
    private static String statementIdOf(String statement) {
        List<StatementSplitter.RawStmt> stmts = StatementSplitter.split(OpenuiLexer.tokenize(statement));
        return stmts.isEmpty() ? null : stmts.get(0).id();
    }
}
