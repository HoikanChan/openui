/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Prompt resources load as UTF-8, cache per resource, and substitute variables exactly once.
 *
 * @since 2026
 */
class PromptResourcesTest {

    private static final String VARIABLES = "repair-prompts/variables.txt";

    @Test
    void loadsChineseAndRepeatedPlaceholdersFromUtf8() {
        assertEquals("中文 {{name}} | {{other}} | {{name}}\n", PromptResources.load(VARIABLES));
    }

    @Test
    void cachesOneInstancePerResourceName() {
        assertSame(PromptResources.load(VARIABLES), PromptResources.load(VARIABLES));
        assertEquals("中文 a | b | a\n", PromptResources.render(VARIABLES, Map.of("name", "a", "other", "b")));
    }

    @Test
    void replacesEveryOccurrenceOfTheSameName() {
        assertEquals("中文 甲 | 乙 | 甲\n", PromptResources.render(VARIABLES, Map.of("name", "甲", "other", "乙")));
    }

    @Test
    void insertedValuesAreLiteralAndNeverReexpanded() {
        Map<String, String> variables = new HashMap<>();
        variables.put("name", "{{other}} <b>&$1\\");
        variables.put("other", "leaked");
        assertEquals("中文 {{other}} <b>&$1\\ | leaked | {{other}} <b>&$1\\\n",
                PromptResources.render(VARIABLES, variables));
    }

    @Test
    void preservesDslFragmentsAndBackslashes() {
        String value = "root = Stack([\"中文\", @Each(data.items, \"row\", {cell: @Render(row.x)} )])";
        assertEquals("中文 " + value + " | x | " + value + "\n",
                PromptResources.render(VARIABLES, Map.of("name", value, "other", "x")));
    }

    @Test
    void missingResourceNamesTheResource() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PromptResources.load("repair-prompts/absent.txt"));
        assertTrue(error.getMessage().contains("repair-prompts/absent.txt"), error.getMessage());
    }

    @Test
    void missingOrNullVariableNamesResourceAndVariable() {
        IllegalArgumentException absent = assertThrows(IllegalArgumentException.class,
                () -> PromptResources.render(VARIABLES, Map.of("name", "a")));
        assertTrue(absent.getMessage().contains(VARIABLES), absent.getMessage());
        assertTrue(absent.getMessage().contains("other"), absent.getMessage());

        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("name", "a");
        nullValue.put("other", null);
        IllegalArgumentException nulled = assertThrows(IllegalArgumentException.class,
                () -> PromptResources.render(VARIABLES, nullValue));
        assertTrue(nulled.getMessage().contains(VARIABLES), nulled.getMessage());
        assertTrue(nulled.getMessage().contains("other"), nulled.getMessage());

        IllegalArgumentException noVariables = assertThrows(IllegalArgumentException.class,
                () -> PromptResources.render(VARIABLES, null));
        assertTrue(noVariables.getMessage().contains("name"), noVariables.getMessage());
    }
}
