package com.example.mcp.server;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;

/**
 * Entry point: an MCP server over stdio that exposes the api-server REST API as tools.
 *
 * <pre>
 *   MCP client --(JSON-RPC over stdin/stdout)--> this server --(HTTP)--> api-server :8080
 * </pre>
 *
 * <p>Configuration: the {@code API_BASE_URL} environment variable (default
 * {@code http://localhost:8080/api/v1}). stdout carries the MCP protocol, so nothing else may
 * print to it; logging goes to stderr.
 *
 * <p>Run with {@code java -jar mcp-server/target/mcp-server.jar}. Normally an MCP client
 * (mcp-client, Claude Code, Claude Desktop, ...) launches it rather than a person.
 */
public class RestApiMcpServer {

    static final String DEFAULT_BASE_URL = "http://localhost:8080/api/v1";

    public static void main(String[] args) {
        String baseUrl = System.getenv().getOrDefault("API_BASE_URL", DEFAULT_BASE_URL);
        McpJsonMapper json = McpJsonDefaults.getMapper();
        RestApiTools tools = new RestApiTools(new RestApiClient(baseUrl), json);

        McpSyncServer server = McpServer.sync(new StdioServerTransportProvider(json))
                .serverInfo("rest-api-mcp-server", "0.0.1")
                .instructions("Tools for the Item and Person REST API at " + baseUrl
                        + ". Results are the API's JSON responses.")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(tools.specifications())
                .build();

        // The stdio transport reads stdin on its own threads; stop cleanly when the client goes away.
        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));
    }
}
