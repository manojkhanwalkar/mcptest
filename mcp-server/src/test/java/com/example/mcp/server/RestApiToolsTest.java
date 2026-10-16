package com.example.mcp.server;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Calls the tool handlers directly, with a {@link FakeRestApi} standing in for the api-server. */
class RestApiToolsTest {

    private final McpJsonMapper json = McpJsonDefaults.getMapper();
    private FakeRestApi fake;
    private RestApiTools tools;

    @BeforeEach
    void setUp() throws IOException {
        fake = new FakeRestApi();
        tools = new RestApiTools(new RestApiClient(fake.baseUrl()), json);
    }

    @AfterEach
    void tearDown() {
        fake.close();
    }

    @Test
    void exposesOneToolPerRestOperation() {
        List<String> names = tools.specifications().stream().map(s -> s.tool().name()).toList();

        assertEquals(List.of(
                "list_items", "get_item", "create_item", "update_item", "delete_item",
                "list_persons", "get_person", "create_person", "update_person", "delete_person"), names);
    }

    @Test
    void annotationsDescribeSideEffects() {
        Tool list = tool("list_items");
        assertTrue(list.annotations().readOnlyHint());
        assertFalse(list.annotations().destructiveHint());

        Tool create = tool("create_item");
        assertFalse(create.annotations().readOnlyHint());
        assertFalse(create.annotations().idempotentHint(), "POST creates a new resource each time");

        Tool delete = tool("delete_person");
        assertTrue(delete.annotations().destructiveHint());
        assertTrue(delete.annotations().idempotentHint());
    }

    @Test
    void inputSchemasMirrorApiRequiredFields() {
        assertEquals(List.of("name"), tool("create_item").inputSchema().get("required"));
        assertEquals(List.of("name", "email"), tool("create_person").inputSchema().get("required"));
        assertEquals(List.of("id", "name", "email"), tool("update_person").inputSchema().get("required"));
        assertEquals(List.of("id"), tool("get_item").inputSchema().get("required"));
        assertTrue(((Map<?, ?>) tool("update_person").inputSchema().get("properties")).keySet()
                .containsAll(List.of("id", "name", "email", "address", "phoneNumber")));
    }

    @Test
    void listItemsPassesArgumentsAsQueryAndReturnsBody() {
        fake.respondWith(200, "[{\"id\":1,\"name\":\"Laptop\"}]");

        CallToolResult result = call("list_items", Map.of("nameContains", "lap"));

        assertFalse(result.isError());
        assertEquals("[{\"id\":1,\"name\":\"Laptop\"}]", text(result));
        assertEquals("GET", fake.lastRequest().method());
        assertEquals("/api/v1/items", fake.lastRequest().path());
        assertEquals("nameContains=lap", fake.lastRequest().query());
    }

    @Test
    void idFillsPathAndIsNotSentInBody() throws IOException {
        fake.respondWith(200, "{\"id\":5,\"name\":\"New\"}");

        // JSON numbers may arrive as Integer, Long or Double; all must work.
        CallToolResult result = call("update_item", Map.of("id", 5.0, "name", "New", "price", 9.5));

        assertFalse(result.isError());
        FakeRestApi.Request request = fake.lastRequest();
        assertEquals("PUT", request.method());
        assertEquals("/api/v1/items/5", request.path());
        assertEquals(Map.of("name", "New", "price", 9.5), json.readValue(request.body(), Map.class));
    }

    @Test
    void createPersonPostsArgumentsAsJson() throws IOException {
        fake.respondWith(201, "{\"id\":1,\"name\":\"Ada\",\"email\":\"ada@example.com\"}");

        CallToolResult result = call("create_person", Map.of("name", "Ada", "email", "ada@example.com"));

        assertFalse(result.isError());
        assertEquals("/api/v1/persons", fake.lastRequest().path());
        assertEquals(Map.of("name", "Ada", "email", "ada@example.com"),
                json.readValue(fake.lastRequest().body(), Map.class));
    }

    @Test
    void deleteWithEmptyResponseReportsSuccess() {
        fake.respondWith(204, "");

        CallToolResult result = call("delete_person", Map.of("id", 3));

        assertFalse(result.isError());
        assertEquals("DELETE", fake.lastRequest().method());
        assertEquals("/api/v1/persons/3", fake.lastRequest().path());
        assertTrue(text(result).contains("\"status\": 204"), text(result));
    }

    @Test
    void httpErrorBecomesToolError() {
        fake.respondWith(404, "{\"detail\":\"Item 42 not found\"}");

        CallToolResult result = call("get_item", Map.of("id", 42));

        assertTrue(result.isError());
        assertEquals("HTTP 404 from GET /items/42: {\"detail\":\"Item 42 not found\"}", text(result));
    }

    @Test
    void missingOrNonNumericIdIsRejectedWithoutHttpCall() {
        CallToolResult missing = call("get_item", Map.of());
        CallToolResult text = call("delete_item", Map.of("id", "abc"));

        assertTrue(missing.isError());
        assertTrue(text.isError());
        assertEquals("Argument 'id' is required and must be an integer", text(missing));
        assertTrue(fake.requests().isEmpty());
    }

    @Test
    void fractionalIdIsRejectedInsteadOfTruncated() {
        CallToolResult result = call("delete_item", Map.of("id", 1.5));

        assertTrue(result.isError());
        assertEquals("Argument 'id' is required and must be an integer", text(result));
        assertTrue(fake.requests().isEmpty());
    }

    @Test
    void nullArgumentsAreTreatedAsEmpty() {
        fake.respondWith(200, "[]");

        CallToolResult result = call("list_persons", null);

        assertFalse(result.isError());
        assertNull(fake.lastRequest().query());
    }

    @Test
    void unreachableApiBecomesToolError() {
        fake.close();

        CallToolResult result = call("list_items", Map.of());

        assertTrue(result.isError());
        assertTrue(text(result).startsWith("Could not reach REST API at " + fake.baseUrl()), text(result));
    }

    // ---------------------------------------------------------------- helpers

    private SyncToolSpecification spec(String name) {
        return tools.specifications().stream()
                .filter(s -> s.tool().name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private Tool tool(String name) {
        return spec(name).tool();
    }

    /** Invokes a handler the way the MCP server would; the handlers don't use the exchange. */
    private CallToolResult call(String name, Map<String, Object> arguments) {
        return spec(name).callHandler().apply(null, new CallToolRequest(name, arguments));
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }
}
