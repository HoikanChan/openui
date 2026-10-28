/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.GenerationSdkException;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PromptRendererTest {
    private static final Map<String, String> TEMPLATES = Map.ofEntries(
            Map.entry("prompt/error-close-orphan.prompt", "body{{/if}}"),
            Map.entry("prompt/error-else-duplicate.prompt", "{{#if flag}}a{{else}}b{{else}}c{{/if}}"),
            Map.entry("prompt/error-else-orphan.prompt", "{{else}}"),
            Map.entry("prompt/error-empty-tag.prompt", "{{}}"),
            Map.entry("prompt/error-for-nested.prompt", "{{#for outer}}x{{#for inner}}y{{/for}}{{/for}}"),
            Map.entry("prompt/error-for-orphan.prompt", "{{/for}}"),
            Map.entry("prompt/error-for-type.prompt", "{{#for rows}}{{name}}{{/for}}"),
            Map.entry("prompt/error-for-unclosed.prompt", "start\n{{#for rows}}\nbody\n"),
            Map.entry("prompt/error-invalid-condition.prompt", "{{#if 1flag}}x{{/if}}"),
            Map.entry("prompt/error-invalid-for.prompt", "{{#for}}x{{/for}}"),
            Map.entry("prompt/error-invalid-tag.prompt", "{{user.name}}"),
            Map.entry("prompt/error-parse-before-render.prompt", "{{missing}}\n{{#if flag}}\n"),
            Map.entry("prompt/error-unclosed-if.prompt", "start\n{{#if flag}}\nbody\n"),
            Map.entry("prompt/error-unclosed-tag.prompt", "line\n{{name"),
            Map.entry("prompt/error-unknown-close.prompt", "{{/each}}"),
            Map.entry("prompt/error-unknown-tag.prompt", "before\n{{#each items}}\nafter\n"),
            Map.entry("prompt/render-basic.prompt", "Hello, {{userName}}!\n{{#if showDetails}}Details for {{userName}}: {{detailText}}\n{{else}}No details.\n{{/if}}Done.\n"),
            Map.entry("prompt/render-branch-variables.prompt", "{{#if flag}}{{whenTrue}}{{else}}{{whenFalse}}{{/if}}"),
            Map.entry("prompt/render-cached.prompt", "cached {{value}}"),
            Map.entry("prompt/render-crlf.prompt", "line one {{name}}\r\n{{#if flag}}on\r\n{{else}}off\r\n{{/if}}end\r\n"),
            Map.entry("prompt/render-for.prompt", "Rows:\n{{#for rows}}{{name}}={{value}}\n{{/for}}Done\n"),
            Map.entry("prompt/render-for-if.prompt", "{{#for rows}}{{#if flag}}on:{{label}}\n{{else}}off:{{label}}\n{{/if}}{{/for}}"),
            Map.entry("prompt/render-for-in-if.prompt", "{{#if show}}[{{#for rows}}{{name}},{{/for}}]{{/if}}"),
            Map.entry("prompt/render-for-scope.prompt", "{{#for rows}}{{name}}/{{outer}}\n{{/for}}"),
            Map.entry("prompt/render-for-standalone.prompt", "Before\n{{#for rows}}\n- {{name}}\n{{/for}}\nAfter\n"),
            Map.entry("prompt/render-for-verbatim.prompt", "{{#for rows}}{{value}}{{/for}}"),
            Map.entry("prompt/render-if-for-if.prompt", "{{#if enabled}}{{#for history}}{{#if raw}}raw:{{value}}\n{{else}}shown:{{value}}\n{{/if}}{{/for}}{{/if}}"),
            Map.entry("prompt/render-if-no-else.prompt", "A{{#if flag}}B{{/if}}C"),
            Map.entry("prompt/render-nested-if.prompt", "{{#if outer}}{{#if inner}}both{{else}}outer only{{/if}}{{else}}none{{/if}}"),
            Map.entry("prompt/render-utf8.prompt", "你好，{{name}}！\n生成结果：{{count}} 条\n"),
            Map.entry("prompt/render-verbatim.prompt", "value={{value}} again={{value}}"),
            Map.entry("prompt/render-whitespace.prompt", "  {{value}}\t tail\n"),
            Map.entry("prompt/standalone.prompt", "Before\n  {{#if flag}}\nYes\n  {{else}}\nNo\n  {{/if}}\nAfter\n"));

    private static String render(String resource, Map<String, ?> model) {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        ClassLoader fixtures = new ClassLoader(previous) {
            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                String source = TEMPLATES.get(name);
                return source == null ? super.getResourceAsStream(name)
                        : new java.io.ByteArrayInputStream(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        };
        Thread.currentThread().setContextClassLoader(fixtures);
        try {
            return PromptRenderer.render(resource, model);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static final String BASIC = "prompt/render-basic.prompt";

    private static final String BRANCH_VARIABLES = "prompt/render-branch-variables.prompt";

    private static final String IF_NO_ELSE = "prompt/render-if-no-else.prompt";

    private static final String FOR = "prompt/render-for.prompt";

    private static GenerationSdkException renderError(String resource, Map<String, ?> model) {
        return assertThrows(GenerationSdkException.class, () -> render(resource, model));
    }

    @Test
    void standaloneControlLinesDoNotAddOutputWhitespace() {
        assertEquals("Before\nYes\nAfter\n", render("prompt/standalone.prompt", Map.of("flag", true)));
        assertEquals("Before\nNo\nAfter\n", render("prompt/standalone.prompt", Map.of("flag", false)));
    }

    @Test
    void rendersIfAndElseBranches() {
        assertEquals("Hello, Ada!\nDetails for Ada: 42 rows\nDone.\n", render(BASIC,
                Map.of("userName", "Ada", "showDetails", true, "detailText", "42 rows")));
        assertEquals("Hello, Ada!\nNo details.\nDone.\n",
                render(BASIC, Map.of("userName", "Ada", "showDetails", false)));
    }

    @Test
    void onlySelectedBranchVariablesAreRequired() {
        assertEquals("true branch", render(BRANCH_VARIABLES,
                Map.of("flag", true, "whenTrue", "true branch")));
        assertEquals("false branch", render(BRANCH_VARIABLES,
                Map.of("flag", false, "whenFalse", "false branch")));
    }

    @Test
    void ifWithoutElseRendersNothingWhenFalse() {
        assertEquals("AC", render(IF_NO_ELSE, Map.of("flag", false)));
        assertEquals("ABC", render(IF_NO_ELSE, Map.of("flag", true)));
    }

    @Test
    void rendersNestedIfBlocksRecursively() {
        String resource = "prompt/render-nested-if.prompt";

        assertEquals("both", render(resource, Map.of("outer", true, "inner", true)));
        assertEquals("outer only", render(resource, Map.of("outer", true, "inner", false)));
        assertEquals("none", render(resource, Map.of("outer", false)));
    }

    @Test
    void insertsValuesVerbatimOnceWithoutEscapingOrRecursiveExpansion() {
        String value = "<b>&\"{{inner}}</b>";

        assertEquals("value=" + value + " again=" + value,
                render("prompt/render-verbatim.prompt", Map.of("value", value)));
    }

    @Test
    void preservesWhitespaceAroundVariables() {
        assertEquals("  X\t tail\n",
                render("prompt/render-whitespace.prompt", Map.of("value", "X")));
    }

    @Test
    void normalizesCrlfToLf() {
        String rendered = render("prompt/render-crlf.prompt", Map.of("name", "Ada", "flag", true));

        assertEquals("line one Ada\non\nend\n", rendered);
        assertFalse(rendered.contains("\r"));
    }

    @Test
    void decodesUtf8Resources() {
        assertEquals("你好，世界！\n生成结果：3 条\n", render("prompt/render-utf8.prompt",
                Map.of("name", "世界", "count", 3)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cachesParsedTemplatesPerResource() throws ReflectiveOperationException {
        Field cacheField = PromptRenderer.class.getDeclaredField("CACHE");
        cacheField.setAccessible(true);
        Map<String, Object> cache = (Map<String, Object>) cacheField.get(null);
        String resource = "prompt/render-cached.prompt";
        cache.remove(resource);

        assertEquals("cached one", render(resource, Map.of("value", "one")));
        Object parsed = cache.get(resource);
        int size = cache.size();

        assertEquals("cached two", render(resource, Map.of("value", "two")));
        assertSame(parsed, cache.get(resource));
        assertEquals(size, cache.size());
    }

    @Test
    void rendersForBlocksOverMapItems() {
        assertEquals("Rows:\nA=1\nB=2\nDone\n", render(FOR, Map.of("rows",
                List.of(Map.of("name", "A", "value", "1"), Map.of("name", "B", "value", "2")))));
    }

    @Test
    void standaloneForControlLinesDoNotAddOutputWhitespace() {
        List<Map<String, String>> rows = List.of(Map.of("name", "a"), Map.of("name", "b"));

        assertEquals("Before\n- a\n- b\nAfter\n",
                render("prompt/render-for-standalone.prompt", Map.of("rows", rows)));
    }

    @Test
    void rendersNothingForNullOrEmptyCollections() {
        assertEquals("Rows:\nDone\n", render(FOR, Map.of("rows", List.of())));
        assertEquals("Before\nAfter\n",
                render("prompt/render-for-standalone.prompt", Map.of("rows", List.of())));

        Map<String, Object> nullModel = new HashMap<>();
        nullModel.put("rows", null);
        assertEquals("Rows:\nDone\n", render(FOR, nullModel));
    }

    @Test
    void resolvesForVariablesFromItemThenOuterModel() {
        List<Map<String, String>> rows = List.of(Map.of("name", "itemName"), Map.of());

        assertEquals("itemName/O\nouterName/O\n",
                render("prompt/render-for-scope.prompt", Map.of("rows", rows, "outer", "O", "name", "outerName")));
    }

    @Test
    void rendersConditionsInsideForBlocks() {
        List<Map<String, Object>> rows = List.of(Map.of("flag", true, "label", "a"),
                Map.of("flag", false, "label", "b"));

        assertEquals("on:a\noff:b\n", render("prompt/render-for-if.prompt", Map.of("rows", rows)));
    }

    @Test
    void rendersForBlocksInsideIfBlocks() {
        List<Map<String, String>> rows = List.of(Map.of("name", "a"), Map.of("name", "b"));
        String resource = "prompt/render-for-in-if.prompt";

        assertEquals("[a,b,]", render(resource, Map.of("show", true, "rows", rows)));
        assertEquals("", render(resource, Map.of("show", false)));
    }

    @Test
    void rendersIfAndForChainsRecursively() {
        List<Map<String, Object>> history = List.of(Map.of("raw", true, "value", "a"),
                Map.of("raw", false, "value", "b"));

        assertEquals("raw:a\nshown:b\n",
                render("prompt/render-if-for-if.prompt", Map.of("enabled", true, "history", history)));
        assertEquals("", render("prompt/render-if-for-if.prompt", Map.of("enabled", false)));
    }

    @Test
    void keepsInsertedValuesVerbatimInsideFor() {
        String value = "<b>{{inner}}</b>";

        assertEquals(value + value,
                render("prompt/render-for-verbatim.prompt", Map.of("rows", List.of(Map.of("value", value),
                        Map.of("value", value)))));
    }

    @Test
    void rejectsNestedForBlocks() {
        GenerationSdkException error = renderError("prompt/error-for-nested.prompt", Map.of());

        assertTrue(error.getMessage().contains("nested #for"));
    }

    @Test
    void reportsUnclosedForAtOpeningPosition() {
        GenerationSdkException error = renderError("prompt/error-for-unclosed.prompt", Map.of());

        assertTrue(error.getMessage().contains("line 2, column 1"));
        assertTrue(error.getMessage().contains("unclosed"));
        assertTrue(error.getMessage().contains("rows"));
    }

    @Test
    void rejectsUnsupportedForValuesAndNonMapItems() {
        String typeResource = "prompt/error-for-type.prompt";

        GenerationSdkException notCollection = renderError(typeResource, Map.of("rows", "text"));
        assertTrue(notCollection.getMessage().contains("must be a Collection"));
        assertTrue(notCollection.getMessage().contains("java.lang.String"));

        GenerationSdkException nonMapItem = renderError(typeResource, Map.of("rows", List.of("x")));
        assertTrue(nonMapItem.getMessage().contains("must be Map<String, ?>"));
        assertTrue(nonMapItem.getMessage().contains("java.lang.String"));

        GenerationSdkException nonStringKey = renderError(typeResource, Map.of("rows", List.of(Map.of(1, "x"))));
        assertTrue(nonStringKey.getMessage().contains("key of type java.lang.Integer"));

        GenerationSdkException nullItem = renderError(typeResource, Map.of("rows", Arrays.asList((Object) null)));
        assertTrue(nullItem.getMessage().contains("must be Map<String, ?> but was null"));
    }

    @Test
    void reportsMissingForAndItemVariables() {
        assertTrue(renderError(FOR, Map.of()).getMessage().contains("missing variable 'rows'"));

        assertTrue(renderError("prompt/render-for-if.prompt", Map.of("rows", List.of(Map.of("flag", true))))
                .getMessage().contains("missing variable 'label'"));
    }

    @Test
    void reportsUnknownOpenTagWithResourcePositionAndTag() {
        GenerationSdkException error = renderError("prompt/error-unknown-tag.prompt", Map.of());

        assertTrue(error.getMessage().contains("prompt/error-unknown-tag.prompt"));
        assertTrue(error.getMessage().contains("line 2, column 1"));
        assertTrue(error.getMessage().contains("unknown tag"));
        assertTrue(error.getMessage().contains("#each"));
    }

    @Test
    void reportsInvalidVariableTag() {
        GenerationSdkException error = renderError("prompt/error-invalid-tag.prompt", Map.of());

        assertTrue(error.getMessage().contains("line 1, column 1"));
        assertTrue(error.getMessage().contains("invalid tag"));
        assertTrue(error.getMessage().contains("user.name"));
    }

    @Test
    void reportsUnclosedTag() {
        GenerationSdkException error = renderError("prompt/error-unclosed-tag.prompt", Map.of());

        assertTrue(error.getMessage().contains("line 2, column 1"));
        assertTrue(error.getMessage().contains("unclosed tag"));
    }

    @Test
    void reportsUnclosedIfAtOpeningPosition() {
        GenerationSdkException error = renderError("prompt/error-unclosed-if.prompt", Map.of());

        assertTrue(error.getMessage().contains("line 2, column 1"));
        assertTrue(error.getMessage().contains("unclosed"));
        assertTrue(error.getMessage().contains("flag"));
    }

    @Test
    void parsesWholeTemplateBeforeRendering() {
        GenerationSdkException error = renderError("prompt/error-parse-before-render.prompt", Map.of());

        assertTrue(error.getMessage().contains("unclosed"));
        assertFalse(error.getMessage().contains("missing variable"));
    }

    @Test
    void rejectsOrphanElseOrphanCloseAndDuplicateElse() {
        assertTrue(renderError("prompt/error-else-orphan.prompt", Map.of()).getMessage()
                .contains("{{else}} without an open {{#if}}"));
        assertTrue(renderError("prompt/error-close-orphan.prompt", Map.of()).getMessage()
                .contains("{{/if}} without an open {{#if}}"));
        assertTrue(renderError("prompt/error-else-duplicate.prompt", Map.of()).getMessage()
                .contains("duplicate {{else}}"));
        assertTrue(renderError("prompt/error-for-orphan.prompt", Map.of()).getMessage()
                .contains("{{/for}} without an open {{#for}}"));
    }

    @Test
    void rejectsEmptyTagsInvalidConditionsAndUnknownClosingTags() {
        assertTrue(renderError("prompt/error-empty-tag.prompt", Map.of()).getMessage().contains("empty tag"));
        assertTrue(renderError("prompt/error-invalid-condition.prompt", Map.of()).getMessage()
                .contains("invalid #if condition"));
        assertTrue(renderError("prompt/error-invalid-for.prompt", Map.of()).getMessage()
                .contains("invalid #for variable"));
        assertTrue(renderError("prompt/error-unknown-close.prompt", Map.of()).getMessage()
                .contains("unknown closing tag"));
    }

    @Test
    void reportsMissingAndNullVariablesByName() {
        GenerationSdkException missing = renderError(BASIC, Map.of("showDetails", false));
        assertTrue(missing.getMessage().contains("missing variable 'userName'"));

        Map<String, Object> model = new HashMap<>();
        model.put("userName", null);
        model.put("showDetails", false);
        GenerationSdkException nullValue = renderError(BASIC, model);
        assertTrue(nullValue.getMessage().contains("variable 'userName' is null"));
    }

    @Test
    void reportsMissingNullAndNonBooleanConditionsByName() {
        assertTrue(renderError(IF_NO_ELSE, Map.of()).getMessage().contains("missing variable 'flag'"));

        Map<String, Object> nullModel = new HashMap<>();
        nullModel.put("flag", null);
        assertEquals("AC", render(IF_NO_ELSE, nullModel));
        for (Object empty : List.of("", "  ", List.of(), Map.of())) {
            assertEquals("AC", render(IF_NO_ELSE, Map.of("flag", empty)));
        }
        for (Object present : List.of("yes", List.of("item"), Map.of("key", "value"))) {
            assertEquals("ABC", render(IF_NO_ELSE, Map.of("flag", present)));
        }
        GenerationSdkException unsupported = renderError(IF_NO_ELSE, Map.of("flag", 42));
        assertTrue(unsupported.getMessage().contains("condition 'flag'"));
        assertTrue(unsupported.getMessage().contains("java.lang.Integer"));
    }

    @Test
    void reportsMissingResource() {
        GenerationSdkException error = renderError("prompt/does-not-exist.prompt", Map.of());

        assertTrue(error.getMessage().contains("prompt/does-not-exist.prompt"));
        assertTrue(error.getMessage().contains("not found"));
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> render(null, Map.of()));
        assertThrows(NullPointerException.class, () -> render(BASIC, null));
    }
}
