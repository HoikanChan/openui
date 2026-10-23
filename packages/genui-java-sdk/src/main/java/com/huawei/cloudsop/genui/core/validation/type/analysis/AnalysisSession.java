package com.huawei.cloudsop.genui.core.validation.type.analysis;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.contract.RuleEvaluator;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan;
import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;
import com.huawei.cloudsop.genui.core.validation.parser.Program;
import com.huawei.cloudsop.genui.core.validation.parser.SourceSpan;
import com.huawei.cloudsop.genui.core.validation.parser.Statement;
import com.huawei.cloudsop.genui.core.validation.semantic.ComponentDef;
import com.huawei.cloudsop.genui.core.validation.semantic.ContractCatalog;
import com.huawei.cloudsop.genui.core.validation.semantic.LazyBuiltinBinders;
import com.huawei.cloudsop.genui.core.validation.semantic.ParamDef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
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
 * 只遍历当前有效程序({@code Program#currentById} 同名 last-wins)中从入口可达的取值语句——被覆盖的旧定义 与不可达辅助语句不产生当前错误(ADR 0004)。无 Render Data Model
 * 时仍检查字面量、调用数量、已知组件与 固定返回类型,数据依赖部分记 Unknown + UNKNOWN_INPUT。
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

    /**
     * Render 延迟模板产物(#77 第 10.3 节):body 位置、声明环境与声明语句保存在会话内,通用类型代数只保留 {@link Type.Template} 形状,不把 AST 与环境塞进类型。
     */
    private record DeferredTemplate(AstNode.Comp call, String statementId, LexicalEnvironment declarationEnv,
            int bodyIndex) {
    }

    /**
     * 模板使用位置的诊断身份:诊断归属实际使用处(语句与路径),模板声明语句另行保留可追溯位置 (#77 第 10.3/11.3 节)。同一模板的不同使用位置不得共用去重身份。
     */
    private record TemplateUseSite(String pathPrefix, String coverageUseSite, String statementId,
            String declarationStatementId) {
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
    private final Map<AstNode, DeferredTemplate> templates = new IdentityHashMap<>();
    private final List<DeferredTemplate> templateOrder = new ArrayList<>();
    private final Set<AstNode> contextCheckedTemplates = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Deque<TemplateUseSite> templateUseSites = new ArrayDeque<>();
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
        checkTemplatesWithoutUseSite();
    }

    /**
     * 未在任何使用位置提供上下文的模板:仍以声明环境检查 body 的独立表达式,依赖 v/row 的部分保持 missing-context 并计入覆盖(#77 第 10.3 节),不能整段跳过。
     */
    private void checkTemplatesWithoutUseSite() {
        // 按下标遍历:body 内可能还有未分析的模板,追加到 templateOrder 的本轮继续处理,不能并发修改。
        for (int index = 0; index < templateOrder.size(); index++) {
            DeferredTemplate template = templateOrder.get(index);
            if (contextCheckedTemplates.contains(template.call())) {
                continue;
            }
            if (!budget.tryConsumeCheck()) {
                coverage.markTraversalTruncated();
                return;
            }
            synth(template.call().args().get(template.bodyIndex()), template.statementId(), template.declarationEnv(),
                    new LinkedHashSet<>());
        }
    }

    /**
     * 可达起点:入口语句 + 从其表达式递归可达的取值语句(同一 currentById 口径;state/query/mutation 的 引用收集照走,但它们自身不作为类型检查起点——既有入口规则)。
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
                for (int binderIndex : spec.activeBinderIndexes(comp.args().size())) {
                    String binder = LazyBuiltinBinders.binderName(spec, comp, binderIndex);
                    if (binder != null) {
                        extended = extended.bind(binder);
                    }
                }
                int bodyIndex = LazyBuiltinBinders.bodyIndex(spec, comp);
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

        /**
         * 把本环境(更内层、优先)的绑定叠加到 {@code base} 上,用于在使用位置保留声明环境并继承使用位置绑定 (ADR 0006):同名以本环境为准,退出后自然回到 {@code base} 的绑定。
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
            return Fact.of(Types.scalar("!".equals(unary.op()) ? Type.ScalarKind.BOOLEAN : Type.ScalarKind.NUMBER));
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
                Set.of(new Fact.Evidence.ExactLength(array.els().size()), new Fact.Evidence.ElementPresence()), losses,
                upstream);
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

    private Fact synthMember(AstNode.Member member, String statementId, LexicalEnvironment env, Set<String> resolving) {
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
            emit("type-field-missing",
                    "Field \"" + member.field() + "\" is absent from " + base.type().displayName() + ".", statementId,
                    null, null, "Use a field present on the object shape.");
            return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamError(errorId);
        }
        if (!base.hasUpstreamErrors() && !base.losses().contains(Fact.Loss.NO_ELEMENT_EVIDENCE)
                && observedMissingRow(base, member.field())) {
            // 观察到的行确定缺该字段是具体反例(#77 §6.2):上界合并掩盖部分缺失(异构行)时,
            // 当前快照里该行确实存在且没有该字段;与纯类型级确定缺失同一诊断。
            String errorId = nextErrorId();
            emit("type-field-missing", "Field \"" + member.field() + "\" is absent from an observed row of "
                    + base.type().displayName() + ".", statementId, null, null,
                    "Use a field present on every observed row.");
            return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamError(errorId);
        }
        return field;
    }

    /** 基值的行级观察证据中存在确定缺该字段的行(读取侧只关联存在的字段值,缺失由本判定补全)。 */
    private static boolean observedMissingRow(Fact base, String field) {
        for (Fact.Evidence item : base.evidence()) {
            if (item instanceof Fact.Evidence.ObservedValue observed && observed.value() instanceof Map<?, ?> row
                    && !row.containsKey(field)) {
                return true;
            }
        }
        return false;
    }

    private Fact synthIndex(AstNode.Index index, String statementId, LexicalEnvironment env, Set<String> resolving) {
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
        if (indexFact.type() instanceof Type.Scalar scalar && scalar.kind() == Type.ScalarKind.NUMBER) {
            return ops.element(base);
        }
        if (indexFact.type() instanceof Type.StringLiterals literals && literals.values().size() == 1) {
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
            case Type.Union union -> !union.alternatives().isEmpty()
                    && union.alternatives().stream().allMatch(AnalysisSession::isStringIndex);
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
            return factFromObserved(path.source(), resolved);
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
            evidence.add(new Fact.Evidence.FieldPresence(path, observed.present(), observed.missing(),
                    observed.nulls(), observed.total()));
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
        // 快照值证据:观察到的实际值(数组元素反例等真实证据);嵌套集合冻结隔离。
        Object snapshotValue = snapshotAtPath(path);
        if (snapshotValue != null) {
            evidence.add(new Fact.Evidence.ObservedValue(path, SnapshotFacts.deepFreeze(snapshotValue)));
        }
        return new Fact(type, evidence, losses, Set.of());
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

    private Fact synthBinOp(AstNode.BinOp binary, String statementId, LexicalEnvironment env, Set<String> resolving) {
        Fact left = synth(binary.left(), statementId, env, resolving);
        Fact right = synth(binary.right(), statementId, env, resolving);
        if (Set.of("==", "!=", "<", "<=", ">", ">=", "&&", "||").contains(binary.op())) {
            if (!left.hasUpstreamErrors() && !right.hasUpstreamErrors()
                    && isObviousOperatorMismatch(binary.op(), left.type(), right.type())) {
                emit("type-operator-mismatch",
                        "Operator " + binary.op() + " cannot compare " + left.type().displayName() + " with "
                                + right.type().displayName() + ".",
                        statementId, null, null, "Compare compatible scalar values; use @Each/@Filter for arrays.");
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
            Type result = Types.scalar(isStringLike(left.type()) || isStringLike(right.type())
                    ? Type.ScalarKind.STRING
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
        return type instanceof Type.Union union && union.alternatives().stream().anyMatch(AnalysisSession::isComposite);
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
        return new Fact(Types.union(left.type(), right.type()), mergeEvidence(left, right), mergeLosses(left, right),
                mergeUpstream(left, right));
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

    private Fact synthComponent(ComponentDef definition, AstNode.Comp comp, String statementId, LexicalEnvironment env,
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
        Checks.CheckResult result = checkObligation(statementId, comp.name() + "." + param.name(), "param",
                "/components/" + comp.name() + "/properties/" + param.name(), actual, expectation, null, comp.name(),
                "/" + param.name());
        boolean componentSlot = containsComponentConstraint(expectation);
        if (componentSlot) {
            if (result.verdict() == Checks.Verdict.MISMATCH) {
                emit("component-slot-type-mismatch",
                        comp.name() + "." + param.name() + " expects " + displayExpectation(expectation)
                                + ", but received " + actual.type().displayName() + ".",
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
                    comp.name() + "." + param.name() + " expects " + displayExpectation(expectation) + ", but received "
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
        return SchemaCompiler.compileParam(schemaMap, "/components/" + component + "/properties/" + param.name());
    }

    /** 既有槽位政策:确知空数组(或空数据数组/无元素证据)用于组件槽位,沿用原有提示与 code。 */
    private void reportComponentSlotPolicy(AstNode.Comp comp, ParamDef param, AstNode argument, Fact actual,
            Checks.CheckResult result, String statementId) {
        AstNode resolved = resolveNode(argument, new LinkedHashSet<>());
        Optional<DataPath> path = dataPathOf(resolved);
        if (path.isPresent() && input.dataModel() != null
                && pathResolver.resolveCardinality(input.dataModel(), path.get()).orElse(-1) == 0) {
            String source = path.get().source();
            emit("type-data-unprovable", source + " is empty and cannot prove the required component element type.",
                    statementId, comp.name(), source,
                    "Provide typed elements or avoid this component slot for the empty result.");
            return;
        }
        boolean emptyLiteral = isDefinitelyEmptySequence(actual.type());
        // 值未知(纯数据依赖)不提示:未证明已计入覆盖,与旧引擎“未知输入不判类型”的行为对齐。
        if (!emptyLiteral) {
            return;
        }
        emit("empty-component-slot",
                comp.name() + "." + param.name() + " requires typed components, but received an empty array literal.",
                statementId, comp.name(), "/" + param.name(), "Provide at least one compatible component value.");
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

    private Checks.CheckResult checkObligation(String statementId, String useSite, String kind, String contractPath,
            Fact fact, Expectation expectation, String checkedPath, String component, String issuePath) {
        Checks.CheckResult result = Checks.check(fact, expectation, checkedPath);
        // 模板使用位置的检查要求带使用位置身份(#77 第 11.3 节):同一模板用于多列时各列独立计数。
        TemplateUseSite site = templateUseSites.peek();
        coverage.record(new CoverageReport.Entry(
                new CoverageReport.Obligation(site == null ? statementId : site.statementId(),
                        site == null ? useSite : site.coverageUseSite() + ":" + useSite, kind, contractPath,
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
        // 契约声明了结构化类型规则时,调用形式与参数模式由 typeRule 独占;params 只服务未迁移的
        // 模板类(Each/Render/Switch)——两处规则同时执行会让同一实参被检查两次。
        boolean hasRule = spec.typeRule() != null;
        if (!hasRule) {
            int minimum = spec.minArity();
            Integer maximum = spec.maxArity().orElse(null);
            if (call.args().size() < minimum || (maximum != null && call.args().size() > maximum)) {
                String expected = maximum == null
                        ? "at least " + minimum
                        : minimum == maximum ? String.valueOf(minimum) : minimum + "-" + maximum;
                emit("builtin-argument-count-mismatch", "@" + call.name() + " expects " + expected
                        + " argument(s), but received " + call.args().size() + ".", statementId, null, "/args",
                        "Use the documented @" + call.name() + " signature.");
            }
        }
        // 逐实参推断并检查;各调用形式的 binder/body 位置来自同一契约编译布局。
        List<Fact> arguments = new ArrayList<>();
        List<BuiltinSpec.ParamSpec> params = spec.params();
        boolean lazyBuiltin = !spec.binderIndexes().isEmpty();
        List<Integer> activeBinders = lazyBuiltin ? spec.activeBinderIndexes(call.args().size()) : List.of();
        int bodyIndex = LazyBuiltinBinders.bodyIndex(spec, call);
        for (int index = 0; index < call.args().size(); index++) {
            AstNode argument = call.args().get(index);
            if (activeBinders.contains(index)) {
                arguments.add(checkBinderArgument(spec, call, index, statementId, env, resolving));
                continue;
            }
            if (lazyBuiltin && index == bodyIndex) {
                // 带 binder 的 builtin(Each/Render):body/模板位只能合成一次,且必须用绑定后的词法环境
                // (eachResult/renderResult 内);此处按外层环境预合成会把同名内层引用错绑到外层行,
                // 并与绑定后的合成重复报错。占位 Fact 保持 arguments 下标与实参位对齐。
                arguments.add(Fact.unknown(Fact.Loss.MISSING_CONTEXT));
                continue;
            }
            Fact actual = synth(argument, statementId, env, resolving);
            arguments.add(actual);
            // 带 binder 的 builtin:body/模板位参数不按普通 params 检查,由使用位置另行检查;
            // 未声明结构化规则的 builtin 每个实参按 params 检查(variadic 不封顶)。
            if (!hasRule && index < params.size() && (!lazyBuiltin || index != bodyIndex)) {
                checkBuiltinParam(spec, call, index, actual, statementId);
            }
        }
        // 结构化类型规则是普通 builtin 的唯一规则源:调用形式、参数模式与结果运算全部来自同一次契约
        // 解析,按运算种类分派到 TypeOps,不按函数名分派(#81/#90;词表与类型规则同源)。
        if (hasRule) {
            return RuleEvaluator.evaluate(spec.typeRule(), call.args().size(), new RuleEvaluator.CallSite() {
                @Override
                public Fact argument(int index) {
                    return index < arguments.size() ? arguments.get(index) : Fact.unknown(Fact.Loss.MISSING_CONTEXT);
                }

                @Override
                public TypeOps ops() {
                    return ops;
                }

                @Override
                public void reportArgumentMismatch(int index, String expectedPattern, Fact actual) {
                    emit("builtin-argument-type-mismatch",
                            "@" + call.name() + " argument " + index + " expects " + expectedPattern + ", but received "
                                    + actual.type().displayName() + ".",
                            statementId, null, "/args/" + index,
                            "Use a value compatible with " + expectedPattern + ".");
                }

                @Override
                public void recordArgumentCheck(int index, RulePlan.TypePattern pattern, Fact actual,
                        Checks.CheckResult result) {
                    // 结构化规则的参数结论与组件/模板检查共用覆盖口径:MATCH/UNPROVEN 都登记,
                    // 未证明不会被隐藏;同一规则模式只按实参位置计一条要求。
                    coverage.record(new CoverageReport.Entry(
                            new CoverageReport.Obligation(statementId, "@" + call.name() + "/args/" + index, "param",
                                    "/builtins/" + call.name() + "/typeRule/parameters/" + index,
                                    RuleEvaluator.displayPattern(pattern)),
                            result.verdict(), List.copyOf(result.reasons()), result.upstreamErrors()));
                }

                @Override
                public void reportArityViolation(int min, OptionalInt max, int actual) {
                    String expected = max.isEmpty()
                            ? "at least " + min
                            : min == max.getAsInt() ? String.valueOf(min) : min + "-" + max.getAsInt();
                    emit("builtin-argument-count-mismatch",
                            "@" + call.name() + " expects " + expected + " argument(s), but received " + actual + ".",
                            statementId, null, "/args", "Use the documented @" + call.name() + " signature.");
                }
            });
        }
        return switch (call.name()) {
            case "Each" -> eachResult(call, arguments, statementId, env, resolving);
            case "Render" -> renderResult(call, arguments, statementId, env, resolving);
            // 模板类语言构造由 Java 承载语义(绑定作用域超出规则表达力),不是第二份函数词表:
            // 三个名字都必须先在契约词表中命中才会走到这里。
            case "Switch" -> switchResult(call, arguments, statementId, env, resolving);
            // 契约没有声明结构化规则、也不是模板构造的 builtin:结果记为未证明并进入覆盖统计,
            // 不按 returnType 兜底推断(returnType 不再是求值规则)。
            default -> Fact.unknown(Fact.Loss.UNSUPPORTED_RULE);
        };
    }

    /**
     * binder 参数(#83):合法形态由契约 nameSyntax 声明(字符串字面量,以及 string-or-identifier 下的裸标识符)—— 合法形态不产出类型事实(binder 不是表达式值,绑定发生在
     * eachResult/renderResult); 非法形态按普通表达式推断并报 binder 形态错。
     */
    private Fact checkBinderArgument(BuiltinSpec spec, AstNode.Comp call, int index, String statementId,
            LexicalEnvironment env, Set<String> resolving) {
        AstNode argument = call.args().get(index);
        if (LazyBuiltinBinders.binderName(spec, call, index) != null) {
            return Fact.of(Types.scalar(Type.ScalarKind.STRING));
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

    /** 未迁移模板构造的非 binder 参数检查(契约 params 的 TypeExpr 编译为检查要求)。 */
    private void checkBuiltinParam(BuiltinSpec spec, AstNode.Comp call, int index, Fact actual, String statementId) {
        BuiltinSpec.ParamSpec param = spec.params().get(index);
        Expectation expectation = builtinParamExpectation(param.typeExpr());
        if (expectation == null) {
            return;
        }
        Checks.CheckResult result = checkObligation(statementId, "@" + call.name() + "/args/" + index, "param",
                "/builtins/" + call.name() + "/params/" + index, actual, expectation, null, null, "/args/" + index);
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
                    return new Expectation.Accept(Types.sequence(elementType, 0, Type.Sequence.UNBOUNDED));
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
        BuiltinSpec spec = input.builtins().spec(call.name());
        if (spec.bodyIndex(call.args().size()) < 0 || arguments.isEmpty() || spec.inputIndex() < 0
                || spec.inputIndex() >= arguments.size()) {
            return Fact.unknown(Fact.Loss.MISSING_CONTEXT);
        }
        Fact input = arguments.get(spec.inputIndex());
        Fact element = ops.element(input);
        boolean emptyPossible = lengthMayBeZero(input);
        // 元素存在已证明:直接观察到元素行,或字段读取的证据关联保留了快照中的嵌套行(#77 §6.5)。
        // 无任何损失不等于观察到元素——Filter 删除保留行后元素类型仍在但存在性未证明(§6.2/§7.5),
        // subset 会显式打上 NO_ELEMENT_EVIDENCE。
        boolean elementExistenceProven = !element.evidence().isEmpty();
        if (!elementExistenceProven && !input.losses().contains(Fact.Loss.NO_ELEMENT_EVIDENCE)) {
            for (Fact.Evidence item : input.evidence()) {
                if (item instanceof Fact.Evidence.ObservedValue observed && observed.value() instanceof List<?> values
                        && !values.isEmpty()) {
                    elementExistenceProven = true;
                }
            }
        }
        boolean elementExistenceUnproven = emptyPossible && !elementExistenceProven;
        Fact boundElement = element.type() instanceof Type.Never
                ? Fact.unknown(Fact.Loss.NO_ELEMENT_EVIDENCE)
                : elementExistenceUnproven ? element.withLoss(Fact.Loss.NO_ELEMENT_EVIDENCE) : element;
        // 保留元素存在性标记以约束缺字段诊断;标量类型误用仍可按安全上界检查。
        Fact checkElement = stripExistenceLosses(boundElement);
        LexicalEnvironment bodyEnv = env;
        List<Integer> binders = spec.activeBinderIndexes(call.args().size());
        for (int position = 0; position < binders.size(); position++) {
            String name = LazyBuiltinBinders.binderName(spec, call, binders.get(position));
            if (name != null) {
                // 元素绑定到第一个 binder;binder 位恒在输入来源之后(加载期校验,#83)。
                Fact bound = position == 0 ? checkElement : Fact.unknown(Fact.Loss.MISSING_CONTEXT);
                bodyEnv = bodyEnv.bind(name, bound);
            }
        }
        Fact body = synth(call.args().get(LazyBuiltinBinders.bodyIndex(spec, call)), statementId, bodyEnv, resolving);
        if (emptyPossible) {
            body = body.withLoss(Fact.Loss.NO_ELEMENT_EVIDENCE);
        }
        return ops.map(input, body);
    }

    /**
     * 去掉输入层面的未知标记,保留元素存在性标记与逐元素精度损失。 存在性未证明时证据一并清空:那些证据来自未证明存在的行(Filter 后不保证任何行保留),不可信。
     */
    private static Fact stripExistenceLosses(Fact fact) {
        if (!fact.losses().contains(Fact.Loss.NO_ELEMENT_EVIDENCE)
                && !fact.losses().contains(Fact.Loss.UNKNOWN_INPUT)) {
            return fact;
        }
        Set<Fact.Loss> losses = new LinkedHashSet<>(fact.losses());
        boolean existenceUnproven = losses.contains(Fact.Loss.NO_ELEMENT_EVIDENCE);
        losses.remove(Fact.Loss.UNKNOWN_INPUT);
        return new Fact(fact.type(), existenceUnproven ? Set.of() : fact.evidence(), losses, fact.upstreamErrors());
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

    /**
     * Render:产生延迟模板产物;body 推迟到使用位置(或没有使用位置时按声明环境)检查,类型只保留 Template 形状。声明环境在这里捕获,Render 自身 binder 遮蔽捕获的同名绑定(#77 第 10.3
     * 节、ADR 0006)。
     */
    private Fact renderResult(AstNode.Comp call, List<Fact> arguments, String statementId, LexicalEnvironment env,
            Set<String> resolving) {
        BuiltinSpec spec = input.builtins().spec(call.name());
        int bodyIndex = spec.bodyIndex(call.args().size());
        if (bodyIndex < 0) {
            return Fact.unknown(Fact.Loss.MISSING_CONTEXT);
        }
        LexicalEnvironment declared = env;
        for (int binderIndex : spec.activeBinderIndexes(call.args().size())) {
            String name = LazyBuiltinBinders.binderName(spec, call, binderIndex);
            if (name != null) {
                declared = declared.bind(name, Fact.unknown(Fact.Loss.MISSING_CONTEXT));
            }
        }
        DeferredTemplate template = new DeferredTemplate(call, statementId, declared, bodyIndex);
        if (templates.putIfAbsent(call, template) == null) {
            templateOrder.add(template);
        }
        return Fact.of(Type.Template.INSTANCE);
    }

    /**
     * Render 在使用位置按提供的上下文检查 body:以声明环境为底座叠加使用位置绑定(ADR 0006 的跨语句继承), Render 自身 binder
     * 再覆盖同名捕获;诊断与覆盖身份按使用位置区分,前一使用位置不得遮蔽后一使用位置 (#77 第 10.3/11.3 节)。
     */
    void checkTemplateAtUseSite(AstNode.Comp render, Map<String, Fact> context, String statementId,
            LexicalEnvironment useSiteEnv, Set<String> resolving, String useSite, String useSitePath) {
        BuiltinSpec spec = input.builtins().spec(render.name());
        int bodyIndex = spec.bodyIndex(render.args().size());
        if (bodyIndex < 0) {
            return;
        }
        DeferredTemplate template = templates.get(render);
        LexicalEnvironment base = template == null ? useSiteEnv : useSiteEnv.over(template.declarationEnv());
        LexicalEnvironment useEnv = base;
        for (int binderIndex : spec.activeBinderIndexes(render.args().size())) {
            String name = LazyBuiltinBinders.binderName(spec, render, binderIndex);
            if (name != null) {
                Fact provided = context.getOrDefault(name, Fact.unknown(Fact.Loss.MISSING_CONTEXT));
                // 使用位置提供的上下文事实不携带数组层面的存在性丢失:逐元素判定只看值上界,
                // 存在性由数组层(列存在性/长度检查)另行判定——与 Each 绑定同一剔除口径。
                useEnv = useEnv.bind(name, stripExistenceLosses(provided));
            }
        }
        contextCheckedTemplates.add(render);
        templateUseSites.push(new TemplateUseSite(useSitePath, useSite, statementId,
                template == null ? null : template.statementId()));
        try {
            synth(render.args().get(bodyIndex), statementId, useEnv, resolving);
        } finally {
            templateUseSites.pop();
        }
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
            Fact defaultFact = arguments.size() > 2
                    ? arguments.get(2)
                    : synth(call.args().get(2), statementId, env, resolving);
            results.add(defaultFact.type());
            losses.addAll(defaultFact.losses());
            upstream.addAll(defaultFact.upstreamErrors());
        } else {
            results.add(Types.scalar(Type.ScalarKind.NULL));
        }
        return new Fact(Types.union(results), Set.of(), losses, upstream);
    }

    // ───────────────────────────────────────────────────────────────────────
    // Table / Chart 消费者检查
    // ───────────────────────────────────────────────────────────────────────

    private void validateTable(AstNode.Comp table, String statementId, LexicalEnvironment env, Set<String> resolving) {
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
        if (!rowShapeKnown && !(rowElement.type() instanceof Type.Unknown) && !(rowElement.type() instanceof Type.Never)
                && !(rowElement.losses().contains(Fact.Loss.UNKNOWN_INPUT))) {
            emit("type-table-row-shape-mismatch",
                    "Table rows must be a flat object array, but received " + rows.type().displayName() + ".",
                    statementId, "Table", "/rows", "Flatten nested @Each results before passing them to Table.");
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
            // 列的诊断归属:命名 Col 语句用其 id,内联列退回 Table 语句;仅供 cell 使用位置定位。
            String columnStatementId = column instanceof AstNode.Ref ref && symbols.containsKey(ref.n())
                    ? ref.n()
                    : statementId;
            checkTableColumn(call, field.v(), rowElement, rows, statementId, env, resolving, rowsPath, noRowEvidence,
                    columnStatementId);
        }
    }

    private void checkTableColumn(AstNode.Comp column, String field, Fact rowElement, Fact rows, String statementId,
            LexicalEnvironment env, Set<String> resolving, String rowsPath, boolean noRowEvidence,
            String columnStatementId) {
        Expectation requirement = new Expectation.RequireField(field, new Expectation.Accept(Type.Unknown.INSTANCE));
        Checks.CheckResult existence = Checks.check(rowElement, requirement, rowsPath);
        // 所有已知行形状都没有该列是静态用法错误;Filter 仅删除行存在证据,不新增字段。
        boolean absentFromShape = isObjectShape(rowElement.type())
                && TypeOps.flatten(rows.type()).stream().noneMatch(branch -> branch instanceof Type.Sequence sequence
                        && sequence.maxLength() == 0)
                && TypeOps.flatten(rowElement.type()).stream().allMatch(branch -> {
                    Type.RecordType record = (Type.RecordType) branch;
                    return record.extra().isClosed() && !record.fields().containsKey(field);
                });
        if (existence.verdict() == Checks.Verdict.MISMATCH && noRowEvidence && !absentFromShape) {
            existence = new Checks.CheckResult(Checks.Verdict.UNPROVEN,
                    Set.of(Checks.Reason.NO_ELEMENT_EVIDENCE), Set.of(), "no retained row counterexample");
        }
        coverage.record(new CoverageReport.Entry(new CoverageReport.Obligation(statementId,
                "Table.columns[" + field + "]", "requireField", "/components/Table/columns/" + field,
                requirement.toString()), existence.verdict(), List.copyOf(existence.reasons()), existence.upstreamErrors()));
        if (existence.verdict() == Checks.Verdict.MISMATCH) {
            emit("type-table-column-missing", "Table column field \"" + field + "\" " + existence.detail() + ".",
                    statementId, "Table", "/columns/" + field, "Use a field present in the Table rows.");
        }
        // 元素 Never(空数组)时不再检查 cell 模板——无行结构可用。行存在性未证明(Filter 等)不妨碍
        // 按行形状上界检查 cell 模板:列存在性已在上方按 noRowEvidence 口径处理;字段值类型上界确定可知,
        // cell 参数级误用(如对 string 列取 Count)按类型报,存在性不影响逐元素类型判定(#83 口径)。
        if (rowElement.type() instanceof Type.Never) {
            return;
        }
        Fact cellFact = ops.readField(rowElement, field);
        validateCellTemplate(column, field, cellFact, rowElement, env, resolving, columnStatementId);
    }

    private void validateCellTemplate(AstNode.Comp column, String field, Fact cellFact, Fact rowFact,
            LexicalEnvironment env, Set<String> resolving, String columnStatementId) {
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
        BuiltinSpec spec = input.builtins().spec(render.name());
        Map<String, Fact> context = new LinkedHashMap<>();
        List<String> binders = new ArrayList<>();
        for (int binderIndex : spec.activeBinderIndexes(render.args().size())) {
            String name = LazyBuiltinBinders.binderName(spec, render, binderIndex);
            if (name != null) {
                binders.add(name);
            }
        }
        if (!binders.isEmpty()) {
            context.put(binders.get(0), cellFact);
        }
        if (binders.size() > 1) {
            context.put(binders.get(1), rowFact);
        }
        checkTemplateAtUseSite(render, context, columnStatementId, env, resolving, "Table.cell[" + field + "]",
                "/columns/" + field + "/cell");
    }

    private void validateChart(AstNode.Comp chart, String statementId, LexicalEnvironment env, Set<String> resolving) {
        if (chart.args().size() < 2) {
            return;
        }
        AstNode labelsNode = chart.args().get(0);
        Fact labels = synth(labelsNode, statementId, env, resolving);
        // 元素要求与长度关系是独立要求(#77 §11.1):元素检查先登记,不受长度是否可证影响。
        checkChartElement(chart, "labels", labelsNode, labels, chartLabelsExpectation(), "/labels", statementId);
        long[] labelRange = lengthRangeOf(labelsNode, labels, env, resolving);
        int seriesIndex = 0;
        for (AstNode seriesNode : arrayElements(chart.args().get(1))) {
            AstNode resolvedSeries = resolveNode(seriesNode, new LinkedHashSet<>());
            if (!(resolvedSeries instanceof AstNode.Comp series) || !"Series".equals(series.name())
                    || series.args().size() < 2) {
                seriesIndex++;
                continue;
            }
            AstNode valuesNode = series.args().get(1);
            Fact values = synth(valuesNode, statementId, env, resolving);
            checkChartElement(chart, "series[" + seriesIndex + "].values", valuesNode, values,
                    chartSeriesValuesExpectation(), "/series/" + seriesIndex + "/values", statementId);
            long[] valueRange = lengthRangeOf(valuesNode, values, env, resolving);
            checkChartLengthRelation(chart, labelsNode, labelRange, valuesNode, valueRange, seriesIndex, statementId);
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
     * Chart 元素要求(标签为 string 或 number 数组;series values 为 number 数组),与长度关系分开登记与检查。
     * 长度不可证明时元素检查照常进行;只有已证明的元素类型冲突才产生诊断。
     */
    private void checkChartElement(AstNode.Comp chart, String requirement, AstNode node, Fact fact,
            Expectation expectation, String usagePath, String statementId) {
        String checkedPath = dataPathOf(node).map(DataPath::source).orElse(null);
        Checks.CheckResult result = checkObligation(statementId, chart.name() + "." + requirement, "chartElement",
                "/components/" + chart.name() + "/properties/" + requirement, fact, expectation, checkedPath,
                chart.name(), usagePath);
        if (result.verdict() == Checks.Verdict.MISMATCH) {
            emit("type-prop-mismatch",
                    chart.name() + "." + requirement + " expects " + displayExpectation(expectation)
                            + ", but received " + fact.type().displayName() + ".",
                    statementId, chart.name(), usagePath,
                    "Provide values compatible with " + displayExpectation(expectation) + ".");
        }
    }

    private static Expectation chartLabelsExpectation() {
        Type stringElements = Types.sequence(Types.scalar(Type.ScalarKind.STRING), 0, Type.Sequence.UNBOUNDED);
        Type numberElements = Types.sequence(Types.scalar(Type.ScalarKind.NUMBER), 0, Type.Sequence.UNBOUNDED);
        return new Expectation.AnyOf(
                List.of(new Expectation.Accept(stringElements), new Expectation.Accept(numberElements)));
    }

    private static Expectation chartSeriesValuesExpectation() {
        return new Expectation.SequenceOf(new Expectation.Accept(Types.scalar(Type.ScalarKind.NUMBER)));
    }

    /**
     * 长度关系独立检查(#77 §11.1):区间互斥可证明长度不等;精确长度相同,或两侧来自同一个已证明保长度的
     * 来源,才证明相等。区间相同但非精确不足以证明相等——记为未证明,不报错也不宣称通过。
     */
    private void checkChartLengthRelation(AstNode.Comp chart, AstNode labelsNode, long[] labelRange,
            AstNode valuesNode, long[] valueRange, int seriesIndex, String statementId) {
        Checks.CheckResult result;
        if (labelRange == null || valueRange == null) {
            result = Checks.CheckResult.unproven(Set.of(Checks.Reason.INSUFFICIENT_EVIDENCE), Set.of(),
                    "chart length cannot be proven");
        } else if (labelRange[0] > valueRange[1] || valueRange[0] > labelRange[1]) {
            result = Checks.CheckResult.mismatch("labels length [" + labelRange[0] + ".." + labelRange[1]
                    + "] cannot match series values length [" + valueRange[0] + ".." + valueRange[1] + "]");
        } else if (provenSameLength(labelsNode, labelRange, valuesNode, valueRange)) {
            result = Checks.CheckResult.match();
        } else {
            result = Checks.CheckResult.unproven(Set.of(Checks.Reason.INSUFFICIENT_EVIDENCE), Set.of(),
                    "overlapping length ranges do not prove equal length");
        }
        String useSite = chart.name() + ".series[" + seriesIndex + "].length";
        coverage.record(new CoverageReport.Entry(
                new CoverageReport.Obligation(statementId, useSite, "chartLengthRelation",
                        "/components/" + chart.name() + "/series",
                        "labels[" + (labelRange == null ? "?" : labelRange[0] + ".." + labelRange[1]) + "] vs values["
                                + (valueRange == null ? "?" : valueRange[0] + ".." + valueRange[1]) + "]"),
                result.verdict(), List.copyOf(result.reasons()), result.upstreamErrors()));
        if (result.verdict() == Checks.Verdict.MISMATCH) {
            emit("type-chart-length-mismatch",
                    chart.name() + " labels length [" + labelRange[0] + ".." + labelRange[1] + "] cannot match series "
                            + seriesIndex + " values length [" + valueRange[0] + ".." + valueRange[1] + "].",
                    statementId, chart.name(), "/series/" + seriesIndex,
                    "Provide one series value for every chart label.");
        }
    }

    /** 精确长度相同,或两侧来自同一个已证明保长度的来源(Each/Sort 保留输入数量),才证明长度相等。 */
    static boolean provenSameLength(AstNode labelsNode, long[] labelRange, AstNode valuesNode,
            long[] valueRange) {
        if (labelRange[0] == labelRange[1] && valueRange[0] == valueRange[1]) {
            return labelRange[0] == valueRange[0];
        }
        String labelsSource = lengthPreservingSource(labelsNode);
        String valuesSource = lengthPreservingSource(valuesNode);
        return labelsSource != null && labelsSource.equals(valuesSource);
    }

    /** 去掉 Each/Sort 这类已证明保长度的运算后,同一符号引用或数据路径即同一数量来源。 */
    private static String lengthPreservingSource(AstNode node) {
        if (node instanceof AstNode.Comp comp && !comp.args().isEmpty()
                && ("Each".equals(comp.name()) || "Sort".equals(comp.name()))) {
            return lengthPreservingSource(comp.args().get(0));
        }
        if (node instanceof AstNode.Ref ref) {
            return "ref:" + ref.n();
        }
        String path = dataPathSource(node);
        return path == null ? null : "path:" + path;
    }

    /**
     * 长度区间:类型事实的数量区间若精确(上界有限)直接使用;无界(元素依赖表达式结构未知)时回退到既有 表达式级基数规则(字面量、数据路径快照基数、Each/Sort 保留输入数量)。
     */
    private long[] lengthRangeOf(AstNode node, Fact fact, LexicalEnvironment env, Set<String> resolving) {
        long[] range = ops.lengthRange(fact);
        if (range != null && range[1] != Type.Sequence.UNBOUNDED) {
            return range;
        }
        OptionalInt known = knownArraySize(node, new LinkedHashSet<>());
        return known.isPresent() ? new long[]{known.getAsInt(), known.getAsInt()} : range;
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
        if (resolved instanceof AstNode.Comp builtin && ("Each".equals(builtin.name()) || "Sort".equals(builtin.name()))
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
        if (node instanceof AstNode.RuntimeRef ref && ref.refType() == AstNode.RefType.DATA && "data".equals(ref.n())) {
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

    private void emit(String code, String message, String statementId, String component, String path, String hint) {
        // 模板使用位置的诊断归属实际使用处:同一模板用于不同列时各自定位,去重身份也含使用位置,
        // 前一列的结果不得吞掉后一列;模板声明语句保留在 hint 中可追溯(#77 第 10.3 节)。
        TemplateUseSite site = templateUseSites.peek();
        String effectiveStatementId = site == null ? statementId : site.statementId();
        String effectivePath = site == null ? path : site.pathPrefix() + (path == null ? "" : path);
        String effectiveHint = hint;
        if (site != null && site.declarationStatementId() != null
                && !site.declarationStatementId().equals(effectiveStatementId)) {
            effectiveHint = (hint == null || hint.isBlank() ? "" : hint + " ") + "Template declared at "
                    + site.declarationStatementId() + ".";
        }
        String key = code + "|" + effectiveStatementId + "|" + component + "|" + effectivePath;
        if (emitted.putIfAbsent(key, code) != null) {
            return;
        }
        nextErrorId();
        SourceSpan span = spans.getOrDefault(effectiveStatementId, SourceSpan.UNKNOWN);
        issues.add(new ValidationIssue(code, ValidationSeverity.ERROR, "type", message, effectiveStatementId, component,
                effectivePath, span.line(), span.column(), effectiveHint, false));
    }
}
