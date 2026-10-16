package com.example.mcp.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP client for mcp-server. It launches the server jar as a subprocess, speaks MCP to it over
 * stdin/stdout, and turns tool results back into JSON data ({@code Map}/{@code List}).
 *
 * <pre>
 *   this client --(MCP over stdio)--> mcp-server --(HTTP)--> api-server
 * </pre>
 * The client never calls the REST API directly; everything goes through MCP tools.
 */
public class RestApiMcpClient implements AutoCloseable {

    /** Thrown when a tool call returns {@code isError: true}, e.g. a 404 or 400 from the REST API. */
    public static class ToolCallException extends RuntimeException {
        public ToolCallException(String tool, String message) {
            super(tool + " failed: " + message);
        }
    }

    private static final TypeRef<Object> ANY_JSON = new TypeRef<>() {
    };

    private final McpSyncClient client;
    private final McpJsonMapper json = McpJsonDefaults.getMapper();

    /**
     * Starts the server and performs the MCP initialize handshake.
     *
     * @param serverJar  path to mcp-server.jar
     * @param apiBaseUrl REST API root passed to the server as {@code API_BASE_URL}
     */
    public RestApiMcpClient(String serverJar, String apiBaseUrl) {
        // Launch the server with the same JVM that runs this client.
        this(ServerParameters.builder(ProcessHandle.current().info().command().orElse("java"))
                .args("-jar", serverJar)
                .addEnvVar("API_BASE_URL", apiBaseUrl)
                .build());
    }

    /**
     * Starts any stdio MCP server described by {@code params} and performs the initialize
     * handshake. Tests use this to launch a fake server.
     */
    public RestApiMcpClient(ServerParameters params) {
        StdioClientTransport transport = new StdioClientTransport(params, json);
        // The server's stderr (its logs) is not part of the protocol; surface it on ours.
        transport.setStdErrorHandler(line -> System.err.println("[mcp-server] " + line));

        this.client = McpClient.sync(transport)
                .clientInfo(new Implementation("rest-api-mcp-client", "0.0.1"))
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        client.initialize();
    }

    /** Name and version the server reported during initialize. */
    public Implementation serverInfo() {
        return client.getServerInfo();
    }

    /** Tools the server advertises. */
    public List<Tool> listTools() {
        return client.listTools().tools();
    }

    /**
     * Calls a tool and parses its text result as JSON.
     *
     * @return a {@code Map} for a single object, a {@code List} for a collection
     * @throws ToolCallException if the server reports an error
     */
    public Object call(String tool, Map<String, Object> arguments) {
        CallToolResult result = client.callTool(new CallToolRequest(tool, arguments));
        String text = result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(c -> ((TextContent) c).text())
                .collect(Collectors.joining("\n"));
        if (Boolean.TRUE.equals(result.isError())) {
            throw new ToolCallException(tool, text);
        }
        try {
            return json.readValue(text, ANY_JSON);
        } catch (IOException e) {
            return text; // Not JSON; hand back the raw text.
        }
    }

    // ---------------------------------------------------------------- typed convenience methods

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listItems(String nameContains) {
        return (List<Map<String, Object>>) call("list_items",
                nameContains == null ? Map.of() : Map.of("nameContains", nameContains));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getItem(long id) {
        return (Map<String, Object>) call("get_item", Map.of("id", id));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createItem(Map<String, Object> item) {
        return (Map<String, Object>) call("create_item", item);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listPersons() {
        return (List<Map<String, Object>>) call("list_persons", Map.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createPerson(Map<String, Object> person) {
        return (Map<String, Object>) call("create_person", person);
    }

    /** Serializes any JSON value to a JSON string. */
    public String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (IOException e) {
            return String.valueOf(value);
        }
    }

    /** Parses a JSON object given on the command line. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseArguments(String jsonObject) throws IOException {
        return json.readValue(jsonObject, Map.class);
    }

    /** Closes the MCP session, which also stops the server subprocess. */
    @Override
    public void close() {
        client.closeGracefully();
    }
}
