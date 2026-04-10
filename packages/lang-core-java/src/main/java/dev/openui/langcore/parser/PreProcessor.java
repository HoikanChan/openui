package dev.openui.langcore.parser;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Text pre-processing pipeline applied before lexing.
 * Steps (applied in order by callers):
 * <ol>
 *   <li>{@link #stripFences(String)} — extract content from code-fence blocks</li>
 *   <li>{@link #stripComments(String)} — remove // and # line comments</li>
 *   <li>{@link #autoClose(String)} — append missing closing brackets/quotes</li>
 * </ol>
 * Ref: Req 7, Req 8
 */
public final class PreProcessor {

    private PreProcessor() {}

    // -------------------------------------------------------------------------
    // Task 3.2 — stripFences
    // -------------------------------------------------------------------------

    /**
     * Extract content from triple-backtick fenced blocks.
     * Multiple blocks are concatenated with '\n'. If no fences are found,
     * the original text is returned as-is. A missing closing fence is tolerated
     * (streaming): the rest of the text is treated as fence content.
     * Triple-backtick sequences inside double-quoted strings are NOT treated as
     * fence boundaries.
     * Ref: Req 7 AC1-4, AC7
     */
    public static String stripFences(String text) {
        final String FENCE = "```";
        StringBuilder result = new StringBuilder();
        int pos = 0;
        boolean foundAnyFence = false;

        while (pos < text.length()) {
            int fenceStart = indexOfFenceOutsideString(text, pos);
            if (fenceStart < 0) break;

            foundAnyFence = true;
            // Skip the opening fence line (up to end of that line)
            int afterFence = fenceStart + FENCE.length();
            // Skip optional language tag on same line
            int eol = text.indexOf('\n', afterFence);
            int contentStart = eol < 0 ? text.length() : eol + 1;

            // Find closing fence
            int closingFence = indexOfFenceOutsideString(text, contentStart);
            if (closingFence < 0) {
                // Unterminated — take rest of text as content (AC7)
                String content = text.substring(contentStart);
                if (result.length() > 0) result.append('\n');
                result.append(content);
                pos = text.length();
            } else {
                String content = text.substring(contentStart, closingFence);
                // Trim trailing newline added by the fence boundary
                if (content.endsWith("\n")) content = content.substring(0, content.length() - 1);
                if (result.length() > 0) result.append('\n');
                result.append(content);
                pos = closingFence + FENCE.length();
                // Skip to end of closing fence line
                int eolClose = text.indexOf('\n', pos);
                pos = eolClose < 0 ? text.length() : eolClose + 1;
            }
        }

        return foundAnyFence ? result.toString() : text;
    }

    /** Find the next ``` that is not inside a double-quoted string. */
    private static int indexOfFenceOutsideString(String text, int from) {
        boolean inString = false;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') { i++; continue; } // skip escaped char
                if (c == '"') inString = false;
            } else {
                if (c == '"') { inString = true; continue; }
                if (text.startsWith("```", i)) return i;
            }
        }
        return -1;
    }

    // -------------------------------------------------------------------------
    // Task 3.1 — stripComments
    // -------------------------------------------------------------------------

    /**
     * Remove {@code //} and {@code #} line comments from text.
     * Comment markers inside double-quoted strings are ignored.
     * Ref: Req 7 AC5-6
     */
    public static String stripComments(String text) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"') {
                // Copy entire string literal verbatim
                sb.append(c);
                i++;
                while (i < text.length()) {
                    char s = text.charAt(i);
                    sb.append(s);
                    if (s == '\\') {
                        i++;
                        if (i < text.length()) { sb.append(text.charAt(i)); i++; }
                    } else if (s == '"') {
                        i++;
                        break;
                    } else {
                        i++;
                    }
                }
            } else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                // Skip to end of line
                while (i < text.length() && text.charAt(i) != '\n') i++;
            } else if (c == '#') {
                // Skip to end of line
                while (i < text.length() && text.charAt(i) != '\n') i++;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Task 3.3 — autoClose
    // -------------------------------------------------------------------------

    /**
     * Append missing closing brackets and quotes so the lexer always sees
     * balanced input. Sets {@code wasIncomplete} if anything was appended.
     * Handles trailing backslash inside strings (streaming split mid-escape).
     * Ref: Req 8 AC1-4
     */
    public static AutoCloseResult autoClose(String text) {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inDouble = false;
        boolean inSingle = false;
        int i = 0;

        while (i < text.length()) {
            char c = text.charAt(i);

            if (inDouble) {
                if (c == '\\') { i += 2; continue; }
                if (c == '"') inDouble = false;
                i++;
                continue;
            }
            if (inSingle) {
                if (c == '\\') { i += 2; continue; }
                if (c == '\'') inSingle = false;
                i++;
                continue;
            }

            if (c == '"') {
                inDouble = true;
            } else if (c == '\'') {
                inSingle = true;
            } else if (c == '(') {
                stack.push(')');
            } else if (c == '[') {
                stack.push(']');
            } else if (c == '{') {
                stack.push('}');
            } else if (c == ')' || c == ']' || c == '}') {
                if (!stack.isEmpty()) stack.pop();
            }
            i++;
        }

        StringBuilder sb = new StringBuilder(text);
        boolean wasIncomplete = false;

        // Close open string literals
        if (inDouble) {
            // Handle trailing backslash (streaming mid-escape)
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\\') {
                sb.append(' '); // pad so the escape is not dangling
            }
            sb.append('"');
            wasIncomplete = true;
        } else if (inSingle) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\\') {
                sb.append(' ');
            }
            sb.append('\'');
            wasIncomplete = true;
        }

        // Close open brackets in reverse order
        while (!stack.isEmpty()) {
            sb.append(stack.pop());
            wasIncomplete = true;
        }

        return new AutoCloseResult(sb.toString(), wasIncomplete);
    }
}
