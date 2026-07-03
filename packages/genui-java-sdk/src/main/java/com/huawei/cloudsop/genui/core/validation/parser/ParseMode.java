package com.huawei.cloudsop.genui.core.validation.parser;

/**
 * Parsing mode for the preprocessor/parser.
 *
 * <p>Mirrors the TS distinction between a one-shot {@code parse()} (complete input) and the
 * streaming parser: {@link #STREAMING} auto-closes unclosed brackets/strings so partial input still
 * parses, whereas {@link #FINAL} leaves the text as-is (unclosed constructs surface as diagnostics).
 */
public enum ParseMode {
  /** Complete input. No auto-closing; unclosed brackets/strings become diagnostics. */
  FINAL,
  /** Partial/streaming input. Unclosed brackets/strings are auto-closed for tolerant parsing. */
  STREAMING
}
