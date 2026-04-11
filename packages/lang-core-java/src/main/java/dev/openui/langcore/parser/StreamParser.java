package dev.openui.langcore.parser;

import dev.openui.langcore.lexer.Lexer;
import dev.openui.langcore.lexer.Token;
import dev.openui.langcore.lexer.TokenType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Streaming parser that accumulates text via {@link #push(String)} or {@link #set(String)}
 * and returns a {@link ParseResult} on each call.
 *
 * <p>Committed statements (those followed by a newline at bracket/ternary depth 0) are
 * stored in an immutable cache using {@code putIfAbsent}. The last incomplete (pending)
 * statement is auto-closed and merged with the committed map, but never overwrites
 * committed entries.
 *
 * <p>All public methods are {@code synchronized} for thread safety.
 * Ref: Req 9 AC1-9, Design §9
 */
public final class StreamParser {

    private final SchemaRegistry schema;
    private final StringBuilder buffer = new StringBuilder();
    private final LinkedHashMap<String, Statement> committed = new LinkedHashMap<>();
    private int completedEnd = 0;   // char offset into buffer of last committed boundary
    private int completedCount = 0;
    private String firstId = null;

    public StreamParser(SchemaRegistry schema) {
        this.schema = schema;
    }

    // -------------------------------------------------------------------------
    // Public API — all synchronized per AC1
    // -------------------------------------------------------------------------

    /**
     * Append {@code chunk} to the buffer and return the updated {@link ParseResult}.
     * Ref: Req 9 AC2
     */
    public synchronized ParseResult push(String chunk) {
        buffer.append(chunk);
        return buildResult();
    }

    /**
     * Set the full text:
     * <ul>
     *   <li>If {@code fullText} starts with the current buffer AND is longer → append the delta only.</li>
     *   <li>Otherwise → reset all state and re-parse from the new text.</li>
     * </ul>
     * Ref: Req 9 AC3, AC8
     */
    public synchronized ParseResult set(String fullText) {
        String current = buffer.toString();
        if (fullText.startsWith(current) && fullText.length() > current.length()) {
            // Delta-append only
            buffer.append(fullText.substring(current.length()));
        } else if (!fullText.equals(current)) {
            // Reset and re-parse
            reset();
            buffer.append(fullText);
        }
        // If equal, no change — just rebuild result
        return buildResult();
    }

    /**
     * Return the current {@link ParseResult} without modifying the buffer.
     * Ref: Req 9 AC9
     */
    public synchronized ParseResult getResult() {
        return buildResult();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Clear all accumulated state. Called on a buffer reset.
     * Ref: Req 9 AC8
     */
    private void reset() {
        buffer.setLength(0);
        committed.clear();
        completedEnd = 0;
        completedCount = 0;
        firstId = null;
    }

    /**
     * Build the current {@link ParseResult} from the accumulated buffer.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Strip comments from the full buffer text.</li>
     *   <li>Scan the stripped text to find the last "commit boundary" — a position
     *       immediately after a newline that occurs at bracket depth 0 and ternary depth 0,
     *       where the next non-whitespace token is not {@code ?} or {@code :}.</li>
     *   <li>Parse the "completed" portion (up to the commit boundary) without autoClose
     *       and commit statements via {@code putIfAbsent}.</li>
     *   <li>Parse the "pending" portion (after the commit boundary) with autoClose and
     *       merge into a temporary map starting from committed (committed entries win).</li>
     *   <li>Materialize the merged map; {@code wasIncomplete} is true when the pending
     *       text had autoClose additions or when any text remains after the commit boundary.</li>
     * </ol>
     * Ref: Req 9 AC4-7
     */
    private ParseResult buildResult() {
        String fullText = buffer.toString();
        String stripped = PreProcessor.stripComments(fullText);

        // Find the last commit boundary in the stripped text.
        // A commit boundary is a char position immediately AFTER a '\n' that appears
        // at bracket depth 0 and ternary depth 0, where the next non-whitespace
        // character is not '?' or ':'.
        int lastBoundary = findLastCommitBoundary(stripped);

        String completedText = stripped.substring(0, lastBoundary);
        String pendingText   = stripped.substring(lastBoundary);

        // --- Commit completed statements ---
        if (lastBoundary > completedEnd || lastBoundary == 0) {
            // Parse completed text and commit new statements
            List<Token> completedTokens = new Lexer(completedText).tokenize();
            LinkedHashMap<String, Statement> completedStmts = new StatementParser().parse(completedTokens);
            for (Map.Entry<String, Statement> entry : completedStmts.entrySet()) {
                String id = entry.getKey();
                committed.putIfAbsent(id, entry.getValue());
                if (firstId == null) firstId = id;
            }
            completedEnd = lastBoundary;
            completedCount = committed.size();
        }

        // --- Parse pending text (with autoClose) ---
        boolean wasIncomplete = !pendingText.isBlank();
        AutoCloseResult closed = PreProcessor.autoClose(pendingText);
        if (closed.wasIncomplete()) {
            wasIncomplete = true;
        }

        LinkedHashMap<String, Statement> merged = new LinkedHashMap<>(committed);

        if (!pendingText.isBlank()) {
            List<Token> pendingTokens = new Lexer(closed.text()).tokenize();
            LinkedHashMap<String, Statement> pendingStmts = new StatementParser().parse(pendingTokens);
            for (Map.Entry<String, Statement> entry : pendingStmts.entrySet()) {
                // putIfAbsent: committed entries always win (AC5, AC6)
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        // meta.incomplete == false when all statements are complete (AC7)
        return new Materializer(schema, wasIncomplete).materialize(merged);
    }

    /**
     * Scan {@code text} to find the last commit boundary position.
     *
     * <p>A commit boundary is the character position immediately after a '\n' at
     * bracket depth 0 and ternary depth 0, where the character(s) following the
     * newline (skipping whitespace/newlines) are not '?' or ':'.
     *
     * <p>Returns 0 if no commit boundary exists (everything is pending).
     * Ref: Req 9 AC4
     */
    private static int findLastCommitBoundary(String text) {
        int n = text.length();
        int bracketDepth = 0;
        int ternaryDepth = 0;
        boolean inDouble = false;
        boolean inSingle = false;

        int lastBoundary = 0;

        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);

            // Handle string literals — bracket/ternary depths are not tracked inside them
            if (inDouble) {
                if (c == '\\') { i++; continue; }
                if (c == '"')  inDouble = false;
                continue;
            }
            if (inSingle) {
                if (c == '\\') { i++; continue; }
                if (c == '\'') inSingle = false;
                continue;
            }

            if (c == '"')  { inDouble = true;  continue; }
            if (c == '\'') { inSingle = true;  continue; }

            // Track bracket depth
            if (c == '(' || c == '[' || c == '{') {
                bracketDepth++;
                continue;
            }
            if (c == ')' || c == ']' || c == '}') {
                if (bracketDepth > 0) bracketDepth--;
                continue;
            }

            // Track ternary depth (? increases, : at ternary depth > 0 decreases)
            if (c == '?' && bracketDepth == 0) {
                ternaryDepth++;
                continue;
            }
            if (c == ':' && bracketDepth == 0 && ternaryDepth > 0) {
                ternaryDepth--;
                continue;
            }

            // Look for a newline at depth 0
            if (c == '\n' && bracketDepth == 0 && ternaryDepth == 0) {
                int afterNewline = i + 1;
                // Peek at the next non-whitespace char (but not newlines — newlines reset depth check)
                int peek = afterNewline;
                while (peek < n && text.charAt(peek) == ' ') {
                    peek++;
                }
                // If we hit end of text or a non-? non-: char, this is a commit boundary
                if (peek >= n) {
                    // newline at end — could be commit boundary, mark it
                    lastBoundary = afterNewline;
                } else {
                    char next = text.charAt(peek);
                    if (next != '?' && next != ':') {
                        lastBoundary = afterNewline;
                    }
                    // else: continuation (ternary continuation on next line) — not a boundary
                }
            }
        }

        return lastBoundary;
    }
}
