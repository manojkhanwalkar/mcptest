package com.example.mcp.client;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Minimal stdio MCP server with canned tool results, started as a subprocess by
 * {@link RestApiMcpClientTest}. It lets the client be tested without mcp-server or a REST API.
 *
 * <ul>
 *   <li>{@code list_items}: two fixed items, filtered by {@code nameContains} if given</li>
 *   <li>{@code list_persons}: one fixed person</li>
 *   <li>{@code get_item}: echoes the id, or a tool error for id 404</li>
 *   <li>{@code create_item}, {@code create_person}: echo the arguments with {@code id: 1}</li>
 *   <li>{@code plain_text}: a result that is not JSON</li>
 * </ul>
 */
public class FakeMcpServer {

    private static final McpJsonMapper JSON = McpJsonDefaults.getMapper();

    public static void main(String[] args) {
        McpSyncServer server = McpServer.sync(new StdioServerTransportProvider(JSON))
                .serverInfo("fake-mcp-server", "9.9.9")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(
                        tool("list_items", a -> ok(listItems((String) a.get("nameContains")))),
                        tool("list_persons", a -> ok("[{\"id\":1,\"name\":\"Ada\",\"email\":\"ada@example.com\"}]")),
                        tool("get_item", a -> ((Number) a.get("id")).longValue() == 404
                                ? error("HTTP 404 from GET /items/404: not found")
                                : ok("{\"id\":" + a.get("id") + ",\"name\":\"Laptop\"}")),
                        tool("create_item", a -> ok(withId(a))),
                        tool("create_person", a -> ok(withId(a))),
                        tool("plain_text", a -> ok("not json at all")))
                .build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));
    }

    private static String listItems(String nameContains) {
        Map<String, String> items = new LinkedHashMap<>();
        items.put("Laptop", "{\"id\":1,\"name\":\"Laptop\",\"price\":1499.0}");
        items.put("Mouse", "{\"id\":2,\"name\":\"Mouse\",\"price\":29.99}");
        return items.entrySet().stream()
                .filter(e -> nameContains == null || e.getKey().toLowerCase().contains(nameContains.toLowerCase()))
                .map(Map.Entry::getValue)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static SyncToolSpecification tool(String name, Function<Map<String, Object>, CallToolResult> handler) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder().name(name).description("fake " + name)
                        .inputSchema(JSON, "{\"type\":\"object\"}").build())
                .callHandler((exchange, request) -> handler.apply(request.arguments()))
                .build();
    }

    private static String withId(Map<String, Object> args) {
        try {
            Map<String, Object> copy = new LinkedHashMap<>();
            copy.put("id", 1);
            copy.putAll(args);
            return JSON.writeValueAsString(copy);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CallToolResult ok(String text) {
        return CallToolResult.builder().addTextContent(text).isError(false).build();
    }

    private static CallToolResult error(String text) {
        return CallToolResult.builder().addTextContent(text).isError(true).build();
    }
}
