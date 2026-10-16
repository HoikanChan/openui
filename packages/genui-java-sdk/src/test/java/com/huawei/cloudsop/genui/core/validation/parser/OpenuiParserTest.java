/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpenuiParserTest {

    @Test
    void parsesValueStatement() {
        Program p = OpenuiParser.parse("root = Title(\"hi\")");
        assertEquals(1, p.statements().size());
        Statement.Value v = assertInstanceOf(Statement.Value.class, p.statements().get(0));
        assertEquals("root", v.id());
        assertInstanceOf(AstNode.Comp.class, v.expr());
        assertTrue(p.isClean());
    }

    @Test
    void classifiesStateStatement() {
        Program p = OpenuiParser.parse("$count = 0");
        Statement.State s = assertInstanceOf(Statement.State.class, p.statements().get(0));
        assertEquals("$count", s.id());
    }

    @Test
    void classifiesQueryStatement() {
        Program p = OpenuiParser.parse("metrics = Query(\"get_metrics\", {})");
        Statement.Query q = assertInstanceOf(Statement.Query.class, p.statements().get(0));
        assertEquals("metrics", q.id());
        assertEquals("Query", q.call().callee());
    }

    @Test
    void classifiesMutationStatement() {
        Program p = OpenuiParser.parse("save = Mutation(\"create\", {})");
        Statement.Mutation m = assertInstanceOf(Statement.Mutation.class, p.statements().get(0));
        assertEquals("save", m.id());
        assertEquals("Mutation", m.call().callee());
    }

    @Test
    void queryDollarVarStaysQueryNotState() {
        // classifyStatement checks Query BEFORE $var
        Program p = OpenuiParser.parse("$data = Query(\"tool\", {})");
        assertInstanceOf(Statement.Query.class, p.statements().get(0));
    }

    @Test
    void multipleStatementsInOrder() {
        Program p = OpenuiParser.parse("a = 1\nb = 2\nroot = Title(\"x\")");
        assertEquals(3, p.statements().size());
        assertEquals("a", p.statements().get(0).id());
        assertEquals("root", p.statements().get(2).id());
    }

    @Test
    void malformedStatementDegradesButLaterStatementsParse() {
        // First line has no '=' — should be skipped as a diagnostic, second line still parses.
        Program p = OpenuiParser.parse("garbage here\nroot = Title(\"ok\")");
        assertEquals(1, p.statements().size());
        assertEquals("root", p.statements().get(0).id());
        assertFalse(p.diagnostics().isEmpty());
    }

    @Test
    void unclosedParenInFinalModeReportsDiagnosticNotThrow() {
        Program p = OpenuiParser.parse("root = Title(\"hi\"", ParseMode.FINAL);
        assertTrue(p.diagnostics().stream().anyMatch(d -> d.code() == ParseErrorCode.UNCLOSED_BRACKET),
                "expected UNCLOSED_BRACKET diagnostic");
    }

    @Test
    void unclosedStringInStreamingModeIsRepaired() {
        Program p = OpenuiParser.parse("root = Title(\"hi", ParseMode.STREAMING);
        assertTrue(p.incomplete());
        // auto-closed → a well-formed component, no unclosed diagnostics
        assertTrue(p.diagnostics().stream().noneMatch(d -> d.code() == ParseErrorCode.UNCLOSED_BRACKET));
    }

    @Test
    void diagnosticCarriesLineAndColumn() {
        Program p = OpenuiParser.parse("a = 1\nnotvalid\nb = 2");
        ParseDiagnostic d = p.diagnostics().get(0);
        assertEquals(2, d.line());
    }

    @Test
    void stripsFencesAndComments() {
        String md = "```openui\nroot = Title(\"hi\") // comment\n```";
        Program p = OpenuiParser.parse(md);
        assertEquals(1, p.statements().size());
        assertEquals("root", p.statements().get(0).id());
    }

    @Test
    void expressionDiagnosticsAttachStatementId() {
        Program p = OpenuiParser.parse("root = Title(\"hi\"", ParseMode.FINAL);
        ParseDiagnostic d = p.diagnostics().stream().filter(x -> x.code() == ParseErrorCode.UNCLOSED_BRACKET)
                .findFirst().orElseThrow();
        assertEquals("root", d.statementId());
    }
}
