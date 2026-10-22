/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr;
import com.huawei.cloudsop.genui.core.validation.contract.RuleEvaluator;
import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ArrayType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.EmptyArrayType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ObjectType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.Primitive;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.PrimitiveKind;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.StringDomainType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.TemplateType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.UnionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 契约驱动的 builtin 类型求值器。
 *
 * <p>
 * 全部 builtin 的 arity/参数类型/返回类型从 base-contract.json 的 builtins 条目派生（唯一真实来源）。按序推断实参类型 → 按参数类型串检查 → 解 T 绑定 → 代入
 * returnType。Each/Render 的 returnType 推断保留代码侧兜底（binder 作用域求值超出类型语法表达能力），其 arity/binder 标记仍从契约读。
 *
 * <p>
 * 求值器语义约定（对齐迁移前行为，防语料漂移）：
 * <ol>
 * <li>param 越界按 null 类型处理（Switch 无 default 分支时并入 null）。</li>
 * <li>空对象的字段并集按 null。</li>
 * <li>union 全有或全无——某分支推断不出来时整个 union 返回无类型，不部分并入。</li>
 * <li>类型不匹配的错误提示用契约条目里的 type 原文渲染期望类型。</li>
 * </ol>
 *
 * @since 2026
 */
final class BuiltinTypeRegistry {

    /** 求值器需要的宿主能力：递归推断 + 问题上报。 */
    interface Context {
        Optional<ValueType> infer(AstNode node, Map<String, ValueType> scope);

        void reportArgumentMismatch(String builtin, int argumentIndex, String expected, ValueType actual);

        void reportArgumentCountMismatch(String builtin, int minimum, Integer maximum, int actual);

        /** 覆盖统计：一条 builtin 参数/数量检查被证明违反。默认实现供旧测试桩使用。 */
        default void recordProvenMismatch() {
        }
    }

    private final Map<String, BuiltinSpec> specs;

    BuiltinTypeRegistry(Iterable<BuiltinSpec> contractBuiltins) {
        Map<String, BuiltinSpec> registered = new LinkedHashMap<>();
        for (BuiltinSpec spec : contractBuiltins) {
            registered.put(spec.name(), spec);
        }
        specs = registered;
    }

    /** 契约派生的校验词表（name 集合）。 */
    Set<String> names() {
        return specs.keySet();
    }

    /** 词表成员判定（含 Each/Render——类型求值器对其有兜底路径）。 */
    boolean known(String name) {
        return specs.containsKey(name);
    }

    Optional<ValueType> infer(AstNode.Comp call, Map<String, ValueType> scope, Context context) {
        BuiltinSpec spec = specs.get(call.name());
        if (spec == null) {
            return Optional.empty();
        }
        // 契约声明了结构化类型规则的 builtin 走规则求值路径：调用形式、参数模式与结果运算全部来自
        // 同一次契约解析，这里不保留任何按函数名的分派（#81；Each/Render 等模板类无 typeRule，走下方兜底）。
        if (spec.typeRule() != null) {
            return structured(spec, call, scope, context);
        }
        int minimum = spec.minArity();
        Integer maximum = spec.maxArity().orElse(null);
        if (call.args().size() < minimum || (maximum != null && call.args().size() > maximum)) {
            context.reportArgumentCountMismatch(call.name(), minimum, maximum, call.args().size());
        }
        // Each/Render：binder 作用域推断留代码侧兜底（见类注释）。
        if ("Each".equals(spec.name())) {
            return each(spec, call, scope, context);
        }
        if ("Render".equals(spec.name())) {
            return render(spec, call, scope, context);
        }
        return generic(spec, call, scope, context);
    }

    /** 结构化规则路径：先递归推断全部实参（发现独立错误），再交由规则求值器完成检查与求解。 */
    private Optional<ValueType> structured(BuiltinSpec spec, AstNode.Comp call, Map<String, ValueType> scope,
            Context context) {
        List<Optional<ValueType>> arguments = new ArrayList<>();
        for (AstNode argument : call.args()) {
            arguments.add(context.infer(argument, scope));
        }
        ValueType result = RuleEvaluator.evaluate(spec.typeRule(), call.args().size(), new RuleEvaluator.CallSite() {
            @Override
            public Optional<ValueType> argumentType(int index) {
                return arguments.get(index);
            }

            @Override
            public void reportArgumentMismatch(int index, String expectedPattern, ValueType actual) {
                context.recordProvenMismatch();
                context.reportArgumentMismatch(spec.name(), index, expectedPattern, actual);
            }

            @Override
            public void reportArityViolation(int minimum, Optional<Integer> maximum, int actual) {
                context.recordProvenMismatch();
                context.reportArgumentCountMismatch(spec.name(), minimum, maximum.orElse(null), actual);
            }
        });
        return Optional.of(result);
    }

    /** 通用求值路径：推断实参 → 逐参检查 → 解 T → 代入 returnType。 */
    private Optional<ValueType> generic(BuiltinSpec spec, AstNode.Comp call, Map<String, ValueType> scope,
            Context context) {
        List<Optional<ValueType>> arguments = new ArrayList<>();
        for (AstNode argument : call.args()) {
            arguments.add(context.infer(argument, scope));
        }
        Map<String, ValueType> bindings = new LinkedHashMap<>();
        List<BuiltinSpec.ParamSpec> params = spec.params();
        for (int i = 0; i < params.size(); i++) {
            if (i >= arguments.size()) {
                break;
            }
            Optional<ValueType> actual = arguments.get(i);
            if (actual.isEmpty()) {
                continue;
            }
            checkParam(spec, i, params.get(i), actual.get(), context);
            // Switch 的 cases 参数：对值逐项推断并解 T（全证明语义见 instantiateCasesUnion）。
            if (params.get(i).typeExpr() instanceof TypeExpr.RecordOf record
                    && record.value() instanceof TypeExpr.TypeVar && call.args().get(i) instanceof AstNode.Obj cases) {
                instantiateCasesUnion(spec, call, cases, i, arguments, bindings, context);
            } else {
                bindTypeVar(params.get(i).typeExpr(), actual.get(), bindings);
            }
        }
        if (spec.returnType() == null) {
            return Optional.empty();
        }
        return instantiate(spec.returnType(), bindings, arguments);
    }

    /**
     * Switch 的 cases 值类型并集：每个 entry 的值类型全证明才返回类型，否则无类型。default 分支缺位时 param 越界按 null。
     */
    private Optional<ValueType> instantiateCasesUnion(BuiltinSpec spec, AstNode.Comp call, AstNode.Obj cases,
            int casesIndex, List<Optional<ValueType>> arguments, Map<String, ValueType> bindings, Context context) {
        List<ValueType> results = new ArrayList<>();
        boolean fullyProven = true;
        for (AstNode.Obj.Entry entry : cases.entries()) {
            Optional<ValueType> result = context.infer(entry.value(), new LinkedHashMap<>());
            fullyProven &= result.isPresent();
            result.ifPresent(results::add);
        }
        // default 分支（cases 的下一参）：越界按 null 类型并入。
        int defaultIndex = casesIndex + 1;
        if (defaultIndex < call.args().size()) {
            Optional<ValueType> defaultResult = arguments.get(defaultIndex);
            fullyProven &= defaultResult.isPresent();
            defaultResult.ifPresent(results::add);
        } else {
            results.add(new Primitive(PrimitiveKind.NULL));
        }
        Optional<ValueType> union = fullyProven && !results.isEmpty()
                ? Optional.of(ValueTypes.union(results))
                : Optional.empty();
        // Switch 的 returnType（T | param(2)）由调用方 generic 继续代入；这里先把 T 绑定为 cases 并集。
        union.ifPresent(value -> bindings.put("T", value));
        return union;
    }

    /** 实参类型 vs 参数类型串检查；期望类型按契约 type 原文渲染。 */
    private static void checkParam(BuiltinSpec spec, int index, BuiltinSpec.ParamSpec param, ValueType actual,
            Context context) {
        TypeExpr expected = param.typeExpr();
        if (expected instanceof TypeExpr.Base base) {
            switch (base.name()) {
                case "any" :
                    return;
                case "number" :
                    if (!ValueTypes.isNumber(actual)) {
                        context.reportArgumentMismatch(spec.name(), index, param.type(), actual);
                    }
                    return;
                case "string" :
                    if (!isString(actual)) {
                        context.reportArgumentMismatch(spec.name(), index, param.type(), actual);
                    }
                    return;
                case "object" :
                    if (!(actual instanceof ObjectType)) {
                        context.reportArgumentMismatch(spec.name(), index, param.type(), actual);
                    }
                    return;
                case "array" :
                    if (!isArrayLike(actual)) {
                        context.reportArgumentMismatch(spec.name(), index, param.type(), actual);
                    }
                    return;
                default :
                    return;
            }
        }
        if (expected instanceof TypeExpr.ArrayOf array) {
            if (!isArrayLike(actual)) {
                context.reportArgumentMismatch(spec.name(), index, param.type(), actual);
                return;
            }
            // number[]：数组元素须全为数（空/无元素信息数组放行，对齐旧 numericAggregate 语义）。
            if (array.element() instanceof TypeExpr.Base element && "number".equals(element.name())) {
                boolean numericArray = arrayElementType(actual).map(ValueTypes::isNumber).orElse(true);
                if (!numericArray) {
                    context.reportArgumentMismatch(spec.name(), index, param.type(), actual);
                }
            }
            return;
        }
        if (expected instanceof TypeExpr.RecordOf || expected instanceof TypeExpr.UnionOf
                || expected instanceof TypeExpr.TypeVar || expected instanceof TypeExpr.ObjectLiteral
                || expected instanceof TypeExpr.ParamRef || expected instanceof TypeExpr.Conditional) {
            // Record/union/T/对象字面量/param(n)/条件式 不做入口检查——检查或求值在 returnType 代入时进行。
            return;
        }
    }

    /** 解 T 绑定：参数类型与实参类型做一次结构匹配。 */
    private static void bindTypeVar(TypeExpr expected, ValueType actual, Map<String, ValueType> bindings) {
        if (expected instanceof TypeExpr.ArrayOf array && array.element() instanceof TypeExpr.TypeVar) {
            arrayElementType(actual).ifPresent(element -> bindings.put("T", element));
            return;
        }
        if (expected instanceof TypeExpr.RecordOf record && record.value() instanceof TypeExpr.TypeVar) {
            if (actual instanceof ObjectType object) {
                // 空对象的字段并集按 null。
                ValueType valueType = object.fields().isEmpty()
                        ? new Primitive(PrimitiveKind.NULL)
                        : ValueTypes.union(new ArrayList<>(object.fields().values()));
                bindings.put("T", valueType);
            }
            return;
        }
        if (expected instanceof TypeExpr.TypeVar) {
            bindings.put("T", actual);
        }
    }

    /** 代入 returnType：T → 绑定类型；param(n) → 第 n 实参类型（越界按 null）。 */
    private Optional<ValueType> instantiate(TypeExpr type, Map<String, ValueType> bindings,
            List<Optional<ValueType>> arguments) {
        if (type instanceof TypeExpr.Base base) {
            return switch (base.name()) {
                case "number" -> Optional.of(new Primitive(PrimitiveKind.NUMBER));
                case "string" -> Optional.of(new Primitive(PrimitiveKind.STRING));
                case "boolean" -> Optional.of(new Primitive(PrimitiveKind.BOOLEAN));
                case "null" -> Optional.of(new Primitive(PrimitiveKind.NULL));
                // any 的运行时类型未知：返回无类型，不制造下游假阳性。
                case "any" -> Optional.empty();
                // object/array 仅作为返回类型时给出可安全并集的最粗类型。
                case "object" -> Optional.of(new ObjectType(Map.of()));
                case "array" -> Optional.of(new EmptyArrayType());
                default -> Optional.empty();
            };
        }
        if (type instanceof TypeExpr.ArrayOf array) {
            return instantiate(array.element(), bindings, arguments).map(ArrayType::new);
        }
        if (type instanceof TypeExpr.RecordOf record) {
            return instantiate(record.value(), bindings, arguments);
        }
        if (type instanceof TypeExpr.ObjectLiteral literal) {
            Map<String, ValueType> fields = new LinkedHashMap<>();
            boolean fullyProven = true;
            for (TypeExpr.ObjectLiteral.Field field : literal.fields()) {
                Optional<ValueType> resolved = instantiate(field.type(), bindings, arguments);
                if (resolved.isEmpty()) {
                    fullyProven = false;
                } else {
                    fields.put(field.name(), resolved.get());
                }
            }
            return fullyProven ? Optional.of(new ObjectType(fields)) : Optional.empty();
        }
        if (type instanceof TypeExpr.UnionOf union) {
            // union 全有或全无：某分支推断不出来 → 整个 union 无类型。
            List<ValueType> alternatives = new ArrayList<>();
            for (TypeExpr alternative : union.alternatives()) {
                Optional<ValueType> resolved = instantiate(alternative, bindings, arguments);
                if (resolved.isEmpty()) {
                    return Optional.empty();
                }
                alternatives.add(resolved.get());
            }
            return Optional.of(ValueTypes.union(alternatives));
        }
        if (type instanceof TypeExpr.TypeVar) {
            return Optional.ofNullable(bindings.get("T"));
        }
        if (type instanceof TypeExpr.ParamRef ref) {
            // param 越界按 null 类型处理。
            if (ref.index() >= arguments.size()) {
                return Optional.of(new Primitive(PrimitiveKind.NULL));
            }
            return arguments.get(ref.index());
        }
        if (type instanceof TypeExpr.Conditional conditional) {
            // 条件分流：param(n) 越界或类型未知时按非数组分支求值。
            boolean isArray = conditional.paramIndex() < arguments.size()
                    && arguments.get(conditional.paramIndex()).map(BuiltinTypeRegistry::isArrayLike).orElse(false);
            return isArray
                    ? instantiate(conditional.whenArray(), bindings, arguments)
                    : instantiate(conditional.otherwise(), bindings, arguments);
        }
        return Optional.empty();
    }

    /** Each 兜底：binder 引入嵌套作用域，返回 ArrayType(模板类型)。arity/binder 标记从契约读。 */
    private Optional<ValueType> each(BuiltinSpec spec, AstNode.Comp call, Map<String, ValueType> scope,
            Context context) {
        Optional<ValueType> input = argument(call, 0, scope, context);
        if (input.isEmpty()) {
            return Optional.empty();
        }
        Optional<ValueType> elementType = arrayElementType(input.get());
        if (elementType.isEmpty()) {
            if (isArrayLike(input.get())) {
                return Optional.of(new EmptyArrayType());
            }
            context.reportArgumentMismatch(call.name(), 0, spec.params().get(0).type(), input.get());
            return Optional.empty();
        }
        if (call.args().size() < 3) {
            return Optional.empty();
        }
        if (!(call.args().get(1) instanceof AstNode.Str binder)) {
            argument(call, 1, scope, context).ifPresent(
                    actual -> context.reportArgumentMismatch(call.name(), 1, "binder string literal", actual));
            return Optional.empty();
        }
        Map<String, ValueType> nestedScope = new LinkedHashMap<>(scope);
        nestedScope.put(binder.v(), elementType.get());
        // sequence.map 语义：数量保留，元素存在证据随源数组传播（空输入 → 空结果，不虚构元素）。
        boolean presence = input.get() instanceof ArrayType source && source.elementPresenceProven();
        return context.infer(call.args().get(2), nestedScope).map(body -> new ArrayType(body, presence));
    }

    /** Render 兜底：前 n-1 参为 binder（值为 null 类型占位），返回 TemplateType。arity/binder 标记从契约读。 */
    private Optional<ValueType> render(BuiltinSpec spec, AstNode.Comp call, Map<String, ValueType> scope,
            Context context) {
        if (call.args().size() < 2) {
            return Optional.empty();
        }
        int bodyIndex = call.args().size() - 1;
        Map<String, ValueType> nestedScope = new LinkedHashMap<>(scope);
        boolean validBinders = true;
        for (int index = 0; index < bodyIndex; index++) {
            if (call.args().get(index) instanceof AstNode.Str binder) {
                nestedScope.put(binder.v(), null);
            } else {
                validBinders = false;
                int argumentIndex = index;
                argument(call, index, scope, context).ifPresent(actual -> context.reportArgumentMismatch(call.name(),
                        argumentIndex, "binder string literal", actual));
            }
        }
        return validBinders
                ? context.infer(call.args().get(bodyIndex), nestedScope).map(TemplateType::new)
                : Optional.empty();
    }

    private static Optional<ValueType> argument(AstNode.Comp call, int index, Map<String, ValueType> scope,
            Context context) {
        return index >= call.args().size() ? Optional.empty() : context.infer(call.args().get(index), scope);
    }

    private static boolean isString(ValueType type) {
        if (type instanceof Primitive primitive) {
            return primitive.kind() == PrimitiveKind.STRING;
        }
        if (type instanceof StringDomainType) {
            return true;
        }
        return type instanceof UnionType union && !union.alternatives().isEmpty()
                && union.alternatives().stream().allMatch(BuiltinTypeRegistry::isString);
    }

    private static boolean isArrayLike(ValueType type) {
        if (type instanceof ArrayType || type instanceof EmptyArrayType) {
            return true;
        }
        return type instanceof UnionType union
                && union.alternatives().stream().allMatch(BuiltinTypeRegistry::isArrayLike);
    }

    private static Optional<ValueType> arrayElementType(ValueType type) {
        if (type instanceof ArrayType array) {
            return Optional.of(array.elementType());
        }
        if (type instanceof EmptyArrayType) {
            return Optional.empty();
        }
        if (type instanceof UnionType union && isArrayLike(union)) {
            List<ValueType> elements = union.alternatives().stream().map(BuiltinTypeRegistry::arrayElementType)
                    .flatMap(Optional::stream).toList();
            return elements.isEmpty() ? Optional.empty() : Optional.of(ValueTypes.union(elements));
        }
        return Optional.empty();
    }
}
