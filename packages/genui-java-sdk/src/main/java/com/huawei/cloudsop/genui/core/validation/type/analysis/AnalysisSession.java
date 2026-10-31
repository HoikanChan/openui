/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;
import com.huawei.cloudsop.genui.core.validation.parser.Program;
import com.huawei.cloudsop.genui.core.validation.parser.SourceSpan;
import com.huawei.cloudsop.genui.core.validation.parser.Statement;
import com.huawei.cloudsop.genui.core.validation.semantic.AstReferenceWalker;
import com.huawei.cloudsop.genui.core.validation.semantic.ComponentDef;
import com.huawei.cloudsop.genui.core.validation.semantic.ContractCatalog;
import com.huawei.cloudsop.genui.core.validation.semantic.ParamDef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

/**
 * 单次 FINAL 校验的类型分析会话(#77 第 4.2 节):有效定义、词法环境、使用上下文、检查登记。
 *
 * <p>只遍历当前有效程序({@code Program#currentById} 同名 last-wins)中从入口可达的取值语句——被覆盖的旧定义 与不可达辅助语句不产生当前错误(ADR
 * 0004)。无 Render Data Model 时仍检查字面量、调用数量、已知组件与 固定返回类型,数据依赖部分记 Unknown + UNKNOWN_INPUT。
 *
 * <p>检查纪律:MISMATCH 才产生 {@link ValidationIssue}(ERROR,沿用现有 code);UNPROVEN 只进 {@link
 * CoverageReport},不单独触发修复;上游错误身份关联的后续检查登记为 blocked,不产生独立反例。
 *
 * @since 2026
 */
public final class AnalysisSession {

    /** 带 cell 模板的 Table 列形如 {@code column(field, label, {cell: Render(...)})}。 */
    private static final int COLUMN_OPTIONS_ARG_INDEX = 2;

    private static final int COLUMN_MIN_ARGS_FOR_OPTIONS = COLUMN_OPTIONS_ARG_INDEX + 1;

    /**
     * 会话输入:程序、组件目录、builtin 词表、Render Data Model 快照(可空)、根语句 id、预算。
     *
     * @param program 已解析的取值程序
     * @param catalog 组件目录
     * @param builtins builtin 词表
     * @param dataModel Render Data Model 快照;null 表示无快照,数据依赖部分记 UNKNOWN_INPUT
     * @param entryId 入口语句 id;null 或不存在时没有可达起点
     * @param budget 会话预算;null 时使用 {@link AnalysisBudget#defaults()}
     * @since 2026
     */
    public record Input(
            Program program,
            ContractCatalog catalog,
            BuiltinCatalog builtins,
            Map<String, Object> dataModel,
            String entryId,
            AnalysisBudget budget) {

        /**
         * 紧凑构造方法:校验程序与两个目录非 null,并补齐默认预算。
         *
         * @throws IllegalArgumentException 程序、组件目录或 builtin 词表为 null 时
         */
        public Input {
            if (program == null || catalog == null || builtins == null) {
                throw new IllegalArgumentException("program, catalog and builtins are required");
            }
            budget = budget == null ? AnalysisBudget.defaults() : budget;
        }
    }

    /**
     * 会话输出:诊断(仅 MISMATCH)与覆盖报告。
     *
     * @param issues 由 MISMATCH 产生的诊断,按发现顺序排列
     * @param coverage 本轮检查要求的覆盖统计
     * @since 2026
     */
    public record Output(List<ValidationIssue> issues, CoverageReport coverage) {}

    /**
     * 模板使用位置的诊断身份:诊断归属实际使用处(语句与路径),模板声明语句另行保留可追溯位置 (#77 第 10.3/11.3 节)。同一模板的不同使用位置不得共用去重身份。
     *
     * @since 2026
     */
    private record TemplateUseSite(
            String pathPrefix,
            String coverageUseSite,
            String statementId,
            String declarationStatementId) {}

    private final Input input;
    private final AnalysisBudget.Tracker budget;
    private final TypeOps ops;
    private final BuiltinAnalysis builtinAnalysis;
    private final DataPathResolver pathResolver = new DataPathResolver();
    private final CoverageReport coverage = new CoverageReport();
    private final List<ValidationIssue> issues = new ArrayList<>();
    private final Map<String, AstNode> symbols = new LinkedHashMap<>();
    private final Map<String, SourceSpan> spans = new LinkedHashMap<>();
    private final Map<String, String> emitted = new LinkedHashMap<>();
    private final Deque<TemplateUseSite> templateUseSites = new ArrayDeque<>();
    private int errorSequence;

    private AnalysisSession(Input input) {
        this.input = input;
        this.budget = input.budget().tracker();
        this.ops = new TypeOps(budget);
        this.builtinAnalysis = new BuiltinAnalysis(input.builtins(), this);
        for (Statement statement : input.program().statements()) {
            spans.put(statement.id(), statement.span());
        }
        Map<String, Statement> current = input.program().currentById();
        for (Map.Entry<String, Statement> entry : current.entrySet()) {
            AstNode expr = entry.getValue().expr();
            if (expr != null) {
                symbols.put(entry.getKey(), expr);
            }
        }
    }

    /**
     * 执行一次完整分析。
     *
     * <p>遍历入口可达的取值语句并登记检查结论;会话预算发生过精度损失时,同步把覆盖报告标记为 遍历截断。
     *
     * @param input 会话输入
     * @return 仅 MISMATCH 产生的诊断与覆盖报告
     */
    public static Output analyze(Input input) {
        AnalysisSession session = new AnalysisSession(input);
        session.run();
        if (session.budget.precisionLost()) {
            session.coverage.markPrecisionLost();
        }
        return new Output(List.copyOf(session.issues), session.coverage);
    }

    private void run() {
        Set<String> roots = reachableRoots();
        LexicalEnvironment rootEnv = LexicalEnvironment.root();
        for (String id : roots) {
            AstNode expr = symbols.get(id);
            if (expr == null) {
                continue;
            }
            if (!budget.tryConsumeCheck()) {
                coverage.markTraversalTruncated();
                break;
            }
            synth(expr, id, rootEnv, new LinkedHashSet<>());
        }
        // 根遍历结束后由 builtin module 补查未消费的延迟模板;补查中新发现的模板继续处理。
        builtinAnalysis.finish();
    }

    /**
     * 可达起点:入口语句 + 从其表达式递归可达的取值语句(同一 currentById 口径;state/query/mutation 的
     * 引用收集照走,但它们自身不作为类型检查起点——既有入口规则)。
     */
    private Set<String> reachableRoots() {
        Set<String> reached = new LinkedHashSet<>();
        if (input.entryId() == null || !symbols.containsKey(input.entryId())) {
            return reached;
        }
        Deque<String> work = new ArrayDeque<>();
        work.add(input.entryId());
        while (!work.isEmpty()) {
            String id = work.poll();
            if (!reached.add(id)) {
                continue;
            }
            AstNode expr = symbols.get(id);
            if (expr == null) {
                continue;
            }
            Set<String> refs = new LinkedHashSet<>();
            AstReferenceWalker.walkReachability(
                    expr,
                    input.builtins(),
                    (name, scope) -> {
                        if (!"data".equals(name)) {
                            refs.add(name);
                        }
                    });
            refs.stream().filter(symbols::containsKey).forEach(work::add);
        }
        return reached;
    }

    // ───────────────────────────────────────────────────────────────────────
    // 词法环境
    // ───────────────────────────────────────────────────────────────────────

    /**
     * 不可变父子链词法环境;binder 只影响声明的 body,嵌套同名正常遮蔽。
     *
     * @since 2026
     */
    static final class LexicalEnvironment {
        private final LexicalEnvironment parent;
        private final String name;
        private final Fact value;

        private LexicalEnvironment(LexicalEnvironment parent, String name, Fact value) {
            this.parent = parent;
            this.name = name;
            this.value = value;
        }

        static LexicalEnvironment root() {
            return new LexicalEnvironment(null, null, null);
        }

        LexicalEnvironment bind(String boundName, Fact boundValue) {
            return new LexicalEnvironment(this, boundName, boundValue);
        }

        Optional<Fact> lookup(String lookedUp) {
            for (LexicalEnvironment env = this; env != null; env = env.parent) {
                if (env.name != null && env.name.equals(lookedUp)) {
                    return Optional.ofNullable(env.value);
                }
            }
            return Optional.empty();
        }

        boolean isBound(String lookedUp) {
            for (LexicalEnvironment env = this; env != null; env = env.parent) {
                if (env.name != null && env.name.equals(lookedUp)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 把本环境(更内层、优先)的绑定叠加到 {@code base} 上,用于在使用位置保留声明环境并继承使用位置绑定 (ADR 0006):同名以本环境为准,退出后自然回到
         * {@code base} 的绑定。
         */
        LexicalEnvironment over(LexicalEnvironment base) {
            List<LexicalEnvironment> chain = new ArrayList<>();
            for (LexicalEnvironment env = this; env != null && env.name != null; env = env.parent) {
                chain.add(env);
            }
            LexicalEnvironment result = base == null ? root() : base;
            for (int index = chain.size() - 1; index >= 0; index--) {
                LexicalEnvironment node = chain.get(index);
                result = result.bind(node.name, node.value);
            }
            return result;
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 表达式推断(synth)
    // ───────────────────────────────────────────────────────────────────────

    Fact synth(AstNode node, String statementId, LexicalEnvironment env, Set<String> resolving) {
        if (node == null) {
            return Fact.unknown(Fact.Loss.MISSING_CONTEXT);
        }
        if (node instanceof AstNode.Str string) {
            return Fact.of(Types.stringLiterals(Set.of(string.v())));
        }
        if (node instanceof AstNode.Num) {
            return Fact.of(Types.scalar(Type.ScalarKind.NUMBER));
        }
        if (node instanceof AstNode.Bool) {
            return Fact.of(Types.scalar(Type.ScalarKind.BOOLEAN));
        }
        if (node instanceof AstNode.Null) {
            return Fact.of(Types.scalar(Type.ScalarKind.NULL));
        }
        if (node instanceof AstNode.Arr array) {
            return synthArray(array, statementId, env, resolving);
        }
        if (node instanceof AstNode.Obj object) {
            return synthObject(object, statementId, env, resolving);
        }
        if (node instanceof AstNode.Ref || node instanceof AstNode.StateRef) {
            return synthRef(node, statementId, env, resolving);
        }
        if (node instanceof AstNode.Member member) {
            return synthMember(member, statementId, env, resolving);
        }
        if (node instanceof AstNode.Index index) {
            return synthIndex(index, statementId, env, resolving);
        }
        if (node instanceof AstNode.Comp comp) {
            return synthCall(comp, statementId, env, resolving);
        }
        if (node instanceof AstNode.Ternary ternary) {
            synth(ternary.cond(), statementId, env, resolving);
            Fact thenFact = synth(ternary.then(), statementId, env, resolving);
            Fact elseFact = synth(ternary.otherwise(), statementId, env, resolving);
            return join(thenFact, elseFact);
        }
        if (node instanceof AstNode.BinOp binary) {
            return synthBinOp(binary, statementId, env, resolving);
        }
        if (node instanceof AstNode.UnaryOp unary) {
            synth(unary.operand(), statementId, env, resolving);
            return Fact.of(
                    Types.scalar(
                            "!".equals(unary.op())
                                    ? Type.ScalarKind.BOOLEAN
                                    : Type.ScalarKind.NUMBER));
        }
        if (node instanceof AstNode.Assign assign) {
            return synth(assign.value(), statementId, env, resolving);
        }
        return Fact.unknown(Fact.Loss.UNSUPPORTED_RULE);
    }

    private Fact synthArray(
            AstNode.Arr array, String statementId, LexicalEnvironment env, Set<String> resolving) {
        if (array.els().isEmpty()) {
            return Fact.of(Types.emptySequence());
        }
        List<Type> elements = new ArrayList<>();
        Set<Fact.Loss> losses = new LinkedHashSet<>();
        Set<String> upstream = new LinkedHashSet<>();
        for (AstNode element : array.els()) {
            Fact elementFact = synth(element, statementId, env, resolving);
            elements.add(elementFact.type());
            losses.addAll(elementFact.losses());
            upstream.addAll(elementFact.upstreamErrors());
        }
        Type element = Types.union(elements);
        return new Fact(
                Types.sequence(element, array.els().size(), array.els().size()),
                Set.of(new Fact.Evidence.ElementPresence()),
                losses,
                upstream);
    }

    private Fact synthObject(
            AstNode.Obj object, String statementId, LexicalEnvironment env, Set<String> resolving) {
        Map<String, Type.Field> fields = new LinkedHashMap<>();
        Set<Fact.Loss> losses = new LinkedHashSet<>();
        Set<String> upstream = new LinkedHashSet<>();
        for (AstNode.Obj.Entry entry : object.entries()) {
            Fact valueFact = synth(entry.value(), statementId, env, resolving);
            // 对象字面量重名键按语言覆盖规则:last-wins,不因未知值省略该键。
            fields.put(entry.key(), Type.Field.required(valueFact.type()));
            losses.addAll(valueFact.losses());
            upstream.addAll(valueFact.upstreamErrors());
        }
        return new Fact(Types.record(fields, Type.Extra.closed()), Set.of(), losses, upstream);
    }

    private Fact synthRef(
            AstNode node, String statementId, LexicalEnvironment env, Set<String> resolving) {
        String name = node instanceof AstNode.Ref ref ? ref.n() : ((AstNode.StateRef) node).n();
        Optional<Fact> bound = env.lookup(name);
        if (bound.isPresent()) {
            return bound.get();
        }
        if (env.isBound(name)) {
            // binder 名已注册但当前使用位置未提供值(如 Render 的 v/row 无上下文)。
            return Fact.unknown(Fact.Loss.MISSING_CONTEXT);
        }
        if (node instanceof AstNode.StateRef) {
            // 可变 State 初始值不代表交互后的所有值。
            return Fact.unknown(Fact.Loss.UNKNOWN_INPUT);
        }
        if ("data".equals(name)) {
            return dataRootFact();
        }
        if (!symbols.containsKey(name) || !resolving.add(name)) {
            return Fact.unknown(Fact.Loss.UNKNOWN_INPUT);
        }
        try {
            // 符号引用即一个数量来源:消费方比较同一符号派生的数组数量时不需要再回溯表达式(#121)。
            return synth(symbols.get(name), name, env, resolving)
                    .withEvidence(new Fact.Evidence.LengthSource("ref:" + name));
        } finally {
            resolving.remove(name);
        }
    }

    private Fact dataRootFact() {
        if (input.dataModel() == null) {
            return Fact.unknown(Fact.Loss.UNKNOWN_INPUT);
        }
        return SnapshotFacts.typeOfValue(input.dataModel(), "data", 0, budget);
    }

    private Fact synthMember(
            AstNode.Member member,
            String statementId,
            LexicalEnvironment env,
            Set<String> resolving) {
        Optional<DataPath> dataPath = dataPathOf(member);
        if (dataPath.isPresent()) {
            return resolveDataPath(dataPath.get(), statementId);
        }
        Fact base = synth(member.obj(), statementId, env, resolving);
        Fact field = ops.readField(base, member.field());
        if (field.type() instanceof Type.Never && !base.hasUpstreamErrors()) {
            // 不确定存在的 binder 元素保留专用标记;本地对象即使没有观察值,关闭形状仍能证明缺字段。
            boolean existenceUnproven = base.losses().contains(Fact.Loss.NO_ELEMENT_EVIDENCE);
            if (existenceUnproven) {
                return Fact.unknown(Fact.Loss.NO_ELEMENT_EVIDENCE);
            }
            // 确定缺失:对象关闭且未列出该字段(基值与空数组可能性无关,或观察证据覆盖)。
            String errorId = nextErrorId();
            emit(
                    "type-field-missing",
                    "Field \""
                            + member.field()
                            + "\" is absent from "
                            + base.type().displayName()
                            + ".",
                    statementId,
                    null,
                    null,
                    "Use a field present on the object shape.");
            return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamError(errorId);
        }
        if (!base.hasUpstreamErrors()
                && !base.losses().contains(Fact.Loss.NO_ELEMENT_EVIDENCE)
                && observedMissingRow(base, member.field())) {
            // 观察到的行确定缺该字段是具体反例(#77 §6.2):上界合并掩盖部分缺失(异构行)时,
            // 当前快照里该行确实存在且没有该字段;与纯类型级确定缺失同一诊断。
            String errorId = nextErrorId();
            emit(
                    "type-field-missing",
                    "Field \""
                            + member.field()
                            + "\" is absent from an observed row of "
                            + base.type().displayName()
                            + ".",
                    statementId,
                    null,
                    null,
                    "Use a field present on every observed row.");
            return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamError(errorId);
        }
        return field;
    }

    /** 基值的行级观察证据中存在确定缺该字段的行(读取侧只关联存在的字段值,缺失由本判定补全)。 */
    private static boolean observedMissingRow(Fact base, String field) {
        for (Fact.Evidence item : base.evidence()) {
            if (item instanceof Fact.Evidence.ObservedValue observed
                    && observed.value() instanceof Map<?, ?> row
                    && !row.containsKey(field)) {
                return true;
            }
        }
        return false;
    }

    private Fact synthIndex(
            AstNode.Index index,
            String statementId,
            LexicalEnvironment env,
            Set<String> resolving) {
        Optional<DataPath> dataPath = dataPathOf(index);
        if (dataPath.isPresent()) {
            return resolveDataPath(dataPath.get(), statementId);
        }
        Fact base = synth(index.obj(), statementId, env, resolving);
        Fact indexFact = synth(index.index(), statementId, env, resolving);
        if (index.index() instanceof AstNode.Num number && number.v() == Math.rint(number.v())) {
            // 字面量下标:精确长度区间可判定越界,越界不伪装成正常元素。
            return ops.elementAt(base, (long) number.v());
        }
        if (indexFact.type() instanceof Type.Scalar scalar
                && scalar.kind() == Type.ScalarKind.NUMBER) {
            return ops.element(base);
        }
        if (indexFact.type() instanceof Type.StringLiterals literals
                && literals.values().size() == 1) {
            return ops.readField(base, literals.values().iterator().next());
        }
        if (isStringIndex(indexFact.type())) {
            // 动态字符串键:全部字段值类型的并集;键是否存在未知,不能按确定缺失返回 Never。
            return ops.readDynamicField(base);
        }
        return ops.element(base);
    }

    /** 下标表达式是否为字符串键(字面值集合或 string 标量,含其联合)。 */
    private static boolean isStringIndex(Type type) {
        return switch (type) {
            case Type.Scalar scalar -> scalar.kind() == Type.ScalarKind.STRING;
            case Type.StringLiterals ignored -> true;
            case Type.Union union ->
                    !union.alternatives().isEmpty()
                            && union.alternatives().stream()
                                    .allMatch(AnalysisSession::isStringIndex);
            default -> false;
        };
    }

    private Fact resolveDataPath(DataPath path, String statementId) {
        if (input.dataModel() == null) {
            // 无 Render Data Model:数据依赖部分 Unknown,静态检查继续。
            return Fact.unknown(Fact.Loss.UNKNOWN_INPUT);
        }
        DataPathResolution resolution = pathResolver.resolve(input.dataModel(), path);
        if (resolution instanceof DataPathResolution.Resolved resolved) {
            Fact fact =
                    factFromObserved(path.source(), resolved)
                            .withEvidence(new Fact.Evidence.LengthSource("path:" + path.source()));
            OptionalInt cardinality = pathResolver.resolveCardinality(input.dataModel(), path);
            return cardinality.isPresent()
                    ? fact.withEvidence(new Fact.Evidence.KnownLength(cardinality.getAsInt()))
                    : fact;
        }
        // 空数组路径:类型已知(空数组),不产生诊断;组件槽位等使用位置按既有政策提示。
        return emitPathIssue(resolution, statementId);
    }

    /** 快照观察结果直接构建事实(观察证据按路径关联,含确定反例所需的快照值)。 */
    private Fact factFromObserved(String path, DataPathResolution.Resolved resolved) {
        Type type = resolved.type();
        Set<Fact.Evidence> evidence = new LinkedHashSet<>();
        Set<Fact.Loss> losses = new LinkedHashSet<>();
        DataPathResolution.Evidence observed = resolved.evidence();
        if (observed != null && observed.total() > 0) {
            evidence.add(
                    new Fact.Evidence.FieldPresence(
                            path,
                            observed.present(),
                            observed.missing(),
                            observed.nulls(),
                            observed.total()));
            if (observed.plucked()) {
                // 数组字段投影直接观察到非 null 成员 —— 投影结果至少含一个元素(直接快照观察,§6.5)。
                if (observed.present() > 0) {
                    evidence.add(new Fact.Evidence.ElementPresence());
                }
                if (observed.missing() > 0) {
                    // 缺失行的运行时处理(保留 null/跳过/失败)契约未定义(#79 未决)。存在性证据保留在
                    // FieldPresence 里,但精确投影结果不可证明:不能丢弃缺失分支后宣称完整通过。
                    losses.add(Fact.Loss.UNKNOWN_INPUT);
                }
            }
        }
        // 快照值证据:观察到的实际值(数组元素反例等真实证据);直接引用快照值,不再递归复制。
        Object snapshotValue = resolved.observedValue();
        if (snapshotValue != null) {
            evidence.add(new Fact.Evidence.ObservedValue(path, snapshotValue));
        }
        return new Fact(type, evidence, losses, Set.of());
    }

    private Fact emitPathIssue(DataPathResolution resolution, String statementId) {
        String code;
        String message;
        String hint;
        if (resolution instanceof DataPathResolution.Missing missing) {
            code = "type-data-path-missing";
            message = missing.path() + " is absent from the Render Data Model.";
            hint = missing.hint();
        } else if (resolution instanceof DataPathResolution.InvalidTraversal invalid) {
            code = "type-data-invalid-traversal";
            message = invalid.path() + " cannot traverse " + invalid.actualType() + ".";
            hint = invalid.hint();
        } else if (resolution instanceof DataPathResolution.Unprovable unprovable) {
            code = "type-data-unprovable";
            message = unprovable.path() + " cannot be proven from the Render Data Model.";
            hint = unprovable.hint();
            emit(code, message, statementId, null, resolution.path(), hint);
            return Fact.unknown(Fact.Loss.UNKNOWN_INPUT);
        } else {
            return Fact.unknown(Fact.Loss.UNKNOWN_INPUT);
        }
        String errorId = nextErrorId();
        emit(code, message, statementId, null, resolution.path(), hint);
        return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamError(errorId);
    }

    private Fact synthBinOp(
            AstNode.BinOp binary,
            String statementId,
            LexicalEnvironment env,
            Set<String> resolving) {
        Fact left = synth(binary.left(), statementId, env, resolving);
        Fact right = synth(binary.right(), statementId, env, resolving);
        if (Set.of("==", "!=", "<", "<=", ">", ">=", "&&", "||").contains(binary.op())) {
            if (!left.hasUpstreamErrors()
                    && !right.hasUpstreamErrors()
                    && isObviousOperatorMismatch(binary.op(), left.type(), right.type())) {
                emit(
                        "type-operator-mismatch",
                        "Operator "
                                + binary.op()
                                + " cannot compare "
                                + left.type().displayName()
                                + " with "
                                + right.type().displayName()
                                + ".",
                        statementId,
                        null,
                        null,
                        "Compare compatible scalar values; use @Each/@Filter for arrays.");
                return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamError(lastErrorId());
            }
            return Fact.of(Types.scalar(Type.ScalarKind.BOOLEAN));
        }
        if ("??".equals(binary.op())) {
            // 移除已处理的 null 分支,再合并右侧结果(缺失与 null 是否等同受语言规则约束)。
            Type leftNonNull = removeNull(left.type());
            return new Fact(
                    Types.union(leftNonNull, right.type()),
                    Set.of(),
                    mergeLosses(left, right),
                    mergeUpstream(left, right));
        }
        if (!left.hasUpstreamErrors() && !right.hasUpstreamErrors()) {
            boolean arithmeticMismatch =
                    "+".equals(binary.op())
                            ? isComposite(left.type()) || isComposite(right.type())
                            : !isNumberLike(left.type()) || !isNumberLike(right.type());
            if (arithmeticMismatch) {
                emit(
                        "type-operator-mismatch",
                        "Operator "
                                + binary.op()
                                + " cannot combine "
                                + left.type().displayName()
                                + " with "
                                + right.type().displayName()
                                + ".",
                        statementId,
                        null,
                        null,
                        "Use scalar operands; use @Each or a data builtin for arrays and objects.");
                return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamError(lastErrorId());
            }
            Type result =
                    Types.scalar(
                            isStringLike(left.type()) || isStringLike(right.type())
                                    ? Type.ScalarKind.STRING
                                    : Type.ScalarKind.NUMBER);
            return new Fact(result, Set.of(), mergeLosses(left, right), mergeUpstream(left, right));
        }
        return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR)
                .withUpstreamErrors(mergeUpstream(left, right));
    }

    private static Type removeNull(Type type) {
        List<Type> branches = new ArrayList<>();
        for (Type branch : TypeOps.flatten(type)) {
            if (branch instanceof Type.Scalar scalar && scalar.kind() == Type.ScalarKind.NULL) {
                continue;
            }
            branches.add(branch);
        }
        return branches.isEmpty() ? type : Types.union(branches);
    }

    private static boolean isNumberLike(Type type) {
        if (type instanceof Type.Scalar scalar) {
            return scalar.kind() == Type.ScalarKind.NUMBER;
        }
        if (type instanceof Type.Unknown) {
            return true;
        }
        return type instanceof Type.Union union
                && union.alternatives().stream().allMatch(AnalysisSession::isNumberLike);
    }

    private static boolean isStringLike(Type type) {
        if (type instanceof Type.Scalar scalar) {
            return scalar.kind() == Type.ScalarKind.STRING;
        }
        if (type instanceof Type.StringLiterals) {
            return true;
        }
        return type instanceof Type.Union union
                && union.alternatives().stream().allMatch(AnalysisSession::isStringLike);
    }

    private static boolean isComposite(Type type) {
        if (type instanceof Type.Sequence
                || type instanceof Type.RecordType
                || type instanceof Type.Component
                || type instanceof Type.Template) {
            return true;
        }
        return type instanceof Type.Union union
                && union.alternatives().stream().anyMatch(AnalysisSession::isComposite);
    }

    private static boolean isObviousOperatorMismatch(String operator, Type left, Type right) {
        if (left instanceof Type.Unknown || right instanceof Type.Unknown) {
            return false;
        }
        boolean leftComposite = isComposite(left);
        boolean rightComposite = isComposite(right);
        if (Set.of("<", "<=", ">", ">=").contains(operator)) {
            return leftComposite || rightComposite;
        }
        return leftComposite != rightComposite;
    }

    private Fact join(Fact left, Fact right) {
        return new Fact(
                Types.union(left.type(), right.type()),
                mergeEvidence(left, right),
                mergeLosses(left, right),
                mergeUpstream(left, right));
    }

    private static Set<Fact.Evidence> mergeEvidence(Fact left, Fact right) {
        Set<Fact.Evidence> merged = new LinkedHashSet<>(left.evidence());
        merged.addAll(right.evidence());
        // 长度来源是单值证据:分支来源不同时联合结果不能冒充其中任一分支的来源,必须一并丢弃。
        Set<Fact.Evidence> leftLength = lengthEvidence(left);
        Set<Fact.Evidence> rightLength = lengthEvidence(right);
        if (!leftLength.equals(rightLength)) {
            merged.removeAll(leftLength);
            merged.removeAll(rightLength);
        }
        return merged;
    }

    private static Set<Fact.Evidence> lengthEvidence(Fact fact) {
        Set<Fact.Evidence> length = new LinkedHashSet<>();
        for (Fact.Evidence item : fact.evidence()) {
            if (item instanceof Fact.Evidence.LengthSource
                    || item instanceof Fact.Evidence.KnownLength) {
                length.add(item);
            }
        }
        return length;
    }

    private static Set<Fact.Loss> mergeLosses(Fact left, Fact right) {
        Set<Fact.Loss> merged = new LinkedHashSet<>(left.losses());
        merged.addAll(right.losses());
        return merged;
    }

    private static Set<String> mergeUpstream(Fact left, Fact right) {
        Set<String> merged = new LinkedHashSet<>(left.upstreamErrors());
        merged.addAll(right.upstreamErrors());
        return merged;
    }

    // ───────────────────────────────────────────────────────────────────────
    // builtin 与组件调用
    // ───────────────────────────────────────────────────────────────────────

    private Fact synthCall(
            AstNode.Comp comp, String statementId, LexicalEnvironment env, Set<String> resolving) {
        if (input.builtins().isBuiltin(comp.name())) {
            // builtin 的全部特殊语义(分派、参数时机、作用域、模板)归 module;这里只交付未求值的调用。
            return builtinAnalysis.analyze(
                    comp, new BuiltinAnalysis.AnalysisContext(this, statementId, env, resolving));
        }
        ComponentDef definition = input.catalog().get(comp.name());
        if (definition == null) {
            for (AstNode argument : comp.args()) {
                synth(argument, statementId, env, resolving);
            }
            return Fact.unknown(Fact.Loss.UNKNOWN_INPUT);
        }
        return synthComponent(definition, comp, statementId, env, resolving);
    }

    private Fact synthComponent(
            ComponentDef definition,
            AstNode.Comp comp,
            String statementId,
            LexicalEnvironment env,
            Set<String> resolving) {
        List<ParamDef> params = definition.params();
        // 位置参数按声明参数顺序映射;超出参数数的实参仍推断(供下游诊断),但不按具名参数检查
        // (多余实参的语义归既有合约检查)。
        for (int index = 0; index < comp.args().size(); index++) {
            AstNode argument = comp.args().get(index);
            Fact actual = synth(argument, statementId, env, resolving);
            if (index >= params.size()) {
                continue;
            }
            ParamDef param = params.get(index);
            Expectation expectation = compileParamExpectation(comp.name(), param);
            if (expectation == null) {
                continue;
            }
            checkComponentParam(
                    comp, param, argument, actual, expectation, statementId, env, resolving);
        }
        if ("Table".equals(comp.name())) {
            validateTable(comp, statementId, env, resolving);
        }
        if ("LineChart".equals(comp.name()) || "BarChart".equals(comp.name())) {
            validateChart(comp, statementId, env, resolving);
        }
        return Fact.of(new Type.Component(comp.name()));
    }

    private void checkComponentParam(
            AstNode.Comp comp,
            ParamDef param,
            AstNode argument,
            Fact actual,
            Expectation expectation,
            String statementId,
            LexicalEnvironment env,
            Set<String> resolving) {
        Checks.CheckResult result =
                checkObligation(
                        statementId,
                        comp.name() + "." + param.name(),
                        "param",
                        "/components/" + comp.name() + "/properties/" + param.name(),
                        actual,
                        expectation,
                        null,
                        comp.name(),
                        "/" + param.name());
        boolean componentSlot = containsComponentConstraint(expectation);
        if (componentSlot) {
            if (result.verdict() == Checks.Verdict.MISMATCH) {
                emit(
                        "component-slot-type-mismatch",
                        comp.name()
                                + "."
                                + param.name()
                                + " expects "
                                + displayExpectation(expectation)
                                + ", but received "
                                + actual.type().displayName()
                                + ".",
                        statementId,
                        comp.name(),
                        "/" + param.name(),
                        "Use a value compatible with " + displayExpectation(expectation) + ".");
            } else {
                // MATCH 但确知空数组、或 UNPROVEN(无元素证据)时按既有槽位政策提示。
                reportComponentSlotPolicy(comp, param, argument, actual, result, statementId);
            }
            return;
        }
        if (result.verdict() == Checks.Verdict.MISMATCH) {
            emit(
                    "type-prop-mismatch",
                    comp.name()
                            + "."
                            + param.name()
                            + " expects "
                            + displayExpectation(expectation)
                            + ", but received "
                            + actual.type().displayName()
                            + ".",
                    statementId,
                    comp.name(),
                    "/" + param.name(),
                    "Use a value compatible with " + displayExpectation(expectation) + ".");
        }
    }

    /** 组件参数 schema → Expectation;schema 不可用时为 null(跳过,对齐旧行为)。 */
    private Expectation compileParamExpectation(String component, ParamDef param) {
        if (!(param.schema() instanceof Map<?, ?> schema)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> schemaMap = (Map<String, Object>) schema;
        return SchemaCompiler.compileParam(
                schemaMap, "/components/" + component + "/properties/" + param.name());
    }

    /** 既有槽位政策:确知空数组(或空数据数组/无元素证据)用于组件槽位,沿用原有提示与 code。 */
    private void reportComponentSlotPolicy(
            AstNode.Comp comp,
            ParamDef param,
            AstNode argument,
            Fact actual,
            Checks.CheckResult result,
            String statementId) {
        AstNode resolved = resolveNode(argument, new LinkedHashSet<>());
        Optional<DataPath> path = dataPathOf(resolved);
        if (path.isPresent()
                && input.dataModel() != null
                && pathResolver.resolveCardinality(input.dataModel(), path.get()).orElse(-1) == 0) {
            String source = path.get().source();
            emit(
                    "type-data-unprovable",
                    source + " is empty and cannot prove the required component element type.",
                    statementId,
                    comp.name(),
                    source,
                    "Provide typed elements or avoid this component slot for the empty result.");
            return;
        }
        boolean emptyLiteral = isDefinitelyEmptySequence(actual.type());
        // 值未知(纯数据依赖)不提示:未证明已计入覆盖,与旧引擎“未知输入不判类型”的行为对齐。
        if (!emptyLiteral) {
            return;
        }
        emit(
                "empty-component-slot",
                comp.name()
                        + "."
                        + param.name()
                        + " requires typed components, but received an empty array literal.",
                statementId,
                comp.name(),
                "/" + param.name(),
                "Provide at least one compatible component value.");
    }

    private static boolean isDefinitelyEmptySequence(Type type) {
        if (type instanceof Type.Sequence sequence) {
            return sequence.maxLength() == 0;
        }
        return type instanceof Type.Union union
                && !union.alternatives().isEmpty()
                && union.alternatives().stream()
                        .allMatch(AnalysisSession::isDefinitelyEmptySequence);
    }

    /** 检查要求是否含组件名义约束(组件槽位,沿用 component-slot-type-mismatch 诊断码)。 */
    private static boolean containsComponentConstraint(Expectation expectation) {
        if (expectation instanceof Expectation.Accept accept) {
            return typeContainsComponent(accept.type());
        }
        if (expectation instanceof Expectation.SequenceOf sequenceOf) {
            return containsComponentConstraint(sequenceOf.elementExpectation());
        }
        if (expectation instanceof Expectation.AnyOf anyOf) {
            return anyOf.expectations().stream()
                    .anyMatch(AnalysisSession::containsComponentConstraint);
        }
        if (expectation instanceof Expectation.AllOf allOf) {
            return allOf.expectations().stream()
                    .anyMatch(AnalysisSession::containsComponentConstraint);
        }
        if (expectation instanceof Expectation.ExactlyOne exactlyOne) {
            return exactlyOne.expectations().stream()
                    .anyMatch(AnalysisSession::containsComponentConstraint);
        }
        return false;
    }

    private static boolean typeContainsComponent(Type type) {
        if (type instanceof Type.Component) {
            return true;
        }
        if (type instanceof Type.Sequence sequence) {
            return typeContainsComponent(sequence.element());
        }
        if (type instanceof Type.Union union) {
            return union.alternatives().stream().anyMatch(AnalysisSession::typeContainsComponent);
        }
        if (type instanceof Type.RecordType record) {
            return record.fields().values().stream()
                    .anyMatch(field -> typeContainsComponent(field.type()));
        }
        return false;
    }

    /** builtin module 协作入口:登记一次参数检查,并沿用当前模板使用位置的覆盖身份。 */
    Checks.CheckResult checkObligation(
            String statementId,
            String useSite,
            String kind,
            String contractPath,
            Fact fact,
            Expectation expectation,
            String checkedPath,
            String component,
            String issuePath) {
        Checks.CheckResult result = Checks.check(fact, expectation, checkedPath);
        // 模板使用位置的检查要求带使用位置身份(#77 第 11.3 节):同一模板用于多列时各列独立计数。
        TemplateUseSite site = templateUseSites.peek();
        coverage.record(
                new CoverageReport.Entry(
                        new CoverageReport.Obligation(
                                site == null ? statementId : site.statementId(),
                                site == null ? useSite : site.coverageUseSite() + ":" + useSite,
                                kind,
                                contractPath),
                        result.verdict(),
                        List.copyOf(result.reasons())));
        return result;
    }

    // ───────────────────────────────────────────────────────────────────────
    // builtin module 协作:共享预算、覆盖、诊断身份与符号解析
    // ───────────────────────────────────────────────────────────────────────

    /** 本次会话的类型运算入口(携带同一份预算)。 */
    TypeOps ops() {
        return ops;
    }

    /** 登记一条 builtin 参数检查结论,与组件/模板检查共用覆盖口径。 */
    void recordCoverage(CoverageReport.Obligation obligation, Checks.CheckResult result) {
        coverage.record(
                new CoverageReport.Entry(
                        obligation, result.verdict(), List.copyOf(result.reasons())));
    }

    /** 消费一次检查预算;耗尽时不再展开新的模板。 */
    boolean consumeCheck() {
        return budget.tryConsumeCheck();
    }

    /** 把覆盖报告标记为遍历截断。 */
    void markTraversalTruncated() {
        coverage.markTraversalTruncated();
    }

    /** 进入模板使用位置:诊断与覆盖身份归属实际使用处。 */
    void pushTemplateUseSite(
            String pathPrefix,
            String coverageUseSite,
            String statementId,
            String declarationStatementId) {
        templateUseSites.push(
                new TemplateUseSite(
                        pathPrefix, coverageUseSite, statementId, declarationStatementId));
    }

    /** 离开模板使用位置。 */
    void popTemplateUseSite() {
        templateUseSites.pop();
    }

    private static String displayExpectation(Expectation expectation) {
        if (expectation instanceof Expectation.Accept accept) {
            return accept.type().displayName();
        }
        if (expectation instanceof Expectation.EnumValues enumValues) {
            return Types.stringLiterals(enumValues.values()).displayName();
        }
        if (expectation instanceof Expectation.AnyOf anyOf) {
            return anyOf.expectations().stream()
                    .map(AnalysisSession::displayExpectation)
                    .reduce((a, b) -> a + " | " + b)
                    .orElse("any");
        }
        if (expectation instanceof Expectation.AllOf allOf) {
            return allOf.expectations().stream()
                    .map(AnalysisSession::displayExpectation)
                    .reduce((a, b) -> a + " & " + b)
                    .orElse("any");
        }
        return expectation.getClass().getSimpleName();
    }

    // ───────────────────────────────────────────────────────────────────────
    // Table / Chart 消费者检查
    // ───────────────────────────────────────────────────────────────────────

    private void validateTable(
            AstNode.Comp table, String statementId, LexicalEnvironment env, Set<String> resolving) {
        if (table.args().size() < 2) {
            return;
        }
        Fact rows = synth(table.args().get(1), statementId, env, resolving);
        if (rows.hasUpstreamErrors()) {
            return;
        }
        Fact rowElement = ops.element(rows);
        // 无元素证据(空数组/元素 Never/元素依赖证据丢失)时,行形状与列存在性属于“无法证明”,不据此报错
        // (第 7.5 节:空数组不能为 Table 推导列结构);元素类型未知的同样不虚构行形状错误。
        boolean noRowEvidence =
                rowElement.type() instanceof Type.Never
                        || rowElement.losses().contains(Fact.Loss.NO_ELEMENT_EVIDENCE)
                        || rowElement.losses().contains(Fact.Loss.UNKNOWN_INPUT);
        boolean rowShapeKnown = isObjectShape(rowElement.type());
        if (!rowShapeKnown
                && !(rowElement.type() instanceof Type.Unknown)
                && !(rowElement.type() instanceof Type.Never)
                && !(rowElement.losses().contains(Fact.Loss.UNKNOWN_INPUT))) {
            emit(
                    "type-table-row-shape-mismatch",
                    "Table rows must be a flat object array, but received "
                            + rows.type().displayName()
                            + ".",
                    statementId,
                    "Table",
                    "/rows",
                    "Flatten nested @Each results before passing them to Table.");
        }
        // 字段集合为空不是退出条件:逐列独立检查,某一列未证明不影响其他列。
        for (AstNode column : arrayElements(table.args().get(0))) {
            AstNode resolvedColumn = resolveNode(column, new LinkedHashSet<>());
            if (!(resolvedColumn instanceof AstNode.Comp call)
                    || !"Col".equals(call.name())
                    || call.args().size() < 2) {
                continue;
            }
            AstNode resolvedField = resolveNode(call.args().get(1), new LinkedHashSet<>());
            if (!(resolvedField instanceof AstNode.Str field)) {
                continue;
            }
            // 列的诊断归属:命名 Col 语句用其 id,内联列退回 Table 语句;仅供 cell 使用位置定位。
            String columnStatementId =
                    column instanceof AstNode.Ref ref && symbols.containsKey(ref.n())
                            ? ref.n()
                            : statementId;
            checkTableColumn(
                    call,
                    field.v(),
                    rowElement,
                    rows,
                    statementId,
                    env,
                    resolving,
                    noRowEvidence,
                    columnStatementId);
        }
    }

    private void checkTableColumn(
            AstNode.Comp column,
            String field,
            Fact rowElement,
            Fact rows,
            String statementId,
            LexicalEnvironment env,
            Set<String> resolving,
            boolean noRowEvidence,
            String columnStatementId) {
        // 列字段存在性按"任意一行带该字段即满足"判定:部分行缺值是合法用法,只有所有行形状都没有才报。
        Checks.CheckResult existence = Checks.checkColumnField(rowElement, field);
        // 所有已知行形状都没有该列是静态用法错误;Filter 仅删除行存在证据,不新增字段。
        boolean absentFromShape =
                isObjectShape(rowElement.type())
                        && TypeOps.flatten(rows.type()).stream()
                                .noneMatch(
                                        branch ->
                                                branch instanceof Type.Sequence sequence
                                                        && sequence.maxLength() == 0)
                        && TypeOps.flatten(rowElement.type()).stream()
                                .allMatch(
                                        branch -> {
                                            Type.RecordType record = (Type.RecordType) branch;
                                            return record.extra().isClosed()
                                                    && !record.fields().containsKey(field);
                                        });
        if (existence.verdict() == Checks.Verdict.MISMATCH && noRowEvidence && !absentFromShape) {
            existence =
                    new Checks.CheckResult(
                            Checks.Verdict.UNPROVEN,
                            Set.of(Fact.Loss.NO_ELEMENT_EVIDENCE),
                            "no retained row counterexample");
        }
        coverage.record(
                new CoverageReport.Entry(
                        new CoverageReport.Obligation(
                                statementId,
                                "Table.columns[" + field + "]",
                                "presentInAnyRow",
                                "/components/Table/columns/" + field),
                        existence.verdict(),
                        List.copyOf(existence.reasons())));
        if (existence.verdict() == Checks.Verdict.MISMATCH) {
            emit(
                    "type-table-column-missing",
                    "Table column field \"" + field + "\" " + existence.detail() + ".",
                    statementId,
                    "Table",
                    "/columns/" + field,
                    "Remove this column from the Table if its metric/attribute is absent from the data (or replace the Table columns with Col definitions matching the actual fields in the rows); never bind a column title to a semantically different field or replace Col with ExtraCol.");
        }
        // 元素 Never(空数组)时不再检查 cell 模板——无行结构可用。行存在性未证明(Filter 等)不妨碍
        // 按行形状上界检查 cell 模板:列存在性已在上方按 noRowEvidence 口径处理;字段值类型上界确定可知,
        // cell 参数级误用(如对 string 列取 Count)按类型报,存在性不影响逐元素类型判定(#83 口径)。
        if (rowElement.type() instanceof Type.Never) {
            return;
        }
        Fact cellFact = ops.readField(rowElement, field);
        validateCellTemplate(
                column, field, cellFact, rowElement, env, resolving, columnStatementId);
    }

    private void validateCellTemplate(
            AstNode.Comp column,
            String field,
            Fact cellFact,
            Fact rowFact,
            LexicalEnvironment env,
            Set<String> resolving,
            String columnStatementId) {
        if (column.args().size() < COLUMN_MIN_ARGS_FOR_OPTIONS
                || !(column.args().get(COLUMN_OPTIONS_ARG_INDEX) instanceof AstNode.Obj options)) {
            return;
        }
        AstNode cell =
                options.entries().stream()
                        .filter(entry -> "cell".equals(entry.key()))
                        .map(AstNode.Obj.Entry::value)
                        .findFirst()
                        .orElse(null);
        // 表格只提供原 cell 表达式、单元格值与整行值;识别模板、绑定名称与使用位置检查归 builtin module。
        builtinAnalysis.checkCellTemplate(
                cell,
                cellFact,
                rowFact,
                new BuiltinAnalysis.AnalysisContext(
                        this,
                        columnStatementId,
                        env,
                        resolving,
                        "Table.cell[" + field + "]",
                        "/columns/" + field + "/cell"));
    }

    private void validateChart(
            AstNode.Comp chart, String statementId, LexicalEnvironment env, Set<String> resolving) {
        if (chart.args().size() < 2) {
            return;
        }
        AstNode labelsNode = chart.args().get(0);
        Fact labels = synth(labelsNode, statementId, env, resolving);
        // 元素要求与长度关系是独立要求(#77 §11.1):元素检查先登记,不受长度是否可证影响。
        checkChartElement(
                chart,
                "labels",
                labelsNode,
                labels,
                chartLabelsExpectation(),
                "/labels",
                statementId);
        long[] labelRange = lengthRangeOf(labels);
        int seriesIndex = 0;
        for (AstNode seriesNode : arrayElements(chart.args().get(1))) {
            AstNode resolvedSeries = resolveNode(seriesNode, new LinkedHashSet<>());
            if (!(resolvedSeries instanceof AstNode.Comp series)
                    || !"Series".equals(series.name())
                    || series.args().size() < 2) {
                seriesIndex++;
                continue;
            }
            AstNode valuesNode = series.args().get(1);
            Fact values = synth(valuesNode, statementId, env, resolving);
            checkChartElement(
                    chart,
                    "series[" + seriesIndex + "].values",
                    valuesNode,
                    values,
                    chartSeriesValuesExpectation(),
                    "/series/" + seriesIndex + "/values",
                    statementId);
            long[] valueRange = lengthRangeOf(values);
            checkChartLengthRelation(
                    chart, labelRange, labels, valueRange, values, seriesIndex, statementId);
            if ("LineChart".equals(chart.name())
                    && valueRange != null
                    && valueRange[1] < 2
                    && !values.hasUpstreamErrors()) {
                emit(
                        "type-chart-insufficient-data",
                        "LineChart series "
                                + seriesIndex
                                + " has only "
                                + valueRange[1]
                                + " point(s), so it cannot draw a trend line.",
                        statementId,
                        chart.name(),
                        "/series/" + seriesIndex,
                        "Provide at least two ordered points for a trend line.");
            }
            validateDuplicateSeriesValues(chart, series, seriesIndex, statementId);
            seriesIndex++;
        }
    }

    /**
     * Chart 元素要求(标签为 string 或 number 数组;series values 为 number 数组),与长度关系分开登记与检查。
     * 长度不可证明时元素检查照常进行;只有已证明的元素类型冲突才产生诊断。
     */
    private void checkChartElement(
            AstNode.Comp chart,
            String requirement,
            AstNode node,
            Fact fact,
            Expectation expectation,
            String usagePath,
            String statementId) {
        String checkedPath = dataPathOf(node).map(DataPath::source).orElse(null);
        Checks.CheckResult result =
                checkObligation(
                        statementId,
                        chart.name() + "." + requirement,
                        "chartElement",
                        "/components/" + chart.name() + "/properties/" + requirement,
                        fact,
                        expectation,
                        checkedPath,
                        chart.name(),
                        usagePath);
        if (result.verdict() == Checks.Verdict.MISMATCH) {
            emit(
                    "type-prop-mismatch",
                    chart.name()
                            + "."
                            + requirement
                            + " expects "
                            + displayExpectation(expectation)
                            + ", but received "
                            + fact.type().displayName()
                            + ".",
                    statementId,
                    chart.name(),
                    usagePath,
                    "Provide values compatible with " + displayExpectation(expectation) + ".");
        }
    }

    private static Expectation chartLabelsExpectation() {
        Type stringElements =
                Types.sequence(Types.scalar(Type.ScalarKind.STRING), 0L, Type.Sequence.UNBOUNDED);
        Type numberElements =
                Types.sequence(Types.scalar(Type.ScalarKind.NUMBER), 0L, Type.Sequence.UNBOUNDED);
        return new Expectation.AnyOf(
                List.of(
                        new Expectation.Accept(stringElements),
                        new Expectation.Accept(numberElements)));
    }

    private static Expectation chartSeriesValuesExpectation() {
        return new Expectation.SequenceOf(
                new Expectation.Accept(Types.scalar(Type.ScalarKind.NUMBER)));
    }

    /**
     * 长度关系独立检查(#77 §11.1):区间互斥可证明长度不等;精确长度相同,或两侧事实携带同一个已证明保长度的
     * 来源,才证明相等。区间相同但非精确不足以证明相等——记为未证明,不报错也不宣称通过。
     */
    private void checkChartLengthRelation(
            AstNode.Comp chart,
            long[] labelRange,
            Fact labels,
            long[] valueRange,
            Fact values,
            int seriesIndex,
            String statementId) {
        Checks.CheckResult result;
        if (labelRange == null || valueRange == null) {
            result =
                    Checks.CheckResult.unproven(
                            Set.of(Fact.Loss.INSUFFICIENT_EVIDENCE),
                            "chart length cannot be proven");
        } else if (labelRange[0] > valueRange[1] || valueRange[0] > labelRange[1]) {
            result =
                    Checks.CheckResult.mismatch(
                            "labels length ["
                                    + labelRange[0]
                                    + ".."
                                    + labelRange[1]
                                    + "] cannot match series values length ["
                                    + valueRange[0]
                                    + ".."
                                    + valueRange[1]
                                    + "]");
        } else if (provenSameLength(labelRange, labels, valueRange, values)) {
            result = Checks.CheckResult.match();
        } else {
            result =
                    Checks.CheckResult.unproven(
                            Set.of(Fact.Loss.INSUFFICIENT_EVIDENCE),
                            "overlapping length ranges do not prove equal length");
        }
        String useSite = chart.name() + ".series[" + seriesIndex + "].length";
        coverage.record(
                new CoverageReport.Entry(
                        new CoverageReport.Obligation(
                                statementId,
                                useSite,
                                "chartLengthRelation",
                                "/components/" + chart.name() + "/series"),
                        result.verdict(),
                        List.copyOf(result.reasons())));
        if (result.verdict() == Checks.Verdict.MISMATCH) {
            emit(
                    "type-chart-length-mismatch",
                    chart.name()
                            + " labels length ["
                            + labelRange[0]
                            + ".."
                            + labelRange[1]
                            + "] cannot match series "
                            + seriesIndex
                            + " values length ["
                            + valueRange[0]
                            + ".."
                            + valueRange[1]
                            + "].",
                    statementId,
                    chart.name(),
                    "/series/" + seriesIndex,
                    "Provide one series value for every chart label.");
        }
    }

    /**
     * 精确长度相同,或两侧事实携带同一组长度来源(Each/Sort 已把输入来源传递到结果),才证明长度相等。
     *
     * <p>长度来源由表达式分析写入 {@link Fact.Evidence.LengthSource};这里不再按函数名回溯表达式。
     */
    static boolean provenSameLength(
            long[] labelRange, Fact labels, long[] valueRange, Fact values) {
        if (labelRange[0] == labelRange[1] && valueRange[0] == valueRange[1]) {
            return labelRange[0] == valueRange[0];
        }
        Set<String> labelSources = labels.lengthSourceIdentities();
        return !labelSources.isEmpty() && labelSources.equals(values.lengthSourceIdentities());
    }

    /** 长度区间:类型事实的数量区间若精确(上界有限)直接使用;无界时使用事实已知的精确数量(数据路径快照基数), 否则保持无界。 */
    private long[] lengthRangeOf(Fact fact) {
        long[] range = ops.lengthRange(fact);
        if (range != null && range[1] != Type.Sequence.UNBOUNDED) {
            return range;
        }
        OptionalLong known = fact.knownLength();
        return known.isPresent() ? new long[] {known.getAsLong(), known.getAsLong()} : range;
    }

    /** 既有产品检查:同一 series 内重复派生序列表达式(独立于类型层,不可遗漏)。 */
    private void validateDuplicateSeriesValues(
            AstNode.Comp chart, AstNode.Comp series, int seriesIndex, String statementId) {
        List<AstNode> values = arrayElements(series.args().get(1));
        Set<AstNode> derivedValues = new LinkedHashSet<>();
        for (AstNode value : values) {
            if (!(value instanceof AstNode.Ref)) {
                continue;
            }
            AstNode resolved = resolveNode(value, new LinkedHashSet<>());
            if (isDerivedExpression(resolved) && !derivedValues.add(resolved)) {
                emit(
                        "type-chart-duplicate-series-value",
                        chart.name()
                                + " series "
                                + seriesIndex
                                + " repeats the same derived expression for multiple categories.",
                        statementId,
                        chart.name(),
                        "/series/" + seriesIndex,
                        "Use a distinct data expression for each category or remove the duplicate category.");
                return;
            }
        }
    }

    private static boolean isDerivedExpression(AstNode node) {
        return node instanceof AstNode.Comp
                || node instanceof AstNode.Member
                || node instanceof AstNode.Index
                || node instanceof AstNode.BinOp
                || node instanceof AstNode.UnaryOp
                || node instanceof AstNode.Ternary;
    }

    private List<AstNode> arrayElements(AstNode node) {
        AstNode resolved = resolveNode(node, new LinkedHashSet<>());
        return resolved instanceof AstNode.Arr array ? array.els() : List.of();
    }

    AstNode resolveNode(AstNode node, Set<String> resolving) {
        if (node instanceof AstNode.Ref ref
                && symbols.containsKey(ref.n())
                && resolving.add(ref.n())) {
            try {
                return resolveNode(symbols.get(ref.n()), resolving);
            } finally {
                resolving.remove(ref.n());
            }
        }
        return node;
    }

    private boolean isObjectShape(Type type) {
        if (type instanceof Type.RecordType) {
            return true;
        }
        return type instanceof Type.Union union
                && !union.alternatives().isEmpty()
                && union.alternatives().stream().allMatch(this::isObjectShape);
    }

    // ───────────────────────────────────────────────────────────────────────
    // 数据路径识别(沿用既有规则)
    // ───────────────────────────────────────────────────────────────────────

    private Optional<DataPath> dataPathOf(AstNode node) {
        String source = dataPathSource(node);
        if (source == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(DataPath.parse(source));
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    private static String dataPathSource(AstNode node) {
        if (node instanceof AstNode.Ref ref && "data".equals(ref.n())) {
            return "data";
        }
        if (node instanceof AstNode.Member member) {
            String parent = dataPathSource(member.obj());
            return parent == null ? null : parent + "." + member.field();
        }
        if (node instanceof AstNode.Index index) {
            String parent = dataPathSource(index.obj());
            if (parent == null) {
                return null;
            }
            if (index.index() instanceof AstNode.Num number
                    && number.v() == Math.rint(number.v())) {
                return parent + "[" + (long) number.v() + "]";
            }
            if (index.index() instanceof AstNode.Str string) {
                return parent + "[\"" + string.v() + "\"]";
            }
        }
        return null;
    }

    // ───────────────────────────────────────────────────────────────────────
    // 诊断发射
    // ───────────────────────────────────────────────────────────────────────

    private String nextErrorId() {
        return "err-" + (++errorSequence);
    }

    private String lastErrorId() {
        return "err-" + errorSequence;
    }

    void emit(
            String code,
            String message,
            String statementId,
            String component,
            String path,
            String hint) {
        // 模板使用位置的诊断归属实际使用处:同一模板用于不同列时各自定位,去重身份也含使用位置,
        // 前一列的结果不得吞掉后一列;模板声明语句保留在 hint 中可追溯(#77 第 10.3 节)。
        TemplateUseSite site = templateUseSites.peek();
        String effectiveStatementId = site == null ? statementId : site.statementId();
        String effectivePath = site == null ? path : site.pathPrefix() + (path == null ? "" : path);
        String effectiveHint = hint;
        if (site != null
                && site.declarationStatementId() != null
                && !site.declarationStatementId().equals(effectiveStatementId)) {
            effectiveHint =
                    (hint == null || hint.isBlank() ? "" : hint + " ")
                            + "Template declared at "
                            + site.declarationStatementId()
                            + ".";
        }
        String key = code + "|" + effectiveStatementId + "|" + component + "|" + effectivePath;
        if (emitted.putIfAbsent(key, code) != null) {
            return;
        }
        nextErrorId();
        SourceSpan span = spans.getOrDefault(effectiveStatementId, SourceSpan.UNKNOWN);
        issues.add(
                new ValidationIssue(
                        code,
                        ValidationSeverity.ERROR,
                        "type",
                        message,
                        effectiveStatementId,
                        component,
                        effectivePath,
                        span.line(),
                        span.column(),
                        effectiveHint,
                        false));
    }
}
