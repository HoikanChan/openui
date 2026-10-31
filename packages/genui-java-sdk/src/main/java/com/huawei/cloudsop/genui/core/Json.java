/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON helpers shared by the SDK: parsing into plain map/list/scalar trees, compact serialization,
 * and a pretty-printer that mirrors {@code JSON.stringify(value, null, 2)}.
 *
 * <p>Parsing normalizes numbers so integers always become {@link Long} and decimals {@link Double},
 * keeping prompt rendering deterministic across callers. This is a non-instantiable utility class.
 *
 * @since 2026
 */
public final class Json {
    /** Doubles beyond this magnitude cannot be represented exactly as a long. */
    private static final double MAX_EXACT_LONG_AS_DOUBLE = 1e15;

    private Json() {}

    /**
     * Parses a JSON document into plain Java values: objects become {@link LinkedHashMap} with
     * string keys, arrays {@link ArrayList}, integers {@link Long} and decimals {@link Double};
     * strings, booleans and {@code null} pass through unchanged.
     *
     * <p>Single-quoted strings are rejected. A syntactically invalid document, or an integer too
     * large for a {@code long}, is reported as {@link GenerationSdkException} rather than a raw
     * parser error.
     *
     * @param json the JSON text to parse
     * @return the normalized value tree, or {@code null} when the text is the JSON literal {@code
     *     null}
     * @throws GenerationSdkException if the text is not valid JSON or an integer overflows a long
     */
    public static Object parse(String json) {
        try {
            Object parsed =
                    JSON.parse(
                            json,
                            JSONReader.Feature.UseDoubleForDecimals,
                            JSONReader.Feature.DisableSingleQuote);
            return normalizeParsed(parsed);
        } catch (JSONException | ArithmeticException error) {
            throw new GenerationSdkException("Invalid JSON: " + error.getMessage(), error);
        }
    }

    /**
     * Serializes a value to compact JSON, writing explicit {@code null}s.
     *
     * <p>Values are normalized first: integral doubles narrow to {@code long} when exactly
     * representable, non-finite numbers become {@code null}, and any other object falls back to
     * {@link String#valueOf(Object)}.
     *
     * @param value the value to serialize; may be {@code null}
     * @return the compact JSON text
     */
    public static String stringify(Object value) {
        return JSON.toJSONString(normalizeForSerialization(value), JSONWriter.Feature.WriteNulls);
    }

    /**
     * Serializes a value to two-space-indented JSON matching {@code JSON.stringify(value, null, 2)}
     * byte for byte, for prompt parity.
     *
     * <p>Key order follows the map's iteration order and non-finite numbers become {@code null}.
     *
     * @param value the value to serialize; may be {@code null}
     * @return the pretty-printed JSON text
     */
    public static String stringifyPretty(Object value) {
        StringBuilder out = new StringBuilder();
        writePretty(out, normalizeForSerialization(value), 0);
        return out.toString();
    }

    /**
     * Casts a parsed value to a JSON object.
     *
     * @param value the value to check
     * @param label a caller-supplied name used in the error message
     * @return the same map instance, unmodified
     * @throws GenerationSdkException if {@code value} is not a {@link Map}
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value, String label) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new GenerationSdkException(label + " must be a JSON object");
    }

    /**
     * Casts a parsed value to a JSON array.
     *
     * @param value the value to check
     * @param label a caller-supplied name used in the error message
     * @return the same list instance, unmodified
     * @throws GenerationSdkException if {@code value} is not a {@link List}
     */
    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object value, String label) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new GenerationSdkException(label + " must be a JSON array");
    }

    private static Object normalizeParsed(Object value) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), normalizeParsed(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof List<?> list) {
            ArrayList<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(normalizeParsed(item));
            }
            return normalized;
        }
        if (value instanceof BigInteger integer) {
            return integer.longValueExact();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof Float || value instanceof Double) {
            return ((Number) value).doubleValue();
        }
        return value;
    }

    private static Object normalizeForSerialization(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(
                        String.valueOf(entry.getKey()),
                        normalizeForSerialization(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof Iterable<?> iterable) {
            ArrayList<Object> normalized = new ArrayList<>();
            for (Object item : iterable) {
                normalized.add(normalizeForSerialization(item));
            }
            return normalized;
        }
        if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number)) {
                return null;
            }
            if (number == Math.rint(number) && Math.abs(number) < MAX_EXACT_LONG_AS_DOUBLE) {
                return (long) number;
            }
            return number;
        }
        if (value instanceof Number) {
            return value;
        }
        return String.valueOf(value);
    }

    private static void writePretty(StringBuilder out, Object value, int depth) {
        if (value instanceof Map<?, ?> map) {
            writePrettyObject(out, map, depth);
        } else if (value instanceof List<?> list) {
            writePrettyArray(out, list, depth);
        } else {
            out.append(JSON.toJSONString(value));
        }
    }

    private static void writePrettyObject(StringBuilder out, Map<?, ?> map, int depth) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }

        out.append("{\n");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(",\n");
            }
            first = false;
            indent(out, depth + 1);
            out.append(JSON.toJSONString(String.valueOf(entry.getKey()))).append(": ");
            writePretty(out, entry.getValue(), depth + 1);
        }
        out.append('\n');
        indent(out, depth);
        out.append('}');
    }

    private static void writePrettyArray(StringBuilder out, List<?> list, int depth) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }

        out.append("[\n");
        for (int index = 0; index < list.size(); index++) {
            if (index > 0) {
                out.append(",\n");
            }
            indent(out, depth + 1);
            writePretty(out, list.get(index), depth + 1);
        }
        out.append('\n');
        indent(out, depth);
        out.append(']');
    }

    private static void indent(StringBuilder out, int depth) {
        out.append("  ".repeat(depth));
    }
}
