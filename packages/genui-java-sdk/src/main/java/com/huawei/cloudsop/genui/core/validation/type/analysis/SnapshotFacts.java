/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Render Data Model 快照的类型化(#77 第 13.1 节:按需访问 + 截断进入上界)。
 *
 * <p>数据值按形状类型化:标量、字符串字面值(域有界)、数组(元素联合 + 精确数量区间)、对象(全 REQUIRED 字段、 关闭尾部)。数组扫描超过样本预算时,未访问部分进入上界(元素并入
 * Unknown 分支)并记录 {@link Fact.Loss#PRECISION_BUDGET}——不能把已扫描样本当完整数据。观察到的值与字段存在性以 {@link
 * Fact.Evidence} 形式保留在事实上。
 *
 * @since 2026
 */
public final class SnapshotFacts {

    private static final int MAX_OBJECT_DEPTH = 16;

    private static final int MAX_ARRAY_SAMPLE = 256;

    private SnapshotFacts() {}

    /**
     * 把一个 JSON 值类型化为事实。{@code path} 用于观察证据关联;{@code depth} 为当前对象深度(预算截断用)。
     *
     * @param value JSON 值;{@code null} 类型化为 null 标量
     * @param path 该值在快照中的路径;{@code null} 表示不记录观察证据
     * @param depth 当前对象深度,超过内部深度上限时截断为 Unknown
     * @return 该值的类型上界与观察证据;预算截断时附带 {@link Fact.Loss#PRECISION_BUDGET}
     */
    public static Fact typeOfValue(Object value, String path, int depth) {
        return typeOfValue(value, path, depth, new AnalysisCompleteness());
    }

    /**
     * 共享会话完整性标记的快照类型化；超过内部深度或采样限制时进入 Unknown 上界。
     *
     * @param value JSON 值;{@code null} 类型化为 null 标量
     * @param path 该值在快照中的路径;{@code null} 表示不记录观察证据
     * @param depth 当前对象深度,超过内部深度上限时截断为 Unknown
     * @param completeness 本次会话的完整性标记
     * @return 该值的类型上界与观察证据;发生截断时附带 {@link Fact.Loss#PRECISION_BUDGET}
     */
    static Fact typeOfValue(
            Object value, String path, int depth, AnalysisCompleteness completeness) {
        Set<Fact.Evidence> evidence = new LinkedHashSet<>();
        Set<Fact.Loss> losses = new LinkedHashSet<>();
        if (path != null) {
            evidence.add(new Fact.Evidence.ObservedValue(path, value));
        }
        Type type = typeOf(value, path, depth, completeness, evidence, losses);
        return new Fact(type, evidence, losses, Set.of());
    }

    private static Type typeOf(
            Object value,
            String path,
            int depth,
            AnalysisCompleteness completeness,
            Set<Fact.Evidence> evidence,
            Set<Fact.Loss> losses) {
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
        if (depth > MAX_OBJECT_DEPTH) {
            // 对象深度超限:进入安全上界,不继续展开。
            completeness.markIncomplete();
            losses.add(Fact.Loss.PRECISION_BUDGET);
            return Type.Unknown.INSTANCE;
        }
        if (value instanceof List<?> list) {
            return typeOfList(list, path, depth, completeness, evidence, losses);
        }
        if (value instanceof Map<?, ?> map) {
            return typeOfMap(map, path, depth, completeness, evidence, losses);
        }
        return Type.Unknown.INSTANCE;
    }

    private static Type typeOfList(
            List<?> list,
            String path,
            int depth,
            AnalysisCompleteness completeness,
            Set<Fact.Evidence> evidence,
            Set<Fact.Loss> losses) {
        if (list.isEmpty()) {
            return Types.emptySequence();
        }
        if (path != null) {
            evidence.add(new Fact.Evidence.ElementPresence());
        }
        List<?> sample =
                list.size() <= MAX_ARRAY_SAMPLE
                        ? list
                        : new ArrayList<>(list.subList(0, MAX_ARRAY_SAMPLE));
        List<Type> elementTypes = new ArrayList<>();
        for (Object element : sample) {
            elementTypes.add(typeOf(element, null, depth + 1, completeness, evidence, losses));
        }
        if (sample.size() < list.size()) {
            // 截断:未访问元素进入上界,不能把已扫描样本当完整数据。
            completeness.markIncomplete();
            losses.add(Fact.Loss.PRECISION_BUDGET);
            elementTypes.add(Type.Unknown.INSTANCE);
        }
        Type element = Types.union(elementTypes);
        return Types.sequence(element, list.size(), list.size());
    }

    private static Type typeOfMap(
            Map<?, ?> map,
            String path,
            int depth,
            AnalysisCompleteness completeness,
            Set<Fact.Evidence> evidence,
            Set<Fact.Loss> losses) {
        Map<String, Type.Field> fields = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            fields.put(
                    key,
                    Type.Field.required(
                            typeOf(
                                    entry.getValue(),
                                    path == null ? null : path + "." + key,
                                    depth + 1,
                                    completeness,
                                    evidence,
                                    losses)));
        }
        return Types.record(fields, Type.Extra.closed());
    }
}
