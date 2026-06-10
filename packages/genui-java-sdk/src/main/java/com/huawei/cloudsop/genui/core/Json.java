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

final class Json {
  private Json() {}

  static Object parse(String json) {
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

  static String stringify(Object value) {
    return JSON.toJSONString(
        normalizeForSerialization(value), JSONWriter.Feature.WriteNulls);
  }

  /** Mirrors {@code JSON.stringify(value, null, 2)} for byte-for-byte prompt parity. */
  static String stringifyPretty(Object value) {
    StringBuilder out = new StringBuilder();
    writePretty(out, normalizeForSerialization(value), 0);
    return out.toString();
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> asObject(Object value, String label) {
    if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
    throw new GenerationSdkException(label + " must be a JSON object");
  }

  @SuppressWarnings("unchecked")
  static List<Object> asList(Object value, String label) {
    if (value instanceof List<?> list) return (List<Object>) list;
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
      for (Object item : list) normalized.add(normalizeParsed(item));
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
            String.valueOf(entry.getKey()), normalizeForSerialization(entry.getValue()));
      }
      return normalized;
    }
    if (value instanceof Iterable<?> iterable) {
      ArrayList<Object> normalized = new ArrayList<>();
      for (Object item : iterable) normalized.add(normalizeForSerialization(item));
      return normalized;
    }
    if (value instanceof Float || value instanceof Double) {
      double number = ((Number) value).doubleValue();
      if (!Double.isFinite(number)) return null;
      if (number == Math.rint(number) && Math.abs(number) < 1e15) {
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
      if (!first) out.append(",\n");
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
      if (index > 0) out.append(",\n");
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
