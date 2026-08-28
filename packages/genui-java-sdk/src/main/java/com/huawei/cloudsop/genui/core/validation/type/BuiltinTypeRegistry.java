package com.huawei.cloudsop.genui.core.validation.type;

import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ArrayType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.EmptyArrayType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ObjectType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.Primitive;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.PrimitiveKind;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.TemplateType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.UnionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Declarative registry for current Data and Template builtin type rules. */
final class BuiltinTypeRegistry {

    interface Context {
        Optional<ValueType> infer(AstNode node, Map<String, ValueType> scope);

        void reportArgumentMismatch(String builtin, int argumentIndex, String expected, ValueType actual);

        void reportArgumentCountMismatch(String builtin, int minimum, int maximum, int actual);
    }

    @FunctionalInterface
    private interface Rule {
        Optional<ValueType> infer(AstNode.Comp call, Map<String, ValueType> scope, Context context);
    }

    private final Map<String, Rule> rules;
    private final Map<String, Arity> arities;

    BuiltinTypeRegistry() {
        Map<String, Rule> registered = new LinkedHashMap<>();
        registered.put("Count", this::count);
        registered.put("First", this::firstOrLast);
        registered.put("Last", this::firstOrLast);
        for (String name : List.of("Sum", "Avg", "Min", "Max")) {
            registered.put(name, this::numericAggregate);
        }
        registered.put("Sort", this::arrayPreserving);
        registered.put("Filter", this::arrayPreserving);
        registered.put("ObjectEntries", this::objectEntries);
        registered.put("ObjectKeys", this::objectKeys);
        for (String name : List.of("Round", "Abs", "Floor", "Ceil")) {
            registered.put(name, this::numericScalar);
        }
        registered.put("Switch", this::switchResult);
        registered.put("FormatDate", this::formatDate);
        for (String name : List.of("FormatBytes", "FormatNumber", "FormatPercent", "FormatDuration")) {
            registered.put(name, this::stringFormatter);
        }
        registered.put("Each", this::each);
        registered.put("Render", this::render);
        rules = Collections.unmodifiableMap(registered);
        arities = Map.ofEntries(arity("Count", 1, 1), arity("First", 1, 1), arity("Last", 1, 1), arity("Sum", 1, 1),
                arity("Avg", 1, 1), arity("Min", 1, 1), arity("Max", 1, 1), arity("Sort", 2, 3), arity("Filter", 4, 4),
                arity("ObjectEntries", 1, 1), arity("ObjectKeys", 1, 1), arity("Round", 1, 2), arity("Abs", 1, 1),
                arity("Floor", 1, 1), arity("Ceil", 1, 1), arity("Switch", 2, 3), arity("FormatDate", 1, 2),
                arity("FormatBytes", 1, 1), arity("FormatNumber", 1, 2), arity("FormatPercent", 1, 2),
                arity("FormatDuration", 1, 2), arity("Each", 3, 3), arity("Render", 2, 3));
    }

    Optional<ValueType> infer(AstNode.Comp call, Map<String, ValueType> scope, Context context) {
        Rule rule = rules.get(call.name());
        if (rule == null) {
            return Optional.empty();
        }
        Arity arity = arities.get(call.name());
        if (arity != null && (call.args().size() < arity.minimum() || call.args().size() > arity.maximum())) {
            context.reportArgumentCountMismatch(call.name(), arity.minimum(), arity.maximum(), call.args().size());
        }
        return rule.infer(call, scope, context);
    }

    Set<String> names() {
        return rules.keySet();
    }

    private Optional<ValueType> count(AstNode.Comp call, Map<String, ValueType> scope, Context context) {
        Optional<ValueType> input = argument(call, 0, scope, context);
        input.ifPresent(actual -> {
            if (!isArrayLike(actual)) {
                context.reportArgumentMismatch(call.name(), 0, "array", actual);
            }
        });
        return Optional.of(number());
    }

    private Optional<ValueType> firstOrLast(AstNode.Comp call, Map<String, ValueType> scope, Context context) {
        Optional<ValueType> input = argument(call, 0, scope, context);
        if (input.isEmpty()) {
            return Optional.empty();
        }
        Optional<ValueType> elementType = arrayElementType(input.get());
        if (elementType.isPresent()) {
            return elementType;
        }
        if (!isArrayLike(input.get())) {
            context.reportArgumentMismatch(call.name(), 0, "array", input.get());
        }
        return Optional.empty();
    }

    private Optional<ValueType> numericAggregate(AstNode.Comp call, Map<String, ValueType> scope, Context context) {
        Optional<ValueType> input = argument(call, 0, scope, context);
        input.ifPresent(actual -> {
            boolean numericArray = isArrayLike(actual)
                    && arrayElementType(actual).map(BuiltinTypeRegistry::isNumber).orElse(true);
            if (!numericArray) {
                context.reportArgumentMismatch(call.name(), 0, "number[]", actual);
            }
        });
        return Optional.of(number());
    }

    private Optional<ValueType> arrayPreserving(AstNode.Comp call, Map<String, ValueType> scope, Context context) {
        Optional<ValueType> input = argument(call, 0, scope, context);
        input.ifPresent(actual -> {
            if (!isArrayLike(actual)) {
                context.reportArgumentMismatch(call.name(), 0, "array", actual);
            }
        });
        return input.filter(BuiltinTypeRegistry::isArrayLike);
    }

    private Optional<ValueType> objectEntries(AstNode.Comp call, Map<String, ValueType> scope, Context context) {
        Optional<ValueType> input = argument(call, 0, scope, context);
        if (input.isEmpty()) {
            return Optional.empty();
        }
        if (!(input.get() instanceof ObjectType object)) {
            context.reportArgumentMismatch(call.name(), 0, "object", input.get());
            return Optional.of(new EmptyArrayType());
        }
        ValueType valueType = object.fields().isEmpty()
                ? new Primitive(PrimitiveKind.NULL)
                : union(new ArrayList<>(object.fields().values()));
        return Optional.of(new ArrayType(new ObjectType(Map.of("key", stringType(), "value", valueType))));
    }

    private Optional<ValueType> objectKeys(AstNode.Comp call, Map<String, ValueType> scope, Context context) {
        Optional<ValueType> input = argument(call, 0, scope, context);
        input.ifPresent(actual -> {
            if (!(actual instanceof ObjectType)) {
                context.reportArgumentMismatch(call.name(), 0, "object", actual);
            }
        });
        return Optional.of(new ArrayType(stringType()));
    }

    private Optional<ValueType> numericScalar(AstNode.Comp call, Map<String, ValueType> scope, Context context) {
        Optional<ValueType> input = argument(call, 0, scope, context);
        input.ifPresent(actual -> {
            if (!isNumber(actual)) {
                context.reportArgumentMismatch(call.name(), 0, "number", actual);
            }
        });
        return Optional.of(number());
    }

    private Optional<ValueType> switchResult(AstNode.Comp call, Map<String, ValueType> scope, Context context) {
        List<ValueType> results = new ArrayList<>();
        if (call.args().size() > 1 && call.args().get(1) instanceof AstNode.Obj cases) {
            for (AstNode.Obj.Entry entry : cases.entries()) {
                context.infer(entry.value(), scope).ifPresent(results::add);
            }
        }
        if (call.args().size() > 2) {
            context.infer(call.args().get(2), scope).ifPresent(results::add);
        }
        return results.isEmpty() ? Optional.empty() : Optional.of(union(results));
    }

    private Optional<ValueType> formatDate(AstNode.Comp call, Map<String, ValueType> scope, Context context) {
        Optional<ValueType> input = argument(call, 0, scope, context);
        if (input.isPresent() && isArrayLike(input.get())) {
            return Optional.of(new ArrayType(stringType()));
        }
        return Optional.of(stringType());
    }

    private Optional<ValueType> stringFormatter(AstNode.Comp call, Map<String, ValueType> scope, Context context) {
        argument(call, 0, scope, context);
        return Optional.of(stringType());
    }

    private Optional<ValueType> each(AstNode.Comp call, Map<String, ValueType> scope, Context context) {
        Optional<ValueType> input = argument(call, 0, scope, context);
        if (input.isEmpty()) {
            return Optional.empty();
        }
        Optional<ValueType> elementType = arrayElementType(input.get());
        if (elementType.isEmpty()) {
            if (isArrayLike(input.get())) {
                return Optional.of(new EmptyArrayType());
            }
            context.reportArgumentMismatch(call.name(), 0, "array", input.get());
            return Optional.empty();
        }
        if (call.args().size() < 3 || !(call.args().get(1) instanceof AstNode.Str binder)) {
            return Optional.empty();
        }
        Map<String, ValueType> nestedScope = new LinkedHashMap<>(scope);
        nestedScope.put(binder.v(), elementType.get());
        return context.infer(call.args().get(2), nestedScope).map(ArrayType::new);
    }

    private Optional<ValueType> render(AstNode.Comp call, Map<String, ValueType> scope, Context context) {
        if (call.args().size() < 2) {
            return Optional.empty();
        }
        int bodyIndex = call.args().size() - 1;
        Map<String, ValueType> nestedScope = new LinkedHashMap<>(scope);
        for (int index = 0; index < bodyIndex; index++) {
            if (call.args().get(index) instanceof AstNode.Str binder) {
                nestedScope.put(binder.v(), null);
            }
        }
        return context.infer(call.args().get(bodyIndex), nestedScope).map(TemplateType::new);
    }

    private static Optional<ValueType> argument(AstNode.Comp call, int index, Map<String, ValueType> scope,
            Context context) {
        return index >= call.args().size() ? Optional.empty() : context.infer(call.args().get(index), scope);
    }

    private static boolean isNumber(ValueType type) {
        if (type instanceof Primitive primitive) {
            return primitive.kind() == PrimitiveKind.NUMBER;
        }
        if (type instanceof UnionType union) {
            return union.alternatives().stream().allMatch(BuiltinTypeRegistry::isNumber);
        }
        return false;
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
            return elements.isEmpty() ? Optional.empty() : Optional.of(union(elements));
        }
        return Optional.empty();
    }

    private static Primitive number() {
        return new Primitive(PrimitiveKind.NUMBER);
    }

    private static ValueType stringType() {
        return new Primitive(PrimitiveKind.STRING);
    }

    private static ValueType union(List<ValueType> values) {
        Set<ValueType> distinct = new LinkedHashSet<>();
        for (ValueType value : values) {
            if (value instanceof UnionType union) {
                distinct.addAll(union.alternatives());
            } else {
                distinct.add(value);
            }
        }
        return distinct.size() == 1 ? distinct.iterator().next() : new UnionType(List.copyOf(distinct));
    }

    private static Map.Entry<String, Arity> arity(String name, int minimum, int maximum) {
        return Map.entry(name, new Arity(minimum, maximum));
    }

    private record Arity(int minimum, int maximum) {
    }
}
