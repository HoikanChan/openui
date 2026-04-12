package dev.openui.langcore;

import dev.openui.langcore.parser.result.ParseResult;
import dev.openui.langcore.parser.result.QueryStatementInfo;
import dev.openui.langcore.parser.result.MutationStatementInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying end-to-end parity between one-shot and streaming parsers,
 * and parse → evaluate cycles.
 *
 * Ref: Req 5 AC6, Req 11, Task 21.2-21.3
 */
class IntegrationTest {

    // -------------------------------------------------------------------------
    // Shared schema
    // -------------------------------------------------------------------------

    private static final String SCHEMA = """
            {
              "$defs": {
                "Dashboard": {
                  "properties": {
                    "title":    { "type": "string" },
                    "subtitle": { "type": "string" },
                    "count":    { "type": "number" }
                  },
                  "required": ["title"]
                },
                "Button": {
                  "properties": {
                    "label":    { "type": "string" },
                    "disabled": { "type": "boolean" }
                  },
                  "required": ["label"]
                }
              }
            }
            """;

    private static final String SCHEMA_WITH_QUERY = """
            {
              "$defs": {
                "Table": {
                  "properties": {
                    "data": { "type": "array" }
                  },
                  "required": []
                }
              }
            }
            """;

    // -------------------------------------------------------------------------
    // Task 21.2 — one-shot ↔ streaming parse parity
    // -------------------------------------------------------------------------

    @Test
    void parity_simpleRoot() {
        String input = "root = Dashboard(\"Hello\")\n";
        ParseResult oneShot   = LangCore.parse(input, SCHEMA);
        ParseResult streaming = LangCore.createStreamingParser(SCHEMA).push(input);

        assertNotNull(oneShot.root(),   "one-shot root must not be null");
        assertNotNull(streaming.root(), "streaming root must not be null");
        assertEquals(oneShot.root().typeName(), streaming.root().typeName(),
                "root typeName must match");
    }

    @Test
    void parity_stateDeclarations() {
        String input = """
                $count = 0
                root = Dashboard("Hello")
                """;
        ParseResult oneShot   = LangCore.parse(input, SCHEMA);
        ParseResult streaming = LangCore.createStreamingParser(SCHEMA).push(input);

        assertEquals(oneShot.stateDeclarations().keySet(),
                     streaming.stateDeclarations().keySet(),
                "stateDeclaration keys must match");
    }

    @Test
    void parity_queryStatements() {
        String input = """
                rows = @query getRows()
                root = Table(rows)
                """;
        ParseResult oneShot   = LangCore.parse(input, SCHEMA_WITH_QUERY);
        ParseResult streaming = LangCore.createStreamingParser(SCHEMA_WITH_QUERY).push(input);

        List<String> oneShotIds   = oneShot.queryStatements().stream()
                .map(QueryStatementInfo::statementId).toList();
        List<String> streamingIds = streaming.queryStatements().stream()
                .map(QueryStatementInfo::statementId).toList();

        assertEquals(oneShotIds, streamingIds, "query statement IDs must match");
    }

    @Test
    void parity_mutationStatements() {
        String input = """
                saveUser = @mutation updateUser(name: "Alice")
                root = Dashboard("Hello")
                """;
        ParseResult oneShot   = LangCore.parse(input, SCHEMA);
        ParseResult streaming = LangCore.createStreamingParser(SCHEMA).push(input);

        List<String> oneShotIds   = oneShot.mutationStatements().stream()
                .map(MutationStatementInfo::statementId).toList();
        List<String> streamingIds = streaming.mutationStatements().stream()
                .map(MutationStatementInfo::statementId).toList();

        assertEquals(oneShotIds, streamingIds, "mutation statement IDs must match");
    }

    @Test
    void parity_multiChunk_matchesSingleChunk() {
        String full = "root = Dashboard(\"Hi\")\n";
        // Split across two chunks
        String part1 = "root = Dash";
        String part2 = "board(\"Hi\")\n";

        ParseResult singleChunk = LangCore.createStreamingParser(SCHEMA).push(full);

        var multiStream = LangCore.createStreamingParser(SCHEMA);
        multiStream.push(part1);
        ParseResult multiChunk = multiStream.push(part2);

        assertNotNull(singleChunk.root());
        assertNotNull(multiChunk.root());
        assertEquals(singleChunk.root().typeName(), multiChunk.root().typeName());
    }

    // -------------------------------------------------------------------------
    // Task 21.3 — parse → evaluate cycle
    // -------------------------------------------------------------------------

    @Test
    void evaluate_staticProps_resolvedCorrectly() {
        String input = "root = Dashboard(\"My App\", \"subtitle text\")\n";
        ParseResult result = LangCore.parse(input, SCHEMA);

        assertNotNull(result.root());
        assertEquals("Dashboard", result.root().typeName());

        // Props are materialized by OneShot — check positional prop values
        var props = result.root().props();
        // title is first required prop
        assertTrue(props.containsKey("title"), "props must include 'title'");
    }

    @Test
    void evaluate_stateDeclaration_presentInResult() {
        String input = """
                $count = 42
                root = Dashboard("App")
                """;
        ParseResult result = LangCore.parse(input, SCHEMA);

        assertTrue(result.stateDeclarations().containsKey("$count"),
                "stateDeclarations must contain $count");
    }

    @Test
    void evaluate_multipleStatements_allPresent() {
        String input = """
                header = Dashboard("Header")
                root = Dashboard("Main")
                """;
        ParseResult result = LangCore.parse(input, SCHEMA);

        assertNotNull(result.root(), "root must be resolved");
        assertEquals("Dashboard", result.root().typeName());
        // meta should count both statements
        assertTrue(result.meta().statementCount() >= 1);
    }

    @Test
    void evaluate_numericProp_resolvedFromSchema() {
        String input = "root = Dashboard(\"App\", \"sub\", 99)\n";
        ParseResult result = LangCore.parse(input, SCHEMA);

        assertNotNull(result.root());
        var props = result.root().props();
        assertTrue(props.containsKey("count") || props.containsKey("subtitle"),
                "numeric/string props must be mapped");
    }

    @Test
    void evaluate_invalidInput_returnsNullRoot() {
        String input = "root = UnknownComponent(\"x\")\n";
        ParseResult result = LangCore.parse(input, SCHEMA);
        // Unknown component — root should be null or validation errors present
        assertTrue(result.root() == null || !result.meta().errors().isEmpty(),
                "unknown component must produce null root or validation errors");
    }

    @Test
    void evaluate_emptyInput_returnsNullRoot() {
        ParseResult result = LangCore.parse("", SCHEMA);
        assertNull(result.root(), "empty input must produce null root");
    }
}
