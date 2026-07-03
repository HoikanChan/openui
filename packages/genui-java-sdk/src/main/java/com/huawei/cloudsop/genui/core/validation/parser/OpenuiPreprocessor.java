package com.huawei.cloudsop.genui.core.validation.parser;

import com.huawei.cloudsop.genui.core.llm.extract.OpenuiCodeExtractor;

/**
 * Cleans an LLM response into parseable openui-lang source.
 *
 * <p>Pipeline mirrors the TS {@code preprocess()} in {@code parser.ts}: fenced-code extraction →
 * comment stripping → whitespace normalization. Fence handling is DELEGATED to {@link
 * OpenuiCodeExtractor} (the SDK's existing markdown-fence logic) rather than re-implemented, so the
 * two stay aligned.
 *
 * <p>{@link #process(String, ParseMode)} additionally runs {@code autoClose} (mirroring {@code
 * statements.ts}) in {@link ParseMode#STREAMING} to repair unclosed brackets/strings for tolerant
 * partial parsing.
 */
public final class OpenuiPreprocessor {

  private OpenuiPreprocessor() {}

  /** Result of preprocessing: cleaned text plus whether auto-close had to repair the input. */
  public record Result(String text, boolean wasIncomplete) {}

  /**
   * Strip fences + comments and normalize whitespace, without auto-closing. Equivalent to the TS
   * {@code preprocess()} one-shot cleanup.
   */
  public static String preprocess(String input) {
    if (input == null) {
      return "";
    }
    String stripped = OpenuiCodeExtractor.extract(input.trim());
    return stripComments(stripped).trim();
  }

  /**
   * Full preprocess for a given mode. In {@link ParseMode#STREAMING} the cleaned text is
   * auto-closed and {@code wasIncomplete} reflects whether any repair was applied. In {@link
   * ParseMode#FINAL} the text is returned untouched with {@code wasIncomplete=false}.
   */
  public static Result process(String input, ParseMode mode) {
    String cleaned = preprocess(input);
    if (mode == ParseMode.STREAMING) {
      return autoClose(cleaned);
    }
    return new Result(cleaned, false);
  }

  /**
   * Strip {@code //} and {@code #} line comments that appear outside of string literals (both
   * {@code "} and {@code '} delimiters). Mirrors {@code stripComments} in {@code parser.ts}.
   */
  static String stripComments(String input) {
    String[] lines = input.split("\n", -1);
    StringBuilder out = new StringBuilder(input.length());
    for (int li = 0; li < lines.length; li++) {
      if (li > 0) {
        out.append('\n');
      }
      out.append(stripCommentFromLine(lines[li]));
    }
    return out.toString();
  }

  private static String stripCommentFromLine(String line) {
    char inStr = 0; // 0 = not in string, else the quote char
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (inStr != 0) {
        if (c == '\\' && i + 1 < line.length()) {
          i++; // skip escaped char
          continue;
        }
        if (c == inStr) {
          inStr = 0;
        }
        continue;
      }
      if (c == '"' || c == '\'') {
        inStr = c;
        continue;
      }
      if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
        return trimEnd(line.substring(0, i));
      }
      if (c == '#') {
        return trimEnd(line.substring(0, i));
      }
    }
    return line;
  }

  /**
   * Auto-close unclosed strings and brackets so partial/streaming input parses without syntax
   * errors. Mirrors {@code autoClose} in {@code statements.ts}.
   */
  static Result autoClose(String input) {
    java.util.Deque<Character> stack = new java.util.ArrayDeque<>();
    char inStr = 0;
    boolean esc = false;

    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (esc) {
        esc = false;
        continue;
      }
      if (c == '\\' && inStr != 0) {
        esc = true;
        continue;
      }
      if (inStr != 0) {
        if (c == inStr) {
          inStr = 0;
        }
        continue;
      }
      if (c == '"' || c == '\'') {
        inStr = c;
        continue;
      }
      if (c == '(' || c == '[' || c == '{') {
        stack.push(c);
      } else if (c == ')' && !stack.isEmpty() && stack.peek() == '(') {
        stack.pop();
      } else if (c == ']' && !stack.isEmpty() && stack.peek() == '[') {
        stack.pop();
      } else if (c == '}' && !stack.isEmpty() && stack.peek() == '{') {
        stack.pop();
      }
    }

    boolean wasIncomplete = inStr != 0 || !stack.isEmpty();
    if (!wasIncomplete) {
      return new Result(input, false);
    }

    StringBuilder out = new StringBuilder(input);
    if (inStr != 0) {
      if (esc) {
        out.append('\\');
      }
      out.append(inStr);
    }
    // Deque iterates top-first, i.e. innermost bracket first — the correct close order.
    for (char open : stack) {
      out.append(open == '(' ? ')' : open == '[' ? ']' : '}');
    }
    return new Result(out.toString(), true);
  }

  private static String trimEnd(String s) {
    int end = s.length();
    while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
      end--;
    }
    return s.substring(0, end);
  }
}
