/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import com.huawei.cloudsop.genui.core.validation.type.analysis.DataPath.Index;
import com.huawei.cloudsop.genui.core.validation.type.analysis.DataPath.Key;
import com.huawei.cloudsop.genui.core.validation.type.analysis.DataPath.Segment;
import com.huawei.cloudsop.genui.core.validation.type.analysis.DataPathResolution.Evidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * 单次快照内、按需遍历一条生成路径的观察器。
 *
 * <p>只读 Render Data Model:逐段记录字段存在性,最后把观察到的值提升为类型内核的 {@link Type}。这是类型阶段
 * 访问数据的唯一生产路径——没有并行的值类型代数或翻译层。
 *
 * @since 2026
 */
public final class DataPathResolver {

    private static final int MAX_STRING_DOMAIN_SIZE = 32;

    public DataPathResolution resolve(Map<String, Object> dataModel, DataPath path) {
        List<Object> values = new ArrayList<>();
        values.add(dataModel == null ? Map.of() : dataModel);
        int pluckDepth = 0;
        Evidence evidence = new Evidence(1, 0, 0, 1, false);

        for (Segment segment : path.segments()) {
            Step step = resolveStep(values, segment, path.source());
            if (step.failure() != null) {
                return step.failure();
            }
            values = step.values();
            evidence = step.evidence();
            pluckDepth += step.plucked() ? 1 : 0;
        }

        Type type = inferValues(values);
        for (int i = 0; i < pluckDepth; i++) {
            type = Types.sequence(type, 0, Type.Sequence.UNBOUNDED);
        }
        // 非投影路径只剩一个值;投影结果是数组,旧快照查值在数组字段投影处返回空,故不保留。
        Object observedValue = pluckDepth == 0 && values.size() == 1 ? values.get(0) : null;
        return new DataPathResolution.Resolved(path.source(), type, evidence, observedValue);
    }

    /**
     * Return a proven array cardinality for a literal data path, or empty when traversal is
     * uncertain.
     */
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
                    return Step.failure(
                            new DataPathResolution.InvalidTraversal(
                                    path,
                                    "array",
                                    "Use @Count("
                                            + path.substring(0, path.length() - ".length".length())
                                            + ") instead."));
                }
                if (list.isEmpty()) {
                    return Step.failure(
                            new DataPathResolution.Unprovable(
                                    path,
                                    "The empty array does not prove member type; use existing data or avoid the member "
                                            + "traversal."));
                }
                plucked = true;
                for (Object item : list) {
                    total++;
                    if (!(item instanceof Map<?, ?> map)) {
                        return Step.failure(
                                new DataPathResolution.InvalidTraversal(
                                        path,
                                        coarseTypeName(item),
                                        "Member access requires an object array."));
                    }
                    if (!map.containsKey(key.name())) {
                        missing++;
                        continue;
                    }
                    Object member = map.get(key.name());
                    if (member == null) {
                        // 显式 null 不是缺失:保留为投影结果的可能值,由类型层与缺失分支分别表达。
                        nulls++;
                        next.add(null);
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
                    next.add(null);
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
                    next.add(null);
                } else {
                    present++;
                    next.add(member);
                }
                continue;
            }
            return Step.failure(
                    new DataPathResolution.InvalidTraversal(
                            path,
                            coarseTypeName(value),
                            "Member access requires an object or array."));
        }

        if (next.isEmpty()) {
            // null 值已作为投影结果保留(next 非空),走到这里说明该步完全没有可取值。
            return Step.failure(
                    new DataPathResolution.Missing(
                            path, "Use a path present in the Render Data Model."));
        }
        return new Step(next, new Evidence(present, missing, nulls, total, plucked), plucked, null);
    }

    /** 把投影后的观察值提升为类型上界;全部为字符串时保留有界字面值域。 */
    private static Type inferValues(List<Object> values) {
        if (values.stream().allMatch(String.class::isInstance)) {
            Set<String> domain = new LinkedHashSet<>();
            values.forEach(value -> domain.add((String) value));
            if (domain.size() <= MAX_STRING_DOMAIN_SIZE) {
                return Types.stringLiterals(domain);
            }
            return Types.scalar(Type.ScalarKind.STRING);
        }
        return Types.union(values.stream().map(DataPathResolver::inferValue).toList());
    }

    private static Type inferValue(Object value) {
        if (value == null) {
            return Types.scalar(Type.ScalarKind.NULL);
        }
        if (value instanceof String || value instanceof Character) {
            return Types.stringLiterals(Set.of(String.valueOf(value)));
        }
        if (value instanceof Number) {
            return Types.scalar(Type.ScalarKind.NUMBER);
        }
        if (value instanceof Boolean) {
            return Types.scalar(Type.ScalarKind.BOOLEAN);
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return Types.emptySequence();
            }
            return Types.sequence(inferValues(new ArrayList<>(list)), 0, Type.Sequence.UNBOUNDED);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Type.Field> fields = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                fields.put(
                        String.valueOf(entry.getKey()),
                        Type.Field.required(inferValue(entry.getValue())));
            }
            return Types.record(fields, Type.Extra.closed());
        }
        return Types.scalar(Type.ScalarKind.STRING);
    }

    /** 遍历失败消息使用的粗粒度类型名(不带字面值,避免把快照内容写进诊断)。 */
    private static String coarseTypeName(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String || value instanceof Character) {
            return "string";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof List<?>) {
            return "array";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        return "unknown";
    }

    private record Step(
            List<Object> values, Evidence evidence, boolean plucked, DataPathResolution failure) {
        static Step failure(DataPathResolution failure) {
            return new Step(List.of(), new Evidence(0, 0, 0, 0, false), false, failure);
        }
    }
}
