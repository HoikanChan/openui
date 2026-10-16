package com.huawei.cloudsop.genui.core.validation.type;

import com.huawei.cloudsop.genui.core.validation.type.DataPath.Index;
import com.huawei.cloudsop.genui.core.validation.type.DataPath.Key;
import com.huawei.cloudsop.genui.core.validation.type.DataPath.Segment;
import com.huawei.cloudsop.genui.core.validation.type.DataPathResolution.Evidence;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ArrayType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ObjectType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.Primitive;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.PrimitiveKind;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.UnionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/** Complete, on-demand resolver for generated paths over one Render Data Model snapshot. */
public final class DataPathResolver {

    private static final int MAX_STRING_DOMAIN_SIZE = 32;

    public DataPathResolution resolve(Map<String, Object> dataModel, DataPath path) {
        List<Object> values = new ArrayList<>();
        values.add(dataModel == null ? Map.of() : dataModel);
        int pluckDepth = 0;
        Evidence evidence = new Evidence(1, 0, 0, 1);

        for (Segment segment : path.segments()) {
            Step step = resolveStep(values, segment, path.source());
            if (step.failure() != null) {
                return step.failure();
            }
            values = step.values();
            evidence = step.evidence();
            pluckDepth += step.plucked() ? 1 : 0;
        }

        ValueType type = inferValues(values);
        for (int i = 0; i < pluckDepth; i++) {
            type = new ArrayType(type);
        }
        return new DataPathResolution.Resolved(path.source(), type, evidence);
    }

    /** Return a proven array cardinality for a literal data path, or empty when traversal is uncertain. */
    public OptionalInt resolveCardinality(Map<String, Object> dataModel, DataPath path) {
        Object current = dataModel;
        for (Segment segment : path.segments()) {
            if (segment instanceof Key key && current instanceof Map<?, ?> map) {
                if (!map.containsKey(key.name())) {
                    return OptionalInt.empty();
                }
                current = map.get(key.name());
                continue;
            }
            if (segment instanceof Index index && current instanceof List<?> list) {
                if (index.value() < 0 || index.value() >= list.size()) {
                    return OptionalInt.empty();
                }
                current = list.get(index.value());
                continue;
            }
            if (segment instanceof Key key && current instanceof List<?> list) {
                List<Object> plucked = new ArrayList<>();
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> map) || !map.containsKey(key.name())) {
                        return OptionalInt.empty();
                    }
                    plucked.add(map.get(key.name()));
                }
                current = plucked;
                continue;
            }
            return OptionalInt.empty();
        }
        return current instanceof List<?> list ? OptionalInt.of(list.size()) : OptionalInt.empty();
    }

    private Step resolveStep(List<Object> current, Segment segment, String path) {
        List<Object> next = new ArrayList<>();
        int present = 0;
        int missing = 0;
        int nulls = 0;
        int total = 0;
        boolean plucked = false;

        for (Object value : current) {
            if (segment instanceof Key key && value instanceof List<?> list) {
                if ("length".equals(key.name())) {
                    return Step.failure(new DataPathResolution.InvalidTraversal(path, "array",
                            "Use @Count(" + path.substring(0, path.length() - ".length".length()) + ") instead."));
                }
                if (list.isEmpty()) {
                    return Step.failure(new DataPathResolution.Unprovable(path,
                            "The empty array does not prove member type; use existing data or avoid the member "
                                    + "traversal."));
                }
                plucked = true;
                for (Object item : list) {
                    total++;
                    if (!(item instanceof Map<?, ?> map)) {
                        return Step.failure(new DataPathResolution.InvalidTraversal(path, typeName(item),
                                "Member access requires an object array."));
                    }
                    if (!map.containsKey(key.name())) {
                        missing++;
                        continue;
                    }
                    Object member = map.get(key.name());
                    if (member == null) {
                        nulls++;
                    } else {
                        present++;
                        next.add(member);
                    }
                }
                continue;
            }

            total++;
            if (segment instanceof Key key && value instanceof Map<?, ?> map) {
                if (!map.containsKey(key.name())) {
                    missing++;
                    continue;
                }
                Object member = map.get(key.name());
                if (member == null) {
                    nulls++;
                } else {
                    present++;
                    next.add(member);
                }
                continue;
            }
            if (segment instanceof Index index && value instanceof List<?> list) {
                if (index.value() < 0 || index.value() >= list.size()) {
                    missing++;
                    continue;
                }
                Object member = list.get(index.value());
                if (member == null) {
                    nulls++;
                } else {
                    present++;
                    next.add(member);
                }
                continue;
            }
            if (segment instanceof Key key && value instanceof List<?>) {
                throw new IllegalStateException("Array key must be handled by pluck: " + key.name());
            }
            return Step.failure(new DataPathResolution.InvalidTraversal(path, typeName(value),
                    "Member access requires an object or array."));
        }

        if (next.isEmpty()) {
            if (nulls > 0 && missing == 0) {
                next.add(null);
            } else if (nulls > 0) {
                return Step.failure(
                        new DataPathResolution.Unprovable(path, "No non-null value proves the requested member type."));
            } else {
                return Step
                        .failure(new DataPathResolution.Missing(path, "Use a path present in the Render Data Model."));
            }
        }
        return new Step(next, new Evidence(present, missing, nulls, total), plucked, null);
    }

    private static ValueType inferValues(List<Object> values) {
        if (values.stream().allMatch(String.class::isInstance)) {
            Set<String> domain = new LinkedHashSet<>();
            values.forEach(value -> domain.add((String) value));
            if (domain.size() <= MAX_STRING_DOMAIN_SIZE) {
                return new ValueType.StringDomainType(domain);
            }
            return new Primitive(PrimitiveKind.STRING);
        }
        List<ValueType> types = values.stream().map(DataPathResolver::inferValue).distinct().toList();
        return types.size() == 1 ? types.getFirst() : new UnionType(types);
    }

    private static ValueType inferValue(Object value) {
        if (value == null) {
            return new Primitive(PrimitiveKind.NULL);
        }
        if (value instanceof String || value instanceof Character) {
            return new ValueType.StringDomainType(Set.of(String.valueOf(value)));
        }
        if (value instanceof Number) {
            return new Primitive(PrimitiveKind.NUMBER);
        }
        if (value instanceof Boolean) {
            return new Primitive(PrimitiveKind.BOOLEAN);
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return new ValueType.EmptyArrayType();
            }
            return new ArrayType(inferValues(new ArrayList<>(list)));
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, ValueType> fields = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                fields.put(String.valueOf(entry.getKey()), inferValue(entry.getValue()));
            }
            return new ObjectType(fields);
        }
        return new Primitive(PrimitiveKind.STRING);
    }

    private static String typeName(Object value) {
        return inferValue(value).displayName();
    }

    private record Step(List<Object> values, Evidence evidence, boolean plucked, DataPathResolution failure) {
        static Step failure(DataPathResolution failure) {
            return new Step(List.of(), new Evidence(0, 0, 0, 0), false, failure);
        }
    }
}
