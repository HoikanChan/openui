package dev.openui.langcore.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PromptGenerator}.
 * Ref: Design §11
 */
class PromptGeneratorTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PromptSpec minimalSpec() {
        return new PromptSpec(
                "Root",
                Map.of("Button", new ComponentPromptSpec("Button(label: string)", "A clickable button")),
                List.of(),
                List.of(),
                false, false, false, false,
                null,
                List.of(), List.of(), List.of()
        );
    }

    private static String generate(PromptSpec spec) {
        return PromptGenerator.generatePrompt(spec);
    }

    // -------------------------------------------------------------------------
    // Component signatures appear in output
    // -------------------------------------------------------------------------

    @Test
    void output_containsComponentSignature() {
        String out = generate(minimalSpec());
        assertTrue(out.contains("Button(label: string)"), "Component signature must appear in output");
    }

    @Test
    void output_containsComponentDescription() {
        String out = generate(minimalSpec());
        assertTrue(out.contains("A clickable button"), "Component description must appear in output");
    }

    @Test
    void output_containsComponentSignaturesHeader() {
        String out = generate(minimalSpec());
        assertTrue(out.contains("## Component Signatures"), "Signatures section header must appear");
    }

    // -------------------------------------------------------------------------
    // Tools section present when tools given
    // -------------------------------------------------------------------------

    @Test
    void toolsSection_presentWhenToolsProvided() {
        PromptSpec spec = new PromptSpec(
                "Root",
                Map.of("Button", new ComponentPromptSpec("Button(label: string)", null)),
                List.of(), List.of("myTool"),
                false, false, false, false,
                null, List.of(), List.of(), List.of()
        );
        String out = generate(spec);
        assertTrue(out.contains("## Available Tools"), "Tools section must appear when tools are provided");
        assertTrue(out.contains("myTool"), "Tool name must appear");
    }

    @Test
    void toolsSection_absentWhenNoTools() {
        String out = generate(minimalSpec());
        assertFalse(out.contains("## Available Tools"), "Tools section must not appear when no tools");
    }

    @Test
    void toolSpec_signatureRendered() {
        ToolSpec tool = new ToolSpec(
                "getUsers",
                "Fetches all users",
                Map.of("type", "object", "properties",
                       Map.of("limit", Map.of("type", "integer")),
                       "required", List.of("limit")),
                Map.of("type", "array", "items", Map.of("type", "object")),
                null
        );
        PromptSpec spec = new PromptSpec(
                "Root",
                Map.of("Button", new ComponentPromptSpec("Button(label: string)", null)),
                List.of(), List.of(tool),
                false, false, false, false,
                null, List.of(), List.of(), List.of()
        );
        String out = generate(spec);
        assertTrue(out.contains("getUsers"), "Tool name must appear");
        assertTrue(out.contains("Fetches all users"), "Tool description must appear");
        assertTrue(out.contains("limit"), "Tool param must appear");
    }

    // -------------------------------------------------------------------------
    // examples / toolExamples appear
    // -------------------------------------------------------------------------

    @Test
    void examples_appear() {
        PromptSpec spec = new PromptSpec(
                "Root",
                Map.of("Button", new ComponentPromptSpec("Button(label: string)", null)),
                List.of(), List.of(),
                false, false, false, false,
                null,
                List.of("root = Button(\"Click me\")"),
                List.of("data = Query(\"tool\", {}, {})"),
                List.of()
        );
        String out = generate(spec);
        assertTrue(out.contains("## Examples"), "Examples section must appear");
        assertTrue(out.contains("root = Button"), "Example content must appear");
        assertTrue(out.contains("data = Query"), "toolExamples content must appear");
    }

    // -------------------------------------------------------------------------
    // preamble is prepended
    // -------------------------------------------------------------------------

    @Test
    void preamble_prependedToOutput() {
        PromptSpec spec = new PromptSpec(
                "Root",
                Map.of("Button", new ComponentPromptSpec("Button(label: string)", null)),
                List.of(), List.of(),
                false, false, false, false,
                "CUSTOM PREAMBLE",
                List.of(), List.of(), List.of()
        );
        String out = generate(spec);
        assertTrue(out.startsWith("CUSTOM PREAMBLE"), "Custom preamble must be at the start");
    }

    @Test
    void defaultPreamble_usedWhenNull() {
        String out = generate(minimalSpec());
        assertTrue(out.startsWith("You are an AI assistant"), "Default preamble must be prepended");
    }

    // -------------------------------------------------------------------------
    // editMode flag alters output
    // -------------------------------------------------------------------------

    @Test
    void editMode_includesEditSection() {
        PromptSpec spec = new PromptSpec(
                "Root",
                Map.of("Button", new ComponentPromptSpec("Button(label: string)", null)),
                List.of(), List.of(),
                true, false, false, false,
                null, List.of(), List.of(), List.of()
        );
        String out = generate(spec);
        assertTrue(out.contains("## Edit Mode"), "Edit mode section must appear when editMode=true");
    }

    @Test
    void editMode_false_noEditSection() {
        String out = generate(minimalSpec());
        assertFalse(out.contains("## Edit Mode"), "Edit mode section must not appear when editMode=false");
    }

    // -------------------------------------------------------------------------
    // inlineMode flag alters output
    // -------------------------------------------------------------------------

    @Test
    void inlineMode_includesInlineSection() {
        PromptSpec spec = new PromptSpec(
                "Root",
                Map.of("Button", new ComponentPromptSpec("Button(label: string)", null)),
                List.of(), List.of(),
                false, true, false, false,
                null, List.of(), List.of(), List.of()
        );
        String out = generate(spec);
        assertTrue(out.contains("## Inline Mode"), "Inline mode section must appear when inlineMode=true");
    }

    // -------------------------------------------------------------------------
    // additionalRules appended
    // -------------------------------------------------------------------------

    @Test
    void additionalRules_appended() {
        PromptSpec spec = new PromptSpec(
                "Root",
                Map.of("Button", new ComponentPromptSpec("Button(label: string)", null)),
                List.of(), List.of(),
                false, false, false, false,
                null, List.of(), List.of(),
                List.of("Always use dark mode")
        );
        String out = generate(spec);
        assertTrue(out.contains("- Always use dark mode"), "Additional rules must appear in output");
    }

    // -------------------------------------------------------------------------
    // componentGroups — grouped sections
    // -------------------------------------------------------------------------

    @Test
    void componentGroups_renderGroupHeaders() {
        ComponentGroup group = new ComponentGroup("Form Components",
                List.of("Button"), List.of("Use for user input."));
        PromptSpec spec = new PromptSpec(
                "Root",
                Map.of("Button", new ComponentPromptSpec("Button(label: string)", null)),
                List.of(group), List.of(),
                false, false, false, false,
                null, List.of(), List.of(), List.of()
        );
        String out = generate(spec);
        assertTrue(out.contains("### Form Components"), "Group header must appear");
        assertTrue(out.contains("Use for user input."), "Group notes must appear");
    }

    // -------------------------------------------------------------------------
    // jsonSchemaTypeStr helper
    // -------------------------------------------------------------------------

    @Test
    void jsonSchemaTypeStr_string()  { assertEquals("string",  PromptGenerator.jsonSchemaTypeStr(Map.of("type", "string"))); }
    @Test
    void jsonSchemaTypeStr_number()  { assertEquals("number",  PromptGenerator.jsonSchemaTypeStr(Map.of("type", "number"))); }
    @Test
    void jsonSchemaTypeStr_boolean() { assertEquals("boolean", PromptGenerator.jsonSchemaTypeStr(Map.of("type", "boolean"))); }
    @Test
    void jsonSchemaTypeStr_array()   { assertEquals("string[]", PromptGenerator.jsonSchemaTypeStr(Map.of("type", "array", "items", Map.of("type", "string")))); }
    @Test
    void jsonSchemaTypeStr_enum()    { assertEquals("\"a\" | \"b\"", PromptGenerator.jsonSchemaTypeStr(Map.of("type", "string", "enum", List.of("a", "b")))); }
    @Test
    void jsonSchemaTypeStr_object()  {
        String result = PromptGenerator.jsonSchemaTypeStr(Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string")),
                "required", List.of("name")));
        assertEquals("{name: string}", result);
    }
    @Test
    void jsonSchemaTypeStr_null()    { assertEquals("any", PromptGenerator.jsonSchemaTypeStr(null)); }
}
