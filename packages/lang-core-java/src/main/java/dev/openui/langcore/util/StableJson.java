package dev.openui.langcore.util;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stable JSON serializer used as a cache-key generator by {@code DefaultQueryManager}.
 *
 * <p>Mirrors the TypeScript {@code stableStringify} function in {@code queryManager.ts}:
 * <ul>
 *   <li>Object keys are sorted recursively (stable across insertion order).</li>
 *   <li>{@code null} → {@code "null"}</li>
 *   <li>{@code undefined} (represented as the sentinel string {@code "__undefined__"})
 *       is preserved as-is when encountered as a value.</li>
 *   <li>{@code NaN}  → {@code "\"__NaN__\""}</li>
 *   <li>{@code Infinity}  → {@code "\"__Inf__\""}</li>
 *   <li>{@code -Infinity} → {@code "\"__-Inf__\""}</li>
 * </ul>
 *
 * No external dependencies — hand-written.
 *
 * Ref: Design §15
 */
public final class StableJson {

    private StableJson() {}

    /**
     * Serialize {@code value} to a stable JSON string.
     *
     * <p>Supported Java types:
     * <ul>
     *   <li>{@code null} → {@code "null"}</li>
     *   <li>{@link Boolean} → {@code "true"} / {@code "false"}</li>
     *   <li>{@link Number} → numeric literal (with special handling for NaN/Infinity)</li>
     *   <li>{@link String} → JSON-escaped string</li>
     *   <li>{@link List} → JSON array (elements processed recursively)</li>
     *   <li>{@link Map} → JSON object with keys sorted alphabetically</li>
     *   <li>Anything else → {@code toString()} wrapped in a JSON string</li>
     * </ul>
     */
    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        appendValue(value, sb);
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static void appendValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
            return;
        }

        if (value instanceof Boolean b) {
            sb.append(b ? "true" : "false");
            return;
        }

        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d)) {
                sb.append("\"__NaN__\"");
            } else if (d == Double.POSITIVE_INFINITY) {
                sb.append("\"__Inf__\"");
            } else if (d == Double.NEGATIVE_INFINITY) {
                sb.append("\"__-Inf__\"");
            } else {
                // Emit as integer if the value is a whole number, to match JSON.stringify behaviour.
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                    sb.append((long) d);
                } else {
                    sb.append(n);
                }
            }
            return;
        }

        if (value instanceof String s) {
            appendJsonString(s, sb);
            return;
        }

        if (value instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                appendValue(list.get(i), sb);
            }
            sb.append(']');
            return;
        }

        if (value instanceof Map<?, ?> map) {
            // Sort keys for stability
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sorted.put(String.valueOf(e.getKey()), e.getValue());
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                appendJsonString(e.getKey(), sb);
                sb.append(':');
                appendValue(e.getValue(), sb);
            }
            sb.append('}');
            return;
        }

        // Fallback: toString wrapped as a JSON string
        appendJsonString(value.toString(), sb);
    }

    private static void appendJsonString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
