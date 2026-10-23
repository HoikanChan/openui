/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** #58: error upstream/downstream association follows the AST and lexical binder scope, not text matching. */
class StatementRelationsTest {

    private static ValidationIssue issueOn(String statementId) {
        return new ValidationIssue("unknown-component", ValidationSeverity.ERROR, "contract", "bad", statementId,
                "Bogus", "", -1, -1, null, true);
    }

    @Test
    void reportsTransitiveUpstreamAndDownstream() {
        String dsl = "root = Stack([header])\nheader = Stack([title])\ntitle = TextContent(\"Hi\")";
        Map<String, List<String>> related = StatementRelations.related(dsl, List.of(issueOn("header")));

        List<String> ids = related.get("header");
        assertTrue(ids.contains("title"), "upstream definition is related: " + ids);
        assertTrue(ids.contains("root"), "downstream user is related: " + ids);
    }

    @Test
    void relatedMapKeepsDiagnosticOrderAndIsUnmodifiable() {
        String dsl = "root = Stack([a, b])\na = TextContent(\"A\")\nb = TextContent(\"B\")";
        Map<String, List<String>> related = StatementRelations.related(dsl, List.of(issueOn("b"), issueOn("a")));

        assertEquals(List.of("b", "a"), List.copyOf(related.keySet()), "diagnosed ids keep the diagnostic list order");
        assertThrows(UnsupportedOperationException.class, () -> related.put("x", List.of()), "result is immutable");
    }

    @Test
    void eachBinderDoesNotCountAsStatementDependency() {
        String dsl = "items = @Each(data.rows, title, TextContent(title))\ntitle = TextContent(\"unrelated\")\n"
                + "root = Stack([items])";
        Map<String, List<String>> related = StatementRelations.related(dsl, List.of(issueOn("items")));

        List<String> ids = related.get("items");
        assertFalse(ids.contains("title"), "binder name must be scoped, not resolved to a same-name statement: " + ids);
        assertTrue(ids.contains("root"), "downstream user is still related: " + ids);
    }
    @Test
    void extractedTemplatesInheritBindingsAcrossTransitiveDefinitions() {
        String dsl = "root = Stack([items])\nitems = @Each(data.rows, row, body)\n"
                + "body = @Render(value, cell)\ncell = TextContent(row.name + value)\n"
                + "row = TextContent(\"unrelated row\")\nvalue = TextContent(\"unrelated value\")";
        List<String> ids = StatementRelations.related(dsl, List.of(issueOn("items"))).get("items");
        assertTrue(ids.containsAll(List.of("body", "cell", "root")), ids.toString());
        assertFalse(ids.contains("row"), "Each binder follows extracted templates: " + ids);
        assertFalse(ids.contains("value"), "Render call-time binder shadows global definition: " + ids);
    }

    @Test
    void extractedTemplateUsedAlsoOutsideScopeKeepsItsGlobalDependency() {
        String dsl = "root = Stack([items, body])\nitems = @Each(data.rows, row, body)\n"
                + "body = TextContent(row)\nrow = \"global\"";
        assertTrue(StatementRelations.related(dsl, List.of(issueOn("body"))).get("body").contains("row"));
        assertFalse(StatementRelations.related(dsl, List.of(issueOn("items"))).get("items").contains("row"),
                "another use of body must not leak its global dependencies into the scoped use");
    }

}
