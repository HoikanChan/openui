package dev.openui.langcore.util;

/**
 * Utilities for unescaping JSON-style double-quoted strings.
 * Handles \", \\, \n, \t, \r, and Unicode escapes (backslash-u followed by 4 hex digits).
 * On malformed input, strips surrounding quotes and returns the raw text.
 * Ref: Req 1 AC9
 */
public final class JsonStringUtil {

    private JsonStringUtil() {}

    /**
     * Unescape a double-quoted JSON string.
     *
     * @param raw the raw token text including surrounding double-quote characters,
     *            e.g. {@code "hello\nworld"}. If unterminated (streaming), the caller
     *            should append a closing quote before passing here.
     * @return the unescaped string content (without surrounding quotes), or the
     *         raw text stripped of leading/trailing quotes if parsing fails.
     */
    public static String unescape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        // Strip leading quote
        int start = raw.charAt(0) == '"' ? 1 : 0;
        // Strip trailing quote if present
        int end = raw.length();
        if (end > start && raw.charAt(end - 1) == '"') {
            end--;
        }

        StringBuilder sb = new StringBuilder(end - start);
        int i = start;
        while (i < end) {
            char c = raw.charAt(i);
            if (c != '\\') {
                sb.append(c);
                i++;
                continue;
            }
            // Escape sequence
            if (i + 1 >= end) {
                // Trailing backslash — treat as literal backslash
                sb.append('\\');
                i++;
                continue;
            }
            char next = raw.charAt(i + 1);
            switch (next) {
                case '"'  -> { sb.append('"');  i += 2; }
                case '\\' -> { sb.append('\\'); i += 2; }
                case '/'  -> { sb.append('/');  i += 2; }
                case 'n'  -> { sb.append('\n'); i += 2; }
                case 'r'  -> { sb.append('\r'); i += 2; }
                case 't'  -> { sb.append('\t'); i += 2; }
                case 'b'  -> { sb.append('\b'); i += 2; }
                case 'f'  -> { sb.append('\f'); i += 2; }
                case 'u'  -> {
                    if (i + 5 < end + 1) {
                        String hex = raw.substring(i + 2, i + 6);
                        try {
                            int cp = Integer.parseInt(hex, 16);
                            sb.append((char) cp);
                            i += 6;
                        } catch (NumberFormatException e) {
                            // Malformed unicode escape — emit raw and advance past backslash only
                            sb.append('\\');
                            i++;
                        }
                    } else {
                        sb.append('\\');
                        i++;
                    }
                }
                default -> {
                    // Unknown escape — emit backslash and the next char literally
                    sb.append('\\');
                    sb.append(next);
                    i += 2;
                }
            }
        }
        return sb.toString();
    }
}
