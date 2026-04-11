package dev.openui.langcore.parser;

import dev.openui.langcore.util.JsonStringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses a JSON Schema string (zero external dependencies) and provides
 * {@link ComponentSchema} lookup by component type name.
 *
 * Only the subset of JSON Schema relevant to OpenUI components is parsed:
 * {@code $defs[Name].properties} (key order = positional arg order),
 * {@code required} array, and per-property {@code default} values.
 *
 * Ref: Design §7
 */
public final class SchemaRegistry {

    private final Map<String, ComponentSchema> components;

    private SchemaRegistry(Map<String, ComponentSchema> components) {
        this.components = components;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Parse a JSON Schema string and build a {@link SchemaRegistry}.
     * Returns {@link #empty()} when {@code jsonSchema} is null/blank or
     * contains no {@code $defs} entry.
     */
    public static SchemaRegistry fromJson(String jsonSchema) {
        if (jsonSchema == null || jsonSchema.isBlank()) {
            return empty();
        }

        Object parsed;
        try {
            parsed = new JsonParser(jsonSchema).parseValue();
        } catch (Exception e) {
            return empty();
        }

        if (!(parsed instanceof Map<?, ?> root)) {
            return empty();
        }

        Object defsRaw = root.get("$defs");
        if (!(defsRaw instanceof Map<?, ?> defs)) {
            return empty();
        }

        Map<String, ComponentSchema> result = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : defs.entrySet()) {
            String componentName = (String) entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?> compDef)) {
                continue;
            }

            Object propsRaw = compDef.get("properties");
            if (!(propsRaw instanceof Map<?, ?> properties)) {
                continue;
            }

            Object requiredRaw = compDef.get("required");
            Set<String> requiredSet;
            if (requiredRaw instanceof List<?> requiredList) {
                requiredSet = new HashSet<>();
                for (Object r : requiredList) {
                    if (r instanceof String s) {
                        requiredSet.add(s);
                    }
                }
            } else {
                requiredSet = Collections.emptySet();
            }

            List<PropDef> props = new ArrayList<>();
            for (Map.Entry<?, ?> propEntry : properties.entrySet()) {
                String propName = (String) propEntry.getKey();
                Object defaultVal = null;
                if (propEntry.getValue() instanceof Map<?, ?> propSchema) {
                    defaultVal = propSchema.get("default");
                }
                boolean isRequired = requiredSet.contains(propName);
                props.add(new PropDef(propName, isRequired, defaultVal));
            }

            result.put(componentName, new ComponentSchema(List.copyOf(props)));
        }

        return new SchemaRegistry(Collections.unmodifiableMap(result));
    }

    /** Look up a component schema by its type name. */
    public Optional<ComponentSchema> lookup(String typeName) {
        return Optional.ofNullable(components.get(typeName));
    }

    /** Returns a registry with no component definitions. */
    public static SchemaRegistry empty() {
        return new SchemaRegistry(Map.of());
    }

    // -------------------------------------------------------------------------
    // Mini recursive-descent JSON parser (no external dependencies)
    // -------------------------------------------------------------------------

    /**
     * Hand-written JSON parser that produces a Java object tree:
     * <ul>
     *   <li>JSON object  → {@link LinkedHashMap}{@code <String, Object>} (preserves key order)</li>
     *   <li>JSON array   → {@link List}{@code <Object>}</li>
     *   <li>JSON string  → {@link String} (unescaped via {@link JsonStringUtil})</li>
     *   <li>JSON number  → {@link Double}</li>
     *   <li>JSON boolean → {@link Boolean}</li>
     *   <li>JSON null    → {@code null}</li>
     * </ul>
     */
    private static final class JsonParser {
        private final String src;
        private int pos;

        JsonParser(String src) {
            this.src = src;
            this.pos = 0;
        }

        // --- Top-level dispatcher ---

        Object parseValue() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new IllegalStateException("Unexpected end of input");
            }
            char c = peek();
            return switch (c) {
                case '"' -> parseString();
                case '{' -> parseObject();
                case '[' -> parseArray();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default  -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield parseNumber();
                    }
                    throw new IllegalStateException("Unexpected character: " + c + " at pos " + pos);
                }
            };
        }

        // --- String ---

        String parseString() {
            skipWhitespace();
            expect('"');
            int start = pos - 1; // include the opening quote
            boolean escaped = false;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    pos++; // consume closing quote
                    // raw includes surrounding quotes
                    String raw = src.substring(start, pos);
                    return JsonStringUtil.unescape(raw);
                }
                pos++;
            }
            throw new IllegalStateException("Unterminated string starting at " + start);
        }

        // --- Number ---

        Double parseNumber() {
            skipWhitespace();
            int start = pos;
            if (pos < src.length() && src.charAt(pos) == '-') {
                pos++;
            }
            consumeDigits();
            if (pos < src.length() && src.charAt(pos) == '.') {
                pos++;
                consumeDigits();
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                consumeDigits();
            }
            return Double.parseDouble(src.substring(start, pos));
        }

        private void consumeDigits() {
            while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') {
                pos++;
            }
        }

        // --- Boolean / null literals ---

        Object parseLiteral(String literal, Object value) {
            if (src.startsWith(literal, pos)) {
                pos += literal.length();
                return value;
            }
            throw new IllegalStateException("Expected '" + literal + "' at pos " + pos);
        }

        // --- Array ---

        List<Object> parseArray() {
            skipWhitespace();
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (pos < src.length() && peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (pos >= src.length()) {
                    throw new IllegalStateException("Unterminated array");
                }
                char sep = src.charAt(pos);
                if (sep == ',') {
                    pos++;
                } else if (sep == ']') {
                    pos++;
                    break;
                } else {
                    throw new IllegalStateException("Expected ',' or ']' at pos " + pos);
                }
            }
            return list;
        }

        // --- Object ---

        LinkedHashMap<String, Object> parseObject() {
            skipWhitespace();
            expect('{');
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (pos < src.length() && peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (pos >= src.length()) {
                    throw new IllegalStateException("Unterminated object");
                }
                char sep = src.charAt(pos);
                if (sep == ',') {
                    pos++;
                } else if (sep == '}') {
                    pos++;
                    break;
                } else {
                    throw new IllegalStateException("Expected ',' or '}' at pos " + pos);
                }
            }
            return map;
        }

        // --- Helpers ---

        char peek() {
            skipWhitespace();
            return src.charAt(pos);
        }

        char consume() {
            return src.charAt(pos++);
        }

        void expect(char c) {
            skipWhitespace();
            if (pos >= src.length() || src.charAt(pos) != c) {
                char got = pos < src.length() ? src.charAt(pos) : 0;
                throw new IllegalStateException(
                        "Expected '" + c + "' but got '" + got + "' at pos " + pos);
            }
            pos++;
        }

        void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    pos++;
                } else {
                    break;
                }
            }
        }
    }
}
