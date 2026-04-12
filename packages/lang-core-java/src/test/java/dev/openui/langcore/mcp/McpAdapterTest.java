package dev.openui.langcore.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link McpAdapter}.
 *
 * Ref: Design §16, Task 18.3
 */
class McpAdapterTest {

    // -------------------------------------------------------------------------
    // extractToolResult — structuredContent
    // -------------------------------------------------------------------------

    @Test
    void extractToolResult_structuredContent_returnsIt() {
        Map<String, Object> structured = Map.of("key", "value");
        McpClientLike.McpResult result = new McpClientLike.McpResult(
                List.of(), structured, false);

        Object actual = McpAdapter.extractToolResult(result);
        assertSame(structured, actual);
    }

    @Test
    void extractToolResult_structuredContent_takesPreferenceOverText() {
        Map<String, Object> structured = Map.of("answer", 42);
        McpClientLike.McpResult result = new McpClientLike.McpResult(
                List.of(new McpClientLike.McpContentItem("text", "ignored")),
                structured, false);

        Object actual = McpAdapter.extractToolResult(result);
        assertSame(structured, actual);
    }

    // -------------------------------------------------------------------------
    // extractToolResult — text content (JSON)
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void extractToolResult_textJson_parsedToMap() {
        McpClientLike.McpResult result = new McpClientLike.McpResult(
                List.of(new McpClientLike.McpContentItem("text", "{\"foo\":\"bar\"}")),
                null, false);

        Object actual = McpAdapter.extractToolResult(result);
        assertInstanceOf(Map.class, actual);
        assertEquals("bar", ((Map<String, Object>) actual).get("foo"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void extractToolResult_textJsonArray_parsedToList() {
        McpClientLike.McpResult result = new McpClientLike.McpResult(
                List.of(new McpClientLike.McpContentItem("text", "[1,2,3]")),
                null, false);

        Object actual = McpAdapter.extractToolResult(result);
        assertInstanceOf(List.class, actual);
        List<Object> list = (List<Object>) actual;
        assertEquals(3, list.size());
    }

    // -------------------------------------------------------------------------
    // extractToolResult — text content (plain string)
    // -------------------------------------------------------------------------

    @Test
    void extractToolResult_textPlainString_returnedAsIs() {
        McpClientLike.McpResult result = new McpClientLike.McpResult(
                List.of(new McpClientLike.McpContentItem("text", "hello world")),
                null, false);

        Object actual = McpAdapter.extractToolResult(result);
        assertEquals("hello world", actual);
    }

    @Test
    void extractToolResult_emptyContent_returnsEmptyString() {
        McpClientLike.McpResult result = new McpClientLike.McpResult(
                List.of(), null, false);

        Object actual = McpAdapter.extractToolResult(result);
        assertEquals("", actual);
    }

    // -------------------------------------------------------------------------
    // extractToolResult — isError
    // -------------------------------------------------------------------------

    @Test
    void extractToolResult_isError_throwsMcpToolError() {
        McpClientLike.McpResult result = new McpClientLike.McpResult(
                List.of(new McpClientLike.McpContentItem("text", "something went wrong")),
                null, true);

        McpToolError ex = assertThrows(McpToolError.class,
                () -> McpAdapter.extractToolResult(result));
        assertTrue(ex.getMessage().contains("something went wrong"));
    }

    @Test
    void extractToolResult_isError_noContent_defaultMessage() {
        McpClientLike.McpResult result = new McpClientLike.McpResult(
                List.of(), null, true);

        McpToolError ex = assertThrows(McpToolError.class,
                () -> McpAdapter.extractToolResult(result));
        assertNotNull(ex.getMessage());
        assertFalse(ex.getMessage().isBlank());
    }

    // -------------------------------------------------------------------------
    // callTool — delegates and unwraps via extractToolResult
    // -------------------------------------------------------------------------

    @Test
    void callTool_delegatesAndUnwraps() throws Exception {
        Map<String, Object> structured = Map.of("result", "ok");
        McpClientLike stubClient = params ->
                CompletableFuture.completedFuture(
                        new McpClientLike.McpResult(List.of(), structured, false));

        McpAdapter adapter = new McpAdapter(stubClient);
        Object result = adapter.callTool("myTool", Map.of("x", 1)).get();
        assertSame(structured, result);
    }

    @Test
    void callTool_propagatesError() {
        McpClientLike stubClient = params ->
                CompletableFuture.completedFuture(
                        new McpClientLike.McpResult(
                                List.of(new McpClientLike.McpContentItem("text", "boom")),
                                null, true));

        McpAdapter adapter = new McpAdapter(stubClient);
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> adapter.callTool("badTool", Map.of()).get());
        assertInstanceOf(McpToolError.class, ex.getCause());
    }
}
