package dev.openui.langcore.mcp;

import dev.openui.langcore.query.ToolProvider;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Adapts an {@link McpClientLike} to the {@link ToolProvider} interface.
 *
 * Ref: Design §16
 */
public final class McpAdapter implements ToolProvider {

    private final McpClientLike client;

    public McpAdapter(McpClientLike client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<Object> callTool(String toolName, Map<String, Object> arguments) {
        return client.callTool(new McpClientLike.McpCallParams(toolName, arguments))
                     .thenApply(McpAdapter::extractToolResult);
    }

    /**
     * Extract the result value from an MCP response, mirroring the TypeScript {@code mcp.ts}:
     * <ol>
     *   <li>{@code isError} → throw {@link McpToolError}</li>
     *   <li>{@code structuredContent != null} → return it directly</li>
     *   <li>Text content items → try to parse the first text as JSON, fall back to raw string</li>
     * </ol>
     */
    public static Object extractToolResult(McpClientLike.McpResult result) {
        if (result.isError()) {
            String errorText = collectText(result);
            throw new McpToolError(errorText.isEmpty() ? "MCP tool error" : errorText);
        }

        if (result.structuredContent() != null) {
            return result.structuredContent();
        }

        String text = collectText(result);
        return tryParseJson(text);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String collectText(McpClientLike.McpResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (McpClientLike.McpContentItem item : result.content()) {
            if ("text".equals(item.type()) && item.text() != null) {
                sb.append(item.text());
            }
        }
        return sb.toString();
    }

    /**
     * Attempt to parse {@code text} as JSON (object or array).
     * Returns the parsed structure on success, or the original string on failure.
     */
    private static Object tryParseJson(String text) {
        if (text == null) return null;
        String trimmed = text.strip();
        if ((trimmed.startsWith("{") || trimmed.startsWith("[")) ) {
            try {
                return new JsonParser(trimmed).parseValue();
            } catch (Exception ignored) {
                // fall through to string
            }
        }
        return text;
    }

    // -------------------------------------------------------------------------
    // Minimal JSON parser (subset — objects, arrays, strings, numbers, booleans, null)
    // -------------------------------------------------------------------------

    private static final class JsonParser {
        private final String src;
        private int pos;

        JsonParser(String src) { this.src = src; }

        Object parseValue() {
            skipWs();
            if (pos >= src.length()) throw new IllegalStateException("empty");
            char c = src.charAt(pos);
            return switch (c) {
                case '"' -> parseString();
                case '{' -> parseObject();
                case '[' -> parseArray();
                case 't' -> parseLit("true",  Boolean.TRUE);
                case 'f' -> parseLit("false", Boolean.FALSE);
                case 'n' -> parseLit("null",  null);
                default  -> {
                    if (c == '-' || (c >= '0' && c <= '9')) yield parseNumber();
                    throw new IllegalStateException("unexpected: " + c);
                }
            };
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '\\') {
                    char e = src.charAt(pos++);
                    sb.append(switch (e) {
                        case '"'  -> '"';
                        case '\\' -> '\\';
                        case '/'  -> '/';
                        case 'n'  -> '\n';
                        case 'r'  -> '\r';
                        case 't'  -> '\t';
                        default   -> e;
                    });
                } else if (c == '"') {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalStateException("unterminated string");
        }

        private Double parseNumber() {
            int start = pos;
            if (pos < src.length() && src.charAt(pos) == '-') pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            if (pos < src.length() && src.charAt(pos) == '.') {
                pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            return Double.parseDouble(src.substring(start, pos));
        }

        private Object parseLit(String lit, Object val) {
            if (!src.startsWith(lit, pos)) throw new IllegalStateException("expected " + lit);
            pos += lit.length();
            return val;
        }

        private java.util.List<Object> parseArray() {
            expect('[');
            java.util.List<Object> list = new java.util.ArrayList<>();
            skipWs();
            if (pos < src.length() && src.charAt(pos) == ']') { pos++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                char sep = src.charAt(pos);
                if (sep == ',') pos++;
                else if (sep == ']') { pos++; break; }
                else throw new IllegalStateException("expected , or ]");
            }
            return list;
        }

        private java.util.Map<String, Object> parseObject() {
            expect('{');
            java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
            skipWs();
            if (pos < src.length() && src.charAt(pos) == '}') { pos++; return map; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs(); expect(':');
                map.put(key, parseValue());
                skipWs();
                char sep = src.charAt(pos);
                if (sep == ',') pos++;
                else if (sep == '}') { pos++; break; }
                else throw new IllegalStateException("expected , or }");
            }
            return map;
        }

        private void skipWs() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') pos++; else break;
            }
        }

        private void expect(char c) {
            skipWs();
            if (pos >= src.length() || src.charAt(pos) != c)
                throw new IllegalStateException("expected '" + c + "' at " + pos);
            pos++;
        }
    }
}
