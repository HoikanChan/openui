package com.huawei.cloudsop.genui.core.validation.type.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 集中类型运算(#77 第 7 节):字段读取、对象覆盖、keys/values/entries、数组元素与数量传播、展平。
 *
 * <p>
 * 运算遵守抽象解释的保守要求:结果必须覆盖真实运算可能产生的值(可以宽,不能漏);证据按运算声明的规则 传播——Sort/Each 保留数量与元素存在证据,Filter 下界归零并删除行保留证据,字段覆盖替换该字段旧证据。
 * 所有运算按分支分配;预算耗尽时返回覆盖原可能值的安全摘要并记录 {@link Fact.Loss#PRECISION_BUDGET}。
 *
 * @since 2026
 */
public final class TypeOps {

    private final AnalysisBudget.Tracker budget;

    public TypeOps(AnalysisBudget.Tracker budget) {
        this.budget = budget;
    }

    // ───────────────────────────────────────────────────────────────────────
    // 字段读取
    // ───────────────────────────────────────────────────────────────────────

    /**
     * 读取对象字段。字段存在但类型未知时保留字段、返回 Unknown;确定缺失与可能缺失分开:确定缺失(关闭对象 无此字段)返回 Never + 缺失标记;开放对象未列出字段按 extra 值类型;OPTIONAL
     * 字段存在性未知,值类型不并 null(缺失与 null 分开)。
     *
     * <p>
     * 证据关联(#83 §6.5):字段值仍在当前快照里——从观察到的对象值逐行提取该字段的实际值作为结果的 ObservedValue
     * 证据(丢失来源路径,值经冻结);观察到的对象行确定缺该字段则该行的读取结果是"不存在"——不产生值证据, 也不虚构缺失反例(关闭类型上界已承载确定缺失判定)。
     */
    public Fact readField(Fact object, String name) {
        if (object.hasUpstreamErrors()) {
            return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamErrors(object.upstreamErrors());
        }
        if (object.type() instanceof Type.Unknown) {
            return Fact.unknown(Fact.Loss.UNKNOWN_INPUT).withLosses(object.losses());
        }
        List<Type> branches = flatten(object.type());
        List<Type> results = new ArrayList<>();
        Set<Fact.Evidence> evidence = new LinkedHashSet<>();
        Set<Fact.Loss> losses = new LinkedHashSet<>(object.losses());
        boolean definitelyMissing = true;
        boolean possiblyPresent = false;
        for (Type branch : branches) {
            if (!(branch instanceof Type.RecordType record)) {
                losses.add(Fact.Loss.UNKNOWN_INPUT);
                results.add(Type.Unknown.INSTANCE);
                definitelyMissing = false;
                possiblyPresent = true;
                continue;
            }
            Type.Field field = record.fields().get(name);
            if (field != null) {
                definitelyMissing = false;
                possiblyPresent = true;
                results.add(field.type());
                continue;
            }
            if (!record.extra().isClosed()) {
                definitelyMissing = false;
                possiblyPresent = true;
                results.add(record.extra().valueType());
            }
        }
        for (Fact.Evidence item : object.evidence()) {
            if (item instanceof Fact.Evidence.ObservedValue observed && observed.value() instanceof Map<?, ?> row
                    && row.containsKey(name)) {
                evidence.add(new Fact.Evidence.ObservedValue(null, SnapshotFacts.deepFreeze(row.get(name))));
            }
        }
        if (definitelyMissing) {
            return new Fact(Type.Never.INSTANCE, Set.of(), losses, object.upstreamErrors());
        }
        if (!possiblyPresent) {
            losses.add(Fact.Loss.UNKNOWN_INPUT);
        }
        return new Fact(unionWithinBudget(results, object), evidence, losses, object.upstreamErrors());
    }

    // ───────────────────────────────────────────────────────────────────────
    // 对象覆盖(MergeObject)
    // ───────────────────────────────────────────────────────────────────────

    /**
     * 按实参顺序覆盖(#77 第 7.2 节):确定后字段覆盖前字段;可选后字段保留两种值可能;未知额外字段可能覆盖 前字段,但不抹去已证明的字段存在性(存在性保留 REQUIRED,值类型放宽为 Unknown)。
     *
     * @param operands
     *            按调用顺序排列的对象事实
     * @return 覆盖结果;无操作数时返回确知空对象
     */
    public Fact overlay(List<Fact> operands) {
        if (operands == null || operands.isEmpty()) {
            return Fact.of(Types.record(Map.of(), Type.Extra.closed()));
        }
        Set<String> upstream = new LinkedHashSet<>();
        for (Fact operand : operands) {
            upstream.addAll(operand.upstreamErrors());
        }
        if (!upstream.isEmpty()) {
            return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamErrors(upstream);
        }
        List<List<Type>> combinations = combinations(operands.stream().map(f -> flatten(f.type())).toList());
        if (combinations == null) {
            // 分支组合超预算:安全摘要,不丢弃“可能是对象”的信息之外也不伪造精确形状。
            return Fact.unknown(Fact.Loss.PRECISION_BUDGET);
        }
        List<Type> results = new ArrayList<>();
        Set<Fact.Loss> losses = new LinkedHashSet<>();
        operands.forEach(operand -> losses.addAll(operand.losses()));
        for (List<Type> combination : combinations) {
            results.add(overlayCombination(combination, losses));
            if (!budget.tryConsumeTypeNodes(1)) {
                losses.add(Fact.Loss.PRECISION_BUDGET);
                return new Fact(Type.Unknown.INSTANCE, Set.of(), losses, Set.of());
            }
        }
        return new Fact(unionWithinBudget(results, null), Set.of(), losses, Set.of());
    }

    private Type overlayCombination(List<Type> combination, Set<Fact.Loss> losses) {
        Map<String, Type.Field> fields = new LinkedHashMap<>();
        Type.Extra extra = Type.Extra.closed();
        for (Type branch : combination) {
            if (!(branch instanceof Type.RecordType record)) {
                // 非对象分支(含 Unknown):可能覆盖任何已知字段 —— 存在性不丢,值类型放宽。
                losses.add(Fact.Loss.UNKNOWN_INPUT);
                Map<String, Type.Field> widened = new LinkedHashMap<>();
                fields.forEach((name, field) -> widened.put(name,
                        new Type.Field(Types.union(field.type(), Type.Unknown.INSTANCE), field.presence())));
                fields = widened;
                extra = Type.Extra.open(Type.Unknown.INSTANCE);
                continue;
            }
            for (var entry : record.fields().entrySet()) {
                String name = entry.getKey();
                Type.Field next = entry.getValue();
                Type.Field previous = fields.get(name);
                if (previous == null && !extra.isClosed()) {
                    previous = Type.Field.optional(extra.valueType());
                }
                if (previous == null) {
                    fields.put(name, next);
                    continue;
                }
                if (next.presence() == Type.Presence.REQUIRED) {
                    fields.put(name, next);
                } else {
                    // 可选后字段保留两种值可能。
                    fields.put(name, new Type.Field(Types.union(previous.type(), next.type()), previous.presence()));
                }
            }
            if (!record.extra().isClosed()) {
                // 开放后对象:未知额外字段可能覆盖前字段 —— 放宽已列字段值,存在性保留。
                Map<String, Type.Field> widened = new LinkedHashMap<>();
                Type extraType = record.extra().valueType();
                for (var entry : fields.entrySet()) {
                    Type.Field field = entry.getValue();
                    Type merged = record.fields().containsKey(entry.getKey())
                            ? field.type()
                            : Types.union(field.type(), extraType);
                    widened.put(entry.getKey(), new Type.Field(merged, field.presence()));
                }
                fields = widened;
                extra = Type.Extra.open(extra.isClosed() ? extraType : Types.union(extra.valueType(), extraType));
            }
        }
        return Types.record(fields, extra);
    }

    // ───────────────────────────────────────────────────────────────────────
    // ObjectKeys / Values / Entries
    // ───────────────────────────────────────────────────────────────────────

    /**
     * 对象键集合(#77 第 7.4 节):已知字段生成字符串字面值联合;开放尾部增加 string 标量;Closed 空对象产生 已知空数组;Open 不能误作空对象。
     */
    public Fact keys(Fact object) {
        return project(object, Projection.KEYS);
    }

    /** 对象值集合:字段值取联合;开放尾部增加其值类型;字段为 Unknown 仍参与结果。 */
    public Fact values(Fact object) {
        return project(object, Projection.VALUES);
    }

    /** 键值行集合:每个字段生成独立 {key: 字面值, value: 对应类型} 分支,保留 key/value 关联。 */
    public Fact entries(Fact object) {
        return project(object, Projection.ENTRIES);
    }

    private enum Projection {
        KEYS, VALUES, ENTRIES
    }

    private Fact project(Fact object, Projection projection) {
        if (object.hasUpstreamErrors()) {
            return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamErrors(object.upstreamErrors());
        }
        if (object.type() instanceof Type.Unknown) {
            return Fact.unknown(Fact.Loss.UNKNOWN_INPUT).withLosses(object.losses());
        }
        List<Type> branches = flatten(object.type());
        List<Type> results = new ArrayList<>();
        Set<Fact.Loss> losses = new LinkedHashSet<>(object.losses());
        for (Type branch : branches) {
            if (!(branch instanceof Type.RecordType record)) {
                losses.add(Fact.Loss.UNKNOWN_INPUT);
                results.add(Type.Unknown.INSTANCE);
                continue;
            }
            results.add(projectRecord(record, projection));
        }
        return new Fact(unionWithinBudget(results, object), Set.of(), losses, object.upstreamErrors());
    }

    private Type projectRecord(Type.RecordType record, Projection projection) {
        List<Type> alternatives = new ArrayList<>();
        long min = 0;
        long max = 0;
        boolean open = !record.extra().isClosed();
        for (var entry : record.fields().entrySet()) {
            boolean required = entry.getValue().presence() == Type.Presence.REQUIRED;
            if (required) {
                min++;
            }
            max++;
            alternatives.add(switch (projection) {
                case KEYS -> Types.stringLiterals(Set.of(entry.getKey()));
                case VALUES -> entry.getValue().type();
                case ENTRIES ->
                    Types.record(Map.of("key", Type.Field.required(Types.stringLiterals(Set.of(entry.getKey()))),
                            "value", Type.Field.required(entry.getValue().type())), Type.Extra.closed());
            });
        }
        if (open) {
            max = Type.Sequence.UNBOUNDED;
            alternatives.add(switch (projection) {
                case KEYS -> Types.scalar(Type.ScalarKind.STRING);
                case VALUES -> record.extra().valueType();
                case ENTRIES -> Types.record(Map.of("key", Type.Field.required(Types.scalar(Type.ScalarKind.STRING)),
                        "value", Type.Field.required(record.extra().valueType())), Type.Extra.closed());
            });
        }
        Type element = Types.union(alternatives);
        return Types.sequence(element, min, max);
    }

    // ───────────────────────────────────────────────────────────────────────
    // 数组元素、数量与证据传播
    // ───────────────────────────────────────────────────────────────────────

    /** 取数组元素事实;非数组输入返回 Never + 未知输入损失;空数组元素为 Never(不制造零长度证据)。 */
    public Fact element(Fact sequence) {
        if (sequence.hasUpstreamErrors()) {
            return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamErrors(sequence.upstreamErrors());
        }
        if (sequence.type() instanceof Type.Unknown) {
            return Fact.unknown(Fact.Loss.UNKNOWN_INPUT).withLosses(sequence.losses());
        }
        List<Type> elements = new ArrayList<>();
        Set<Fact.Loss> losses = new LinkedHashSet<>(sequence.losses());
        for (Type branch : flatten(sequence.type())) {
            if (branch instanceof Type.Sequence seq) {
                elements.add(seq.element());
            } else {
                losses.add(Fact.Loss.UNKNOWN_INPUT);
                elements.add(Type.Unknown.INSTANCE);
            }
        }
        Set<Fact.Evidence> evidence = new LinkedHashSet<>();
        for (Fact.Evidence item : sequence.evidence()) {
            if (item instanceof Fact.Evidence.ObservedValue observed && observed.value() instanceof List<?> values) {
                for (Object value : values) {
                    evidence.add(new Fact.Evidence.ObservedValue(observed.path(), value));
                }
            }
        }
        return new Fact(Types.union(elements), evidence, losses, sequence.upstreamErrors());
    }

    /** 数量区间事实(供 Chart 长度等比较);来源不保持数量时返回 null 表示未知。 */
    public long[] lengthRange(Fact sequence) {
        if (sequence.type() instanceof Type.Unknown || sequence.hasUpstreamErrors()) {
            return null;
        }
        long min = Long.MAX_VALUE;
        long max = 0;
        for (Type branch : flatten(sequence.type())) {
            if (!(branch instanceof Type.Sequence seq)) {
                return null;
            }
            min = Math.min(min, seq.minLength());
            max = Math.max(max, seq.maxLength());
        }
        return min == Long.MAX_VALUE ? null : new long[]{min, max};
    }

    /** Sort:元素类型不变,保留数量区间;保留元素存在证据,顺序证据失效(本模型不承载顺序证据)。 */
    public Fact preserve(Fact sequence) {
        return sequence;
    }

    /** Filter:元素类型为输入安全上界;下界降到 0;删除“某行必然保留”的存在证据。 */
    public Fact subset(Fact sequence) {
        if (sequence.hasUpstreamErrors()) {
            return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamErrors(sequence.upstreamErrors());
        }
        if (sequence.type() instanceof Type.Unknown) {
            return sequence;
        }
        List<Type> results = new ArrayList<>();
        Set<Fact.Loss> losses = new LinkedHashSet<>(sequence.losses());
        for (Type branch : flatten(sequence.type())) {
            if (branch instanceof Type.Sequence seq) {
                results.add(Types.sequence(seq.element(), 0, seq.maxLength()));
            } else {
                losses.add(Fact.Loss.UNKNOWN_INPUT);
                results.add(Type.Unknown.INSTANCE);
            }
        }
        // 存在证据被 Filter 删除:只保留长度为零也可能的安全上界,并打上"元素存在未证明"的丢失标记——
        // 无任何损失不意味着观察到元素(#77 §6.2/§6.5/§7.5:Filter 可删掉快照里的保留行)。
        // 保留的元素类型上界仍确定:谓词只选行不改行,逐元素判定(字段读取/元素级参数检查)在该上界上进行;
        // 存在性由数组层(this loss + 空证据)判定,不打 UNKNOWN_INPUT(那会连元素级确定性一并吞掉)。
        losses.add(Fact.Loss.NO_ELEMENT_EVIDENCE);
        return new Fact(Types.union(results), Set.of(), losses, sequence.upstreamErrors());
    }

    /** Each 映射:保留输入数量区间,元素替换为模板 body 类型;空输入仍产生空序列。 */
    public Fact map(Fact sequence, Fact body) {
        if (sequence.hasUpstreamErrors() || body.hasUpstreamErrors()) {
            Set<String> upstream = new LinkedHashSet<>(sequence.upstreamErrors());
            upstream.addAll(body.upstreamErrors());
            return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamErrors(upstream);
        }
        if (sequence.type() instanceof Type.Unknown) {
            return Fact.unknown(Fact.Loss.UNKNOWN_INPUT).withLosses(sequence.losses());
        }
        List<Type> results = new ArrayList<>();
        Set<Fact.Loss> losses = new LinkedHashSet<>(sequence.losses());
        losses.addAll(body.losses());
        for (Type branch : flatten(sequence.type())) {
            if (branch instanceof Type.Sequence seq) {
                results.add(Types.sequence(body.type(), seq.minLength(), seq.maxLength()));
            } else {
                losses.add(Fact.Loss.UNKNOWN_INPUT);
                results.add(Type.Unknown.INSTANCE);
            }
        }
        // 输入确定非空(每个可能的数组分支长度下界都 ≥1,或观察到实际非空数组值)时,输出元素确定来自 body:
        // 摘掉 NO_ELEMENT_EVIDENCE,生成形状(如 Each 产生的对象行)可支撑确定性的列存在性判定。
        // #77 §6.5:单个联合分支非空不能证明元素存在——另一个分支可能为空,运行时拿到的未必是 body 形状。
        // 因此只有全部分支都非空(或实际观察)才摘除该 loss。
        boolean inputProvablyNonempty = sequence.evidence().stream()
                .anyMatch(e -> e instanceof Fact.Evidence.ObservedValue observed
                        && observed.value() instanceof List<?> values && !values.isEmpty());
        if (!inputProvablyNonempty) {
            List<Type> branches = flatten(sequence.type());
            inputProvablyNonempty = !branches.isEmpty() && branches.stream()
                    .allMatch(branch -> branch instanceof Type.Sequence seq && seq.minLength() >= 1);
        }
        if (inputProvablyNonempty) {
            losses.remove(Fact.Loss.NO_ELEMENT_EVIDENCE);
        }
        // 仅在输入元素存在已证明时传播 body 的观察值,并包回输出数组这一层。
        // body 自身的长度/字段证据不描述输出数组,不得直接复制到输出层。
        Set<Fact.Evidence> evidence = new LinkedHashSet<>();
        List<Object> observedElements = new ArrayList<>();
        for (Fact.Evidence item : body.evidence()) {
            if (item instanceof Fact.Evidence.ObservedValue observed) {
                observedElements.add(SnapshotFacts.deepFreeze(observed.value()));
            }
        }
        if (inputProvablyNonempty && !observedElements.isEmpty()) {
            String path = body.evidence().stream().filter(e -> e instanceof Fact.Evidence.ObservedValue)
                    .map(e -> ((Fact.Evidence.ObservedValue) e).path()).filter(p -> p != null).findFirst().orElse(null);
            evidence.add(new Fact.Evidence.ObservedValue(path, List.copyOf(observedElements)));
        }
        return new Fact(Types.union(results), evidence, losses, Set.of());
    }

    /** First/Last 选择:元素类型上界;空数组结果值契约未定义(#79 未决),不为空数组补 null 证据。 */
    public Fact select(Fact sequence) {
        return element(sequence);
    }

    /**
     * Flatten 一层(#79 矩阵:接受 array[],null/非数组容错为空数组;混合元素策略未定——严格 params 下可拒绝 已知非数组元素,但类型上界不能把元素未知误作空数组)。
     */
    public Fact flattenOne(Fact sequence) {
        if (sequence.hasUpstreamErrors()) {
            return Fact.unknown(Fact.Loss.BLOCKED_BY_ERROR).withUpstreamErrors(sequence.upstreamErrors());
        }
        if (sequence.type() instanceof Type.Unknown) {
            return Fact.unknown(Fact.Loss.UNKNOWN_INPUT).withLosses(sequence.losses());
        }
        List<Type> results = new ArrayList<>();
        Set<Fact.Loss> losses = new LinkedHashSet<>(sequence.losses());
        for (Type branch : flatten(sequence.type())) {
            if (!(branch instanceof Type.Sequence outer)) {
                losses.add(Fact.Loss.UNKNOWN_INPUT);
                results.add(Type.Unknown.INSTANCE);
                continue;
            }
            List<Type> inner = flatten(outer.element());
            List<Type> sequenceBranches = inner.stream().filter(t -> t instanceof Type.Sequence).toList();
            boolean hasNonSequence = inner.stream()
                    .anyMatch(t -> !(t instanceof Type.Sequence) && !(t instanceof Type.Never));
            if (sequenceBranches.isEmpty()) {
                // 空外层(元素 Never)或完全无内层数组:只可能得到空数组。
                if (!hasNonSequence) {
                    results.add(Types.emptySequence());
                } else {
                    losses.add(Fact.Loss.UNKNOWN_INPUT);
                    results.add(Type.Unknown.INSTANCE);
                }
                continue;
            }
            if (hasNonSequence) {
                // 混合元素策略未定义:保守放宽,不误作空数组。
                losses.add(Fact.Loss.UNKNOWN_INPUT);
                results.add(Type.Unknown.INSTANCE);
                continue;
            }
            long min = outer.minLength() == 0
                    ? 0
                    : sequenceBranches.stream().mapToLong(s -> ((Type.Sequence) s).minLength()).min().orElse(0);
            long max = 0;
            boolean unbounded = outer.maxLength() == Type.Sequence.UNBOUNDED;
            for (Type elementBranch : sequenceBranches) {
                Type.Sequence innerSeq = (Type.Sequence) elementBranch;
                if (innerSeq.maxLength() == Type.Sequence.UNBOUNDED) {
                    unbounded = true;
                } else if (!unbounded) {
                    max = Math.max(max, safeMultiply(outer.maxLength(), innerSeq.maxLength()));
                }
            }
            Type element = Types.union(sequenceBranches.stream().map(s -> ((Type.Sequence) s).element()).toList());
            results.add(Types.sequence(element, min, unbounded ? Type.Sequence.UNBOUNDED : max));
        }
        return new Fact(unionWithinBudget(results, sequence), Set.of(), losses, sequence.upstreamErrors());
    }

    private static long safeMultiply(long left, long right) {
        if (left == 0 || right == 0) {
            return 0;
        }
        long result = left * right;
        return result / left == right ? result : Type.Sequence.UNBOUNDED;
    }

    // ───────────────────────────────────────────────────────────────────────
    // 联合分支工具
    // ───────────────────────────────────────────────────────────────────────

    /** 展开联合为分支列表(非联合类型返回单元素列表)。公开供契约规则求值等运算消费方复用同一分支语义。 */
    public static List<Type> flatten(Type type) {
        return type instanceof Type.Union union ? union.alternatives() : List.of(type);
    }

    /** 多操作数的分支组合(笛卡尔),逐步折叠并在展开前检查预算;超限返回 null 表示须改用安全摘要。 */
    private List<List<Type>> combinations(List<List<Type>> branchSets) {
        List<List<Type>> acc = new ArrayList<>();
        acc.add(List.of());
        for (List<Type> branches : branchSets) {
            if (!budget.unionBranchesWithinBudget((long) acc.size() * branches.size())) {
                return null;
            }
            List<List<Type>> next = new ArrayList<>();
            for (List<Type> prefix : acc) {
                for (Type branch : branches) {
                    List<Type> combined = new ArrayList<>(prefix);
                    combined.add(branch);
                    next.add(combined);
                }
            }
            acc = next;
        }
        return acc;
    }

    /** 联合并在预算超限时改用安全摘要(Unknown + PRECISION_BUDGET),不制造通过或错误证据。 */
    private Type unionWithinBudget(List<Type> alternatives, Fact source) {
        if (!budget.unionBranchesWithinBudget(alternatives.size())) {
            Set<Fact.Loss> losses = new LinkedHashSet<>();
            if (source != null) {
                losses.addAll(source.losses());
            }
            losses.add(Fact.Loss.PRECISION_BUDGET);
            return Type.Unknown.INSTANCE;
        }
        return Types.union(alternatives);
    }
}
