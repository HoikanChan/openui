package dev.openui.langcore.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validation utility — mirrors {@code utils/validation.ts}.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link #builtInValidators()} — the standard set of rule implementations</li>
 *   <li>{@link #parseRules(Object)} — parse a list of rule strings into {@link ParsedRule}s</li>
 *   <li>{@link #parseStructuredRules(Object)} — parse a {@code Map} of rule key/values</li>
 *   <li>{@link #validate(Object, List, Map)} — run rules against a value</li>
 * </ul>
 *
 * Ref: Design §19
 */
public final class Validation {

    private Validation() {}

    // -------------------------------------------------------------------------
    // ValidatorFn
    // -------------------------------------------------------------------------

    /**
     * A single validator: returns an error message string on failure,
     * or {@code null} on success.
     */
    @FunctionalInterface
    public interface ValidatorFn {
        /** @return error message, or {@code null} if valid */
        String test(Object value, Object param);
    }

    // -------------------------------------------------------------------------
    // Built-in validators
    // -------------------------------------------------------------------------

    private static final Set<String> NUMERIC_RULES = Set.of("min", "max", "minLength", "maxLength");

    public static Map<String, ValidatorFn> builtInValidators() {
        Map<String, ValidatorFn> m = new LinkedHashMap<>();

        m.put("required", (value, param) -> {
            if (isEmpty(value)) return "This field is required";
            if (value instanceof Map<?, ?> map && !map.isEmpty()) {
                boolean allBooleans = map.values().stream().allMatch(v -> v instanceof Boolean);
                boolean anyTrue     = map.values().stream().anyMatch(v -> Boolean.TRUE.equals(v));
                if (allBooleans && !anyTrue) return "At least one option is required";
            }
            return null;
        });

        m.put("email", (value, param) -> {
            if (isEmpty(value)) return null;
            if (!(value instanceof String s)) return "Please enter a valid email";
            return s.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ? null : "Please enter a valid email";
        });

        m.put("url", (value, param) -> {
            if (isEmpty(value)) return null;
            if (!(value instanceof String s)) return "Please enter a valid URL";
            try {
                new java.net.URI(s).toURL();
                return null;
            } catch (Exception e) {
                return "Please enter a valid URL";
            }
        });

        m.put("numeric", (value, param) -> {
            if (isEmpty(value)) return null;
            if (value instanceof Number n && !Double.isNaN(n.doubleValue())) return null;
            if (value instanceof String s) {
                try { Double.parseDouble(s.strip()); return null; }
                catch (NumberFormatException ignored) {}
            }
            return "Must be a number";
        });

        m.put("min", (value, param) -> {
            if (isEmpty(value)) return null;
            double n = toDouble(value);
            if (Double.isNaN(n)) return null;
            double min = toDouble(param);
            return n >= min ? null : "Must be at least " + formatNum(min);
        });

        m.put("max", (value, param) -> {
            if (isEmpty(value)) return null;
            double n = toDouble(value);
            if (Double.isNaN(n)) return null;
            double max = toDouble(param);
            return n <= max ? null : "Must be no more than " + formatNum(max);
        });

        m.put("minLength", (value, param) -> {
            if (isEmpty(value)) return null;
            if (!(value instanceof String s)) return null;
            int min = (int) toDouble(param);
            return s.length() >= min ? null : "Must be at least " + min + " characters";
        });

        m.put("maxLength", (value, param) -> {
            if (isEmpty(value)) return null;
            if (!(value instanceof String s)) return null;
            int max = (int) toDouble(param);
            return s.length() <= max ? null : "Must be no more than " + max + " characters";
        });

        m.put("pattern", (value, param) -> {
            if (isEmpty(value)) return null;
            if (!(value instanceof String s) || !(param instanceof String p)) return null;
            try {
                return Pattern.compile(p).matcher(s).find() ? null : "Invalid format";
            } catch (Exception e) {
                return null;
            }
        });

        return m;
    }

    // -------------------------------------------------------------------------
    // parseRules
    // -------------------------------------------------------------------------

    /**
     * Parse a list of rule strings into {@link ParsedRule}s.
     * Non-string elements are silently skipped.
     *
     * <pre>
     *   "required"       → ParsedRule("required", null)
     *   "min:8"          → ParsedRule("min", 8.0)
     *   "pattern:^[a-z]" → ParsedRule("pattern", "^[a-z]")
     * </pre>
     */
    public static List<ParsedRule> parseRules(Object rules) {
        if (!(rules instanceof List<?> list)) return List.of();
        List<ParsedRule> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String s) result.add(parseRule(s));
        }
        return result;
    }

    /**
     * Parse a structured rules {@link Map} into {@link ParsedRule}s.
     * Keys with {@code false}/{@code null} values are skipped;
     * {@code true} values produce a rule with no param.
     */
    public static List<ParsedRule> parseStructuredRules(Object rules) {
        if (!(rules instanceof Map<?, ?> map)) return List.of();
        List<ParsedRule> result = new ArrayList<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object val = e.getValue();
            if (val == null || Boolean.FALSE.equals(val)) continue;
            String name = String.valueOf(e.getKey());
            if (Boolean.TRUE.equals(val)) {
                result.add(new ParsedRule(name, null));
            } else {
                result.add(new ParsedRule(name, val));
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // validate
    // -------------------------------------------------------------------------

    /**
     * Run all rules against {@code value}. Stops on the first failure.
     * Custom validators in {@code validators} take precedence over built-ins.
     *
     * @return error message on first failure, {@code null} if all rules pass
     */
    public static String validate(Object value, List<ParsedRule> rules,
                                  Map<String, ValidatorFn> validators) {
        Map<String, ValidatorFn> builtIn = builtInValidators();
        for (ParsedRule rule : rules) {
            ValidatorFn fn = (validators != null) ? validators.get(rule.name()) : null;
            if (fn == null) fn = builtIn.get(rule.name());
            if (fn == null) continue; // unknown rule — skip silently
            String error = fn.test(value, rule.param());
            if (error != null) return error;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ParsedRule parseRule(String rule) {
        int colonIdx = rule.indexOf(':');
        if (colonIdx == -1) return new ParsedRule(rule, null);
        String name   = rule.substring(0, colonIdx);
        String rawArg = rule.substring(colonIdx + 1);
        Object arg;
        if (NUMERIC_RULES.contains(name)) {
            try { arg = Double.parseDouble(rawArg); }
            catch (NumberFormatException e) { arg = rawArg; }
        } else {
            arg = rawArg;
        }
        return new ParsedRule(name, arg);
    }

    static boolean isEmpty(Object value) {
        if (value == null || "".equals(value)) return true;
        if (value instanceof List<?> l) return l.isEmpty();
        if (value instanceof Map<?, ?> m) return m.isEmpty();
        return false;
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { /* fall */ }
        }
        return Double.NaN;
    }

    private static String formatNum(double n) {
        return (n == Math.floor(n) && !Double.isInfinite(n)) ? String.valueOf((long) n) : String.valueOf(n);
    }
}
