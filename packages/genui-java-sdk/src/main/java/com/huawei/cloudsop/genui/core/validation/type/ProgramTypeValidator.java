package com.huawei.cloudsop.genui.core.validation.type;

import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.ValidationSnapshot;
import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.parser.Program;
import com.huawei.cloudsop.genui.core.validation.parser.SourceSpan;
import com.huawei.cloudsop.genui.core.validation.parser.Statement;
import com.huawei.cloudsop.genui.core.validation.semantic.ComponentDef;
import com.huawei.cloudsop.genui.core.validation.semantic.ContractCatalog;
import com.huawei.cloudsop.genui.core.validation.semantic.ParamDef;
import com.huawei.cloudsop.genui.core.validation.type.DataPathResolution.InvalidTraversal;
import com.huawei.cloudsop.genui.core.validation.type.DataPathResolution.Missing;
import com.huawei.cloudsop.genui.core.validation.type.DataPathResolution.Resolved;
import com.huawei.cloudsop.genui.core.validation.type.DataPathResolution.Unprovable;
import com.huawei.cloudsop.genui.core.validation.type.TypeAssignability.Result;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ArrayType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ComponentType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.EmptyArrayType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ObjectType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.Primitive;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.PrimitiveKind;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.StringDomainType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.UnionType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Static type-validation pass over a parsed generated program and concrete Render Data Model. */
public final class ProgramTypeValidator {

    private final DataPathResolver pathResolver = new DataPathResolver();
    private final SchemaTypeCompiler schemaCompiler = new SchemaTypeCompiler();
    private final TypeAssignability assignability = new TypeAssignability();

    /**
     * Validate proven expression and component-prop types. Unsupported or unproven checks are skipped. Streaming
     * validation stays on the existing structural pass; the complete FINAL program is typed.
     */
    public List<ValidationIssue> validate(ValidationSnapshot snapshot) {
        if (snapshot.mode() != ValidationMode.FINAL || snapshot.dataModel() == null) {
            return List.of();
        }
        Program program = snapshot.program();
        Context context = new Context(snapshot);
        // #56 同名覆盖:类型检查只遍历当前有效定义(Program#currentById,与 ProgramAnalyzer 共用同一去重口径),
        // 被覆盖语句的历史类型错误不再产生当前错误;修改定义造成的下游不匹配仍会被发现。
        for (Statement statement : program.currentById().values()) {
            if (statement instanceof Statement.Value value
                    && !snapshot.analysis().orphaned().contains(statement.id())) {
                context.infer(value.expr(), statement.id(), new LinkedHashMap<>(), new HashSet<>());
            }
        }
        snapshot.typeCoverage().record(context.checked, context.mismatched);
        return List.copyOf(context.issues);
    }

    private final class Context {
        private final ContractCatalog catalog;
        private final Map<String, Object> dataModel;
        private final Map<String, AstNode> symbols = new LinkedHashMap<>();
        private final Map<String, SourceSpan> spans = new LinkedHashMap<>();
        private final List<ValidationIssue> issues = new ArrayList<>();
        private final Set<String> emitted = new LinkedHashSet<>();
        private int checked;
        private int mismatched;

        private final BuiltinTypeRegistry builtinTypes;

        Context(ValidationSnapshot snapshot) {
            this.catalog = snapshot.catalog();
            this.dataModel = snapshot.dataModel();
            this.builtinTypes = new BuiltinTypeRegistry(snapshot.builtins().specsForTypeValidation());
            for (Statement statement : snapshot.program().currentById().values()) {
                spans.put(statement.id(), statement.span());
                if (statement instanceof Statement.Value value) {
                    symbols.put(statement.id(), value.expr());
                } else if (statement instanceof Statement.State state) {
                    symbols.put(statement.id(), state.init());
                }
            }
        }

        Optional<ValueType> infer(AstNode node, String statementId, Map<String, ValueType> scope,
                Set<String> resolving) {
            if (node == null) {
                return Optional.empty();
            }
            if (node instanceof AstNode.Str string) {
                return Optional.of(new StringDomainType(Set.of(string.v())));
            }
            if (node instanceof AstNode.Num) {
                return Optional.of(new Primitive(PrimitiveKind.NUMBER));
            }
            if (node instanceof AstNode.Bool) {
                return Optional.of(new Primitive(PrimitiveKind.BOOLEAN));
            }
            if (node instanceof AstNode.Null) {
                return Optional.of(new Primitive(PrimitiveKind.NULL));
            }
            if (node instanceof AstNode.Arr array) {
                if (array.els().isEmpty()) {
                    return Optional.of(new EmptyArrayType());
                }
                List<ValueType> elementTypes = new ArrayList<>();
                for (AstNode element : array.els()) {
                    infer(element, statementId, scope, resolving).ifPresent(elementTypes::add);
                }
                return elementTypes.size() == array.els().size()
                        ? Optional.of(new ArrayType(ValueTypes.union(elementTypes)))
                        : Optional.empty();
            }
            if (node instanceof AstNode.Obj object) {
                Map<String, ValueType> fields = new LinkedHashMap<>();
                for (AstNode.Obj.Entry entry : object.entries()) {
                    infer(entry.value(), statementId, scope, resolving)
                            .ifPresent(value -> fields.put(entry.key(), value));
                }
                return Optional.of(new ObjectType(fields));
            }
            if (node instanceof AstNode.Ref ref) {
                if (scope.containsKey(ref.n())) {
                    return Optional.ofNullable(scope.get(ref.n()));
                }
                if (!symbols.containsKey(ref.n()) || !resolving.add(ref.n())) {
                    return Optional.empty();
                }
                try {
                    return infer(symbols.get(ref.n()), ref.n(), scope, resolving);
                } finally {
                    resolving.remove(ref.n());
                }
            }
            Optional<DataPath> dataPath = dataPath(node);
            if (dataPath.isPresent()) {
                DataPathResolution resolution = pathResolver.resolve(dataModel, dataPath.get());
                if (resolution instanceof Resolved resolved) {
                    // 直接数据观察：路径本身解析出的具体值列表也是观察证据。投影数组的存在证据由
                    // DataPathResolver 依值列表标注；顶层路径（如 data.items 取到非空 List）在此补标。
                    return Optional.of(withObservedPresence(resolved.type(), resolved.evidence()));
                }
                emitPathIssue(resolution, statementId);
                return Optional.empty();
            }
            if (node instanceof AstNode.Member member) {
                return infer(member.obj(), statementId, scope, resolving).flatMap(type -> field(type, member.field()));
            }
            if (node instanceof AstNode.Index index) {
                Optional<ValueType> container = infer(index.obj(), statementId, scope, resolving);
                if (container.isPresent() && container.get() instanceof ArrayType array) {
                    return Optional.of(array.elementType());
                }
                return Optional.empty();
            }
            if (node instanceof AstNode.Comp component) {
                return inferComponent(component, statementId, scope, resolving);
            }
            if (node instanceof AstNode.Ternary ternary) {
                infer(ternary.cond(), statementId, scope, resolving);
                Optional<ValueType> thenType = infer(ternary.then(), statementId, scope, resolving);
                Optional<ValueType> elseType = infer(ternary.otherwise(), statementId, scope, resolving);
                return thenType.isPresent() && elseType.isPresent()
                        ? Optional.of(ValueTypes.union(List.of(thenType.get(), elseType.get())))
                        : Optional.empty();
            }
            if (node instanceof AstNode.BinOp binary) {
                Optional<ValueType> left = infer(binary.left(), statementId, scope, resolving);
                Optional<ValueType> right = infer(binary.right(), statementId, scope, resolving);
                if (Set.of("==", "!=", "<", "<=", ">", ">=", "&&", "||").contains(binary.op())) {
                    if (left.isPresent() && right.isPresent()
                            && isObviousOperatorMismatch(binary.op(), left.get(), right.get())) {
                        emit("type-operator-mismatch",
                                "Operator " + binary.op() + " cannot compare " + left.get().displayName() + " with "
                                        + right.get().displayName() + ".",
                                statementId, null, null,
                                "Compare compatible scalar values; use @Each/@Filter for arrays.");
                        return Optional.empty();
                    }
                    return Optional.of(new Primitive(PrimitiveKind.BOOLEAN));
                }
                if ("??".equals(binary.op()) && left.isPresent() && right.isPresent()) {
                    return Optional.of(ValueTypes.union(List.of(left.get(), right.get())));
                }
                if (left.isPresent() && right.isPresent()) {
                    boolean arithmeticMismatch = "+".equals(binary.op())
                            ? isComposite(left.get()) || isComposite(right.get())
                            : !ValueTypes.isNumber(left.get()) || !ValueTypes.isNumber(right.get());
                    if (arithmeticMismatch) {
                        emit("type-operator-mismatch",
                                "Operator " + binary.op() + " cannot combine " + left.get().displayName() + " with "
                                        + right.get().displayName() + ".",
                                statementId, null, null,
                                "Use scalar operands; use @Each or a data builtin for arrays and objects.");
                        return Optional.empty();
                    }
                    boolean stringResult = left.get().displayName().equals("string")
                            || right.get().displayName().equals("string");
                    return Optional.of(new Primitive(stringResult ? PrimitiveKind.STRING : PrimitiveKind.NUMBER));
                }
                return Optional.empty();
            }
            if (node instanceof AstNode.UnaryOp unary) {
                infer(unary.operand(), statementId, scope, resolving);
                return Optional
                        .of(new Primitive("!".equals(unary.op()) ? PrimitiveKind.BOOLEAN : PrimitiveKind.NUMBER));
            }
            return Optional.empty();
        }

        private Optional<ValueType> inferComponent(AstNode.Comp component, String statementId,
                Map<String, ValueType> scope, Set<String> resolving) {
            if (builtinTypes.known(component.name())) {
                return builtinTypes.infer(component, scope, new BuiltinTypeRegistry.Context() {
                    @Override
                    public Optional<ValueType> infer(AstNode node, Map<String, ValueType> nestedScope) {
                        return Context.this.infer(node, statementId, nestedScope, resolving);
                    }

                    @Override
                    public void reportArgumentMismatch(String builtin, int argumentIndex, String expected,
                            ValueType actual) {
                        emit("builtin-argument-type-mismatch",
                                "@" + builtin + " argument " + argumentIndex + " expects " + expected
                                        + ", but received " + actual.displayName() + ".",
                                statementId, null, "/args/" + argumentIndex,
                                "Use a value compatible with " + expected + ".");
                    }

                    @Override
                    public void reportArgumentCountMismatch(String builtin, int minimum, Integer maximum, int actual) {
                        String expected = maximum == null
                                ? "at least " + minimum
                                : minimum == maximum ? String.valueOf(minimum) : minimum + "-" + maximum;
                        emit("builtin-argument-count-mismatch",
                                "@" + builtin + " expects " + expected + " argument(s), but received " + actual + ".",
                                statementId, null, "/args", "Use the documented @" + builtin + " signature.");
                    }

                    @Override
                    public void recordProvenMismatch() {
                        checked++;
                        mismatched++;
                    }
                });
            }
            ComponentDef definition = catalog.get(component.name());
            if (definition == null) {
                for (AstNode argument : component.args()) {
                    infer(argument, statementId, scope, resolving);
                }
                return Optional.empty();
            }
            List<ParamDef> params = definition.params();
            for (int index = 0; index < component.args().size(); index++) {
                AstNode argument = component.args().get(index);
                Optional<ValueType> actual = infer(argument, statementId, scope, resolving);
                if (index >= params.size() || actual.isEmpty()) {
                    continue;
                }
                ParamDef param = params.get(index);
                Optional<ExpectedType> expected = schemaCompiler.compile(param.schema());
                if (expected.isEmpty()) {
                    continue;
                }
                Result result = assignability.check(actual.get(), expected.get());
                if (result != Result.UNPROVEN) {
                    checked++;
                }
                if (result == Result.MISMATCH) {
                    mismatched++;
                    emitMismatch(component.name(), param.name(), expected.get(), actual.get(), statementId);
                } else if (result == Result.UNPROVEN && actual.get() instanceof EmptyArrayType
                        && containsComponentConstraint(expected.get())) {
                    AstNode resolvedArgument = resolveNode(argument, new HashSet<>());
                    Optional<DataPath> path = dataPath(resolvedArgument);
                    if (path.isPresent()) {
                        emit("type-data-unprovable",
                                path.get().source() + " is empty and cannot prove the required component element type.",
                                statementId, component.name(), path.get().source(),
                                "Provide typed elements or avoid this component slot for the empty result.");
                    } else {
                        emit("empty-component-slot",
                                component.name() + "." + param.name()
                                        + " requires typed components, but received an empty array literal.",
                                statementId, component.name(), "/" + param.name(),
                                "Provide at least one compatible component value.");
                    }
                }
            }
            if ("Table".equals(component.name())) {
                validateTable(component, statementId, scope, resolving);
            }
            if ("LineChart".equals(component.name()) || "BarChart".equals(component.name())) {
                validateChart(component, statementId, scope, resolving);
            }
            return Optional.of(new ComponentType(component.name()));
        }

        private void validateChart(AstNode.Comp chart, String statementId, Map<String, ValueType> scope,
                Set<String> resolving) {
            if (chart.args().size() < 2) {
                return;
            }
            OptionalInt labelCount = knownArraySize(chart.args().get(0), new HashSet<>());
            List<AstNode> seriesNodes = arrayElements(chart.args().get(1), new HashSet<>());
            for (int seriesIndex = 0; seriesIndex < seriesNodes.size(); seriesIndex++) {
                AstNode resolvedSeries = resolveNode(seriesNodes.get(seriesIndex), new HashSet<>());
                if (!(resolvedSeries instanceof AstNode.Comp series) || !"Series".equals(series.name())
                        || series.args().size() < 2) {
                    continue;
                }
                // 元素存在证据检查（#81）：LineChart 需要至少两个已证明存在的点。元素类型已知但
                // 存在证据被 Filter 等运算删除时，检查显式未完成——按未证明记录，不虚构通过。
                if ("LineChart".equals(chart.name())) {
                    Optional<ValueType> valuesType = infer(series.args().get(1), statementId, scope, resolving);
                    if (valuesType.isPresent() && lacksElementPresence(valuesType.get())) {
                        checked++;
                        emit("type-chart-insufficient-data", "LineChart series " + seriesIndex
                                + " cannot prove at least two points: the source provides no element-presence evidence.",
                                statementId, chart.name(), "/series/" + seriesIndex,
                                "Provide a source with proven elements (direct data or evidence-preserving operations like Sort).");
                        validateDuplicateSeriesValues(chart, series, seriesIndex, statementId);
                        continue;
                    }
                }
                OptionalInt valueCount = knownArraySize(series.args().get(1), new HashSet<>());
                if (labelCount.isPresent() && valueCount.isPresent()
                        && labelCount.getAsInt() != valueCount.getAsInt()) {
                    checked++;
                    mismatched++;
                    emit("type-chart-length-mismatch",
                            chart.name() + " has " + labelCount.getAsInt() + " labels, but series " + seriesIndex
                                    + " has " + valueCount.getAsInt() + " values.",
                            statementId, chart.name(), "/series/" + seriesIndex,
                            "Provide one series value for every chart label.");
                }
                if ("LineChart".equals(chart.name()) && valueCount.isPresent() && valueCount.getAsInt() < 2) {
                    checked++;
                    mismatched++;
                    emit("type-chart-insufficient-data",
                            "LineChart series " + seriesIndex + " has only " + valueCount.getAsInt()
                                    + " point(s), so it cannot draw a trend line.",
                            statementId, chart.name(), "/series/" + seriesIndex,
                            "Provide at least two ordered points for a trend line.");
                }
                validateDuplicateSeriesValues(chart, series, seriesIndex, statementId);
            }
        }

        private void validateDuplicateSeriesValues(AstNode.Comp chart, AstNode.Comp series, int seriesIndex,
                String statementId) {
            List<AstNode> values = arrayElements(series.args().get(1), new HashSet<>());
            Set<AstNode> derivedValues = new HashSet<>();
            for (AstNode value : values) {
                if (!(value instanceof AstNode.Ref)) {
                    continue;
                }
                AstNode resolved = resolveNode(value, new HashSet<>());
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

        private OptionalInt knownArraySize(AstNode node, Set<String> resolving) {
            AstNode resolved = resolveNode(node, resolving);
            if (resolved instanceof AstNode.Arr array) {
                return OptionalInt.of(array.els().size());
            }
            Optional<DataPath> path = dataPath(resolved);
            if (path.isPresent()) {
                return pathResolver.resolveCardinality(dataModel, path.get());
            }
            if (resolved instanceof AstNode.Comp builtin
                    && ("Each".equals(builtin.name()) || "Sort".equals(builtin.name())) && !builtin.args().isEmpty()) {
                return knownArraySize(builtin.args().get(0), resolving);
            }
            return OptionalInt.empty();
        }

        private void validateTable(AstNode.Comp table, String statementId, Map<String, ValueType> scope,
                Set<String> resolving) {
            if (table.args().size() < 2) {
                return;
            }
            Optional<ValueType> rows = infer(table.args().get(1), statementId, scope, resolving);
            if (rows.isEmpty()) {
                return;
            }
            Set<String> fields = rowFields(rows.get());
            if (fields.isEmpty()) {
                return;
            }
            Optional<ValueType> rowType = rowElementType(rows.get());
            if (rowType.isEmpty()) {
                return;
            }
            if (!isObjectShape(rowType.get())) {
                emit("type-table-row-shape-mismatch",
                        "Table rows must be a flat object array, but received " + rows.get().displayName() + ".",
                        statementId, "Table", "/rows", "Flatten nested @Each results before passing them to Table.");
                return;
            }
            for (AstNode column : arrayElements(table.args().get(0), new HashSet<>())) {
                AstNode resolvedColumn = resolveNode(column, new HashSet<>());
                if (!(resolvedColumn instanceof AstNode.Comp call) || !"Col".equals(call.name())
                        || call.args().size() < 2) {
                    continue;
                }
                AstNode resolvedField = resolveNode(call.args().get(1), new HashSet<>());
                if (!(resolvedField instanceof AstNode.Str field)) {
                    continue;
                }
                Optional<ValueType> valueType = fieldType(rowType.get(), field.v());
                if (valueType.isEmpty() || !fieldPresentInAll(rowType.get(), field.v())) {
                    String detail = valueType.isEmpty() ? "the proven row shape" : "some rows";
                    emit("type-table-column-missing",
                            "Table column field \"" + field.v() + "\" is absent from " + detail + ".", statementId,
                            "Table", "/columns/" + field.v(), "Use a field present in the Table rows.");
                    continue;
                }
                validateCellTemplate(call, valueType.get(), rowType.get(), statementId, scope, resolving);
            }
        }

        private void validateCellTemplate(AstNode.Comp column, ValueType valueType, ValueType rowType,
                String statementId, Map<String, ValueType> outerScope, Set<String> resolving) {
            if (column.args().size() < 3 || !(column.args().get(2) instanceof AstNode.Obj options)) {
                return;
            }
            AstNode cell = options.entries().stream().filter(entry -> "cell".equals(entry.key()))
                    .map(AstNode.Obj.Entry::value).findFirst().orElse(null);
            AstNode resolvedCell = resolveNode(cell, new HashSet<>());
            if (!(resolvedCell instanceof AstNode.Comp render) || !"Render".equals(render.name())
                    || render.args().size() < 2) {
                return;
            }
            int bodyIndex = render.args().size() - 1;
            Map<String, ValueType> renderScope = new LinkedHashMap<>(outerScope);
            if (render.args().get(0) instanceof AstNode.Str valueBinder) {
                renderScope.put(valueBinder.v(), valueType);
            }
            if (bodyIndex > 1 && render.args().get(1) instanceof AstNode.Str rowBinder) {
                renderScope.put(rowBinder.v(), rowType);
            }
            infer(render.args().get(bodyIndex), statementId, renderScope, resolving);
        }

        private List<AstNode> arrayElements(AstNode node, Set<String> resolving) {
            AstNode resolved = resolveNode(node, resolving);
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

        /** 直接观察证据标注：解析值的 Evidence 覆盖非空数组时，为数组类型补元素存在证据。 */
        private ValueType withObservedPresence(ValueType type, DataPathResolution.Evidence evidence) {
            if (type instanceof ArrayType array && evidence.total() > 0) {
                return array.withElementPresence(true);
            }
            if (type instanceof UnionType union) {
                return ValueTypeOps.union(
                        union.alternatives().stream().map(branch -> withObservedPresence(branch, evidence)).toList());
            }
            return type;
        }

        private void emitPathIssue(DataPathResolution resolution, String statementId) {
            String code;
            String message;
            String hint;
            if (resolution instanceof Missing missing) {
                code = "type-data-path-missing";
                message = missing.path() + " is absent from the Render Data Model.";
                hint = missing.hint();
            } else if (resolution instanceof InvalidTraversal invalid) {
                code = "type-data-invalid-traversal";
                message = invalid.path() + " cannot traverse " + invalid.actualType() + ".";
                hint = invalid.hint();
            } else if (resolution instanceof Unprovable unprovable) {
                code = "type-data-unprovable";
                message = unprovable.path() + " cannot be proven from the Render Data Model.";
                hint = unprovable.hint();
            } else {
                return;
            }
            emit(code, message, statementId, null, resolution.path(), hint);
        }

        private void emitMismatch(String component, String param, ExpectedType expected, ValueType actual,
                String statementId) {
            String message = component + "." + param + " expects " + expected.displayName() + ", but received "
                    + actual.displayName() + ".";
            emit(containsComponentConstraint(expected) ? "component-slot-type-mismatch" : "type-prop-mismatch", message,
                    statementId, component, "/" + param, "Use a value compatible with " + expected.displayName() + ".");
        }

        private void emit(String code, String message, String statementId, String component, String path, String hint) {
            String key = code + "|" + statementId + "|" + component + "|" + path;
            if (!emitted.add(key)) {
                return;
            }
            SourceSpan span = spans.getOrDefault(statementId, SourceSpan.UNKNOWN);
            issues.add(new ValidationIssue(code, ValidationSeverity.ERROR, "type", message, statementId, component,
                    path, span.line(), span.column(), hint, false));
        }
    }

    /** 期望中含数组元素约束（元素类型或元素组件槽位被限定）。 */
    private static boolean requiresElementEvidence(ExpectedType expected) {
        if (expected instanceof ExpectedType.ArrayConstraint array) {
            return array.elementConstraint() != null;
        }
        if (expected instanceof ExpectedType.UnionConstraint union) {
            return union.alternatives().stream().anyMatch(ProgramTypeValidator::requiresElementEvidence);
        }
        return false;
    }

    /** 实际值缺少元素存在证据：空数组，或被删除了存在保证的数组（Filter 子集）。 */
    private static boolean lacksElementPresence(ValueType type) {
        if (type instanceof EmptyArrayType) {
            return true;
        }
        if (type instanceof ArrayType array) {
            return !array.elementPresenceProven();
        }
        if (type instanceof UnionType union) {
            return union.alternatives().stream().anyMatch(ProgramTypeValidator::lacksElementPresence);
        }
        return false;
    }

    private static boolean containsComponentConstraint(ExpectedType expected) {
        if (expected instanceof ExpectedType.ComponentConstraint) {
            return true;
        }
        if (expected instanceof ExpectedType.ArrayConstraint array) {
            return array.elementConstraint() != null && containsComponentConstraint(array.elementConstraint());
        }
        if (expected instanceof ExpectedType.UnionConstraint union) {
            return union.alternatives().stream().anyMatch(ProgramTypeValidator::containsComponentConstraint);
        }
        return false;
    }

    private static Set<String> rowFields(ValueType type) {
        if (type instanceof ArrayType array) {
            return rowFields(array.elementType());
        }
        if (type instanceof ObjectType object) {
            return object.fields().keySet();
        }
        if (type instanceof UnionType union) {
            Set<String> fields = new LinkedHashSet<>();
            union.alternatives().forEach(alternative -> fields.addAll(rowFields(alternative)));
            return fields;
        }
        return Set.of();
    }

    private static Optional<ValueType> rowElementType(ValueType type) {
        if (type instanceof ArrayType array) {
            return Optional.of(array.elementType());
        }
        if (type instanceof UnionType union) {
            List<ValueType> elements = union.alternatives().stream().map(ProgramTypeValidator::rowElementType)
                    .flatMap(Optional::stream).toList();
            return elements.isEmpty() ? Optional.empty() : Optional.of(ValueTypes.union(elements));
        }
        return Optional.empty();
    }

    private static Optional<ValueType> fieldType(ValueType type, String path) {
        ValueType current = type;
        for (String segment : path.split("\\.")) {
            Optional<ValueType> next = field(current, segment);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            current = next.get();
        }
        return Optional.of(current);
    }

    private static Optional<ValueType> field(ValueType type, String name) {
        if (type instanceof ObjectType object) {
            return Optional.ofNullable(object.fields().get(name));
        }
        if (type instanceof UnionType union) {
            List<ValueType> present = union.alternatives().stream().map(alternative -> field(alternative, name))
                    .flatMap(Optional::stream).toList();
            return present.isEmpty() ? Optional.empty() : Optional.of(ValueTypes.union(present));
        }
        return Optional.empty();
    }

    private static boolean fieldPresentInAll(ValueType type, String path) {
        String[] segments = path.split("\\.");
        return fieldPresentInAll(type, segments, 0);
    }

    private static boolean fieldPresentInAll(ValueType type, String[] segments, int index) {
        if (type instanceof UnionType union) {
            return !union.alternatives().isEmpty() && union.alternatives().stream()
                    .allMatch(alternative -> fieldPresentInAll(alternative, segments, index));
        }
        if (!(type instanceof ObjectType object)) {
            return false;
        }
        ValueType value = object.fields().get(segments[index]);
        if (value == null) {
            return false;
        }
        return index == segments.length - 1 || fieldPresentInAll(value, segments, index + 1);
    }

    private static boolean isObjectShape(ValueType type) {
        if (type instanceof ObjectType) {
            return true;
        }
        return type instanceof UnionType union && !union.alternatives().isEmpty()
                && union.alternatives().stream().allMatch(ProgramTypeValidator::isObjectShape);
    }

    private static boolean isDerivedExpression(AstNode node) {
        return node instanceof AstNode.Comp || node instanceof AstNode.Member || node instanceof AstNode.Index
                || node instanceof AstNode.BinOp || node instanceof AstNode.UnaryOp || node instanceof AstNode.Ternary;
    }

    private static boolean isObviousOperatorMismatch(String operator, ValueType left, ValueType right) {
        boolean leftComposite = isComposite(left);
        boolean rightComposite = isComposite(right);
        if (Set.of("<", "<=", ">", ">=").contains(operator)) {
            return leftComposite || rightComposite;
        }
        return leftComposite != rightComposite;
    }

    private static boolean isComposite(ValueType type) {
        if (type instanceof UnionType union) {
            return union.alternatives().stream().anyMatch(ProgramTypeValidator::isComposite);
        }
        return type instanceof ArrayType || type instanceof EmptyArrayType || type instanceof ObjectType
                || type instanceof ComponentType || type instanceof ValueType.TemplateType;
    }

    private static Optional<DataPath> dataPath(AstNode node) {
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
}
