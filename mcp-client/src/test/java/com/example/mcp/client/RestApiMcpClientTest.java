package com.example.mcp.client;

import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the client against {@link FakeMcpServer}, launched over stdio as a subprocess exactly
 * as the real mcp-server would be.
 */
class RestApiMcpClientTest {

    private static RestApiMcpClient client;

    @BeforeAll
    static void connect() {
        String java = ProcessHandle.current().info().command().orElse("java");
        client = new RestApiMcpClient(ServerParameters.builder(java)
                .args("-cp", System.getProperty("java.class.path"), FakeMcpServer.class.getName())
                .build());
    }

    @AfterAll
    static void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void handshakeExposesServerInfo() {
        assertEquals("fake-mcp-server", client.serverInfo().name());
        assertEquals("9.9.9", client.serverInfo().version());
    }

    @Test
    void listsServerTools() {
        List<String> names = client.listTools().stream().map(Tool::name).toList();

        assertTrue(names.containsAll(List.of("list_items", "get_item", "create_item", "list_persons")), names.toString());
    }

    @Test
    void listItemsParsesJsonArray() {
        List<Map<String, Object>> items = client.listItems(null);

        assertEquals(2, items.size());
        assertEquals("Laptop", items.get(0).get("name"));
        assertEquals(29.99, ((Number) items.get(1).get("price")).doubleValue());
    }

    @Test
    void listItemsPassesNameFilter() {
        List<Map<String, Object>> items = client.listItems("LAP");

        assertEquals(List.of("Laptop"), items.stream().map(i -> i.get("name")).toList());
    }

    @Test
    void getItemParsesJsonObject() {
        Map<String, Object> item = client.getItem(7);

        assertEquals(7, ((Number) item.get("id")).intValue());
        assertEquals("Laptop", item.get("name"));
    }

    @Test
    void createSendsArgumentsAndReturnsCreatedObject() {
        Map<String, Object> newItem = new LinkedHashMap<>();
        newItem.put("name", "Desk");
        newItem.put("price", 250.0);

        Map<String, Object> created = client.createItem(newItem);

        assertEquals(1, ((Number) created.get("id")).intValue());
        assertEquals("Desk", created.get("name"));
        assertEquals(250.0, ((Number) created.get("price")).doubleValue());
    }

    @Test
    void createPersonAndListPersons() {
        Map<String, Object> created = client.createPerson(Map.of("name", "Grace", "email", "grace@example.com"));
        List<Map<String, Object>> persons = client.listPersons();

        assertEquals("grace@example.com", created.get("email"));
        assertEquals("Ada", persons.getFirst().get("name"));
    }

    @Test
    void toolErrorThrowsToolCallExceptionWithServerMessage() {
        RestApiMcpClient.ToolCallException e =
                assertThrows(RestApiMcpClient.ToolCallException.class, () -> client.getItem(404));

        assertEquals("get_item failed: HTTP 404 from GET /items/404: not found", e.getMessage());
    }

    @Test
    void nonJsonResultIsReturnedAsText() {
        Object result = client.call("plain_text", Map.of());

        assertEquals("not json at all", result);
    }

    @Test
    void unknownToolFails() {
        assertThrows(RuntimeException.class, () -> client.call("no_such_tool", Map.of()));
    }

    @Test
    void parseArgumentsAndToJsonRoundTrip() throws IOException {
        Map<String, Object> args = client.parseArguments("{\"id\":3,\"name\":\"x\"}");

        assertEquals(3, ((Number) args.get("id")).intValue());
        assertInstanceOf(String.class, client.toJson(args));
        assertEquals(args, client.parseArguments(client.toJson(args)));
    }
}
