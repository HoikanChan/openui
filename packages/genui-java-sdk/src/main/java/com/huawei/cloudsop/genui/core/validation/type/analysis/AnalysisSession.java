package com.huawei.cloudsop.genui.core.validation.type.analysis;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;
import com.huawei.cloudsop.genui.core.validation.parser.Program;
import com.huawei.cloudsop.genui.core.validation.parser.SourceSpan;
import com.huawei.cloudsop.genui.core.validation.parser.Statement;
import com.huawei.cloudsop.genui.core.validation.semantic.ComponentDef;
import com.huawei.cloudsop.genui.core.validation.semantic.ContractCatalog;
import com.huawei.cloudsop.genui.core.validation.semantic.ParamDef;
import com.huawei.cloudsop.genui.core.validation.type.DataPath;
import com.huawei.cloudsop.genui.core.validation.type.DataPathResolution;
import com.huawei.cloudsop.genui.core.validation.type.DataPathResolver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * 单次 FINAL 校验的类型分析会话(#77 第 4.2 节):有效定义、词法环境、使用上下文、检查登记。
 *
 * <p>
 * 只遍历当前有效程序({@code Program#currentById} 同名 last-wins)中从入口可达的取值语句——被覆盖的旧定义
 * 与不可达辅助语句不产生当前错误(ADR 0004)。无 Render Data Model 时仍检查字面量、调用数量、已知组件与
 * 固定返回类型,数据依赖部分记 Unknown + UNKNOWN_INPUT。
 *
 * <p>
 * 检查纪律:MISMATCH 才产生 {@link ValidationIssue}(ERROR,沿用现有 code);UNPROVEN 只进
 * {@link CoverageReport},不单独触发修复;上游错误身份关联的后续检查登记为 blocked,不产生独立反例。
 *
 * @since 2026
 */
public final class AnalysisSession {

    /** 会话输入:程序、组件目录、builtin 词表、Render Data Model 快照(可空)、根语句 id、预算。 */
    public record Input(Program program, ContractCatalog catalog, BuiltinCatalog builtins,
            Map<String, Object> dataModel, String entryId, AnalysisBudget budget) {

        public Input {
            if (program == null || catalog == null || builtins == null) {
                throw new IllegalArgumentException("program, catalog and builtins are required");
            }
            budget = budget == null ? AnalysisBudget.defaults() : budget;
        }
    }

    /** 会话输出:诊断(仅 MISMATCH)与覆盖报告。 */
    public record Output(List<ValidationIssue> issues, CoverageReport coverage) {
    }

    private final Input input;
    private final AnalysisBudget.Tracker budget;
    private final TypeOps ops;
    private final DataPathResolver pathResolver = new DataPathResolver();
    private final CoverageReport coverage = new CoverageReport();
    private final List<ValidationIssue> issues = new ArrayList<>();
    private final Map<String, AstNode> symbols = new LinkedHashMap<>();
    private final Map<String, SourceSpan> spans = new LinkedHashMap<>();
    private final Map<String, String> emitted = new LinkedHashMap<>();
    private int errorSequence;

    private AnalysisSession(Input input) {
        this.input = input;
        this.budget = input.budget().tracker();
        this.ops = new TypeOps(budget);
        for (Statement statement : input.program().statements()) {
            spans.put(statement.id(), statement.span());
        }
        Map<String, Statement> current = input.program().currentById();
        for (Map.Entry<String, Statement> entry : current.entrySet()) {
            AstNode expr = expressionOf(entry.getValue());
            if (expr != null) {
                symbols.put(entry.getKey(), expr);
            }
        }
    }

    /** 执行一次完整分析。 */
    public static Output analyze(Input input) {
        AnalysisSession session = new AnalysisSession(input);
        session.run();
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
            collectRefs(expr, refs, LexicalEnvironment.root(), new LinkedHashSet<>());
            refs.stream().filter(symbols::containsKey).forEach(work::add);
        }
        return reached;
    }

    /** 收集表达式引用的语句名(binder 遮蔽的名字不算语句引用)。 */
    private void collectRefs(AstNode node, Set<String> out, LexicalEnvironment env, Set<AstNode> visiting) {
        if (node == null || !visiting.add(node)) {
            return;
        }
        if (node instanceof AstNode.Ref ref) {
            if (!env.isBound(ref.n()) && !"data".equals(ref.n())) {
                out.add(ref.n());
            }
            return;
        }
        if (node instanceof AstNode.Comp comp) {
            BuiltinSpec spec = input.builtins().spec(comp.name());
            if (spec != null && !spec.binderIndexes().isEmpty()) {
                LexicalEnvironment extended = env;
                for (int binderIndex : spec.binderIndexes()) {
                    if (binderIndex < comp.args().size()
                            && comp.args().get(binderIndex) instanceof AstNode.Str binder) {
                        extended = extended.bind(binder.v());
                    }
                }
                int bodyIndex = comp.args().size() - 1;
                for (int i = 0; i < comp.args().size(); i++) {
                    collectRefs(comp.args().get(i), out, i == bodyIndex ? extended : env, visiting);
                }
            } else {
                comp.args().forEach(arg -> collectRefs(arg, out, env, visiting));
            }
            comp.mappedProps().values().forEach(value -> collectRefs(value, out, env, visiting));
            return;
        }
        if (node instanceof AstNode.Arr arr) {
            arr.els().forEach(el -> collectRefs(el, out, env, visiting));
            return;
        }
        if (node instanceof AstNode.Obj obj) {
            obj.entries().forEach(entry -> collectRefs(entry.value(), out, env, visiting));
            return;
        }
        if (node instanceof AstNode.BinOp bin) {
            collectRefs(bin.left(), out, env, visiting);
            collectRefs(bin.right(), out, env, visiting);
            return;
        }
        if (node instanceof AstNode.UnaryOp unary) {
            collectRefs(unary.operand(), out, env, visiting);
            return;
        }
        if (node instanceof AstNode.Ternary ternary) {
            collectRefs(ternary.cond(), out, env, visiting);
            collectRefs(ternary.then(), out, env, visiting);
            collectRefs(ternary.otherwise(), out, env, visiting);
            return;
        }
        if (node instanceof AstNode.Member member) {
            collectRefs(member.obj(), out, env, visiting);
            return;
        }
        if (node instanceof AstNode.Index index) {
            collectRefs(index.obj(), out, env, visiting);
            collectRefs(index.index(), out, env, visiting);
        }
    }

    private static AstNode expressionOf(Statement statement) {
        if (statement instanceof Statement.Value value) {
            return value.expr();
        }
        if (statement instanceof Statement.State state) {
            return state.init();
        }
        if (statement instanceof Statement.Query query) {
            return query.expr();
        }
        if (statement instanceof Statement.Mutation mutation) {
            return mutation.expr();
        }
        return null;
    }

    // ───────────────────────────────────────────────────────────────────────
    // 词法环境
    // ───────────────────────────────────────────────────────────────────────

    /** 不可变父子链词法环境;binder 只影响声明的 body,嵌套同名正常遮蔽。 */
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

        /** 只注册名字(引用收集用,不带值)。 */
        LexicalEnvironment bind(String boundName) {
            return bind(boundName, null);
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
        if (node instanceof AstNode.RuntimeRef runtimeRef) {
            return synthRuntimeRef(runtimeRef, statementId, env, resolving);
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
            return Fact.of(Types
                    .scalar("!".equals(unary.op()) ? Type.ScalarKind.BOOLEAN : Type.ScalarKind.NUMBER));
        }
        if (node instanceof AstNode.Assign assign) {
            return synth(assign.value(), statementId, env, resolving);
        }
        return Fact.unknown(Fact.Loss.UNSUPPORTED_RULE);
    }

    private Fact synthArray(AstNode.Arr array, String statementId, LexicalEnvironment env, Set<String> resolving) {
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
        return new Fact(Types.sequence(element, array.els().size(), array.els().size()),
                Set.of(new Fact.Evidence.ExactLength(array.els().size()),
                        new Fact.Evidence.ElementPresence()),
                losses, upstream);
    }

    private Fact synthObject(AstNode.Obj object, String statementId, LexicalEnvironment env, Set<String> resolving) {
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

    private Fact synthRef(AstNode node, String statementId, LexicalEnvironment env, Set<String> resolving) {
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
            return synth(symbols.get(name), name, env, resolving);
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

    private Fact synthRuntimeRef(AstNode.RuntimeRef runtimeRef, String statementId, LexicalEnvironment env,
            Set<String> resolving) {
        if (runtimeRef.refType() == AstNode.RefType.DATA && "data".equals(runtimeRef.n())) {
            return dataRootFact();
        }
        // Query/Mutation 无结果契约:Unknown。
        return Fact.unknown(Fact.Loss.UNKNOWN_INPUT);
    }

    private Fact synthMember(AstNode.Member member, String statementId, LexicalEnvironment env,
            Set<String> resolving) {
        Optional<DataPath> dataPath = dataPathOf(member);
        if (dataPath.isPresent()) {
            return resolveDataPath(dataPath.get(), statementId);
        }
        Fact base = synth(member.obj(), statementId, env, resolving);
        Fact field = ops.readField(base, member.field());
        if (field.type() instanceof Type.Never && !base.hasUpstreamErrors()) {
            // 确定缺失:对象关闭且未列出该字段。
            String errorId = nextErrorId();
            emit("type-field-missing",
                    "Field \"" + member.field() + "\" is absent from " + base.type().displayName() + ".",
                    statementId, null, null, "Use a field present on the object shape.");
            return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamError(errorId);
        }
        return field;
    }

    private Fact synthIndex(AstNode.Index index, String statementId, LexicalEnvironment env,
            Set<String> resolving) {
        Optional<DataPath> dataPath = dataPathOf(index);
        if (dataPath.isPresent()) {
            return resolveDataPath(dataPath.get(), statementId);
        }
        Fact base = synth(index.obj(), statementId, env, resolving);
        Fact indexFact = synth(index.index(), statementId, env, resolving);
        if (indexFact.type() instanceof Type.Scalar scalar && scalar.kind() == Type.ScalarKind.NUMBER) {
            return ops.element(base);
        }
        if (indexFact.type() instanceof Type.StringLiterals literals && literals.values().size() == 1) {
            return ops.readField(base, literals.values().iterator().next());
        }
        if (indexFact.type() instanceof Type.Scalar stringScalar
                && stringScalar.kind() == Type.ScalarKind.STRING) {
            return ops.readField(base, "?");
        }
        return ops.element(base);
    }

    private Fact resolveDataPath(DataPath path, String statementId) {
        if (input.dataModel() == null) {
            // 无 Render Data Model:数据依赖部分 Unknown,静态检查继续。
            return Fact.unknown(Fact.Loss.UNKNOWN_INPUT);
        }
        DataPathResolution resolution = pathResolver.resolve(input.dataModel(), path);
        if (resolution instanceof DataPathResolution.Resolved resolved) {
            return factFromObserved(path.source(), resolved);
        }
        // 空数组路径:类型已知(空数组),不产生诊断;组件槽位等使用位置按既有政策提示。
        return emitPathIssue(resolution, statementId);
    }

    /** 把旧解析器的值类型翻译为新类型事实(观察证据按路径关联,含确定反例所需的快照值)。 */
    private Fact factFromObserved(String path, DataPathResolution.Resolved resolved) {
        Type type = new TypeTranslator().translate(resolved.type());
        Set<Fact.Evidence> evidence = new LinkedHashSet<>();
        DataPathResolution.Evidence oldEvidence = resolved.evidence();
        if (oldEvidence != null && oldEvidence.total() > 0) {
            evidence.add(new Fact.Evidence.FieldPresence(path, oldEvidence.present(), oldEvidence.missing(),
                    oldEvidence.nulls(), oldEvidence.total()));
        }
        // 快照值证据:观察到的实际值(数组元素反例等真实证据);嵌套集合冻结隔离。
        Object snapshotValue = snapshotAtPath(path);
        if (snapshotValue != null) {
            evidence.add(new Fact.Evidence.ObservedValue(path, SnapshotFacts.deepFreeze(snapshotValue)));
        }
        return new Fact(type, evidence, Set.of(), Set.of());
    }

    /** 从 Render Data Model 按数据路径取快照值(数组 pluck 路径返回数组本身)。 */
    private Object snapshotAtPath(String pathSource) {
        if (input.dataModel() == null || pathSource == null) {
            return null;
        }
        try {
            DataPath path = DataPath.parse(pathSource);
            Object current = input.dataModel();
            for (DataPath.Segment segment : path.segments()) {
                if (segment instanceof DataPath.Key key) {
                    if (current instanceof Map<?, ?> map) {
                        current = map.get(key.name());
                    } else {
                        return null;
                    }
                } else if (segment instanceof DataPath.Index index) {
                    if (current instanceof List<?> list && index.value() >= 0 && index.value() < list.size()) {
                        current = list.get((int) index.value());
                    } else {
                        return null;
                    }
                }
            }
            return current;
        } catch (IllegalArgumentException error) {
            return null;
        }
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

    private Fact synthBinOp(AstNode.BinOp binary, String statementId, LexicalEnvironment env,
            Set<String> resolving) {
        Fact left = synth(binary.left(), statementId, env, resolving);
        Fact right = synth(binary.right(), statementId, env, resolving);
        if (Set.of("==", "!=", "<", "<=", ">", ">=", "&&", "||").contains(binary.op())) {
            if (!left.hasUpstreamErrors() && !right.hasUpstreamErrors()
                    && isObviousOperatorMismatch(binary.op(), left.type(), right.type())) {
                emit("type-operator-mismatch",
                        "Operator " + binary.op() + " cannot compare " + left.type().displayName() + " with "
                                + right.type().displayName() + ".",
                        statementId, null, null,
                        "Compare compatible scalar values; use @Each/@Filter for arrays.");
                return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamError(lastErrorId());
            }
            return Fact.of(Types.scalar(Type.ScalarKind.BOOLEAN));
        }
        if ("??".equals(binary.op())) {
            // 移除已处理的 null 分支,再合并右侧结果(缺失与 null 是否等同受语言规则约束)。
            Type leftNonNull = removeNull(left.type());
            return new Fact(Types.union(leftNonNull, right.type()), Set.of(), mergeLosses(left, right),
                    mergeUpstream(left, right));
        }
        if (!left.hasUpstreamErrors() && !right.hasUpstreamErrors()) {
            boolean arithmeticMismatch = "+".equals(binary.op())
                    ? isComposite(left.type()) || isComposite(right.type())
                    : !isNumberLike(left.type()) || !isNumberLike(right.type());
            if (arithmeticMismatch) {
                emit("type-operator-mismatch",
                        "Operator " + binary.op() + " cannot combine " + left.type().displayName() + " with "
                                + right.type().displayName() + ".",
                        statementId, null, null,
                        "Use scalar operands; use @Each or a data builtin for arrays and objects.");
                return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamError(lastErrorId());
            }
            Type result = Types.scalar(
                    isStringLike(left.type()) || isStringLike(right.type()) ? Type.ScalarKind.STRING
                            : Type.ScalarKind.NUMBER);
            return new Fact(result, Set.of(), mergeLosses(left, right), mergeUpstream(left, right));
        }
        return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamErrors(mergeUpstream(left, right));
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
        if (type instanceof Type.Sequence || type instanceof Type.RecordType || type instanceof Type.Component
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
        return new Fact(Types.union(left.type(), right.type()), mergeEvidence(left, right),
                mergeLosses(left, right), mergeUpstream(left, right));
    }

    private static Set<Fact.Evidence> mergeEvidence(Fact left, Fact right) {
        Set<Fact.Evidence> merged = new LinkedHashSet<>(left.evidence());
        merged.addAll(right.evidence());
        return merged;
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

    private Fact synthCall(AstNode.Comp comp, String statementId, LexicalEnvironment env, Set<String> resolving) {
        BuiltinSpec spec = input.builtins().spec(comp.name());
        if (spec != null) {
            return synthBuiltin(spec, comp, statementId, env, resolving);
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

    private Fact synthComponent(ComponentDef definition, AstNode.Comp comp, String statementId,
            LexicalEnvironment env, Set<String> resolving) {
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
            checkComponentParam(comp, param, argument, actual, expectation, statementId, env, resolving);
        }
        if ("Table".equals(comp.name())) {
            validateTable(comp, statementId, env, resolving);
        }
        if ("LineChart".equals(comp.name()) || "BarChart".equals(comp.name())) {
            validateChart(comp, statementId, env, resolving);
        }
        return Fact.of(new Type.Component(comp.name()));
    }

    private void checkComponentParam(AstNode.Comp comp, ParamDef param, AstNode argument, Fact actual,
            Expectation expectation, String statementId, LexicalEnvironment env, Set<String> resolving) {
        Checks.CheckResult result = checkObligation(statementId, comp.name() + "." + param.name(),
                "param", "/components/" + comp.name() + "/properties/" + param.name(),
                actual, expectation, null, comp.name(), "/" + param.name());
        boolean componentSlot = containsComponentConstraint(expectation);
        if (componentSlot) {
            if (result.verdict() == Checks.Verdict.MISMATCH) {
                emit("component-slot-type-mismatch",
                        comp.name() + "." + param.name() + " expects "
                                + displayExpectation(expectation) + ", but received "
                                + actual.type().displayName() + ".",
                        statementId, comp.name(), "/" + param.name(),
                        "Use a value compatible with " + displayExpectation(expectation) + ".");
            } else {
                // MATCH 但确知空数组、或 UNPROVEN(无元素证据)时按既有槽位政策提示。
                reportComponentSlotPolicy(comp, param, argument, actual, result, statementId);
            }
            return;
        }
        if (result.verdict() == Checks.Verdict.MISMATCH) {
            emit("type-prop-mismatch",
                    comp.name() + "." + param.name() + " expects "
                            + displayExpectation(expectation) + ", but received "
                            + actual.type().displayName() + ".",
                    statementId, comp.name(), "/" + param.name(),
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
        return SchemaCompiler.compileParam(schemaMap,
                "/components/" + component + "/properties/" + param.name());
    }

    /** 既有槽位政策:确知空数组(或空数据数组/无元素证据)用于组件槽位,沿用原有提示与 code。 */
    private void reportComponentSlotPolicy(AstNode.Comp comp, ParamDef param, AstNode argument, Fact actual,
            Checks.CheckResult result, String statementId) {
        AstNode resolved = resolveNode(argument, new LinkedHashSet<>());
        Optional<DataPath> path = dataPathOf(resolved);
        if (path.isPresent() && input.dataModel() != null
                && pathResolver.resolveCardinality(input.dataModel(), path.get()).orElse(-1) == 0) {
            String source = path.get().source();
            emit("type-data-unprovable",
                    source + " is empty and cannot prove the required component element type.", statementId,
                    comp.name(), source,
                    "Provide typed elements or avoid this component slot for the empty result.");
            return;
        }
        boolean emptyLiteral = isDefinitelyEmptySequence(actual.type());
        // 值未知(纯数据依赖)不提示:未证明已计入覆盖,与旧引擎“未知输入不判类型”的行为对齐。
        if (!emptyLiteral) {
            return;
        }
        emit("empty-component-slot",
                comp.name() + "." + param.name()
                        + " requires typed components, but received an empty array literal.",
                statementId, comp.name(), "/" + param.name(),
                "Provide at least one compatible component value.");
    }

    private static boolean isDefinitelyEmptySequence(Type type) {
        if (type instanceof Type.Sequence sequence) {
            return sequence.maxLength() == 0;
        }
        return type instanceof Type.Union union && !union.alternatives().isEmpty()
                && union.alternatives().stream().allMatch(AnalysisSession::isDefinitelyEmptySequence);
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
            return anyOf.expectations().stream().anyMatch(AnalysisSession::containsComponentConstraint);
        }
        if (expectation instanceof Expectation.AllOf allOf) {
            return allOf.expectations().stream().anyMatch(AnalysisSession::containsComponentConstraint);
        }
        if (expectation instanceof Expectation.ExactlyOne exactlyOne) {
            return exactlyOne.expectations().stream().anyMatch(AnalysisSession::containsComponentConstraint);
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
            return record.fields().values().stream().anyMatch(field -> typeContainsComponent(field.type()));
        }
        return false;
    }

    private Checks.CheckResult checkObligation(String statementId, String useSite, String kind,
            String contractPath, Fact fact, Expectation expectation, String checkedPath, String component,
            String issuePath) {
        Checks.CheckResult result = Checks.check(fact, expectation, checkedPath);
        coverage.record(new CoverageReport.Entry(
                new CoverageReport.Obligation(statementId, useSite, kind, contractPath,
                        expectation.toString()),
                result.verdict(), List.copyOf(result.reasons()), result.upstreamErrors()));
        return result;
    }

    private static String displayExpectation(Expectation expectation) {
        if (expectation instanceof Expectation.Accept accept) {
            return accept.type().displayName();
        }
        if (expectation instanceof Expectation.EnumValues enumValues) {
            return Types.stringLiterals(enumValues.values()).displayName();
        }
        if (expectation instanceof Expectation.AnyOf anyOf) {
            return anyOf.expectations().stream().map(AnalysisSession::displayExpectation)
                    .reduce((a, b) -> a + " | " + b).orElse("any");
        }
        if (expectation instanceof Expectation.AllOf allOf) {
            return allOf.expectations().stream().map(AnalysisSession::displayExpectation)
                    .reduce((a, b) -> a + " & " + b).orElse("any");
        }
        return expectation.getClass().getSimpleName();
    }

    // ───────────────────────────────────────────────────────────────────────
    // builtin 调用:契约 params + 运算分派
    // ───────────────────────────────────────────────────────────────────────

    private Fact synthBuiltin(BuiltinSpec spec, AstNode.Comp call, String statementId, LexicalEnvironment env,
            Set<String> resolving) {
        int minimum = spec.minArity();
        Integer maximum = spec.maxArity().orElse(null);
        if (call.args().size() < minimum || (maximum != null && call.args().size() > maximum)) {
            String expected = maximum == null ? "at least " + minimum
                    : minimum == maximum ? String.valueOf(minimum) : minimum + "-" + maximum;
            emit("builtin-argument-count-mismatch",
                    "@" + call.name() + " expects " + expected + " argument(s), but received "
                            + call.args().size() + ".",
                    statementId, null, "/args", "Use the documented @" + call.name() + " signature.");
        }
        // 逐实参推断并检查(variadic 每个实参受检查)。binder 位置按调用形态对齐:binder 只可能是前
        // args.size()-1 个参数中的声明位(Render 2 参调用只有 index 0 是 binder,3 参调用 index 0/1 都是)。
        List<Fact> arguments = new ArrayList<>();
        List<BuiltinSpec.ParamSpec> params = spec.params();
        int binderCount = Math.max(0, call.args().size() - 1);
        for (int index = 0; index < call.args().size(); index++) {
            AstNode argument = call.args().get(index);
            boolean binderPosition = index < binderCount && index < params.size() && params.get(index).binder();
            if (binderPosition) {
                arguments.add(checkBinderArgument(spec, call, index, statementId, env, resolving));
                continue;
            }
            Fact actual = synth(argument, statementId, env, resolving);
            arguments.add(actual);
            // 带 binder 的 builtin(Each/Render):body/模板位参数不按普通 params 检查,由使用位置另行检查;
            // 普通 builtin 的每个实参都按 params 检查(variadic 不封顶)。
            boolean lazyBuiltin = !spec.binderIndexes().isEmpty();
            if (index < params.size() && (!lazyBuiltin || index < binderCount)) {
                checkBuiltinParam(spec, call, index, actual, statementId);
            }
        }
        // 模板类 binder 布局仍按契约标记识别;运算分派按功能种类。
        return switch (call.name()) {
            case "Each" -> eachResult(call, arguments, statementId, env, resolving);
            case "Render" -> renderResult(call, arguments, statementId, env, resolving);
            case "MergeObject" -> ops.overlay(arguments);
            case "Filter" -> arguments.isEmpty() ? Fact.unknown(Fact.Loss.MISSING_CONTEXT)
                    : ops.subset(arguments.getFirst());
            case "Sort" -> arguments.isEmpty() ? Fact.unknown(Fact.Loss.MISSING_CONTEXT)
                    : ops.preserve(arguments.getFirst());
            case "Flatten" -> arguments.isEmpty() ? Fact.unknown(Fact.Loss.MISSING_CONTEXT)
                    : ops.flattenOne(arguments.getFirst());
            case "ObjectKeys" -> arguments.isEmpty() ? Fact.unknown(Fact.Loss.MISSING_CONTEXT)
                    : ops.keys(arguments.getFirst());
            case "ObjectValues" -> arguments.isEmpty() ? Fact.unknown(Fact.Loss.MISSING_CONTEXT)
                    : ops.values(arguments.getFirst());
            case "ObjectEntries" -> arguments.isEmpty() ? Fact.unknown(Fact.Loss.MISSING_CONTEXT)
                    : ops.entries(arguments.getFirst());
            case "First", "Last" -> arguments.isEmpty() ? Fact.unknown(Fact.Loss.MISSING_CONTEXT)
                    : ops.select(arguments.getFirst());
            case "Switch" -> switchResult(call, arguments, statementId, env, resolving);
            default -> genericResult(spec, call, arguments);
        };
    }

    /**
     * binder 参数(#80):必须是字符串字面量;裸标识符是表达式引用,语义层若已报 unresolved-ref,此处不再重复
     * 报 binder 形态错(同一根源不制造两条诊断);其它非字符串表达式报 binder 形态错。
     */
    private Fact checkBinderArgument(BuiltinSpec spec, AstNode.Comp call, int index, String statementId,
            LexicalEnvironment env, Set<String> resolving) {
        AstNode argument = call.args().get(index);
        if (argument instanceof AstNode.Str) {
            return Fact.of(Types.scalar(Type.ScalarKind.STRING));
        }
        if (argument instanceof AstNode.Ref) {
            // 裸标识符按表达式处理;其引用合法性归既有语义检查。
            return synth(argument, statementId, env, resolving);
        }
        Fact actual = synth(argument, statementId, env, resolving);
        if (!actual.hasUpstreamErrors()) {
            emit("builtin-argument-type-mismatch",
                    "@" + call.name() + " argument " + index + " expects binder string literal, but received "
                            + actual.type().displayName() + ".",
                    statementId, null, "/args/" + index, "Use a string literal binder name.");
        }
        return actual;
    }

    /** 非 binder 参数的类型检查(契约 params 的 TypeExpr 编译为检查要求)。 */
    private void checkBuiltinParam(BuiltinSpec spec, AstNode.Comp call, int index, Fact actual,
            String statementId) {
        BuiltinSpec.ParamSpec param = spec.params().get(index);
        Expectation expectation = builtinParamExpectation(param.typeExpr());
        if (expectation == null) {
            return;
        }
        Checks.CheckResult result = checkObligation(statementId, "@" + call.name() + "/args/" + index, "param",
                "/builtins/" + call.name() + "/params/" + index, actual, expectation, null, null,
                "/args/" + index);
        if (result.verdict() == Checks.Verdict.MISMATCH) {
            emit("builtin-argument-type-mismatch",
                    "@" + call.name() + " argument " + index + " expects " + param.type() + ", but received "
                            + actual.type().displayName() + ".",
                    statementId, null, "/args/" + index, "Use a value compatible with " + param.type() + ".");
        }
    }

    /** 契约参数 TypeExpr → 检查要求;涉及泛型变量的入口检查由调用处完成,此处返回 null 表示无静态入口要求。 */
    private static Expectation builtinParamExpectation(TypeExpr expr) {
        if (expr instanceof TypeExpr.Base base) {
            return switch (base.name()) {
                case "any" -> new Expectation.Accept(Type.Unknown.INSTANCE);
                case "number" -> new Expectation.Accept(Types.scalar(Type.ScalarKind.NUMBER));
                case "string" -> new Expectation.Accept(Types.scalar(Type.ScalarKind.STRING));
                case "boolean" -> new Expectation.Accept(Types.scalar(Type.ScalarKind.BOOLEAN));
                case "object" -> new Expectation.Accept(openRecordType());
                case "array" -> new Expectation.Accept(Types.unknownSequence());
                default -> null;
            };
        }
        if (expr instanceof TypeExpr.ArrayOf array) {
            if (array.element() instanceof TypeExpr.Base element) {
                Expectation elementExpectation = builtinParamExpectation(element);
                Type elementType = elementExpectation instanceof Expectation.Accept accept ? accept.type() : null;
                if (elementType != null) {
                    return new Expectation.Accept(
                            Types.sequence(elementType, 0, Type.Sequence.UNBOUNDED));
                }
            }
            return new Expectation.Accept(Types.unknownSequence());
        }
        // RecordOf/ObjectLiteral/UnionOf/TypeVar/ParamRef/Conditional:入口检查在结果代入或调用级完成。
        return null;
    }

    private static Type openRecordType() {
        return Types.record(Map.of(), Type.Extra.open(Type.Unknown.INSTANCE));
    }

    /** Each:元素绑定 binder 检查 body,数量保留;空输入仍检查 body 中独立错误,元素依赖记 no-element-evidence。 */
    private Fact eachResult(AstNode.Comp call, List<Fact> arguments, String statementId, LexicalEnvironment env,
            Set<String> resolving) {
        if (call.args().size() < 3 || arguments.isEmpty()) {
            return Fact.unknown(Fact.Loss.MISSING_CONTEXT);
        }
        Fact input = arguments.get(0);
        Fact element = ops.element(input);
        boolean emptyPossible = lengthMayBeZero(input);
        Fact boundElement = element.type() instanceof Type.Never
                ? Fact.unknown(Fact.Loss.NO_ELEMENT_EVIDENCE)
                : element;
        LexicalEnvironment bodyEnv = binderName(call.args().get(1))
                .map(name -> env.bind(name, boundElement)).orElse(env);
        Fact body = synth(call.args().get(2), statementId, bodyEnv, resolving);
        if (emptyPossible) {
            body = body.withLoss(Fact.Loss.NO_ELEMENT_EVIDENCE);
        }
        return ops.map(input, body);
    }

    private static boolean lengthMayBeZero(Fact sequence) {
        for (Type branch : TypeOps.flatten(sequence.type())) {
            if (branch instanceof Type.Sequence seq && seq.minLength() == 0) {
                return true;
            }
            if (branch instanceof Type.Unknown) {
                return true;
            }
        }
        return false;
    }

    /** Render:产生延迟模板事实;使用位置(Table 列)另行检查 body。 */
    private Fact renderResult(AstNode.Comp call, List<Fact> arguments, String statementId, LexicalEnvironment env,
            Set<String> resolving) {
        if (call.args().size() < 2) {
            return Fact.unknown(Fact.Loss.MISSING_CONTEXT);
        }
        // 声明环境被捕获;使用位置未提供上下文时依赖 v/row 的部分记 missing-context。
        LexicalEnvironment declared = env;
        for (int index = 0; index < call.args().size() - 1; index++) {
            Optional<String> name = binderName(call.args().get(index));
            if (name.isPresent()) {
                declared = declared.bind(name.get(), Fact.unknown(Fact.Loss.MISSING_CONTEXT));
            }
        }
        synth(call.args().get(call.args().size() - 1), statementId, declared, resolving);
        return Fact.of(Type.Template.INSTANCE);
    }

    /** Render 在使用位置按提供的上下文检查 body。 */
    void checkTemplateAtUseSite(AstNode.Comp render, Map<String, Fact> context, String statementId,
            LexicalEnvironment env, Set<String> resolving, String useSite) {
        LexicalEnvironment useEnv = env;
        for (int index = 0; index < render.args().size() - 1; index++) {
            int binderIndex = index;
            Optional<String> name = binderName(render.args().get(binderIndex));
            if (name.isPresent()) {
                Fact provided = context.getOrDefault(name.get(), Fact.unknown(Fact.Loss.MISSING_CONTEXT));
                useEnv = useEnv.bind(name.get(), provided);
            }
        }
        synth(render.args().get(render.args().size() - 1), statementId, useEnv, resolving);
    }

    /** binder 参数取绑定名:仅字符串字面量(裸标识符不作 binder)。 */
    private static Optional<String> binderName(AstNode node) {
        if (node instanceof AstNode.Str string) {
            return Optional.of(string.v());
        }
        return Optional.empty();
    }

    /** Switch:cases 在当前环境推断,结果为 cases 值与 default 的联合;未传 default 并入 null(#79 矩阵)。 */
    private Fact switchResult(AstNode.Comp call, List<Fact> arguments, String statementId, LexicalEnvironment env,
            Set<String> resolving) {
        if (call.args().size() < 2 || !(call.args().get(1) instanceof AstNode.Obj cases)) {
            return Fact.unknown(Fact.Loss.MISSING_CONTEXT);
        }
        List<Type> results = new ArrayList<>();
        Set<Fact.Loss> losses = new LinkedHashSet<>();
        Set<String> upstream = new LinkedHashSet<>();
        for (AstNode.Obj.Entry entry : cases.entries()) {
            Fact caseFact = synth(entry.value(), statementId, env, resolving);
            results.add(caseFact.type());
            losses.addAll(caseFact.losses());
            upstream.addAll(caseFact.upstreamErrors());
        }
        if (call.args().size() > 2) {
            Fact defaultFact = arguments.size() > 2 ? arguments.get(2)
                    : synth(call.args().get(2), statementId, env, resolving);
            results.add(defaultFact.type());
            losses.addAll(defaultFact.losses());
            upstream.addAll(defaultFact.upstreamErrors());
        } else {
            results.add(Types.scalar(Type.ScalarKind.NULL));
        }
        return new Fact(Types.union(results), Set.of(), losses, upstream);
    }

    /** 固定/条件返回类型的代入(returnType 中的 T 由实参元素下界合并解出;未知输入参与约束,不虚假精确)。 */
    private Fact genericResult(BuiltinSpec spec, AstNode.Comp call, List<Fact> arguments) {
        if (spec.returnType() == null) {
            return Fact.unknown(Fact.Loss.UNSUPPORTED_RULE);
        }
        Map<String, Type> lowerBounds = new LinkedHashMap<>();
        List<BuiltinSpec.ParamSpec> params = spec.params();
        for (int index = 0; index < params.size() && index < arguments.size(); index++) {
            collectLowerBounds(params.get(index).typeExpr(), arguments.get(index).type(), lowerBounds);
        }
        Type result = instantiate(spec.returnType(), lowerBounds, arguments);
        if (result == null) {
            return Fact.unknown(Fact.Loss.UNBOUND_TYPE_VARIABLE);
        }
        Set<Fact.Loss> losses = new LinkedHashSet<>();
        Set<String> upstream = new LinkedHashSet<>();
        for (Fact argument : arguments) {
            losses.addAll(argument.losses());
            upstream.addAll(argument.upstreamErrors());
        }
        return new Fact(result, Set.of(), losses, upstream);
    }

    /** 收集 T 的下界:数组匹配取元素,Record 匹配取值并集,裸 T 取整体;未知输入并入 Unknown 下界。 */
    private static void collectLowerBounds(TypeExpr expected, Type actual, Map<String, Type> lowerBounds) {
        if (expected instanceof TypeExpr.ArrayOf array && array.element() instanceof TypeExpr.TypeVar) {
            List<Type> elements = new ArrayList<>();
            for (Type branch : TypeOps.flatten(actual)) {
                if (branch instanceof Type.Sequence seq) {
                    elements.add(seq.element());
                } else if (branch instanceof Type.Unknown) {
                    elements.add(Type.Unknown.INSTANCE);
                }
            }
            if (!elements.isEmpty()) {
                mergeBound(lowerBounds, Types.union(elements));
            }
            return;
        }
        if (expected instanceof TypeExpr.RecordOf record && record.value() instanceof TypeExpr.TypeVar) {
            List<Type> values = new ArrayList<>();
            for (Type branch : TypeOps.flatten(actual)) {
                if (branch instanceof Type.RecordType recordType) {
                    recordType.fields().values().forEach(field -> values.add(field.type()));
                    if (!recordType.extra().isClosed()) {
                        values.add(recordType.extra().valueType());
                    }
                } else if (branch instanceof Type.Unknown) {
                    values.add(Type.Unknown.INSTANCE);
                }
            }
            if (!values.isEmpty()) {
                mergeBound(lowerBounds, Types.union(values));
            }
            return;
        }
        if (expected instanceof TypeExpr.TypeVar) {
            mergeBound(lowerBounds, actual);
        }
    }

    private static void mergeBound(Map<String, Type> lowerBounds, Type bound) {
        lowerBounds.merge("T", bound, Types::union);
    }

    /** 代入 returnType;T 未绑定时返回 null(UNBOUND_TYPE_VARIABLE 由调用方记录)。 */
    private static Type instantiate(TypeExpr expr, Map<String, Type> lowerBounds, List<Fact> arguments) {
        if (expr instanceof TypeExpr.Base base) {
            return switch (base.name()) {
                case "number" -> Types.scalar(Type.ScalarKind.NUMBER);
                case "string" -> Types.scalar(Type.ScalarKind.STRING);
                case "boolean" -> Types.scalar(Type.ScalarKind.BOOLEAN);
                case "null" -> Types.scalar(Type.ScalarKind.NULL);
                case "any" -> Type.Unknown.INSTANCE;
                case "object" -> openRecordType();
                case "array" -> Types.unknownSequence();
                default -> null;
            };
        }
        if (expr instanceof TypeExpr.ArrayOf array) {
            Type element = instantiate(array.element(), lowerBounds, arguments);
            return element == null ? null : Types.sequence(element, 0, Type.Sequence.UNBOUNDED);
        }
        if (expr instanceof TypeExpr.RecordOf record) {
            return instantiate(record.value(), lowerBounds, arguments);
        }
        if (expr instanceof TypeExpr.ObjectLiteral literal) {
            Map<String, Type.Field> fields = new LinkedHashMap<>();
            for (TypeExpr.ObjectLiteral.Field field : literal.fields()) {
                Type fieldType = instantiate(field.type(), lowerBounds, arguments);
                if (fieldType == null) {
                    return null;
                }
                fields.put(field.name(), Type.Field.required(fieldType));
            }
            return Types.record(fields, Type.Extra.closed());
        }
        if (expr instanceof TypeExpr.UnionOf union) {
            List<Type> alternatives = new ArrayList<>();
            for (TypeExpr alternative : union.alternatives()) {
                Type resolved = instantiate(alternative, lowerBounds, arguments);
                if (resolved != null) {
                    alternatives.add(resolved);
                }
            }
            return alternatives.isEmpty() ? null : Types.union(alternatives);
        }
        if (expr instanceof TypeExpr.TypeVar) {
            return lowerBounds.get("T");
        }
        if (expr instanceof TypeExpr.ParamRef ref) {
            return ref.index() < arguments.size() ? arguments.get(ref.index()).type()
                    : Types.scalar(Type.ScalarKind.NULL);
        }
        if (expr instanceof TypeExpr.Conditional conditional) {
            boolean isArray = conditional.paramIndex() < arguments.size()
                    && isSequenceLike(arguments.get(conditional.paramIndex()).type());
            return instantiate(isArray ? conditional.whenArray() : conditional.otherwise(), lowerBounds,
                    arguments);
        }
        return null;
    }

    private static boolean isSequenceLike(Type type) {
        if (type instanceof Type.Sequence) {
            return true;
        }
        return type instanceof Type.Union union && !union.alternatives().isEmpty()
                && union.alternatives().stream().allMatch(AnalysisSession::isSequenceLike);
    }

    // ───────────────────────────────────────────────────────────────────────
    // Table / Chart 消费者检查
    // ───────────────────────────────────────────────────────────────────────

    private void validateTable(AstNode.Comp table, String statementId, LexicalEnvironment env,
            Set<String> resolving) {
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
        boolean noRowEvidence = rowElement.type() instanceof Type.Never
                || rowElement.losses().contains(Fact.Loss.NO_ELEMENT_EVIDENCE)
                || rowElement.losses().contains(Fact.Loss.UNKNOWN_INPUT);
        boolean rowShapeKnown = isObjectShape(rowElement.type());
        if (!rowShapeKnown && !(rowElement.type() instanceof Type.Unknown)
                && !(rowElement.type() instanceof Type.Never)
                && !(rowElement.losses().contains(Fact.Loss.UNKNOWN_INPUT))) {
            emit("type-table-row-shape-mismatch",
                    "Table rows must be a flat object array, but received " + rows.type().displayName() + ".",
                    statementId, "Table", "/rows",
                    "Flatten nested @Each results before passing them to Table.");
        }
        String rowsPath = dataPathOf(table.args().get(1)).map(DataPath::source).orElse(null);
        // 字段集合为空不是退出条件:逐列独立检查,某一列未证明不影响其他列。
        for (AstNode column : arrayElements(table.args().get(0))) {
            AstNode resolvedColumn = resolveNode(column, new LinkedHashSet<>());
            if (!(resolvedColumn instanceof AstNode.Comp call) || !"Col".equals(call.name())
                    || call.args().size() < 2) {
                continue;
            }
            AstNode resolvedField = resolveNode(call.args().get(1), new LinkedHashSet<>());
            if (!(resolvedField instanceof AstNode.Str field)) {
                continue;
            }
            checkTableColumn(call, field.v(), rowElement, rows, statementId, env, resolving, rowsPath,
                    noRowEvidence);
        }
    }

    private void checkTableColumn(AstNode.Comp column, String field, Fact rowElement, Fact rows,
            String statementId, LexicalEnvironment env, Set<String> resolving, String rowsPath,
            boolean noRowEvidence) {
        Expectation requirement = new Expectation.RequireField(field,
                new Expectation.Accept(Type.Unknown.INSTANCE));
        Checks.CheckResult existence = checkObligation(statementId, "Table.columns[" + field + "]", "requireField",
                "/components/Table/columns/" + field, rowElement, requirement, rowsPath, "Table",
                "/columns/" + field);
        // 无行证据时的 MISMATCH 是“可能缺”而非“确定缺”(#77 用户故事 8):记 UNPROVEN,不报阻塞错误。
        if (existence.verdict() == Checks.Verdict.MISMATCH && !noRowEvidence) {
            emit("type-table-column-missing",
                    "Table column field \"" + field + "\" " + existence.detail() + ".", statementId,
                    "Table", "/columns/" + field, "Use a field present in the Table rows.");
        }
        // 元素无证据(空数组/无行)时不再检查 cell 模板——无行结构可用。
        if (noRowEvidence || rowElement.type() instanceof Type.Never) {
            return;
        }
        Fact cellFact = ops.readField(rowElement, field);
        validateCellTemplate(column, cellFact, rowElement, statementId, env, resolving);
    }

    private void validateCellTemplate(AstNode.Comp column, Fact cellFact, Fact rowFact, String statementId,
            LexicalEnvironment env, Set<String> resolving) {
        if (column.args().size() < 3 || !(column.args().get(2) instanceof AstNode.Obj options)) {
            return;
        }
        AstNode cell = options.entries().stream().filter(entry -> "cell".equals(entry.key()))
                .map(AstNode.Obj.Entry::value).findFirst().orElse(null);
        AstNode resolvedCell = resolveNode(cell, new LinkedHashSet<>());
        if (!(resolvedCell instanceof AstNode.Comp render) || !"Render".equals(render.name())
                || render.args().size() < 2) {
            return;
        }
        // 每个使用位置分别检查:v 绑定列字段事实,row 绑定行事实。
        Map<String, Fact> context = new LinkedHashMap<>();
        List<String> binders = new ArrayList<>();
        for (int index = 0; index < render.args().size() - 1; index++) {
            binderName(render.args().get(index)).ifPresent(binders::add);
        }
        if (!binders.isEmpty()) {
            context.put(binders.get(0), cellFact);
        }
        if (binders.size() > 1) {
            context.put(binders.get(1), rowFact);
        }
        checkTemplateAtUseSite(render, context, statementId, env, resolving, "Table.cell");
    }

    private void validateChart(AstNode.Comp chart, String statementId, LexicalEnvironment env,
            Set<String> resolving) {
        if (chart.args().size() < 2) {
            return;
        }
        Fact labels = synth(chart.args().get(0), statementId, env, resolving);
        long[] labelRange = lengthRangeOf(chart.args().get(0), labels, env, resolving);
        int seriesIndex = 0;
        for (AstNode seriesNode : arrayElements(chart.args().get(1))) {
            AstNode resolvedSeries = resolveNode(seriesNode, new LinkedHashSet<>());
            if (!(resolvedSeries instanceof AstNode.Comp series) || !"Series".equals(series.name())
                    || series.args().size() < 2) {
                seriesIndex++;
                continue;
            }
            Fact values = synth(series.args().get(1), statementId, env, resolving);
            long[] valueRange = lengthRangeOf(series.args().get(1), values, env, resolving);
            // 长度关系独立检查:区间互斥才可证明不等;精确长度相同才证明相等。
            if (labelRange != null && valueRange != null
                    && (labelRange[0] > valueRange[1] || valueRange[0] > labelRange[1])) {
                emit("type-chart-length-mismatch",
                        chart.name() + " labels length [" + labelRange[0] + ".." + labelRange[1]
                                + "] cannot match series " + seriesIndex + " values length [" + valueRange[0]
                                + ".." + valueRange[1] + "].",
                        statementId, chart.name(), "/series/" + seriesIndex,
                        "Provide one series value for every chart label.");
            }
            if ("LineChart".equals(chart.name()) && valueRange != null && valueRange[1] < 2
                    && !values.hasUpstreamErrors()) {
                emit("type-chart-insufficient-data",
                        "LineChart series " + seriesIndex + " has only " + valueRange[1]
                                + " point(s), so it cannot draw a trend line.",
                        statementId, chart.name(), "/series/" + seriesIndex,
                        "Provide at least two ordered points for a trend line.");
            }
            validateDuplicateSeriesValues(chart, series, seriesIndex, statementId);
            seriesIndex++;
        }
    }

    /**
     * 长度区间:类型事实的数量区间若精确(上界有限)直接使用;无界(元素依赖表达式结构未知)时回退到既有
     * 表达式级基数规则(字面量、数据路径快照基数、Each/Sort 保留输入数量)。
     */
    private long[] lengthRangeOf(AstNode node, Fact fact, LexicalEnvironment env, Set<String> resolving) {
        long[] range = ops.lengthRange(fact);
        if (range != null && range[1] != Type.Sequence.UNBOUNDED) {
            return range;
        }
        OptionalInt known = knownArraySize(node, new LinkedHashSet<>());
        return known.isPresent() ? new long[] {known.getAsInt(), known.getAsInt()} : range;
    }

    private OptionalInt knownArraySize(AstNode node, Set<String> resolving) {
        AstNode resolved = resolveNode(node, resolving);
        if (resolved instanceof AstNode.Arr array) {
            return OptionalInt.of(array.els().size());
        }
        Optional<DataPath> path = dataPathOf(resolved);
        if (path.isPresent() && input.dataModel() != null) {
            return pathResolver.resolveCardinality(input.dataModel(), path.get());
        }
        if (resolved instanceof AstNode.Comp builtin
                && ("Each".equals(builtin.name()) || "Sort".equals(builtin.name()))
                && !builtin.args().isEmpty()) {
            return knownArraySize(builtin.args().get(0), resolving);
        }
        return OptionalInt.empty();
    }

    /** 既有产品检查:同一 series 内重复派生序列表达式(独立于类型层,不可遗漏)。 */
    private void validateDuplicateSeriesValues(AstNode.Comp chart, AstNode.Comp series, int seriesIndex,
            String statementId) {
        List<AstNode> values = arrayElements(series.args().get(1));
        Set<AstNode> derivedValues = new LinkedHashSet<>();
        for (AstNode value : values) {
            if (!(value instanceof AstNode.Ref)) {
                continue;
            }
            AstNode resolved = resolveNode(value, new LinkedHashSet<>());
            if (isDerivedExpression(resolved) && !derivedValues.add(resolved)) {
                emit("type-chart-duplicate-series-value",
                        chart.name() + " series " + seriesIndex
                                + " repeats the same derived expression for multiple categories.",
                        statementId, chart.name(), "/series/" + seriesIndex,
                        "Use a distinct data expression for each category or remove the duplicate category.");
                return;
            }
        }
    }

    private static boolean isDerivedExpression(AstNode node) {
        return node instanceof AstNode.Comp || node instanceof AstNode.Member || node instanceof AstNode.Index
                || node instanceof AstNode.BinOp || node instanceof AstNode.UnaryOp || node instanceof AstNode.Ternary;
    }

    private List<AstNode> arrayElements(AstNode node) {
        AstNode resolved = resolveNode(node, new LinkedHashSet<>());
        return resolved instanceof AstNode.Arr array ? array.els() : List.of();
    }

    private AstNode resolveNode(AstNode node, Set<String> resolving) {
        if (node instanceof AstNode.Ref ref && symbols.containsKey(ref.n()) && resolving.add(ref.n())) {
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
        return type instanceof Type.Union union && !union.alternatives().isEmpty()
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
        if (node instanceof AstNode.RuntimeRef ref && ref.refType() == AstNode.RefType.DATA
                && "data".equals(ref.n())) {
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
            if (index.index() instanceof AstNode.Num number && number.v() == Math.rint(number.v())) {
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

    private void emit(String code, String message, String statementId, String component, String path,
            String hint) {
        String key = code + "|" + statementId + "|" + component + "|" + path;
        if (emitted.putIfAbsent(key, code) != null) {
            return;
        }
        nextErrorId();
        SourceSpan span = spans.getOrDefault(statementId, SourceSpan.UNKNOWN);
        issues.add(new ValidationIssue(code, ValidationSeverity.ERROR, "type", message, statementId, component,
                path, span.line(), span.column(), hint, false));
    }
}
