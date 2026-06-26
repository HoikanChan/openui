package com.huawei.cloudsop.genui.core.llm;

import java.util.Locale;
import java.util.function.Supplier;
import java.util.logging.Logger;

final class LlmDebugLog {
  private static final Logger LOGGER = Logger.getLogger("com.huawei.cloudsop.genui.core.llm");
  private static final String PREFIX = "[genui-llm-debug]";
  private static final String ENABLED_PROPERTY = "genui.llm.debug";
  private static final String ENABLED_ENV = "GENUI_LLM_DEBUG";
  private static final String MAX_CHARS_PROPERTY = "genui.llm.debug.maxChars";
  private static final String MAX_CHARS_ENV = "GENUI_LLM_DEBUG_MAX_CHARS";
  private static final int DEFAULT_MAX_CHARS = 16_000;

  private LlmDebugLog() {}

  static void log(String event, Supplier<String> payload) {
    if (!enabled()) {
      return;
    }
    LOGGER.info(() -> PREFIX + " " + event + "\n" + truncate(payload.get()));
  }

  private static boolean enabled() {
    return enabled(System.getProperty(ENABLED_PROPERTY)) || enabled(System.getenv(ENABLED_ENV));
  }

  private static boolean enabled(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return normalized.equals("true")
        || normalized.equals("1")
        || normalized.equals("yes")
        || normalized.equals("on");
  }

  private static String truncate(String value) {
    String text = value == null ? "" : value;
    int maxChars = maxChars();
    if (text.length() <= maxChars) {
      return text;
    }
    int omitted = text.length() - maxChars;
    return text.substring(0, maxChars) + "\n...<truncated " + omitted + " chars>";
  }

  private static int maxChars() {
    String configured = System.getProperty(MAX_CHARS_PROPERTY);
    if (configured == null || configured.isBlank()) {
      configured = System.getenv(MAX_CHARS_ENV);
    }
    if (configured == null || configured.isBlank()) {
      return DEFAULT_MAX_CHARS;
    }
    try {
      int value = Integer.parseInt(configured.trim());
      return value > 0 ? value : DEFAULT_MAX_CHARS;
    } catch (NumberFormatException ignored) {
      return DEFAULT_MAX_CHARS;
    }
  }
}
