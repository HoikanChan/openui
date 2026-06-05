package com.huawei.clodsop.genui.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
  private Json() {}

  static Object parse(String json) {
    return new Parser(json).parse();
  }

  static String stringify(Object value) {
    StringBuilder out = new StringBuilder();
    writeValue(out, value);
    return out.toString();
  }

  /** Mirrors {@code JSON.stringify(value, null, 2)} (2-space indent) for byte-for-byte prompt parity. */
  static String stringifyPretty(Object value) {
    StringBuilder out = new StringBuilder();
    writePretty(out, value, 0);
    return out.toString();
  }

  private static void writePretty(StringBuilder out, Object value, int depth) {
    if (value instanceof Map<?, ?> map) {
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
        writeString(out, String.valueOf(entry.getKey()));
        out.append(": ");
        writePretty(out, entry.getValue(), depth + 1);
      }
      out.append('\n');
      indent(out, depth);
      out.append('}');
    } else if (value instanceof Iterable<?> iterable) {
      java.util.Iterator<?> it = iterable.iterator();
      if (!it.hasNext()) {
        out.append("[]");
        return;
      }
      out.append("[\n");
      boolean first = true;
      while (it.hasNext()) {
        if (!first) out.append(",\n");
        first = false;
        indent(out, depth + 1);
        writePretty(out, it.next(), depth + 1);
      }
      out.append('\n');
      indent(out, depth);
      out.append(']');
    } else {
      writeValue(out, value);
    }
  }

  private static void indent(StringBuilder out, int depth) {
    for (int i = 0; i < depth * 2; i++) out.append(' ');
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

  private static void writeValue(StringBuilder out, Object value) {
    if (value == null) {
      out.append("null");
    } else if (value instanceof String text) {
      writeString(out, text);
    } else if (value instanceof Number number) {
      writeNumber(out, number);
    } else if (value instanceof Boolean) {
      out.append(value);
    } else if (value instanceof Map<?, ?> map) {
      out.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!first) out.append(',');
        first = false;
        writeString(out, String.valueOf(entry.getKey()));
        out.append(':');
        writeValue(out, entry.getValue());
      }
      out.append('}');
    } else if (value instanceof Iterable<?> iterable) {
      out.append('[');
      boolean first = true;
      for (Object item : iterable) {
        if (!first) out.append(',');
        first = false;
        writeValue(out, item);
      }
      out.append(']');
    } else {
      writeString(out, String.valueOf(value));
    }
  }

  /** Format numbers like JavaScript's {@code JSON.stringify}: integral doubles print without ".0". */
  private static void writeNumber(StringBuilder out, Number number) {
    if (number instanceof Double || number instanceof Float) {
      double d = number.doubleValue();
      if (Double.isFinite(d) && d == Math.rint(d) && Math.abs(d) < 1e15) {
        out.append(Long.toString((long) d));
        return;
      }
      out.append(Double.toString(d));
      return;
    }
    out.append(number.toString());
  }

  private static void writeString(StringBuilder out, String text) {
    out.append('"');
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      switch (ch) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (ch < 0x20) {
            out.append(String.format("\\u%04x", (int) ch));
          } else {
            out.append(ch);
          }
        }
      }
    }
    out.append('"');
  }

  private static final class Parser {
    private final String input;
    private int index;

    Parser(String input) {
      this.input = input;
    }

    Object parse() {
      Object value = parseValue();
      skipWhitespace();
      if (index != input.length()) {
        throw error("Unexpected trailing JSON content");
      }
      return value;
    }

    private Object parseValue() {
      skipWhitespace();
      if (index >= input.length()) throw error("Unexpected end of JSON");
      char ch = input.charAt(index);
      return switch (ch) {
        case '{' -> parseObject();
        case '[' -> parseArray();
        case '"' -> parseString();
        case 't' -> parseLiteral("true", Boolean.TRUE);
        case 'f' -> parseLiteral("false", Boolean.FALSE);
        case 'n' -> parseLiteral("null", null);
        default -> {
          if (ch == '-' || Character.isDigit(ch)) yield parseNumber();
          throw error("Unexpected JSON token '" + ch + "'");
        }
      };
    }

    private Map<String, Object> parseObject() {
      expect('{');
      LinkedHashMap<String, Object> object = new LinkedHashMap<>();
      skipWhitespace();
      if (peek('}')) {
        index++;
        return object;
      }
      while (true) {
        skipWhitespace();
        String key = parseString();
        skipWhitespace();
        expect(':');
        object.put(key, parseValue());
        skipWhitespace();
        if (peek('}')) {
          index++;
          return object;
        }
        expect(',');
      }
    }

    private List<Object> parseArray() {
      expect('[');
      ArrayList<Object> array = new ArrayList<>();
      skipWhitespace();
      if (peek(']')) {
        index++;
        return array;
      }
      while (true) {
        array.add(parseValue());
        skipWhitespace();
        if (peek(']')) {
          index++;
          return array;
        }
        expect(',');
      }
    }

    private String parseString() {
      expect('"');
      StringBuilder out = new StringBuilder();
      while (index < input.length()) {
        char ch = input.charAt(index++);
        if (ch == '"') return out.toString();
        if (ch != '\\') {
          out.append(ch);
          continue;
        }
        if (index >= input.length()) throw error("Unterminated JSON escape");
        char escaped = input.charAt(index++);
        switch (escaped) {
          case '"' -> out.append('"');
          case '\\' -> out.append('\\');
          case '/' -> out.append('/');
          case 'b' -> out.append('\b');
          case 'f' -> out.append('\f');
          case 'n' -> out.append('\n');
          case 'r' -> out.append('\r');
          case 't' -> out.append('\t');
          case 'u' -> out.append(parseUnicodeEscape());
          default -> throw error("Unsupported JSON escape \\" + escaped + "'");
        }
      }
      throw error("Unterminated JSON string");
    }

    private char parseUnicodeEscape() {
      if (index + 4 > input.length()) throw error("Incomplete unicode escape");
      String hex = input.substring(index, index + 4);
      index += 4;
      try {
        return (char) Integer.parseInt(hex, 16);
      } catch (NumberFormatException error) {
        throw error("Invalid unicode escape " + hex);
      }
    }

    private Object parseLiteral(String literal, Object value) {
      if (!input.startsWith(literal, index)) {
        throw error("Expected " + literal);
      }
      index += literal.length();
      return value;
    }

    private Number parseNumber() {
      int start = index;
      if (peek('-')) index++;
      readDigits();
      if (peek('.')) {
        index++;
        readDigits();
      }
      if (peek('e') || peek('E')) {
        index++;
        if (peek('+') || peek('-')) index++;
        readDigits();
      }

      String number = input.substring(start, index);
      try {
        if (number.contains(".") || number.contains("e") || number.contains("E")) {
          return Double.parseDouble(number);
        }
        return Long.parseLong(number);
      } catch (NumberFormatException error) {
        throw error("Invalid number " + number);
      }
    }

    private void readDigits() {
      int start = index;
      while (index < input.length() && Character.isDigit(input.charAt(index))) index++;
      if (start == index) throw error("Expected digit");
    }

    private void skipWhitespace() {
      while (index < input.length() && Character.isWhitespace(input.charAt(index))) index++;
    }

    private boolean peek(char expected) {
      return index < input.length() && input.charAt(index) == expected;
    }

    private void expect(char expected) {
      if (!peek(expected)) throw error("Expected '" + expected + "'");
      index++;
    }

    private GenerationSdkException error(String message) {
      return new GenerationSdkException(message + " at JSON offset " + index);
    }
  }
}
