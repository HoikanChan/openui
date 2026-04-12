package dev.openui.langcore.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Validation}.
 *
 * Ref: Design §19, Task 20.2
 */
class ValidationTest {

    private static final Map<String, Validation.ValidatorFn> VALIDATORS =
            Validation.builtInValidators();

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static String run(String name, Object value, Object param) {
        return VALIDATORS.get(name).test(value, param);
    }

    // -------------------------------------------------------------------------
    // required
    // -------------------------------------------------------------------------

    @Test
    void required_null_fails() {
        assertNotNull(run("required", null, null));
    }

    @Test
    void required_emptyString_fails() {
        assertNotNull(run("required", "", null));
    }

    @Test
    void required_emptyList_fails() {
        assertNotNull(run("required", List.of(), null));
    }

    @Test
    void required_emptyMap_fails() {
        assertNotNull(run("required", Map.of(), null));
    }

    @Test
    void required_nonEmpty_passes() {
        assertNull(run("required", "hello", null));
        assertNull(run("required", 0, null));
        assertNull(run("required", List.of("a"), null));
    }

    @Test
    void required_allFalseBooleansMap_fails() {
        assertNotNull(run("required", Map.of("a", false, "b", false), null));
    }

    @Test
    void required_atLeastOneTrueBoolean_passes() {
        assertNull(run("required", Map.of("a", false, "b", true), null));
    }

    // -------------------------------------------------------------------------
    // email
    // -------------------------------------------------------------------------

    @Test
    void email_valid_passes() {
        assertNull(run("email", "user@example.com", null));
    }

    @Test
    void email_invalid_fails() {
        assertNotNull(run("email", "not-an-email", null));
    }

    @Test
    void email_empty_passes() {
        assertNull(run("email", "", null));  // empty → skip
    }

    // -------------------------------------------------------------------------
    // url
    // -------------------------------------------------------------------------

    @Test
    void url_valid_passes() {
        assertNull(run("url", "https://example.com", null));
    }

    @Test
    void url_invalid_fails() {
        assertNotNull(run("url", "not a url", null));
    }

    @Test
    void url_empty_passes() {
        assertNull(run("url", null, null));
    }

    // -------------------------------------------------------------------------
    // numeric
    // -------------------------------------------------------------------------

    @Test
    void numeric_number_passes() {
        assertNull(run("numeric", 42, null));
        assertNull(run("numeric", 3.14, null));
    }

    @Test
    void numeric_numericString_passes() {
        assertNull(run("numeric", "3.14", null));
    }

    @Test
    void numeric_nonNumericString_fails() {
        assertNotNull(run("numeric", "abc", null));
    }

    @Test
    void numeric_empty_passes() {
        assertNull(run("numeric", null, null));
    }

    // -------------------------------------------------------------------------
    // min / max
    // -------------------------------------------------------------------------

    @Test
    void min_aboveThreshold_passes() {
        assertNull(run("min", 10, 5.0));
    }

    @Test
    void min_belowThreshold_fails() {
        assertNotNull(run("min", 3, 5.0));
    }

    @Test
    void min_equalThreshold_passes() {
        assertNull(run("min", 5, 5.0));
    }

    @Test
    void max_belowThreshold_passes() {
        assertNull(run("max", 3, 10.0));
    }

    @Test
    void max_aboveThreshold_fails() {
        assertNotNull(run("max", 15, 10.0));
    }

    @Test
    void min_empty_passes() {
        assertNull(run("min", null, 5.0));
    }

    // -------------------------------------------------------------------------
    // minLength / maxLength
    // -------------------------------------------------------------------------

    @Test
    void minLength_longEnough_passes() {
        assertNull(run("minLength", "hello", 3.0));
    }

    @Test
    void minLength_tooShort_fails() {
        assertNotNull(run("minLength", "hi", 3.0));
    }

    @Test
    void maxLength_shortEnough_passes() {
        assertNull(run("maxLength", "hi", 10.0));
    }

    @Test
    void maxLength_tooLong_fails() {
        assertNotNull(run("maxLength", "hello world", 5.0));
    }

    @Test
    void minLength_nonString_passes() {
        assertNull(run("minLength", 42, 3.0));
    }

    // -------------------------------------------------------------------------
    // pattern
    // -------------------------------------------------------------------------

    @Test
    void pattern_matching_passes() {
        assertNull(run("pattern", "abc123", "[a-z]+\\d+"));
    }

    @Test
    void pattern_notMatching_fails() {
        assertNotNull(run("pattern", "UPPER", "^[a-z]+$"));
    }

    @Test
    void pattern_empty_passes() {
        assertNull(run("pattern", null, "^[a-z]+$"));
    }

    @Test
    void pattern_invalidRegex_passes() {
        // Invalid regex must not throw — returns null
        assertNull(run("pattern", "value", "[invalid"));
    }

    // -------------------------------------------------------------------------
    // parseRules — from list of strings
    // -------------------------------------------------------------------------

    @Test
    void parseRules_emptyList_returnsEmpty() {
        assertTrue(Validation.parseRules(List.of()).isEmpty());
    }

    @Test
    void parseRules_nonList_returnsEmpty() {
        assertTrue(Validation.parseRules("required").isEmpty());
        assertTrue(Validation.parseRules(null).isEmpty());
    }

    @Test
    void parseRules_simpleRule_noParam() {
        List<ParsedRule> rules = Validation.parseRules(List.of("required"));
        assertEquals(1, rules.size());
        assertEquals("required", rules.get(0).name());
        assertNull(rules.get(0).param());
    }

    @Test
    void parseRules_numericParam_parsedAsDouble() {
        List<ParsedRule> rules = Validation.parseRules(List.of("min:8"));
        assertEquals("min", rules.get(0).name());
        assertEquals(8.0, rules.get(0).param());
    }

    @Test
    void parseRules_stringParam_keptAsString() {
        List<ParsedRule> rules = Validation.parseRules(List.of("pattern:^[a-z]+$"));
        assertEquals("pattern", rules.get(0).name());
        assertEquals("^[a-z]+$", rules.get(0).param());
    }

    @Test
    void parseRules_multipleRules() {
        List<ParsedRule> rules = Validation.parseRules(
                List.of("required", "minLength:3", "maxLength:50"));
        assertEquals(3, rules.size());
        assertEquals("minLength", rules.get(1).name());
        assertEquals(3.0, rules.get(1).param());
    }

    @Test
    void parseRules_nonStringElementsSkipped() {
        List<Object> input = new java.util.ArrayList<>();
        input.add("required");
        input.add(42);
        input.add(null);
        List<ParsedRule> rules = Validation.parseRules(input);
        assertEquals(1, rules.size());
    }

    // -------------------------------------------------------------------------
    // parseStructuredRules — from Map
    // -------------------------------------------------------------------------

    @Test
    void parseStructuredRules_trueValue_noParam() {
        List<ParsedRule> rules = Validation.parseStructuredRules(Map.of("required", true));
        assertEquals(1, rules.size());
        assertEquals("required", rules.get(0).name());
        assertNull(rules.get(0).param());
    }

    @Test
    void parseStructuredRules_numericValue_usedAsParam() {
        List<ParsedRule> rules = Validation.parseStructuredRules(Map.of("min", 5));
        assertEquals("min", rules.get(0).name());
        assertEquals(5, rules.get(0).param());
    }

    @Test
    void parseStructuredRules_falseValue_skipped() {
        List<ParsedRule> rules = Validation.parseStructuredRules(Map.of("required", false));
        assertTrue(rules.isEmpty());
    }

    @Test
    void parseStructuredRules_nullValue_skipped() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("required", null);
        List<ParsedRule> rules = Validation.parseStructuredRules(m);
        assertTrue(rules.isEmpty());
    }

    @Test
    void parseStructuredRules_nonMap_returnsEmpty() {
        assertTrue(Validation.parseStructuredRules(null).isEmpty());
        assertTrue(Validation.parseStructuredRules(List.of("required")).isEmpty());
    }

    // -------------------------------------------------------------------------
    // validate — short-circuits on first failure
    // -------------------------------------------------------------------------

    @Test
    void validate_allPass_returnsNull() {
        List<ParsedRule> rules = Validation.parseRules(List.of("required", "minLength:3"));
        assertNull(Validation.validate("hello", rules, null));
    }

    @Test
    void validate_firstFails_returnsError() {
        List<ParsedRule> rules = Validation.parseRules(List.of("required", "minLength:3"));
        assertNotNull(Validation.validate("", rules, null));
    }

    @Test
    void validate_shortCircuits() {
        // "required" fails first — minLength should never run
        int[] count = {0};
        Map<String, Validation.ValidatorFn> custom = Map.of(
                "minLength", (v, p) -> { count[0]++; return null; }
        );
        List<ParsedRule> rules = Validation.parseRules(List.of("required", "minLength:3"));
        Validation.validate("", rules, custom);
        // With short-circuit, required fails first so minLength validator is never called
        // (custom takes precedence but required runs first)
        // Actually "required" uses built-in since it's not in custom — let's just verify error returned
        // and custom minLength was not called
        assertEquals(0, count[0], "minLength must not run after required fails");
    }

    @Test
    void validate_unknownRule_skipped() {
        List<ParsedRule> rules = List.of(new ParsedRule("nonexistent", null));
        assertNull(Validation.validate("value", rules, null));
    }

    @Test
    void validate_customValidatorOverridesBuiltIn() {
        Map<String, Validation.ValidatorFn> custom = Map.of(
                "required", (v, p) -> "custom error"
        );
        List<ParsedRule> rules = List.of(new ParsedRule("required", null));
        assertEquals("custom error", Validation.validate("nonempty", rules, custom));
    }
}
