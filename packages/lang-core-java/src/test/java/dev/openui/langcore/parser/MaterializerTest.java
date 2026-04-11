package dev.openui.langcore.parser;

import dev.openui.langcore.lexer.Lexer;
import dev.openui.langcore.parser.ast.ElementNode;
import dev.openui.langcore.parser.ast.LiteralNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Materializer}.
 * Covers ValidationError codes, hasDynamicProps, root selection, orphaned GC,
 * stateDeclarations implicit null, meta.incomplete, queryStatements, and mutationStatements.
 * Ref: Req 4.*, 5.*, 6.*
 */
class MaterializerTest {

    private static final String SCHEMA = """
        {
          "$defs": {
            "Button": {
              "properties": {
                "label": { "type": "string" },
                "disabled": { "type": "boolean" }
              },
              "required": ["label"]
            },
            "Card": {
              "properties": {
                "title": { "type": "string" },
                "body": { "type": "string" }
              },
              "required": ["title", "body"]
            },
            "Box": {
              "properties": {
                "size": { "type": "number", "default": 10 }
              },
              "required": ["size"]
            }
          }
        }
        """;

    private ParseResult parse(String src) {
        SchemaRegistry reg = SchemaRegistry.fromJson(SCHEMA);
        List<dev.openui.langcore.lexer.Token> tokens = new Lexer(src).tokenize();
        LinkedHashMap<String, Statement> stmts = new StatementParser().parse(tokens);
        return new Materializer(reg, false).materialize(stmts);
    }

    private ParseResult parseWithRoot(String src, String rootName) {
        SchemaRegistry reg = SchemaRegistry.fromJson(SCHEMA);
        List<dev.openui.langcore.lexer.Token> tokens = new Lexer(src).tokenize();
        LinkedHashMap<String, Statement> stmts = new StatementParser().parse(tokens);
        return new Materializer(reg, false, rootName).materialize(stmts);
    }

    // -------------------------------------------------------------------------
    // ValidationError codes (Req 4 AC3-7)
    // -------------------------------------------------------------------------

    /** AC3: excess-args — extra arg beyond prop count is dropped; component is still valid */
    @Test
    void excessArgs_errorRecorded() {
        ParseResult result = parse("root = Card(\"title\", \"body\", \"extra\")");
        List<ValidationError> errors = result.meta().errors();
        assertEquals(1, errors.size());
        assertEquals("excess-args", errors.get(0).code());
        assertEquals("Card", errors.get(0).component());
        // Root still resolves because excess-args is a warning, not fatal
        assertNotNull(result.root());
        assertEquals("Card", result.root().typeName());
    }

    /** AC4: missing-required — required prop absent with no default → root null */
    @Test
    void missingRequired_errorRecorded_rootNull() {
        // Card requires both "title" and "body"; supply only "title"
        ParseResult result = parse("root = Card(\"title\")");
        List<ValidationError> errors = result.meta().errors();
        assertTrue(errors.stream().anyMatch(e -> "missing-required".equals(e.code())),
                "Expected missing-required error");
        assertNull(result.root(), "Root should be null when required prop is missing");
    }

    /** AC5: null-required — required prop explicitly null → root null */
    @Test
    void nullRequired_errorRecorded_rootNull() {
        ParseResult result = parse("root = Card(null, null)");
        List<ValidationError> errors = result.meta().errors();
        assertTrue(errors.stream().anyMatch(e -> "null-required".equals(e.code())),
                "Expected null-required error");
        assertNull(result.root(), "Root should be null when required prop is null");
    }

    /** AC6: unknown-component → error recorded, root null */
    @Test
    void unknownComponent_errorRecorded_rootNull() {
        ParseResult result = parse("root = Unknown(\"x\")");
        List<ValidationError> errors = result.meta().errors();
        assertEquals(1, errors.size());
        assertEquals("unknown-component", errors.get(0).code());
        assertNull(result.root());
    }

    /** AC7: inline-reserved — Query used as prop value → inline-reserved error */
    @Test
    void inlineReserved_errorRecorded() {
        // Box has "size" as first prop; we pass Query(...) as prop value
        ParseResult result = parse("root = Box(Query(\"tool\"))");
        List<ValidationError> errors = result.meta().errors();
        assertTrue(errors.stream().anyMatch(e -> "inline-reserved".equals(e.code())),
                "Expected inline-reserved error for Query used as prop value");
    }

    /** AC4 variant: default value used when required arg is absent */
    @Test
    void defaultValue_usedWhenArgMissing() {
        // Box has size (required, default=10); call with no args
        ParseResult result = parse("root = Box()");
        assertNotNull(result.root(), "Root should not be null when default is available");
        assertEquals("Box", result.root().typeName());
        LiteralNode sizeNode = (LiteralNode) result.root().props().get("size");
        assertNotNull(sizeNode);
        assertEquals(10.0, sizeNode.value());
    }

    // -------------------------------------------------------------------------
    // hasDynamicProps (Req 4 AC8)
    // -------------------------------------------------------------------------

    /** All literal args → hasDynamicProps == false */
    @Test
    void hasDynamicProps_false_allLiterals() {
        ParseResult result = parse("root = Button(\"click\")");
        assertNotNull(result.root());
        assertFalse(result.root().hasDynamicProps());
    }

    /** StateRef arg → hasDynamicProps == true */
    @Test
    void hasDynamicProps_true_withStateRef() {
        ParseResult result = parse("root = Button($label)");
        assertNotNull(result.root());
        assertTrue(result.root().hasDynamicProps());
    }

    /** RefNode arg (another statement used as value) → hasDynamicProps == true */
    @Test
    void hasDynamicProps_true_withRef() {
        ParseResult result = parse("label = \"click\"\nroot = Button(label)");
        assertNotNull(result.root());
        assertTrue(result.root().hasDynamicProps());
    }

    // -------------------------------------------------------------------------
    // Root selection (Req 6 AC1-6)
    // -------------------------------------------------------------------------

    /** AC1: statement named "root" is selected */
    @Test
    void rootSelection_ac1_namedRoot() {
        ParseResult result = parse("other = Card(\"t\", \"b\")\nroot = Button(\"click\")");
        assertNotNull(result.root());
        assertEquals("Button", result.root().typeName());
    }

    /** AC2: no "root" statement, but rootName passed and that statement exists */
    @Test
    void rootSelection_ac2_rootName() {
        ParseResult result = parseWithRoot("myRoot = Button(\"click\")\nother = Card(\"t\", \"b\")", "myRoot");
        assertNotNull(result.root());
        assertEquals("Button", result.root().typeName());
    }

    /** AC4: no "root" and no rootName; first ValueStatement with ElementNode is selected */
    @Test
    void rootSelection_ac4_firstComponentCall() {
        // No statement named "root", no rootName override
        ParseResult result = parse("btn = Button(\"click\")");
        assertNotNull(result.root());
        assertEquals("Button", result.root().typeName());
    }

    /** AC5: no component calls at all; first statement is selected (root may be null if expr is not ElementNode) */
    @Test
    void rootSelection_ac5_firstStatement() {
        ParseResult result = parse("x = 1");
        // The first statement is selected as rootId, but its expr is a LiteralNode (not ElementNode)
        // so root stays null per the implementation
        assertNull(result.root());
        assertEquals(1, result.meta().statementCount());
    }

    /** AC6: empty statements → root null */
    @Test
    void rootSelection_ac6_noStatementsRootNull() {
        ParseResult result = parse("");
        assertNull(result.root());
    }

    // -------------------------------------------------------------------------
    // Orphaned GC (Req 5 AC5)
    // -------------------------------------------------------------------------

    /** Unreachable statement (not referenced from root) is orphaned */
    @Test
    void orphaned_unreachableStatement() {
        ParseResult result = parse("root = Button(\"click\")\nunused = Card(\"t\", \"b\")");
        assertNotNull(result.root());
        assertTrue(result.meta().orphaned().contains("unused"),
                "Expected 'unused' in orphaned list");
        assertFalse(result.meta().orphaned().contains("root"),
                "Root should not be orphaned");
    }

    /** Statement only reachable via root is not orphaned; unreferenced one is */
    @Test
    void orphaned_unreferencedSecondStatement() {
        ParseResult result = parse("root = Button(\"click\")\nother = Button(\"other\")");
        assertTrue(result.meta().orphaned().contains("other"),
                "Expected 'other' to be orphaned since root does not reference it");
    }

    // -------------------------------------------------------------------------
    // stateDeclarations implicit null (Req 5 AC8)
    // -------------------------------------------------------------------------

    /** Explicitly declared $count → stateDeclarations contains "$count" with value 0.0 */
    @Test
    void stateDeclarations_explicitDeclaration() {
        ParseResult result = parse("$count = 0\nroot = Button(\"click\")");
        assertTrue(result.stateDeclarations().containsKey("$count"),
                "Expected $count in stateDeclarations");
        assertEquals(0.0, result.stateDeclarations().get("$count"));
    }

    /** Undeclared $label referenced in root → stateDeclarations contains "$label" with null */
    @Test
    void stateDeclarations_implicitNull() {
        ParseResult result = parse("root = Button($label)");
        assertTrue(result.stateDeclarations().containsKey("$label"),
                "Expected implicit $label in stateDeclarations");
        assertNull(result.stateDeclarations().get("$label"),
                "Implicit state ref should have null default value");
    }

    // -------------------------------------------------------------------------
    // meta.incomplete (Req 5 AC3)
    // -------------------------------------------------------------------------

    /** wasIncomplete=true → meta.incomplete == true */
    @Test
    void metaIncomplete_trueWhenFlagSet() {
        SchemaRegistry reg = SchemaRegistry.fromJson(SCHEMA);
        List<dev.openui.langcore.lexer.Token> tokens = new Lexer("root = Button(\"click\")").tokenize();
        LinkedHashMap<String, Statement> stmts = new StatementParser().parse(tokens);
        ParseResult result = new Materializer(reg, true).materialize(stmts);
        assertTrue(result.meta().incomplete());
    }

    // -------------------------------------------------------------------------
    // queryStatements / mutationStatements (Req 5 AC9-10)
    // -------------------------------------------------------------------------

    /** Query statement is extracted into queryStatements */
    @Test
    void queryStatements_extracted() {
        ParseResult result = parse("data = Query(\"myTool\", {})\nroot = Button(\"click\")");
        assertEquals(1, result.queryStatements().size());
        assertEquals("data", result.queryStatements().get(0).statementId());
    }

    /** Mutation statement is extracted into mutationStatements */
    @Test
    void mutationStatements_extracted() {
        ParseResult result = parse("save = Mutation(\"saveTool\", {})\nroot = Button(\"click\")");
        assertEquals(1, result.mutationStatements().size());
        assertEquals("save", result.mutationStatements().get(0).statementId());
    }
}
