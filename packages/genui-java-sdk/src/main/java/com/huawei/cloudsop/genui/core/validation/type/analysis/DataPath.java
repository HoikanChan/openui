/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * 具体 {@code data...} 路径:只含字面量键与下标,由类型阶段的快照观察器解析。
 *
 * @param segments 按出现顺序排列的路径段
 * @param source 原始路径文本,用于诊断与覆盖身份
 * @since 2026
 */
public record DataPath(List<Segment> segments, String source) {

    /** 紧凑构造方法:把路径段列表归一化为不可变列表。 */
    public DataPath {
        segments = List.copyOf(segments);
    }

    /**
     * 解析字面量数据路径。
     *
     * <p>支持 {@code data}、点号成员访问与 {@code [index]} / {@code ["key"]} 形式;动态下标不在 此层支持。
     *
     * @param source 以 {@code data} 开头的路径文本
     * @return 解析出的不可变路径
     * @throws IllegalArgumentException 路径不以 data 开头、成员为空、下标未闭合或下标不是字面量时
     */
    public static DataPath parse(String source) {
        if (source == null
                || !(source.equals("data")
                        || source.startsWith("data.")
                        || source.startsWith("data["))) {
            throw new IllegalArgumentException("Data path must start with data: " + source);
        }
        List<Segment> segments = new ArrayList<>();
        int index = "data".length();
        while (index < source.length()) {
            char marker = source.charAt(index);
            if (marker == '.') {
                int start = ++index;
                while (index < source.length() && isIdentifierPart(source.charAt(index))) {
                    index++;
                }
                if (start == index) {
                    throw new IllegalArgumentException("Empty member in data path: " + source);
                }
                segments.add(new Key(source.substring(start, index)));
                continue;
            }
            if (marker == '[') {
                int close = source.indexOf(']', index + 1);
                if (close < 0) {
                    throw new IllegalArgumentException("Unclosed index in data path: " + source);
                }
                String token = source.substring(index + 1, close).trim();
                if ((token.startsWith("\"") && token.endsWith("\""))
                        || (token.startsWith("'") && token.endsWith("'"))) {
                    segments.add(new Key(token.substring(1, token.length() - 1)));
                } else {
                    try {
                        segments.add(new Index(Integer.parseInt(token)));
                    } catch (NumberFormatException error) {
                        throw new IllegalArgumentException(
                                "Only literal data indexes are supported: " + source, error);
                    }
                }
                index = close + 1;
                continue;
            }
            throw new IllegalArgumentException(
                    "Unexpected data path token at " + index + ": " + source);
        }
        return new DataPath(segments, source);
    }

    private static boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$' || value == '-';
    }

    /**
     * 路径中的一段:字面量键或数组下标。
     *
     * @since 2026
     */
    public sealed interface Segment permits Key, Index {}

    /**
     * 对象成员键。
     *
     * @param name 成员名
     * @since 2026
     */
    public record Key(String name) implements Segment {}

    /**
     * 数组下标。
     *
     * @param value 非负下标值
     * @since 2026
     */
    public record Index(int value) implements Segment {}
}
